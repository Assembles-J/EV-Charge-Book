package com.evchargebook.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripCaptureTimeRulesTest {
    @Test
    fun `elapsed realtime wins when wall clock moves backwards`() {
        val result = TripCaptureTimeRules.between(
            previousEpochMillis = 20_000L,
            previousElapsedRealtimeNanos = 5_000_000_000L,
            currentEpochMillis = 10_000L,
            currentElapsedRealtimeNanos = 9_000_000_000L,
        )

        assertTrue(result.accepted)
        assertFalse(result.requiresRebase)
        assertEquals(4_000L, result.deltaMillis)
        assertEquals(TripCaptureTimeAuthority.ELAPSED_REALTIME, result.authority)
    }

    @Test
    fun `legacy point falls back to epoch time`() {
        val result = TripCaptureTimeRules.between(
            previousEpochMillis = 10_000L,
            previousElapsedRealtimeNanos = null,
            currentEpochMillis = 14_000L,
            currentElapsedRealtimeNanos = 9_000_000_000L,
        )

        assertTrue(result.accepted)
        assertEquals(4_000L, result.deltaMillis)
        assertEquals(TripCaptureTimeAuthority.EPOCH_FALLBACK, result.authority)
    }

    @Test
    fun `elapsed realtime reset with advancing epoch starts a new baseline`() {
        val result = TripCaptureTimeRules.between(
            previousEpochMillis = 10_000L,
            previousElapsedRealtimeNanos = 50_000_000_000L,
            currentEpochMillis = 20_000L,
            currentElapsedRealtimeNanos = 2_000_000_000L,
        )

        assertTrue(result.accepted)
        assertTrue(result.requiresRebase)
        assertNull(result.deltaMillis)
        assertEquals(TripCaptureTimeAuthority.ELAPSED_REALTIME_REBASE, result.authority)
        assertTrue(result.breaksContinuity(120_000L))
    }

    @Test
    fun `older delayed point is rejected when elapsed and epoch both regress`() {
        val result = TripCaptureTimeRules.between(
            previousEpochMillis = 20_000L,
            previousElapsedRealtimeNanos = 20_000_000_000L,
            currentEpochMillis = 15_000L,
            currentElapsedRealtimeNanos = 15_000_000_000L,
        )

        assertFalse(result.accepted)
        assertEquals("out_of_order_elapsed_realtime", result.rejectReason)
    }

    @Test
    fun `duplicate elapsed capture is rejected`() {
        val result = TripCaptureTimeRules.between(
            previousEpochMillis = 20_000L,
            previousElapsedRealtimeNanos = 20_000_000_000L,
            currentEpochMillis = 21_000L,
            currentElapsedRealtimeNanos = 20_000_000_000L,
        )

        assertFalse(result.accepted)
        assertEquals("duplicate_elapsed_realtime", result.rejectReason)
    }
}
