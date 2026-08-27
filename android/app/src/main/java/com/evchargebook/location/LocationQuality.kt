package com.evchargebook.location

/**
 * Quality classification for location fixes.
 * Coordinates remain the source of truth; quality only describes confidence.
 */
enum class LocationQuality {
    GOOD,
    NORMAL,
    POOR,
    INVALID;

    companion object {
        fun fromAccuracy(accuracyMeters: Float?): LocationQuality {
            if (accuracyMeters == null || accuracyMeters <= 0f) return INVALID
            return when {
                accuracyMeters < 20f -> GOOD
                accuracyMeters < 100f -> NORMAL
                accuracyMeters < 500f -> POOR
                else -> INVALID
            }
        }
    }
}
