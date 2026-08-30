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
            .setMinUpdateIntervalMillis(2_000L)
            .setMinUpdateDistanceMeters(0f)
            // Ask Play services to deliver fixes immediately instead of intentionally batching them.
            // Android/OEM may still delay delivery while screen-off; when that happens we preserve
            // each Location's original capture timestamp and let Trip continuity rules decide truth.
            .setMaxUpdateDelayMillis(0L)
            .build()

        val locationCallbackImpl = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach(locationCallback)
            }
        }

        callback?.let(client::removeLocationUpdates)
        callback = locationCallbackImpl
        // TripTrackingService starts the source from an IO coroutine. Always supply a real Looper;
        // passing null from a non-Looper worker thread can fail registration on device.
        client.requestLocationUpdates(request, locationCallbackImpl, Looper.getMainLooper())
    }

    override fun stop() {
        callback?.let(client::removeLocationUpdates)
        callback = null
    }
}
