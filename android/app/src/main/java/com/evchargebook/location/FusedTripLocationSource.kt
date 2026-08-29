package com.evchargebook.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.location.LocationServices

/**
 * Production location source backed by Google's fused provider.
 *
 * Kept behind TripLocationSource so Trip business logic does not depend on the
 * concrete Android location API.
 */
class FusedTripLocationSource(
    context: Context
) : TripLocationSource {

    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var callback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    override fun start(locationCallback: (Location) -> Unit) {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            4_000L
        )
            .setMinUpdateDistanceMeters(0f)
            .build()

        val locationCallbackImpl = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach(locationCallback)
            }
        }

        callback = locationCallbackImpl
        client.requestLocationUpdates(request, locationCallbackImpl, null)
    }

    override fun stop() {
        callback?.let(client::removeLocationUpdates)
        callback = null
    }
}
