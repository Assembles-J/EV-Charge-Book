package com.evchargebook.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
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
 * Non-GMS devices transparently fall back to Android's platform fused/GPS/network source so
 * starting a Trip never depends on Google Play services being installed.
 */
class FusedTripLocationSource(private val context: Context) : TripLocationSource {
    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val platformFallback = PlatformTripLocationSource(context)
    private val fallbackStarted = AtomicBoolean(false)

    @Volatile private var running = false
    private var callback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    override fun start(callback: (Location) -> Unit) {
        stop()
        running = true
        fallbackStarted.set(false)

        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) {
            startPlatformFallback(callback)
            return
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4_000L)
            .setMinUpdateIntervalMillis(2_000L)
            .setMinUpdateDistanceMeters(0f)
            .setMaxUpdateDelayMillis(0L)
            .build()

        val fusedCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach(callback)
            }
        }

        this.callback = fusedCallback
        // The service starts this source from an IO coroutine; always provide a concrete Looper.
        client.requestLocationUpdates(request, fusedCallback, Looper.getMainLooper())
            .addOnFailureListener {
                if (!running) return@addOnFailureListener
                this.callback?.let(client::removeLocationUpdates)
                this.callback = null
                startPlatformFallback(callback)
            }
    }

    @SuppressLint("MissingPermission")
    private fun startPlatformFallback(callback: (Location) -> Unit) {
        if (!running || !fallbackStarted.compareAndSet(false, true)) return
        platformFallback.start(callback)
    }

    override fun stop() {
        running = false
        callback?.let(client::removeLocationUpdates)
        callback = null
        platformFallback.stop()
        fallbackStarted.set(false)
    }
}
