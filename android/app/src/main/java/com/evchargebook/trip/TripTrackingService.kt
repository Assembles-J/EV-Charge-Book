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
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.evchargebook.MainActivity
import com.evchargebook.data.database.AppDatabase
import com.evchargebook.data.entity.TripDiagnosticEventEntity
import com.evchargebook.data.entity.TripDiagnosticEventType
import com.evchargebook.data.entity.TripPointEntity
import com.evchargebook.data.entity.TripStatus
import com.evchargebook.domain.TripCaptureTimeRules
import com.evchargebook.domain.TripContinuityRules
import com.evchargebook.domain.TripDiagnosticSamplingRules
import com.evchargebook.domain.TripGpsHealth
import com.evchargebook.domain.TripGpsHealthSnapshot
import com.evchargebook.domain.TripGpsHealthStatus
import com.evchargebook.domain.TripNotificationProgress
import com.evchargebook.domain.TripRules
import com.evchargebook.domain.TripSamplingRules
import com.evchargebook.domain.TripServiceLifecycleRules
import com.evchargebook.domain.TripSpeedTrustRules
import com.evchargebook.domain.TripTrackingRepairReason
import com.evchargebook.domain.TripTrackingRepairRules
import com.evchargebook.location.FusedTripLocationSource
import com.evchargebook.location.TripLocationSource
import java.util.concurrent.atomic.AtomicBoolean
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
    private val repairInProgress = AtomicBoolean(false)
    private lateinit var locationManager: LocationManager
    private var locationSource: TripLocationSource? = null
    private val tripDao by lazy { AppDatabase.getInstance(applicationContext).tripDao() }
    private var currentTripId: Long? = null
    private var lastPoint: TripPointEntity? = null
    private var healthMonitorJob: Job? = null

    @Volatile private var trackingStartedAtEpochMillis: Long = 0L
    @Volatile private var trackingStartedAtElapsedRealtimeMillis: Long = 0L
    @Volatile private var tripStartedAtEpochMillis: Long = 0L
    @Volatile private var currentDistanceMeters: Double = 0.0
    @Volatile private var lastCallbackAtEpochMillis: Long? = null
    @Volatile private var lastCallbackAtElapsedRealtimeMillis: Long? = null
    @Volatile private var lastCallbackProvider: String? = null
    @Volatile private var lastAcceptedPointAtEpochMillis: Long? = null
    @Volatile private var lastAcceptedPointAtElapsedRealtimeMillis: Long? = null
    @Volatile private var lastAcceptedProvider: String? = null
    @Volatile private var rejectedPointCount: Int = 0
    @Volatile private var lastHealthStatus: TripGpsHealthStatus? = null

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> Unit
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
                val database = AppDatabase.getInstance(applicationContext).openHelper.writableDatabase
                val persistedStatus = database.query(
                    "SELECT status FROM trip_sessions WHERE id = ? LIMIT 1",
                    arrayOf<Any?>(tripId)
                ).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
                if (TripServiceLifecycleRules.shouldRecordUnexpectedDestroy(persistedStatus)) {
                    database.execSQL(
                        "INSERT INTO trip_diagnostic_events (tripId, occurredAtEpochMillis, type, provider, detail) VALUES (?, ?, ?, NULL, ?)",
                        arrayOf<Any?>(tripId, System.currentTimeMillis(), TripDiagnosticEventType.SERVICE_DESTROY, "service destroyed while trip active")
                    )
                }
            }
        }
        healthMonitorJob?.cancel()
        runCatching { locationSource?.stop() }
        locationSource = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun beginTracking(tripId: Long, redelivered: Boolean) {
        if (currentTripId == tripId) return
        currentTripId = tripId
        repairInProgress.set(false)
        getSystemService(NotificationManager::class.java).cancel(REPAIR_NOTIFICATION_ID)
        trackingStartedAtEpochMillis = System.currentTimeMillis()
        trackingStartedAtElapsedRealtimeMillis = SystemClock.elapsedRealtime()
        tripStartedAtEpochMillis = 0L
        currentDistanceMeters = 0.0
        lastCallbackAtEpochMillis = null
        lastCallbackAtElapsedRealtimeMillis = null
        lastCallbackProvider = null
        lastAcceptedPointAtEpochMillis = null
        lastAcceptedPointAtElapsedRealtimeMillis = null
        lastAcceptedProvider = null
        rejectedPointCount = 0
        lastHealthStatus = null

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
            tripStartedAtEpochMillis = session.startedAtEpochMillis
            currentDistanceMeters = session.distanceMeters
            recordEvent(
                tripId,
                if (redelivered) TripDiagnosticEventType.SERVICE_REDELIVERED else TripDiagnosticEventType.SERVICE_START,
                detail = if (redelivered) "START_REDELIVER_INTENT redelivery" else "foreground tracking started"
            )
            recordEvent(tripId, TripDiagnosticEventType.POWER_STATE, detail = powerStateDetail())
            lastPoint = tripDao.getLatestPoint(tripId)
            lastPoint?.let {
                lastAcceptedPointAtEpochMillis = it.capturedAtEpochMillis
                val nowElapsed = SystemClock.elapsedRealtime()
                lastAcceptedPointAtElapsedRealtimeMillis = it.capturedAtElapsedRealtimeNanos
                    ?.div(1_000_000L)
                    ?.takeIf { pointElapsed -> pointElapsed <= nowElapsed }
                lastAcceptedProvider = it.provider
            }
            updateNotification()
            requestLocationUpdatesOrInterrupt(tripId)
            startHealthMonitor()
        }
    }

    private fun requestLocationUpdatesOrInterrupt(tripId: Long) {
        val fineGranted = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseGranted = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

        if (!fineGranted && !coarseGranted) {
            serviceScope.launch { interruptForRepair(tripId, TripTrackingRepairReason.LOCATION_PERMISSION_MISSING) }
            return
        }

        val gpsEnabled = providerEnabled(LocationManager.GPS_PROVIDER)
        val networkEnabled = providerEnabled(LocationManager.NETWORK_PROVIDER)
        if (fineGranted && !gpsEnabled) serviceScope.launch {
            recordEvent(tripId, TripDiagnosticEventType.PROVIDER_DISABLED, provider = LocationManager.GPS_PROVIDER)
        }
        if (coarseGranted && !networkEnabled) serviceScope.launch {
            recordEvent(tripId, TripDiagnosticEventType.PROVIDER_DISABLED, provider = LocationManager.NETWORK_PROVIDER)
        }

        val hasUsableProvider = (fineGranted && gpsEnabled) || (coarseGranted && networkEnabled)
        if (!hasUsableProvider) {
            serviceScope.launch { interruptForRepair(tripId, TripTrackingRepairReason.LOCATION_PROVIDER_DISABLED) }
            return
        }

        val registration = runCatching {
            locationSource?.stop()
            FusedTripLocationSource(applicationContext).also { source ->
                locationSource = source
                source.start(
                    callback = callback@{ location ->
                        val activeTripId = currentTripId ?: return@callback
                        val callbackAtEpoch = System.currentTimeMillis()
                        val callbackAtElapsed = SystemClock.elapsedRealtime()
                        val previousCallbackElapsed = lastCallbackAtElapsedRealtimeMillis
                        val previousProvider = lastCallbackProvider
                        val provider = location.provider
                        val callbackGapMs = previousCallbackElapsed?.let { (callbackAtElapsed - it).coerceAtLeast(0L) }
                        lastCallbackAtEpochMillis = callbackAtEpoch
                        lastCallbackAtElapsedRealtimeMillis = callbackAtElapsed
                        lastCallbackProvider = provider
                        serviceScope.launch {
                            if (previousProvider == null || previousProvider != provider) {
                                recordEvent(
                                    activeTripId,
                                    TripDiagnosticEventType.LOCATION_SOURCE,
                                    provider = provider,
                                    detail = if (previousProvider == null) {
                                        "first_callback"
                                    } else {
                                        "provider_changed previous=$previousProvider current=$provider callbackGapMs=${callbackGapMs ?: 0L}"
                                    }
                                )
                            }
                            if (callbackGapMs != null && callbackGapMs >= CALLBACK_GAP_DIAGNOSTIC_MS) {
                                recordEvent(
                                    activeTripId,
                                    TripDiagnosticEventType.LOCATION_CALLBACK_GAP,
                                    provider = provider,
                                    detail = "callbackGapMs=$callbackGapMs captureEpochMillis=${location.time} captureElapsedRealtimeNanos=${location.elapsedRealtimeNanos}"
                                )
                                recordEvent(activeTripId, TripDiagnosticEventType.POWER_STATE, provider = provider, detail = powerStateDetail())
                            }
                            pointMutex.withLock { handleLocation(activeTripId, location) }
                        }
                    },
                    signalCallback = signal@{ signal ->
                        val activeTripId = currentTripId ?: return@signal
                        serviceScope.launch {
                            val type = if (signal.availability != null) {
                                TripDiagnosticEventType.LOCATION_AVAILABILITY
                            } else {
                                TripDiagnosticEventType.LOCATION_SOURCE
                            }
                            val detail = buildString {
                                signal.availability?.let { append("available=$it") }
                                signal.detail?.takeIf { it.isNotBlank() }?.let { extra ->
                                    if (isNotEmpty()) append(' ')
                                    append(extra)
                                }
                            }.ifBlank { null }
                            recordEvent(
                                activeTripId,
                                type,
                                provider = signal.source,
                                detail = detail,
                            )
                        }
                    },
                )
            }
        }
        registration.exceptionOrNull()?.let { error ->
            serviceScope.launch {
                recordEvent(
                    tripId,
                    TripDiagnosticEventType.LOCATION_REGISTRATION_FAILED,
                    detail = "fused:${error::class.java.simpleName}".take(MAX_DETAIL_LENGTH)
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
                val tripId = currentTripId ?: break
                val repairReason = currentRepairReason()
                if (repairReason != null) {
                    interruptForRepair(tripId, repairReason)
                    break
                }
                val snapshot = currentHealthSnapshot()
                if (snapshot.status != lastHealthStatus) {
                    val previous = lastHealthStatus
                    lastHealthStatus = snapshot.status
                    recordEvent(
                        tripId,
                        TripDiagnosticEventType.GPS_HEALTH_TRANSITION,
                        provider = lastCallbackProvider,
                        detail = "previous=${previous?.name ?: "none"} current=${snapshot.status.name} callbackAgeSeconds=${snapshot.secondsSinceLastCallback ?: -1} acceptedAgeSeconds=${snapshot.secondsSinceLastAcceptedPoint ?: -1}"
                    )
                    if (snapshot.status in setOf(TripGpsHealthStatus.DEGRADED, TripGpsHealthStatus.LOST, TripGpsHealthStatus.LONG_GAP)) {
                        recordEvent(tripId, TripDiagnosticEventType.POWER_STATE, provider = lastCallbackProvider, detail = powerStateDetail())
                    }
                }
                updateNotification()
                delay(HEALTH_REFRESH_INTERVAL_MS)
            }
        }
    }

    private fun currentRepairReason(): TripTrackingRepairReason? {
        val fineGranted = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseGranted = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        val hasLocationPermission = fineGranted || coarseGranted
        val hasUsableProvider =
            (fineGranted && providerEnabled(LocationManager.GPS_PROVIDER)) ||
                (coarseGranted && providerEnabled(LocationManager.NETWORK_PROVIDER))
        return TripTrackingRepairRules.evaluate(
            hasLocationPermission = hasLocationPermission,
            hasUsableLocationProvider = hasUsableProvider
        )
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun providerEnabled(provider: String): Boolean =
        runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)

    private fun powerStateDetail(): String =
        TripBackgroundExecutionDiagnostics.read(this).diagnosticDetail()

    private suspend fun interruptForRepair(tripId: Long, reason: TripTrackingRepairReason) {
        if (!repairInProgress.compareAndSet(false, true)) return
        when (reason) {
            TripTrackingRepairReason.LOCATION_PERMISSION_MISSING ->
                recordEvent(tripId, TripDiagnosticEventType.PERMISSION_MISSING, detail = "location permission missing while trip active")
            TripTrackingRepairReason.LOCATION_PROVIDER_DISABLED ->
                recordEvent(tripId, TripDiagnosticEventType.PROVIDER_DISABLED, detail = "no usable location provider while trip active")
        }
        markInterrupted(tripId)
        postRepairNotification(reason)
        stopTrackingAndSelf()
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

        val capturedAtEpoch = location.time.takeIf { it > 0L } ?: System.currentTimeMillis()
        val capturedAtElapsed = location.elapsedRealtimeNanos.takeIf { it > 0L }
        val previous = lastPoint
        val captureDelta = previous?.let {
            TripCaptureTimeRules.between(
                previousEpochMillis = it.capturedAtEpochMillis,
                previousElapsedRealtimeNanos = it.capturedAtElapsedRealtimeNanos,
                currentEpochMillis = capturedAtEpoch,
                currentElapsedRealtimeNanos = capturedAtElapsed,
            )
        }
        if (captureDelta != null && !captureDelta.accepted) {
            rejectLocation(tripId, location.provider, captureDelta.rejectReason ?: "capture_time_rejected")
            return
        }
        if (captureDelta?.requiresRebase == true) {
            recordEvent(
                tripId,
                TripDiagnosticEventType.CAPTURE_TIME_REBASE,
                provider = location.provider,
                detail = "previousEpochMillis=${previous?.capturedAtEpochMillis} previousElapsedRealtimeNanos=${previous?.capturedAtElapsedRealtimeNanos} currentEpochMillis=$capturedAtEpoch currentElapsedRealtimeNanos=$capturedAtElapsed"
            )
        }

        val rawDeltaSeconds = when {
            previous == null -> null
            captureDelta?.requiresRebase == true -> TripContinuityRules.LONG_GAP_SECONDS
            else -> captureDelta?.deltaSecondsOrNull()
        }
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
        val accuracy = location.accuracy.takeIf { location.hasAccuracy() }?.toDouble()
        val speedAccuracy = if (Build.VERSION.SDK_INT >= 26 && location.hasSpeedAccuracy()) {
            location.speedAccuracyMetersPerSecond.toDouble()
        } else {
            null
        }
        val aggregateSpeed = if (
            TripSpeedTrustRules.eligibleForAggregate(
                reportedSpeedMps = rawSpeed,
                deltaSeconds = statsDeltaSeconds,
                trustedDistanceMeters = trustedSegmentDistance,
                continuityAllowsSpeed = continuity.speedEligibleForAggregate
            )
        ) rawSpeed else null
        val maxSpeedCandidate = if (
            TripSpeedTrustRules.eligibleForMaxSpeed(
                reportedSpeedMps = rawSpeed,
                deltaSeconds = statsDeltaSeconds,
                trustedDistanceMeters = trustedSegmentDistance,
                continuityAllowsSpeed = continuity.speedEligibleForAggregate,
                provider = location.provider,
                horizontalAccuracyMeters = accuracy,
                speedAccuracyMps = speedAccuracy
            )
        ) rawSpeed else null
        val decision = TripSamplingRules.decide(statsDeltaSeconds, trustedSegmentDistance, aggregateSpeed, accuracy)
        if (!decision.accept) {
            rejectLocation(
                tripId,
                location.provider,
                "sampling_rejected:${decision.reason ?: "unknown"}"
            )
            return
        }

        val newMovingSeconds = TripSamplingRules.movingSeconds(session.movingSeconds ?: 0, statsDeltaSeconds, decision.moving)
        val newStoppedSeconds = TripSamplingRules.stoppedSeconds(session.stoppedSeconds ?: 0, statsDeltaSeconds, decision.moving)
        val newDistance = session.distanceMeters + if (decision.moving) trustedSegmentDistance else 0.0
        val altitude = location.altitude.takeIf { location.hasAltitude() }

        val point = TripPointEntity(
            tripId = tripId,
            capturedAtEpochMillis = capturedAtEpoch,
            capturedAtElapsedRealtimeNanos = capturedAtElapsed,
            latitude = location.latitude,
            longitude = location.longitude,
            altitudeMeters = altitude,
            speedMps = rawSpeed,
            bearingDegrees = location.bearing.takeIf { location.hasBearing() }?.toDouble(),
            horizontalAccuracyMeters = accuracy,
            verticalAccuracyMeters = if (Build.VERSION.SDK_INT >= 26 && location.hasVerticalAccuracy()) location.verticalAccuracyMeters.toDouble() else null,
            speedAccuracyMps = speedAccuracy,
            provider = location.provider
        )
        val pointId = tripDao.insertPoint(point)
        lastPoint = point.copy(id = pointId)
        lastAcceptedPointAtEpochMillis = System.currentTimeMillis()
        lastAcceptedPointAtElapsedRealtimeMillis = SystemClock.elapsedRealtime()
        lastAcceptedProvider = location.provider

        val wallClockElapsed = ((capturedAtEpoch - session.startedAtEpochMillis) / 1000).coerceAtLeast(0)
        val elapsed = maxOf(session.elapsedSeconds, wallClockElapsed)
        tripDao.updateSession(
            session.copy(
                distanceMeters = newDistance,
                elapsedSeconds = elapsed,
                movingSeconds = newMovingSeconds,
                stoppedSeconds = newStoppedSeconds,
                averageSpeedMps = if (newMovingSeconds > 0) newDistance / newMovingSeconds else null,
                maxSpeedMps = listOfNotNull(session.maxSpeedMps, maxSpeedCandidate).maxOrNull(),
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
        currentDistanceMeters = newDistance
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
                elapsedSeconds = maxOf(session.elapsedSeconds, TripRules.elapsedSeconds(session.startedAtEpochMillis, endedAt)),
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
        runCatching { locationSource?.stop() }
        locationSource = null
        currentTripId = null
        tripStartedAtEpochMillis = 0L
        currentDistanceMeters = 0.0
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

    private fun currentHealthSnapshot(): TripGpsHealthSnapshot {
        val nowElapsed = SystemClock.elapsedRealtime()
        return TripGpsHealth.evaluateMonotonic(
            nowElapsedRealtimeMillis = nowElapsed,
            trackingStartedAtElapsedRealtimeMillis = trackingStartedAtElapsedRealtimeMillis.takeIf { it > 0L } ?: nowElapsed,
            lastCallbackAtElapsedRealtimeMillis = lastCallbackAtElapsedRealtimeMillis,
            lastAcceptedPointAtElapsedRealtimeMillis = lastAcceptedPointAtElapsedRealtimeMillis,
        )
    }

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
        .setContentIntent(openActiveTripPendingIntent(0))
        .addAction(
            android.R.drawable.ic_menu_view,
            "打开行程",
            openActiveTripPendingIntent(1)
        )
        .build()

    private fun postRepairNotification(reason: TripTrackingRepairReason) {
        val title: String
        val text: String
        val actionLabel: String
        when (reason) {
            TripTrackingRepairReason.LOCATION_PERMISSION_MISSING -> {
                title = "行程已中断 · 缺少定位权限"
                text = "恢复定位权限后回到行程，确认继续记录。"
                actionLabel = "权限设置"
            }
            TripTrackingRepairReason.LOCATION_PROVIDER_DISABLED -> {
                title = "行程已中断 · 系统定位已关闭"
                text = "开启系统定位后回到行程，确认继续记录。"
                actionLabel = "开启定位"
            }
        }
        val notification = NotificationCompat.Builder(this, WARNING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openActiveTripPendingIntent(20))
            .addAction(android.R.drawable.ic_menu_manage, actionLabel, repairSettingsPendingIntent(reason))
            .addAction(android.R.drawable.ic_menu_view, "打开行程", openActiveTripPendingIntent(21))
            .build()
        runCatching {
            getSystemService(NotificationManager::class.java).notify(REPAIR_NOTIFICATION_ID, notification)
        }
    }

    private fun repairSettingsPendingIntent(reason: TripTrackingRepairReason): PendingIntent {
        val intent = when (reason) {
            TripTrackingRepairReason.LOCATION_PERMISSION_MISSING ->
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            TripTrackingRepairReason.LOCATION_PROVIDER_DISABLED ->
                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            this,
            30 + reason.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openActiveTripPendingIntent(requestCode: Int): PendingIntent = PendingIntent.getActivity(
        this,
        requestCode,
        Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_ACTIVE_TRIP, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun notificationTitle(status: TripGpsHealthStatus): String = when (status) {
        TripGpsHealthStatus.WAITING, TripGpsHealthStatus.GOOD -> "EV Charge Book 正在记录行程"
        TripGpsHealthStatus.DEGRADED -> "EV Charge Book · GPS 更新变慢"
        TripGpsHealthStatus.LOST -> "EV Charge Book · GPS 暂时中断"
        TripGpsHealthStatus.LONG_GAP -> "EV Charge Book · GPS 长时间中断"
    }

    private fun notificationText(snapshot: TripGpsHealthSnapshot): String {
        val progressText = tripStartedAtEpochMillis.takeIf { it > 0L }?.let { startedAt ->
            val elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1000).coerceAtLeast(0L)
            TripNotificationProgress.format(elapsedSeconds, currentDistanceMeters)
        }
        val ageText = snapshot.secondsSinceLastAcceptedPoint?.let { "最近有效定位 ${formatAge(it)}前" } ?: snapshot.message
        val providerText = when (lastAcceptedProvider) {
            LocationManager.GPS_PROVIDER -> "GPS"
            LocationManager.NETWORK_PROVIDER -> "网络定位"
            "fused" -> "融合定位"
            null -> null
            else -> lastAcceptedProvider
        }
        return listOfNotNull(
            progressText,
            ageText,
            providerText,
            rejectedPointCount.takeIf { it > 0 }?.let { "已过滤 $it 点" }
        ).joinToString(" · ")
    }

    private fun formatAge(seconds: Long): String = if (seconds < 60) "${seconds}秒" else "${seconds / 60}分${seconds % 60}秒"

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "行程记录", NotificationManager.IMPORTANCE_LOW).apply {
                description = "用户主动开启行程后显示持续定位与 GPS 健康状态"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(WARNING_CHANNEL_ID, "行程异常", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "行程因定位权限或系统定位状态中断时提供一次性修复入口"
            }
        )
    }

    companion object {
        private const val ACTION_START = "com.evchargebook.trip.START"
        private const val ACTION_STOP = "com.evchargebook.trip.STOP"
        private const val EXTRA_TRIP_ID = "trip_id"
        private const val CHANNEL_ID = "trip_tracking"
        private const val WARNING_CHANNEL_ID = "trip_warnings"
        private const val NOTIFICATION_ID = 2201
        private const val REPAIR_NOTIFICATION_ID = 2202
        private const val HEALTH_REFRESH_INTERVAL_MS = 10_000L
        private const val CALLBACK_GAP_DIAGNOSTIC_MS = 5_000L
        private const val MAX_DETAIL_LENGTH = 320

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
