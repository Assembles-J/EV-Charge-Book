package com.evchargebook.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TripGpsHealthTest {
    @Test
    fun `waiting before first callback`() {
        val snapshot = TripGpsHealth.evaluate(
            nowEpochMillis = 10_000L,
            trackingStartedAtEpochMillis = 0L,
            lastCallbackAtEpochMillis = null,
            lastAcceptedPointAtEpochMillis = null
        )
        assertEquals(TripGpsHealthStatus.WAITING, snapshot.status)
    }

    @Test
    fun `good when accepted point is fresh`() {
        val snapshot = TripGpsHealth.evaluate(
            nowEpochMillis = 20_000L,
            trackingStartedAtEpochMillis = 0L,
            lastCallbackAtEpochMillis = 19_000L,
            lastAcceptedPointAtEpochMillis = 18_000L
        )
        assertEquals(TripGpsHealthStatus.GOOD, snapshot.status)
        assertEquals(2L, snapshot.secondsSinceLastAcceptedPoint)
    }

    @Test
    fun `degraded after fifteen seconds without accepted point`() {
        val snapshot = TripGpsHealth.evaluate(
            nowEpochMillis = 30_000L,
            trackingStartedAtEpochMillis = 0L,
            lastCallbackAtEpochMillis = 29_000L,
            lastAcceptedPointAtEpochMillis = 15_000L
        )
        assertEquals(TripGpsHealthStatus.DEGRADED, snapshot.status)
    }

    @Test
    fun `lost after thirty seconds without accepted point`() {
        val snapshot = TripGpsHealth.evaluate(
            nowEpochMillis = 60_000L,
            trackingStartedAtEpochMillis = 0L,
            lastCallbackAtEpochMillis = 59_000L,
            lastAcceptedPointAtEpochMillis = 30_000L
        )
        assertEquals(TripGpsHealthStatus.LOST, snapshot.status)
    }

    @Test
    fun `long gap after two minutes`() {
        val snapshot = TripGpsHealth.evaluate(
            nowEpochMillis = 180_000L,
            trackingStartedAtEpochMillis = 0L,
            lastCallbackAtEpochMillis = 179_000L,
            lastAcceptedPointAtEpochMillis = 60_000L
        )
        assertEquals(TripGpsHealthStatus.LONG_GAP, snapshot.status)
    }
}
