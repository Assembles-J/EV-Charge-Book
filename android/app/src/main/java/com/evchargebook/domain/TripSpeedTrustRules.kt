package com.evchargebook.domain

object TripSpeedTrustRules {
    const val MIN_CORROBORATING_DISTANCE_METERS = 5.0
    private const val MIN_EXPECTED_DISTANCE_RATIO = 0.25
    private const val MAX_TRUSTED_HORIZONTAL_ACCURACY_METERS = 25.0
    private const val MAX_TRUSTED_SPEED_ACCURACY_MPS = 3.0
    private const val EXTREME_SPEED_REQUIRES_ACCURACY_MPS = 25.0

    fun eligibleForAggregate(
        reportedSpeedMps: Double?,
        deltaSeconds: Long,
        trustedDistanceMeters: Double,
        continuityAllowsSpeed: Boolean
    ): Boolean {
        if (!continuityAllowsSpeed || reportedSpeedMps == null || !reportedSpeedMps.isFinite() || reportedSpeedMps < 0.0) return false
        if (reportedSpeedMps < TripSamplingRules.MOVING_SPEED_MPS) return true
        if (deltaSeconds <= 0) return false

        val expectedDistance = reportedSpeedMps * deltaSeconds
        val requiredDistance = maxOf(
            MIN_CORROBORATING_DISTANCE_METERS,
            expectedDistance * MIN_EXPECTED_DISTANCE_RATIO
        )
        return trustedDistanceMeters >= requiredDistance
    }

    fun eligibleForMeasuredSpeed(
        reportedSpeedMps: Double?,
        provider: String?,
        horizontalAccuracyMeters: Double?,
        speedAccuracyMps: Double?
    ): Boolean {
        if (reportedSpeedMps == null || !reportedSpeedMps.isFinite() || reportedSpeedMps < 0.0) return false
        val trustedProvider = provider.equals("gps", ignoreCase = true) || provider.equals("fused", ignoreCase = true)
        if (!trustedProvider) return false

        val horizontalAccuracy = horizontalAccuracyMeters ?: return false
        if (!horizontalAccuracy.isFinite() || horizontalAccuracy < 0.0 || horizontalAccuracy > MAX_TRUSTED_HORIZONTAL_ACCURACY_METERS) {
            return false
        }

        if (reportedSpeedMps >= EXTREME_SPEED_REQUIRES_ACCURACY_MPS && speedAccuracyMps == null) return false
        if (speedAccuracyMps != null &&
            (!speedAccuracyMps.isFinite() || speedAccuracyMps < 0.0 || speedAccuracyMps > MAX_TRUSTED_SPEED_ACCURACY_MPS)
        ) {
            return false
        }
        return true
    }

    fun eligibleForMaxSpeed(
        reportedSpeedMps: Double?,
        deltaSeconds: Long,
        trustedDistanceMeters: Double,
        continuityAllowsSpeed: Boolean,
        provider: String?,
        horizontalAccuracyMeters: Double?,
        speedAccuracyMps: Double?
    ): Boolean =
        eligibleForAggregate(
            reportedSpeedMps = reportedSpeedMps,
            deltaSeconds = deltaSeconds,
            trustedDistanceMeters = trustedDistanceMeters,
            continuityAllowsSpeed = continuityAllowsSpeed
        ) && eligibleForMeasuredSpeed(
            reportedSpeedMps = reportedSpeedMps,
            provider = provider,
            horizontalAccuracyMeters = horizontalAccuracyMeters,
            speedAccuracyMps = speedAccuracyMps
        )
}
