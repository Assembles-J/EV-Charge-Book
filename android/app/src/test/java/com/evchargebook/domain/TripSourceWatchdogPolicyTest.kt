package com.evchargebook.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripSourceWatchdogPolicyTest {
    private val millisToNanos = 1_000_000L

    @Test
    fun freshFusedFixCanRefreshSourceWatchdog() {
        val now = 300_000L * millisToNanos
        assertTrue(TripSourceWatchdogPolicy.refreshesWatchdog(null, now, now))
        assertTrue(TripSourceWatchdogPolicy.refreshesWatchdog(
            null, now - 10_000L * millisToNanos, now
        ))
        assertTrue(TripSourceWatchdogPolicy.refreshesWatchdog(
            now - 3_000L * millisToNanos, now - 2_000L * millisToNanos, now
        ))
    }

    @Test
    fun duplicateOrOutOfOrderFusedFixCannotKeepSilentSourceAlive() {
        val now = 300_000L * millisToNanos
        val prior = now - 1_000L * millisToNanos
        assertFalse(TripSourceWatchdogPolicy.refreshesWatchdog(prior, prior, now))
        assertFalse(TripSourceWatchdogPolicy.refreshesWatchdog(prior, prior - 1L, now))
    }

    @Test
    fun delayedBatchStillBelongsToTripCapturePipelineButNotSourceWatchdog() {
        val now = 300_000L * millisToNanos
        val oneMinuteOld = now - 60_000L * millisToNanos
        assertTrue(TripContinuityRules.isFreshLocation(60_000L))
        assertFalse(TripSourceWatchdogPolicy.refreshesWatchdog(null, oneMinuteOld, now))
        assertFalse(TripSourceWatchdogPolicy.refreshesWatchdog(
            null, now - 10_001L * millisToNanos, now
        ))
    }

    @Test
    fun absentOrFutureMonotonicCaptureCannotSilentlyResetWatchdog() {
        val now = 300_000L * millisToNanos
        assertFalse(TripSourceWatchdogPolicy.refreshesWatchdog(null, 0L, now))
        assertFalse(TripSourceWatchdogPolicy.refreshesWatchdog(null, -1L, now))
        assertFalse(TripSourceWatchdogPolicy.refreshesWatchdog(null, now + 1L, now))
    }
}
