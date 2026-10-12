package com.evchargebook.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.evchargebook.domain.TripSourceWatchdogPolicy
import com.evchargebook.domain.TripPlatformRecoveryPolicy
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Production Trip source backed by Google Play services fused location when available.
 *
 * A successful registration is not treated as proof that the provider is healthy: some devices
 * can accept a request but never deliver a fix. If the primary source stays silent, switch to the
 * framework GPS/network fallback before the Trip health UI reaches its degraded boundary.
 *
 * LocationAvailability is emitted as diagnostics only. It is explicitly not a Trip continuity or
 * interruption authority because Google documents it as a best-effort availability estimate.
 */
class FusedTripLocationSource(private val context: Context) : TripLocationSource {
    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val platformFallback = PlatformTripLocationSource(context)
    private val fallbackStarted = AtomicBoolean(false)
    private val locationThreadOwner = RestartableResourceOwner(
        create = { HandlerThread("evcb-trip-location") },
        start = { it.start() },
        stop = { it.quitSafely() },
    )

    @Volatile private var running = false
    private var callback: LocationCallback? = null
    private var locationHandler: Handler? = null
    private var primarySilenceWatchdog: Runnable? = null
    private var platformSilenceWatchdog: Runnable? = null
    private var platformRecoveryAttempts: Int = 0
    private var platformMonitoredProvider: String? = null
    private var lastReportedAvailability: Boolean? = null
    @Volatile private var lastFreshFusedFixElapsedRealtimeNanos: Long? = null
    @Volatile private var lastFreshPlatformFixElapsedRealtimeNanos: Long? = null

    @SuppressLint("MissingPermission")
    override fun start(
        callback: (Location) -> Unit,
        signalCallback: (TripLocationSourceSignal) -> Unit,
    ) {
        stop()
        val locationThread = locationThreadOwner.acquire()
        locationHandler = Handler(locationThread.looper)
        running = true
        fallbackStarted.set(false)
        platformRecoveryAttempts = 0
        platformMonitoredProvider = null
        lastReportedAvailability = null
        lastFreshFusedFixElapsedRealtimeNanos = null
        lastFreshPlatformFixElapsedRealtimeNanos = null

        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) {
            startPlatformFallback(
                callback = callback,
                signalCallback = signalCallback,
                reason = "google_play_services_unavailable",
            )
            return
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
            .setMinUpdateIntervalMillis(1_000L)
            .setMinUpdateDistanceMeters(0f)
            .setMaxUpdateDelayMillis(0L)
            .build()

        val fusedCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (!running || fallbackStarted.get() ||
                    this@FusedTripLocationSource.callback !== this
                ) return
                val locations = result.locations
                if (locations.isEmpty()) return

                // Callback delivery is not itself a fresh fix: OEMs can repeat a cached position
                // or release an old batch after sleep. Only new, recently captured monotonic fixes
                // can postpone the 12s provider failover. Forward every callback to the Trip
                // pipeline unchanged, so sampling/continuity still own persisted truth.
                val nowElapsedNanos = SystemClock.elapsedRealtimeNanos()
                val newestFreshFix = locations.asSequence()
                    .map { it.elapsedRealtimeNanos }
                    .filter { fixElapsedNanos ->
                        TripSourceWatchdogPolicy.refreshesWatchdog(
                            previousFreshFixElapsedRealtimeNanos = lastFreshFusedFixElapsedRealtimeNanos,
                            fixElapsedRealtimeNanos = fixElapsedNanos,
                            nowElapsedRealtimeNanos = nowElapsedNanos,
                        )
                    }
                    .maxOrNull()
                if (newestFreshFix != null) {
                    lastFreshFusedFixElapsedRealtimeNanos = newestFreshFix
                    armPrimarySilenceWatchdog(callback, signalCallback)
                }
                locations.forEach(callback)
            }

