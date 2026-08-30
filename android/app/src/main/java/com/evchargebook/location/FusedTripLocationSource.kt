package com.evchargebook.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/** Production Trip source backed by Google Play services fused location. */
class FusedTripLocationSource(context: Context) : TripLocationSource {
    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var callback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    override fun start(callback: (Location) -> Unit) {
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

        this.callback?.let(client::removeLocationUpdates)
        this.callback = fusedCallback
        // The service starts this source from an IO coroutine; always provide a concrete Looper.
        client.requestLocationUpdates(request, fusedCallback, Looper.getMainLooper())
    }

    override fun stop() {
        callback?.let(client::removeLocationUpdates)
        callback = null
    }
}
