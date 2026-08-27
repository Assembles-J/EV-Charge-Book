package com.evchargebook.location

/**
 * Raw location fact model.
 * Coordinates are authoritative; address is optional presentation data.
 */
data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val altitudeMeters: Double?,
    val provider: String?,
    val timestampEpochMillis: Long,
    val quality: LocationQuality,
    val address: String? = null
)
