package com.evchargebook.domain

import org.junit.Assert.assertEquals
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
    fun throttlesOneSecondStationaryPoint() {
        assertFalse(TripSamplingRules.decide(1, 1.0, 0.0, 10.0).accept)
    }

    @Test
    fun keepsStationaryHeartbeatAfterTwoSeconds() {
        val decision = TripSamplingRules.decide(2, 1.0, 0.0, 10.0)
        assertTrue(decision.accept)
        assertFalse(decision.moving)
    }

    @Test
    fun trustedZeroSpeedWinsOverSeveralMetresOfStationaryGpsDrift() {
        val decision = TripSamplingRules.decide(2, 6.5, 0.0, 8.0)
        assertTrue(decision.accept)
        assertFalse(decision.moving)
    }

    @Test
    fun distanceStillDetectsMovementWhenTrustedSpeedIsUnavailable() {
        val decision = TripSamplingRules.decide(2, 6.5, null, 8.0)
        assertTrue(decision.accept)
        assertTrue(decision.moving)
    }

    @Test
    fun stationaryHeartbeatAccumulatesStoppedTime() {
        val decision = TripSamplingRules.decide(2, 1.0, 0.0, 10.0)
        assertTrue(decision.accept)
        assertFalse(decision.moving)
        assertEquals(32L, TripSamplingRules.stoppedSeconds(30L, 2L, decision.moving))
        assertEquals(30L, TripSamplingRules.movingSeconds(30L, 2L, decision.moving))
    }

    @Test
    fun acceptsOneSecondMovingSegment() {
        val decision = TripSamplingRules.decide(1, 6.0, 6.0, 8.0)
        assertTrue(decision.accept)
        assertTrue(decision.moving)
    }
}
