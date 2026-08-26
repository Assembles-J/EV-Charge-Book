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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.evchargebook.MainActivity
import com.evchargebook.data.database.AppDatabase
import com.evchargebook.data.entity.TripPointEntity
import com.evchargebook.data.entity.TripStatus
import com.evchargebook.domain.TripRules
import com.evchargebook.domain.TripSamplingRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    private val locationListener = LocationListener { location ->
        val tripId = currentTripId ?: return@LocationListener
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
                if (tripId > 0L) beginTracking(tripId) else stopSelf()
            }
            else -> stopSelf()
        }
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { locationManager.removeUpdates(locationListener) }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun beginTracking(tripId: Long) {
        if (currentTripId == tripId) return
        currentTripId = tripId

        val foregroundStarted = runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            )
        }.isSuccess

        if (!foregroundStarted) {
            serviceScope.launch {
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
            lastPoint = tripDao.getPoints(tripId).lastOrNull()
            requestLocationUpdatesOrInterrupt(tripId)
        }
    }

    private fun requestLocationUpdatesOrInterrupt(tripId: Long) {
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val provider = when {
            fineGranted && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            coarseGranted && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }

        if (provider == null) {
            serviceScope.launch {
                markInterrupted(tripId)
                stopTrackingAndSelf()
            }
            return
        }

        try {
            locationManager.requestLocationUpdates(provider, SAMPLE_INTERVAL_MS, SAMPLE_DISTANCE_METERS, locationListener)
        } catch (_: SecurityException) {
            serviceScope.launch {
                markInterrupted(tripId)
                stopTrackingAndSelf()
            }
        }
    }

    private suspend fun handleLocation(tripId: Long, location: Location) {
        if (!isBasicLocationValid(location)) return
        val session = tripDao.getSession(tripId) ?: return
        if (session.status != TripStatus.RECORDING) return

        val capturedAt = location.time.takeIf { it > 0 } ?: System.currentTimeMillis()
        val previous = lastPoint
        if (previous != null && capturedAt <= previous.capturedAtEpochMillis) return

        val segmentDistance = previous?.let { previousPoint ->
            val result = FloatArray(1)
            Location.distanceBetween(previousPoint.latitude, previousPoint.longitude, location.latitude, location.longitude, result)
            result[0].toDouble().coerceAtLeast(0.0)
        } ?: 0.0
        val deltaSeconds = previous?.let { ((capturedAt - it.capturedAtEpochMillis) / 1000).coerceAtLeast(0) } ?: 0
        val speed = location.speed.takeIf { location.hasSpeed() }?.toDouble()
        val accuracy = location.accuracy.takeIf { location.hasAccuracy() }?.toDouble()
        val decision = TripSamplingRules.decide(deltaSeconds, segmentDistance, speed, accuracy)
        if (!decision.accept) return

        val newMovingSeconds = TripSamplingRules.movingSeconds(session.movingSeconds ?: 0, deltaSeconds, decision.moving)
        val newStoppedSeconds = TripSamplingRules.stoppedSeconds(session.stoppedSeconds ?: 0, deltaSeconds, decision.moving)
        val newDistance = session.distanceMeters + segmentDistance
        val altitude = location.altitude.takeIf { location.hasAltitude() }

        val point = TripPointEntity(
            tripId = tripId,
            capturedAtEpochMillis = capturedAt,
            latitude = location.latitude,
            longitude = location.longitude,
            altitudeMeters = altitude,
            speedMps = speed,
            bearingDegrees = location.bearing.takeIf { location.hasBearing() }?.toDouble(),
            horizontalAccuracyMeters = accuracy,
            verticalAccuracyMeters = if (Build.VERSION.SDK_INT >= 26 && location.hasVerticalAccuracy()) location.verticalAccuracyMeters.toDouble() else null,
            speedAccuracyMps = if (Build.VERSION.SDK_INT >= 26 && location.hasSpeedAccuracy()) location.speedAccuracyMetersPerSecond.toDouble() else null,
            provider = location.provider
        )
        val pointId = tripDao.insertPoint(point)
        lastPoint = point.copy(id = pointId)

        val elapsed = ((capturedAt - session.startedAtEpochMillis) / 1000).coerceAtLeast(0)
        tripDao.updateSession(
            session.copy(
                distanceMeters = newDistance,
                elapsedSeconds = elapsed,
                movingSeconds = newMovingSeconds,
                stoppedSeconds = newStoppedSeconds,
                averageSpeedMps = if (newMovingSeconds > 0) newDistance / newMovingSeconds else null,
                maxSpeedMps = listOfNotNull(session.maxSpeedMps, speed).maxOrNull(),
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

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setContentTitle("EV Charge Book 正在记录行程")
        .setContentText("定位仅用于本次主动开始的行程")
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .addAction(
            android.R.drawable.ic_media_pause,
            "结束行程",
            PendingIntent.getService(this, 1, Intent(this, TripTrackingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        )
        .build()

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "行程记录", NotificationManager.IMPORTANCE_LOW).apply { description = "用户主动开启行程后显示持续定位状态" })
    }

    companion object {
        private const val ACTION_START = "com.evchargebook.trip.START"
        private const val ACTION_STOP = "com.evchargebook.trip.STOP"
        private const val EXTRA_TRIP_ID = "trip_id"
        private const val CHANNEL_ID = "trip_tracking"
        private const val NOTIFICATION_ID = 2201
        private const val SAMPLE_INTERVAL_MS = 4_000L
        private const val SAMPLE_DISTANCE_METERS = 8f

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
