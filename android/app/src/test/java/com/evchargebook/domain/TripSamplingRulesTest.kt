package com.evchargebook.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripSamplingRulesTest {
    @Test
    fun rejectsPoorAccuracy() {
        assertFalse(TripSamplingRules.decide(5, 10.0, 2.0, 150.0).accept)
    }

    @Test
    fun rejectsGpsJumpByImpliedSpeed() {
        assertFalse(TripSamplingRules.decide(2, 500.0, null, 10.0).accept)
    }

    @Test
    fun throttlesStationaryPointsBeforeFifteenSeconds() {
        assertFalse(TripSamplingRules.decide(8, 1.0, 0.0, 10.0).accept)
    }

    @Test
    fun keepsStationaryHeartbeatAfterFifteenSeconds() {
        val decision = TripSamplingRules.decide(15, 1.0, 0.0, 10.0)
        assertTrue(decision.accept)
        assertFalse(decision.moving)
    }

    @Test
    fun acceptsNormalMovingSegment() {
        val decision = TripSamplingRules.decide(4, 18.0, 4.0, 8.0)
        assertTrue(decision.accept)
        assertTrue(decision.moving)
    }
}
