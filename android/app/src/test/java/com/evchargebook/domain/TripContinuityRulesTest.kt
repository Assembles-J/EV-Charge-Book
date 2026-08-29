package com.evchargebook.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripContinuityRulesTest {
    @Test
    fun `first point establishes baseline only`() {
        val decision = TripContinuityRules.decide(null, null, "gps")
        assertTrue(decision.acceptPoint)
        assertFalse(decision.countDistance)
        assertFalse(decision.countDuration)
        assertFalse(decision.speedEligibleForAggregate)
    }

    @Test
    fun `long gap restarts trusted baseline`() {
        val decision = TripContinuityRules.decide(120, "gps", "gps")
        assertTrue(decision.acceptPoint)
        assertFalse(decision.countDistance)
        assertFalse(decision.countDuration)
        assertFalse(decision.speedEligibleForAggregate)
    }

    @Test
    fun `continuous gps segment is fully trusted`() {
        val decision = TripContinuityRules.decide(4, "gps", "gps")
        assertTrue(decision.acceptPoint)
        assertTrue(decision.countDistance)
        assertTrue(decision.countDuration)
        assertTrue(decision.speedEligibleForAggregate)
    }

    @Test
    fun `recent network point is ignored after gps`() {
        val decision = TripContinuityRules.decide(4, "gps", "network")
        assertFalse(decision.acceptPoint)
    }

    @Test
    fun `gps recovery after network resets distance baseline`() {
        val decision = TripContinuityRules.decide(4, "network", "gps")
        assertTrue(decision.acceptPoint)
        assertFalse(decision.countDistance)
        assertFalse(decision.countDuration)
        assertTrue(decision.speedEligibleForAggregate)
    }

    @Test
    fun `lock screen delayed callback remains eligible within delivery grace`() {
        assertTrue(TripContinuityRules.isFreshLocation(10 * 60_000L))
        assertFalse(TripContinuityRules.isFreshLocation(10 * 60_000L + 1L))
    }
}
