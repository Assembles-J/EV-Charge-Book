package com.evchargebook.location

import kotlin.math.roundToLong

/**
 * Small process-local cache for derived address text.
 *
 * Coordinates remain the source of truth. Only successful, non-blank address text is cached;
 * failures are intentionally not cached so a later user retry can call the geocoder again.
 */
class GeocodeMemoryCache(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES
) {
    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    private data class CoordinateKey(val latitudeBucket: Long, val longitudeBucket: Long)

    private val entries = object : LinkedHashMap<CoordinateKey, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CoordinateKey, String>?): Boolean =
            size > maxEntries
    }

    @Synchronized
    fun get(latitude: Double, longitude: Double): String? =
        entries[key(latitude, longitude)]

    @Synchronized
    fun put(latitude: Double, longitude: Double, address: String?) {
        val normalized = address?.trim()?.takeIf { it.isNotEmpty() } ?: return
        entries[key(latitude, longitude)] = normalized
    }

    @Synchronized
    internal fun size(): Int = entries.size

    private fun key(latitude: Double, longitude: Double): CoordinateKey = CoordinateKey(
        latitudeBucket = (latitude * COORDINATE_SCALE).roundToLong(),
        longitudeBucket = (longitude * COORDINATE_SCALE).roundToLong()
    )

    companion object {
        const val DEFAULT_MAX_ENTRIES = 128
        private const val COORDINATE_SCALE = 10_000.0 // ~11 m latitude buckets; enough for address reuse.
    }
}
