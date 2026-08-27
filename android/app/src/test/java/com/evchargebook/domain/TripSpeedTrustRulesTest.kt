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
}
