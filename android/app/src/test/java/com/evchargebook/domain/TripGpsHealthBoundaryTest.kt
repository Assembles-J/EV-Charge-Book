package com.evchargebook.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TripGpsHealthBoundaryTest {
    @Test
    fun `threshold boundaries are deterministic`() {
        fun statusAt(seconds: Long): TripGpsHealthStatus = TripGpsHealth.evaluate(
            nowEpochMillis = seconds * 1000L,
            trackingStartedAtEpochMillis = 0L,
            lastCallbackAtEpochMillis = seconds * 1000L,
            lastAcceptedPointAtEpochMillis = 0L
        ).status

        assertEquals(TripGpsHealthStatus.GOOD, statusAt(14))
        assertEquals(TripGpsHealthStatus.DEGRADED, statusAt(15))
        assertEquals(TripGpsHealthStatus.LOST, statusAt(30))
        assertEquals(TripGpsHealthStatus.LONG_GAP, statusAt(120))
    }
}
