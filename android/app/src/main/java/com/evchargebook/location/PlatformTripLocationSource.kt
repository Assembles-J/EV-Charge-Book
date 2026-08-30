package com.evchargebook.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper

/**
 * Framework-only fallback for devices where Google Play services location is unavailable.
 *
 * Prefer the platform fused provider when the device exposes it; otherwise fall back to the
 * same GPS/network providers the app historically used. This keeps Trip recording functional
 * on non-GMS Android devices without changing Trip continuity or persistence rules.
 */
class PlatformTripLocationSource(context: Context) : TripLocationSource {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var listener: LocationListener? = null

    @SuppressLint("MissingPermission")
    override fun start(callback: (Location) -> Unit) {
        stop()
        val newListener = LocationListener(callback)
        listener = newListener

        val providers = locationManager.allProviders
        val fusedProvider = PLATFORM_FUSED_PROVIDER.takeIf { it in providers }
        if (fusedProvider != null) {
            val fusedRegistration = runCatching {
                locationManager.requestLocationUpdates(
                    fusedProvider,
                    SAMPLE_INTERVAL_MS,
                    0f,
                    newListener,
                    Looper.getMainLooper()
                )
            }
            if (fusedRegistration.isSuccess) return
        }

        val fallbackProviders = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { it in providers }
        if (fallbackProviders.isEmpty()) {
            listener = null
            throw IllegalStateException("No platform location provider available")
        }

        fallbackProviders.forEach { provider ->
            locationManager.requestLocationUpdates(
                provider,
                SAMPLE_INTERVAL_MS,
                0f,
                newListener,
                Looper.getMainLooper()
            )
        }
    }

    override fun stop() {
        listener?.let { current -> runCatching { locationManager.removeUpdates(current) } }
        listener = null
    }

    private companion object {
        // LocationManager.FUSED_PROVIDER is API 31, but the provider name itself has been used by
        // Android's fused provider since earlier releases. Use the stable provider string because
        // this app supports API 26+.
        const val PLATFORM_FUSED_PROVIDER = "fused"
        const val SAMPLE_INTERVAL_MS = 4_000L
    }
}
