package com.evchargebook.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
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
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var running = false
    private var callback: LocationCallback? = null
    private var primarySilenceWatchdog: Runnable? = null
    private var lastReportedAvailability: Boolean? = null

    @SuppressLint("MissingPermission")
    override fun start(
        callback: (Location) -> Unit,
        signalCallback: (TripLocationSourceSignal) -> Unit,
    ) {
        stop()
        running = true
        fallbackStarted.set(false)
        lastReportedAvailability = null

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
                if (!running || fallbackStarted.get()) return
                val locations = result.locations
                if (locations.isEmpty()) return
                armPrimarySilenceWatchdog(callback, signalCallback)
                locations.forEach(callback)
            }

            override fun onLocationAvailability(locationAvailability: LocationAvailability) {
                if (!running || fallbackStarted.get()) return
                reportAvailability(locationAvailability.isLocationAvailable, signalCallback)
            }
        }

        this.callback = fusedCallback
        // The service starts this source from an IO coroutine; always provide a concrete Looper.
        client.requestLocationUpdates(request, fusedCallback, Looper.getMainLooper())
            .addOnSuccessListener {
                if (running && this.callback === fusedCallback && !fallbackStarted.get()) {
                    signalCallback(
                        TripLocationSourceSignal(
                            source = SOURCE_FUSED,
                            detail = "registered high_accuracy intervalMs=1000",
                        )
                    )
                    armPrimarySilenceWatchdog(callback, signalCallback)
                }
            }
            .addOnFailureListener { error ->
                if (!running) return@addOnFailureListener
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
        signalCallback(
            TripLocationSourceSignal(
                source = SOURCE_PLATFORM,
                detail = "fallback_started reason=$reason",
            )
        )
        platformFallback.start(callback, signalCallback)
    }

    private fun armPrimarySilenceWatchdog(
        callback: (Location) -> Unit,
        signalCallback: (TripLocationSourceSignal) -> Unit,
    ) {
        cancelPrimarySilenceWatchdog()
        if (!running || fallbackStarted.get()) return
        val watchdog = Runnable {
            if (running && !fallbackStarted.get()) {
                startPlatformFallback(
                    callback = callback,
                    signalCallback = signalCallback,
                    reason = "fused_silent_${PRIMARY_SILENCE_TIMEOUT_MS}ms",
                )
            }
        }
        primarySilenceWatchdog = watchdog
        mainHandler.postDelayed(watchdog, PRIMARY_SILENCE_TIMEOUT_MS)
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
        primarySilenceWatchdog?.let(mainHandler::removeCallbacks)
        primarySilenceWatchdog = null
    }

    override fun stop() {
        running = false
        cancelPrimarySilenceWatchdog()
        callback?.let(client::removeLocationUpdates)
        callback = null
        platformFallback.stop()
        fallbackStarted.set(false)
        lastReportedAvailability = null
    }

    private companion object {
        const val SOURCE_FUSED = "fused"
        const val SOURCE_PLATFORM = "platform"

        // TripGpsHealth becomes DEGRADED after 15s; fail over before the user sees a dead source.
        const val PRIMARY_SILENCE_TIMEOUT_MS = 12_000L
    }
}
