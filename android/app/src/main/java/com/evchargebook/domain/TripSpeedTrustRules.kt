package com.evchargebook.domain

object TripSpeedTrustRules {
    const val MIN_CORROBORATING_DISTANCE_METERS = 5.0
    private const val MIN_EXPECTED_DISTANCE_RATIO = 0.25

    fun eligibleForAggregate(
        reportedSpeedMps: Double?,
        deltaSeconds: Long,
        trustedDistanceMeters: Double,
        continuityAllowsSpeed: Boolean
    ): Boolean {
        if (!continuityAllowsSpeed || reportedSpeedMps == null || reportedSpeedMps < 0.0) return false
        if (reportedSpeedMps < TripSamplingRules.MOVING_SPEED_MPS) return true
        if (deltaSeconds <= 0) return false

        val expectedDistance = reportedSpeedMps * deltaSeconds
        val requiredDistance = maxOf(
            MIN_CORROBORATING_DISTANCE_METERS,
            expectedDistance * MIN_EXPECTED_DISTANCE_RATIO
        )
        return trustedDistanceMeters >= requiredDistance
    }
}
