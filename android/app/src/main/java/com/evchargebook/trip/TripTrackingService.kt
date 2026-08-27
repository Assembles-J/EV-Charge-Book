package com.evchargebook.trip

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.evchargebook.MainActivity
import com.evchargebook.data.database.AppDatabase
import com.evchargebook.data.entity.TripDiagnosticEventEntity
import com.evchargebook.data.entity.TripDiagnosticEventType
import com.evchargebook.data.entity.TripPointEntity
import com.evchargebook.data.entity.TripStatus
import com.evchargebook.domain.TripContinuityRules
import com.evchargebook.domain.TripDiagnosticSamplingRules
import com.evchargebook.domain.TripGpsHealth
import com.evchargebook.domain.TripGpsHealthSnapshot
import com.evchargebook.domain.TripGpsHealthStatus
import com.evchargebook.domain.TripRules
import com.evchargebook.domain.TripSamplingRules
import com.evchargebook.domain.TripSpeedTrustRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import kotlin.math.min

class TripTrackingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pointMutex = Mutex()
    private lateinit var locationManager: LocationManager
    private val tripDao by lazy { AppDatabase.getInstance(applicationContext).tripDao() }
    private var currentTripId: Long? = null
    private var lastPoint: TripPointEntity? = null
    private var healthMonitorJob: Job? = null

    @Volatile private var trackingStartedAtEpochMillis: Long = 0L
    @Volatile private var lastCallbackAtEpochMillis: Long? = null
    @Volatile private var lastAcceptedPointAtEpochMillis: Long? = null
    @Volatile private var lastAcceptedProvider: String? = null
    @Volatile private var rejectedPointCount: Int = 0

    private val locationListener = LocationListener { location ->
        val tripId = currentTripId ?: return@LocationListener
        lastCallbackAtEpochMillis = System.currentTimeMillis()
        serviceScope.launch { pointMutex.withLock { handleLocation(tripId, location) } }
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopFromNotification()
            ACTION_START -> {
                val tripId = intent.getLongExtra(EXTRA_TRIP_ID, 0L)
                if (tripId > 0L) beginTracking(tripId, flags and START_FLAG_REDELIVERY != 0) else stopSelf()
            }
            else -> stopSelf()
        }
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        currentTripId?.let { tripId ->
            runCatching {
                AppDatabase.getInstance(applicationContext).openHelper.writableDatabase.execSQL(
                    "INSERT INTO trip_diagnostic_events (tripId, occurredAtEpochMillis, type, provider, detail) VALUES (?, ?, ?, NULL, ?)",
                    arrayOf(tripId, System.currentTimeMillis(), TripDiagnosticEventType.SERVICE_DESTROY, "service destroyed while trip active")
                )
            }
        }
        healthMonitorJob?.cancel()
        runCatching { locationManager.removeUpdates(locationListener) }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun beginTracking(tripId: Long, redelivered: Boolean) {
        if (currentTripId == tripId) return
        currentTripId = tripId
        trackingStartedAtEpochMillis = System.currentTimeMillis()
        lastCallbackAtEpochMillis = null
        lastAcceptedPointAtEpochMillis = null
        lastAcceptedProvider = null
        rejectedPointCount = 0

        val foregroundStarted = runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(currentHealthSnapshot()),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            )
        }.isSuccess

        if (!foregroundStarted) {
            serviceScope.launch {
                recordEvent(tripId, TripDiagnosticEventType.LOCATION_REGISTRATION_FAILED, detail = "foreground service start failed")
                markInterrupted(tripId)
                stopTrackingAndSelf()
            }
            return
        }

        serviceScope.launch {
            val session = tripDao.getSession(tripId)
            if (session == null || session.status != TripStatus.RECORDING) {
                stopTrackingAndSelf()
                return@launch
            }
            recordEvent(
                tripId,
                if (redelivered) TripDiagnosticEventType.SERVICE_REDELIVERED else TripDiagnosticEventType.SERVICE_START,
                detail = if (redelivered) "START_REDELIVER_INTENT redelivery" else "foreground tracking started"
            )
            lastPoint = tripDao.getPoints(tripId).lastOrNull()
            lastPoint?.let {
                lastAcceptedPointAtEpochMillis = it.capturedAtEpochMillis
                lastAcceptedProvider = it.provider
            }
            requestLocationUpdatesOrInterrupt(tripId)
            startHealthMonitor()
        }
    }

    private fun requestLocationUpdatesOrInterrupt(tripId: Long) {
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            serviceScope.launch {
                recordEvent(tripId, TripDiagnosticEventType.PERMISSION_MISSING, detail = "fine=false coarse=false")
                markInterrupted(tripId)
                stopTrackingAndSelf()
            }
            return
        }

        val gpsEnabled = runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
        val networkEnabled = runCatching { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)
        if (fineGranted && !gpsEnabled) serviceScope.launch {
            recordEvent(tripId, TripDiagnosticEventType.PROVIDER_DISABLED, provider = LocationManager.GPS_PROVIDER)
        }
        if (coarseGranted && !networkEnabled) serviceScope.launch {
            recordEvent(tripId, TripDiagnosticEventType.PROVIDER_DISABLED, provider = LocationManager.NETWORK_PROVIDER)
        }

        val providers = buildList {
            if (fineGranted && gpsEnabled) add(LocationManager.GPS_PROVIDER)
            if (coarseGranted && networkEnabled) add(LocationManager.NETWORK_PROVIDER)
        }.distinct()

        if (providers.isEmpty()) {
            serviceScope.launch {
                markInterrupted(tripId)
                stopTrackingAndSelf()
            }
            return
        }

        val registration = runCatching {
            providers.forEach { provider ->
                locationManager.requestLocationUpdates(provider, SAMPLE_INTERVAL_MS, SAMPLE_DISTANCE_METERS, locationListener, Looper.getMainLooper())
            }
        }
        registration.exceptionOrNull()?.let { error ->
            serviceScope.launch {
                recordEvent(
                    tripId,
                    TripDiagnosticEventType.LOCATION_REGISTRATION_FAILED,
                    detail = error::class.java.simpleName.take(MAX_DETAIL_LENGTH)
                )
                markInterrupted(tripId)
                stopTrackingAndSelf()
            }
        }
    }

    private fun startHealthMonitor() {
        healthMonitorJob?.cancel()
        healthMonitorJob = serviceScope.launch {
            while (isActive && currentTripId != null) {
                updateNotification()
                delay(HEALTH_REFRESH_INTERVAL_MS)
            }
        }
    }

    private suspend fun handleLocation(tripId: Long, location: Location) {
        if (!isBasicLocationValid(location)) {
            rejectLocation(tripId, location.provider, "basic_invalid")
            return
        }
        if (!isFreshLocation(location)) {
            rejectLocation(tripId, location.provider, "stale_callback")
            return
        }
        val session = tripDao.getSession(tripId) ?: return
        if (session.status != TripStatus.RECORDING) return

        val capturedAt = location.time.takeIf { it > 0 } ?: System.currentTimeMillis()
        val previous = lastPoint
        if (previous != null && capturedAt <= previous.capturedAtEpochMillis) {
            rejectLocation(tripId, location.provider, "non_monotonic_time")
            return
        }

        val rawDeltaSeconds = previous?.let { ((capturedAt - it.capturedAtEpochMillis) / 1000).coerceAtLeast(0) }
        val continuity = TripContinuityRules.decide(
            deltaSeconds = rawDeltaSeconds,
            previousProvider = previous?.provider,
            currentProvider = location.provider
        )
        if (!continuity.acceptPoint) {
            rejectLocation(tripId, location.provider, "continuity_rejected")
            return
        }

        val rawSegmentDistance = previous?.let { previousPoint ->
            val result = FloatArray(1)
            Location.distanceBetween(previousPoint.latitude, previousPoint.longitude, location.latitude, location.longitude, result)
            result[0].toDouble().coerceAtLeast(0.0)
        } ?: 0.0
        val trustedSegmentDistance = if (continuity.countDistance) rawSegmentDistance else 0.0
        val statsDeltaSeconds = if (continuity.countDuration) rawDeltaSeconds ?: 0 else 0
        val rawSpeed = location.speed.takeIf { location.hasSpeed() }?.toDouble()
        val aggregateSpeed = if (
            TripSpeedTrustRules.eligibleForAggregate(
                reportedSpeedMps = rawSpeed,
                deltaSeconds = statsDeltaSeconds,
                trustedDistanceMeters = trustedSegmentDistance,
                continuityAllowsSpeed = continuity.speedEligibleForAggregate
            )
        ) rawSpeed else null
        val accuracy = location.accuracy.takeIf { location.hasAccuracy() }?.toDouble()
        val decision = TripSamplingRules.decide(statsDeltaSeconds, trustedSegmentDistance, aggregateSpeed, accuracy)
        if (!decision.accept) {
            rejectLocation(tripId, location.provider, "sampling_rejected")
            return
        }

        val newMovingSeconds = TripSamplingRules.movingSeconds(session.movingSeconds ?: 0, statsDeltaSeconds, decision.moving)
        val newStoppedSeconds = TripSamplingRules.stoppedSeconds(session.stoppedSeconds ?: 0, statsDeltaSeconds, decision.moving)
        val newDistance = session.distanceMeters + trustedSegmentDistance
        val altitude = location.altitude.takeIf { location.hasAltitude() }

        val point = TripPointEntity(
            tripId = tripId,
            capturedAtEpochMillis = capturedAt,
            latitude = location.latitude,
            longitude = location.longitude,
            altitudeMeters = altitude,
            speedMps = rawSpeed,
            bearingDegrees = location.bearing.takeIf { location.hasBearing() }?.toDouble(),
            horizontalAccuracyMeters = accuracy,
            verticalAccuracyMeters = if (Build.VERSION.SDK_INT >= 26 && location.hasVerticalAccuracy()) location.verticalAccuracyMeters.toDouble() else null,
            speedAccuracyMps = if (Build.VERSION.SDK_INT >= 26 && location.hasSpeedAccuracy()) location.speedAccuracyMetersPerSecond.toDouble() else null,
            provider = location.provider
        )
        val pointId = tripDao.insertPoint(point)
        lastPoint = point.copy(id = pointId)
        lastAcceptedPointAtEpochMillis = System.currentTimeMillis()
        lastAcceptedProvider = location.provider

        val elapsed = ((capturedAt - session.startedAtEpochMillis) / 1000).coerceAtLeast(0)
        tripDao.updateSession(
            session.copy(
                distanceMeters = newDistance,
                elapsedSeconds = elapsed,
                movingSeconds = newMovingSeconds,
                stoppedSeconds = newStoppedSeconds,
                averageSpeedMps = if (newMovingSeconds > 0) newDistance / newMovingSeconds else null,
                maxSpeedMps = listOfNotNull(session.maxSpeedMps, aggregateSpeed).maxOrNull(),
                startLatitude = session.startLatitude ?: location.latitude,
                startLongitude = session.startLongitude ?: location.longitude,
                endLatitude = location.latitude,
                endLongitude = location.longitude,
                startAltitudeMeters = session.startAltitudeMeters ?: altitude,
                endAltitudeMeters = altitude ?: session.endAltitudeMeters,
                minAltitudeMeters = mergeMin(session.minAltitudeMeters, altitude),
                maxAltitudeMeters = mergeMax(session.maxAltitudeMeters, altitude)
            )
        )
        updateNotification()
    }

    private suspend fun rejectLocation(tripId: Long, provider: String?, reason: String) {
        rejectedPointCount += 1
        if (TripDiagnosticSamplingRules.shouldPersistRejectedPoint(rejectedPointCount)) {
            recordEvent(
                tripId,
                TripDiagnosticEventType.LOCATION_REJECTED,
                provider = provider,
                detail = "count=$rejectedPointCount reason=$reason"
            )
        }
        updateNotification()
    }

    private suspend fun recordEvent(tripId: Long, type: String, provider: String? = null, detail: String? = null) {
        runCatching {
            tripDao.insertDiagnosticEvent(
                TripDiagnosticEventEntity(
                    tripId = tripId,
                    occurredAtEpochMillis = System.currentTimeMillis(),
                    type = type,
                    provider = provider,
                    detail = detail?.take(MAX_DETAIL_LENGTH)
                )
            )
        }
    }

    private fun stopFromNotification() {
        serviceScope.launch {
            currentTripId?.let { completeTrip(it) }
            stopTrackingAndSelf()
        }
    }

    private suspend fun completeTrip(tripId: Long) {
        val session = tripDao.getSession(tripId) ?: return
        if (session.status !in setOf(TripStatus.RECORDING, TripStatus.INTERRUPTED)) return
        val endedAt = System.currentTimeMillis()
        tripDao.updateSession(
            session.copy(
                endedAtEpochMillis = endedAt,
                elapsedSeconds = TripRules.elapsedSeconds(session.startedAtEpochMillis, endedAt),
                status = TripStatus.COMPLETED
            )
        )
    }

    private suspend fun markInterrupted(tripId: Long) {
        val session = tripDao.getSession(tripId) ?: return
        if (session.status == TripStatus.RECORDING) tripDao.updateSession(session.copy(status = TripStatus.INTERRUPTED))
    }

    private fun stopTrackingAndSelf() {
        healthMonitorJob?.cancel()
        healthMonitorJob = null
        runCatching { locationManager.removeUpdates(locationListener) }
        currentTripId = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun isBasicLocationValid(location: Location): Boolean {
        if (!location.latitude.isFinite() || !location.longitude.isFinite()) return false
        if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return false
        return true
    }

    private fun isFreshLocation(location: Location): Boolean {
        val locationElapsedNanos = location.elapsedRealtimeNanos.takeIf { it > 0L } ?: return true
        val ageNanos = (SystemClock.elapsedRealtimeNanos() - locationElapsedNanos).coerceAtLeast(0L)
        return TripContinuityRules.isFreshLocation(ageNanos / 1_000_000L)
    }

    private fun currentHealthSnapshot(): TripGpsHealthSnapshot = TripGpsHealth.evaluate(
        nowEpochMillis = System.currentTimeMillis(),
        trackingStartedAtEpochMillis = trackingStartedAtEpochMillis.takeIf { it > 0L } ?: System.currentTimeMillis(),
        lastCallbackAtEpochMillis = lastCallbackAtEpochMillis,
        lastAcceptedPointAtEpochMillis = lastAcceptedPointAtEpochMillis
    )

    private fun updateNotification() {
        if (currentTripId == null) return
        val snapshot = currentHealthSnapshot()
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(snapshot)) }
    }

    private fun buildNotification(snapshot: TripGpsHealthSnapshot) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setContentTitle(notificationTitle(snapshot.status))
        .setContentText(notificationText(snapshot))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .addAction(
            android.R.drawable.ic_media_pause,
            "结束行程",
            PendingIntent.getService(this, 1, Intent(this, TripTrackingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        )
        .build()

    private fun notificationTitle(status: TripGpsHealthStatus): String = when (status) {
        TripGpsHealthStatus.WAITING, TripGpsHealthStatus.GOOD -> "EV Charge Book 正在记录行程"
        TripGpsHealthStatus.DEGRADED -> "EV Charge Book · GPS 更新变慢"
        TripGpsHealthStatus.LOST -> "EV Charge Book · GPS 暂时中断"
        TripGpsHealthStatus.LONG_GAP -> "EV Charge Book · GPS 长时间中断"
    }

    private fun notificationText(snapshot: TripGpsHealthSnapshot): String {
        val ageText = snapshot.secondsSinceLastAcceptedPoint?.let { "最近有效定位 ${formatAge(it)}前" } ?: snapshot.message
        val providerText = when (lastAcceptedProvider) {
            LocationManager.GPS_PROVIDER -> "GPS"
            LocationManager.NETWORK_PROVIDER -> "网络定位"
            null -> null
            else -> lastAcceptedProvider
        }
        return listOfNotNull(ageText, providerText, rejectedPointCount.takeIf { it > 0 }?.let { "已过滤 $it 点" }).joinToString(" · ")
    }

    private fun formatAge(seconds: Long): String = if (seconds < 60) "${seconds}秒" else "${seconds / 60}分${seconds % 60}秒"

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "行程记录", NotificationManager.IMPORTANCE_LOW).apply {
                description = "用户主动开启行程后显示持续定位与 GPS 健康状态"
            }
        )
    }

    companion object {
        private const val ACTION_START = "com.evchargebook.trip.START"
        private const val ACTION_STOP = "com.evchargebook.trip.STOP"
        private const val EXTRA_TRIP_ID = "trip_id"
        private const val CHANNEL_ID = "trip_tracking"
        private const val NOTIFICATION_ID = 2201
        private const val SAMPLE_INTERVAL_MS = 4_000L
        private const val SAMPLE_DISTANCE_METERS = 8f
        private const val HEALTH_REFRESH_INTERVAL_MS = 10_000L
        private const val MAX_DETAIL_LENGTH = 160

        fun start(context: Context, tripId: Long) {
            val intent = Intent(context, TripTrackingService::class.java).setAction(ACTION_START).putExtra(EXTRA_TRIP_ID, tripId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TripTrackingService::class.java))
        }

        private fun mergeMin(current: Double?, value: Double?): Double? = when {
            current == null -> value
            value == null -> current
            else -> min(current, value)
        }

        private fun mergeMax(current: Double?, value: Double?): Double? = when {
            current == null -> value
            value == null -> current
            else -> max(current, value)
        }
    }
}