            override fun onLocationAvailability(locationAvailability: LocationAvailability) {
                if (!running || fallbackStarted.get() ||
                    this@FusedTripLocationSource.callback !== this
                ) return
                reportAvailability(locationAvailability.isLocationAvailable, signalCallback)
            }
        }

        this.callback = fusedCallback
        client.requestLocationUpdates(request, fusedCallback, locationThread.looper)
            .addOnSuccessListener {
                if (running && this.callback === fusedCallback && !fallbackStarted.get()) {
                    signalCallback(
                        TripLocationSourceSignal(
                            source = SOURCE_FUSED,
                            detail = "registered high_accuracy intervalMs=1000 callbackLooper=trip_location",
                        )
                    )
                    armPrimarySilenceWatchdog(callback, signalCallback)
                }
            }
            .addOnFailureListener { error ->
                if (!running || this.callback !== fusedCallback || fallbackStarted.get()) return@addOnFailureListener
                startPlatformFallback(
                    callback = callback,
                    signalCallback = signalCallback,
                    reason = "fused_registration_failed:${error::class.java.simpleName}",
                )
            }
    }

    @SuppressLint("MissingPermission")
    private fun startPlatformFallback(
        callback: (Location) -> Unit,
        signalCallback: (TripLocationSourceSignal) -> Unit,
        reason: String,
    ) {
        if (!running || !fallbackStarted.compareAndSet(false, true)) return
        cancelPrimarySilenceWatchdog()
        this.callback?.let(client::removeLocationUpdates)
        this.callback = null
        lastReportedAvailability = null
        lastFreshPlatformFixElapsedRealtimeNanos = null
        signalCallback(
            TripLocationSourceSignal(
                source = SOURCE_PLATFORM,
                detail = "fallback_started reason=$reason",
            )
        )
        // This can run on the dedicated watchdog HandlerThread. Never let a temporary GPS /
        // network registration failure escape into the Looper and crash the process.
        runCatching { registerPlatformFallback(callback, signalCallback) }
            .onFailure { error ->
                signalCallback(
                    TripLocationSourceSignal(
                        source = SOURCE_PLATFORM,
                        detail = "fallback_registration_failed reason=$reason error=${error::class.java.simpleName}",
                    )
                )
                // Even with no registered provider, allow only the existing bounded 30s/60s
                // retries. The service independently handles a persistent disabled provider.
                armPlatformSilenceWatchdog(callback, signalCallback)
            }
    }

    @SuppressLint("MissingPermission")
    private fun registerPlatformFallback(
        callback: (Location) -> Unit,
        signalCallback: (TripLocationSourceSignal) -> Unit,
    ) {
        cancelPlatformSilenceWatchdog()
        platformMonitoredProvider = null
        platformFallback.stop()
        platformFallback.start(
            callback = { location ->
                if (!running || !fallbackStarted.get()) return@start
                if (TripPlatformRecoveryPolicy.callbackRefreshesWatchdog(
                        monitoredProvider = platformMonitoredProvider,
                        callbackProvider = location.provider,
                    )
                ) {
                    val fixElapsedNanos = location.elapsedRealtimeNanos
                    if (TripSourceWatchdogPolicy.refreshesWatchdog(
                            previousFreshFixElapsedRealtimeNanos = lastFreshPlatformFixElapsedRealtimeNanos,
                            fixElapsedRealtimeNanos = fixElapsedNanos,
                            nowElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
                        )
                    ) {
                        // A repeated GPS fix must not repeatedly postpone bounded recovery.
                        lastFreshPlatformFixElapsedRealtimeNanos = fixElapsedNanos
                        armPlatformSilenceWatchdog(callback, signalCallback)
                    }
                }
                callback(location)
            },
            signalCallback = signalCallback,
        )
        platformMonitoredProvider =
            TripPlatformRecoveryPolicy.monitoredProvider(platformFallback.registeredProviders())
        signalCallback(
            TripLocationSourceSignal(
                source = SOURCE_PLATFORM,
                detail = "recovery_watchdog provider=${platformMonitoredProvider ?: "none"} completedAttempts=$platformRecoveryAttempts",
            )
        )
        armPlatformSilenceWatchdog(callback, signalCallback)
    }

    private fun armPrimarySilenceWatchdog(
        callback: (Location) -> Unit,
        signalCallback: (TripLocationSourceSignal) -> Unit,
    ) {
        cancelPrimarySilenceWatchdog()
        if (!running || fallbackStarted.get()) return
        val handler = locationHandler ?: return
        val scheduledAtElapsedMs = SystemClock.elapsedRealtime()
        val watchdog = Runnable {
            if (running && !fallbackStarted.get()) {
                val actualDelayMs = SystemClock.elapsedRealtime() - scheduledAtElapsedMs
                signalCallback(
                    TripLocationSourceSignal(
                        source = SOURCE_FUSED,
                        detail = "silence_watchdog_fired expectedDelayMs=$PRIMARY_SILENCE_TIMEOUT_MS actualDelayMs=$actualDelayMs lateByMs=${(actualDelayMs - PRIMARY_SILENCE_TIMEOUT_MS).coerceAtLeast(0L)}",
                    )
                )
                startPlatformFallback(
                    callback = callback,
                    signalCallback = signalCallback,
                    reason = "fused_silent_${PRIMARY_SILENCE_TIMEOUT_MS}ms",
                )
            }
        }
        primarySilenceWatchdog = watchdog
        handler.postDelayed(watchdog, PRIMARY_SILENCE_TIMEOUT_MS)
    }

    private fun armPlatformSilenceWatchdog(
        callback: (Location) -> Unit,
        signalCallback: (TripLocationSourceSignal) -> Unit,
    ) {
        cancelPlatformSilenceWatchdog()
        if (!running || !fallbackStarted.get()) return
        val handler = locationHandler ?: return
        // A failed initial registration has no monitored provider yet. It still gets the
        // bounded retry budget; a successful registration switches to GPS/network authority.
        val monitoredProvider = platformMonitoredProvider ?: "none"
        val delayMillis =
            TripPlatformRecoveryPolicy.recoveryDelayMillis(platformRecoveryAttempts) ?: return
        val scheduledAtElapsedMs = SystemClock.elapsedRealtime()
        val watchdog = Runnable {
            if (!running || !fallbackStarted.get()) return@Runnable
            val actualDelayMs = SystemClock.elapsedRealtime() - scheduledAtElapsedMs
            val attempt = platformRecoveryAttempts + 1
            platformRecoveryAttempts = attempt
            signalCallback(
                TripLocationSourceSignal(
                    source = SOURCE_PLATFORM,
                    detail = "recovery_reregister attempt=$attempt provider=$monitoredProvider silenceMs=$delayMillis actualDelayMs=$actualDelayMs lateByMs=${(actualDelayMs - delayMillis).coerceAtLeast(0L)}",
                )
            )
            runCatching {
                registerPlatformFallback(callback, signalCallback)
            }.onFailure { error ->
                signalCallback(
                    TripLocationSourceSignal(
                        source = SOURCE_PLATFORM,
                        detail = "recovery_reregister_failed attempt=$attempt provider=$monitoredProvider error=${error::class.java.simpleName}",
                    )
                )
                armPlatformSilenceWatchdog(callback, signalCallback)
            }
        }
        platformSilenceWatchdog = watchdog
        handler.postDelayed(watchdog, delayMillis)
    }

    private fun reportAvailability(
        available: Boolean,
        signalCallback: (TripLocationSourceSignal) -> Unit,
    ) {
        if (lastReportedAvailability == available) return
        lastReportedAvailability = available
        signalCallback(
            TripLocationSourceSignal(
                source = SOURCE_FUSED,
                availability = available,
                detail = "google_location_availability",
            )
        )
    }

    private fun cancelPrimarySilenceWatchdog() {
        val handler = locationHandler
        if (handler != null) primarySilenceWatchdog?.let(handler::removeCallbacks)
        primarySilenceWatchdog = null
    }

    private fun cancelPlatformSilenceWatchdog() {
        val handler = locationHandler
        if (handler != null) platformSilenceWatchdog?.let(handler::removeCallbacks)
        platformSilenceWatchdog = null
    }

    override fun stop() {
        running = false
        cancelPrimarySilenceWatchdog()
        cancelPlatformSilenceWatchdog()
        callback?.let(client::removeLocationUpdates)
        callback = null
        platformFallback.stop()
        locationHandler = null
        locationThreadOwner.release()
        fallbackStarted.set(false)
        platformRecoveryAttempts = 0
        platformMonitoredProvider = null
        lastReportedAvailability = null
        lastFreshFusedFixElapsedRealtimeNanos = null
        lastFreshPlatformFixElapsedRealtimeNanos = null
    }

    private companion object {
        const val SOURCE_FUSED = "fused"
        const val SOURCE_PLATFORM = "platform"

        // TripGpsHealth becomes DEGRADED after 15s; fail over before the user sees a dead source.
        const val PRIMARY_SILENCE_TIMEOUT_MS = 12_000L
    }
}
