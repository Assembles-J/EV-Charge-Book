package com.evchargebook.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripSpeedTrustRulesTest {
    @Test
    fun `rejects stationary high-speed spike`() {
        assertFalse(
            TripSpeedTrustRules.eligibleForAggregate(
                reportedSpeedMps = 33.3,
                deltaSeconds = 4,
                trustedDistanceMeters = 3.0,
                continuityAllowsSpeed = true
            )
        )
    }

    @Test
    fun `keeps short real highway peak when distance corroborates it`() {
        assertTrue(
            TripSpeedTrustRules.eligibleForAggregate(
                reportedSpeedMps = 30.3,
                deltaSeconds = 4,
                trustedDistanceMeters = 110.0,
                continuityAllowsSpeed = true
            )
        )
    }

    @Test
    fun `first baseline point cannot update aggregate speed`() {
        assertFalse(
            TripSpeedTrustRules.eligibleForAggregate(
                reportedSpeedMps = 30.0,
                deltaSeconds = 0,
                trustedDistanceMeters = 0.0,
                continuityAllowsSpeed = false
            )
        )
    }

    @Test
    fun `trusted stationary speed remains eligible`() {
        assertTrue(
            TripSpeedTrustRules.eligibleForAggregate(
                reportedSpeedMps = 0.2,
                deltaSeconds = 4,
                trustedDistanceMeters = 0.5,
                continuityAllowsSpeed = true
            )
        )
    }

    @Test
    fun `trip32 contradictory zero speed falls back to distance motion`() {
        assertFalse(
            TripSpeedTrustRules.eligibleForAggregate(
                reportedSpeedMps = 0.0,
                deltaSeconds = 72,
                trustedDistanceMeters = 1_523.5,
                continuityAllowsSpeed = true
            )
        )
        assertTrue(
            TripSamplingRules.decide(
                deltaSeconds = 72,
                segmentDistanceMeters = 1_523.5,
                reportedSpeedMps = null,
                horizontalAccuracyMeters = 100.0
            ).moving
        )
    }

    @Test
    fun `coarse displacement inside conservative accuracy envelope still trusts zero speed`() {
        assertTrue(
            TripSpeedTrustRules.eligibleForAggregate(
                reportedSpeedMps = 0.0,
                deltaSeconds = 60,
                trustedDistanceMeters = 350.0,
                continuityAllowsSpeed = true
            )
        )
    }

    @Test
    fun `small stationary drift still trusts zero speed`() {
        assertTrue(
            TripSpeedTrustRules.eligibleForAggregate(
                reportedSpeedMps = 0.0,
                deltaSeconds = 2,
                trustedDistanceMeters = 6.5,
                continuityAllowsSpeed = true
            )
        )
    }

    @Test
    fun `network speed spike cannot update max speed even when distance corroborates it`() {
        assertFalse(
            TripSpeedTrustRules.eligibleForMaxSpeed(
                reportedSpeedMps = 34.005,
                deltaSeconds = 4,
                trustedDistanceMeters = 140.0,
                continuityAllowsSpeed = true,
                provider = "network",
                horizontalAccuracyMeters = 100.0,
                speedAccuracyMps = null
            )
        )
    }

    @Test
    fun `accurate gps highway peak can update max speed`() {
        assertTrue(
            TripSpeedTrustRules.eligibleForMaxSpeed(
                reportedSpeedMps = 30.3,
                deltaSeconds = 4,
                trustedDistanceMeters = 110.0,
                continuityAllowsSpeed = true,
                provider = "gps",
                horizontalAccuracyMeters = 8.0,
                speedAccuracyMps = 1.2
            )
        )
    }

    @Test
    fun `accurate fused highway peak can update max speed`() {
        assertTrue(
            TripSpeedTrustRules.eligibleForMaxSpeed(
                reportedSpeedMps = 30.3,
                deltaSeconds = 4,
                trustedDistanceMeters = 110.0,
                continuityAllowsSpeed = true,
                provider = "fused",
                horizontalAccuracyMeters = 8.0,
                speedAccuracyMps = 1.2
            )
        )
    }

    @Test
    fun `extreme gps peak without speed accuracy cannot update max speed`() {
        assertFalse(
            TripSpeedTrustRules.eligibleForMaxSpeed(
                reportedSpeedMps = 34.005,
                deltaSeconds = 4,
                trustedDistanceMeters = 140.0,
                continuityAllowsSpeed = true,
                provider = "gps",
                horizontalAccuracyMeters = 8.0,
                speedAccuracyMps = null
            )
        )
    }

    @Test
    fun `moderate gps speed without speed accuracy remains usable`() {
        assertTrue(
            TripSpeedTrustRules.eligibleForMaxSpeed(
                reportedSpeedMps = 20.0,
                deltaSeconds = 4,
                trustedDistanceMeters = 80.0,
                continuityAllowsSpeed = true,
                provider = "gps",
                horizontalAccuracyMeters = 8.0,
                speedAccuracyMps = null
            )
        )
    }

    @Test
    fun `coarse fused point cannot update max speed`() {
        assertFalse(
            TripSpeedTrustRules.eligibleForMaxSpeed(
                reportedSpeedMps = 30.3,
                deltaSeconds = 4,
                trustedDistanceMeters = 110.0,
                continuityAllowsSpeed = true,
                provider = "fused",
                horizontalAccuracyMeters = 60.0,
                speedAccuracyMps = 1.2
            )
        )
    }

    @Test
    fun `accurate gps speed is eligible for visualization`() {
        assertTrue(
            TripSpeedTrustRules.eligibleForMeasuredSpeed(
                reportedSpeedMps = 15.0,
                provider = "gps",
                horizontalAccuracyMeters = 6.0,
                speedAccuracyMps = 1.0
            )
        )
    }

    @Test
    fun `accurate fused speed is eligible for visualization`() {
        assertTrue(
            TripSpeedTrustRules.eligibleForMeasuredSpeed(
                reportedSpeedMps = 15.0,
                provider = "fused",
                horizontalAccuracyMeters = 6.0,
                speedAccuracyMps = 1.0
            )
        )
    }

    @Test
    fun `network speed is not eligible for visualization`() {
        assertFalse(
            TripSpeedTrustRules.eligibleForMeasuredSpeed(
                reportedSpeedMps = 39.6,
                provider = "network",
                horizontalAccuracyMeters = 30.0,
                speedAccuracyMps = null
            )
        )
    }
}
