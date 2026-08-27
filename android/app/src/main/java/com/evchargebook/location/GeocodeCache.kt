package com.evchargebook.location

/**
 * Small in-memory cache boundary for reverse geocoding.
 *
 * Coordinates remain the source of truth. This cache only stores presentation data.
 */
class GeocodeCache {
    private data class Entry(
        val latitude: Double,
        val longitude: Double,
        val address: String
    )

    private val entries = mutableListOf<Entry>()

    fun get(latitude: Double, longitude: Double): String? {
        return entries.firstOrNull {
            distanceApproxMeters(it.latitude, it.longitude, latitude, longitude) <= 50
        }?.address
    }

    fun put(latitude: Double, longitude: Double, address: String) {
        entries.removeAll {
            distanceApproxMeters(it.latitude, it.longitude, latitude, longitude) <= 50
        }
        entries.add(Entry(latitude, longitude, address))
    }

    private fun distanceApproxMeters(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double
    ): Double {
        val latDiff = (lat1 - lat2) * 111_000
        val lngDiff = (lng1 - lng2) * 111_000
        return kotlin.math.sqrt(latDiff * latDiff + lngDiff * lngDiff)
    }
}
