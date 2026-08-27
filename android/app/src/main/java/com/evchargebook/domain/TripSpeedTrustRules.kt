package com.evchargebook.domain

object TripSpeedTrustRules {
    const val MIN_CORROBORATING_DISTANCE_METERS = 5.0
    private const val MIN_EXPECTED_DISTANCE_RATIO = 0.25
    private const val MAX_PEAK_HORIZONTAL_ACCURACY_METERS = 25.0
    private const val MAX_PEAK_SPEED_ACCURACY_MPS = 3.0

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

    fun eligibleForMaxSpeed(
        reportedSpeedMps: Double?,
        deltaSeconds: Long,
        trustedDistanceMeters: Double,
        continuityAllowsSpeed: Boolean,
        provider: String?,
        horizontalAccuracyMeters: Double?,
        speedAccuracyMps: Double?
    ): Boolean {
        if (!eligibleForAggregate(reportedSpeedMps, deltaSeconds, trustedDistanceMeters, continuityAllowsSpeed)) {
            return false
        }
        if (!provider.equals("gps", ignoreCase = true)) return false

        val horizontalAccuracy = horizontalAccuracyMeters ?: return false
        if (!horizontalAccuracy.isFinite() || horizontalAccuracy < 0.0 || horizontalAccuracy > MAX_PEAK_HORIZONTAL_ACCURACY_METERS) {
            return false
        }

        if (speedAccuracyMps != null &&
            (!speedAccuracyMps.isFinite() || speedAccuracyMps < 0.0 || speedAccuracyMps > MAX_PEAK_SPEED_ACCURACY_MPS)
        ) {
            return false
        }
        return true
    }
}
