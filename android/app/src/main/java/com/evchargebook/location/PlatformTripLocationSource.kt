package com.evchargebook.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper

/**
 * Framework-only fallback for devices where Google Play services location is unavailable or stalls.
 *
 * Do not prefer the framework provider named "fused" here. Some OEM/China ROMs expose that name
 * and accept registration without ever producing fixes. The fallback deliberately returns to the
 * historically proven GPS/network providers and registers every enabled provider independently.
 */
class PlatformTripLocationSource(context: Context) : TripLocationSource {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var listener: LocationListener? = null

    @Volatile
    private var registeredProviderNames: Set<String> = emptySet()

    @SuppressLint("MissingPermission")
    override fun start(
        callback: (Location) -> Unit,
        signalCallback: (TripLocationSourceSignal) -> Unit,
    ) {
        stop()
        val newListener = LocationListener(callback)
        listener = newListener

        val providers = locationManager.allProviders.toSet()
        val candidates = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { provider ->
                provider in providers && runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
            }

        val successfulProviders = linkedSetOf<String>()
        var lastFailure: Throwable? = null
        candidates.forEach { provider ->
            runCatching {
                locationManager.requestLocationUpdates(
                    provider,
                    SAMPLE_INTERVAL_MS,
                    0f,
                    newListener,
                    Looper.getMainLooper()
                )
            }.onSuccess {
                successfulProviders += provider
            }.onFailure { error ->
                lastFailure = error
            }
        }

        if (successfulProviders.isEmpty()) {
            listener = null
            registeredProviderNames = emptySet()
            throw IllegalStateException("No enabled platform GPS/network provider could be registered", lastFailure)
        }

        registeredProviderNames = successfulProviders.toSet()
        signalCallback(
            TripLocationSourceSignal(
                source = SOURCE_PLATFORM,
                detail = "registered providers=${successfulProviders.joinToString(",")}",
            )
        )
    }

    fun registeredProviders(): Set<String> = registeredProviderNames

    override fun stop() {
        listener?.let { current -> runCatching { locationManager.removeUpdates(current) } }
        listener = null
        registeredProviderNames = emptySet()
    }

    private companion object {
        const val SOURCE_PLATFORM = "platform"
        const val SAMPLE_INTERVAL_MS = 1_000L
    }
}
