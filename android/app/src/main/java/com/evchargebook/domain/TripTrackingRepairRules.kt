package com.evchargebook.domain

enum class TripTrackingRepairReason {
    LOCATION_PERMISSION_MISSING,
    LOCATION_PROVIDER_DISABLED
}

object TripTrackingRepairRules {
    fun evaluate(
        hasLocationPermission: Boolean,
        hasUsableLocationProvider: Boolean
    ): TripTrackingRepairReason? = when {
        !hasLocationPermission -> TripTrackingRepairReason.LOCATION_PERMISSION_MISSING
        !hasUsableLocationProvider -> TripTrackingRepairReason.LOCATION_PROVIDER_DISABLED
        else -> null
    }
}
