package com.evchargebook.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
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

    @SuppressLint("MissingPermission")
    override fun start(callback: (Location) -> Unit) {
        stop()
        running = true
        fallbackStarted.set(false)

        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) {
            startPlatformFallback(callback)
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
                armPrimarySilenceWatchdog(callback)
                locations.forEach(callback)
            }
        }

        this.callback = fusedCallback
        // The service starts this source from an IO coroutine; always provide a concrete Looper.
        client.requestLocationUpdates(request, fusedCallback, Looper.getMainLooper())
            .addOnSuccessListener {
                if (running && this.callback === fusedCallback && !fallbackStarted.get()) {
                    armPrimarySilenceWatchdog(callback)
                }
            }
            .addOnFailureListener {
                if (!running) return@addOnFailureListener
                startPlatformFallback(callback)
            }
    }

    @SuppressLint("MissingPermission")
    private fun startPlatformFallback(callback: (Location) -> Unit) {
        if (!running || !fallbackStarted.compareAndSet(false, true)) return
        cancelPrimarySilenceWatchdog()
        this.callback?.let(client::removeLocationUpdates)
        this.callback = null
        platformFallback.start(callback)
    }

    private fun armPrimarySilenceWatchdog(callback: (Location) -> Unit) {
        cancelPrimarySilenceWatchdog()
        if (!running || fallbackStarted.get()) return
        val watchdog = Runnable {
            if (running && !fallbackStarted.get()) {
                startPlatformFallback(callback)
            }
        }
        primarySilenceWatchdog = watchdog
        mainHandler.postDelayed(watchdog, PRIMARY_SILENCE_TIMEOUT_MS)
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
    }

    private companion object {
        // TripGpsHealth becomes DEGRADED after 15s; fail over before the user sees a dead source.
        const val PRIMARY_SILENCE_TIMEOUT_MS = 12_000L
    }
}
