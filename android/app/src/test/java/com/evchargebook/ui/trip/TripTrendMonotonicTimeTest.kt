package com.evchargebook.ui.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripTrendMonotonicTimeTest {
    @Test
    fun `wall clock rollback keeps trend continuous when elapsed realtime advances`() {
        val timeline = buildTripTrendTimelineV06(
            samples = listOf(
                TripTrendSampleV06(
                    timestamp = 20_000L,
                    value = 10.0,
                    capturedAtElapsedRealtimeNanos = 5_000_000_000L,
                ),
                TripTrendSampleV06(
                    timestamp = 10_000L,
                    value = 20.0,
                    capturedAtElapsedRealtimeNanos = 9_000_000_000L,
                ),
            ),
            longGapMs = 120_000L,
        )

        assertEquals(4_000L, timeline.last().timelineMillis)
        assertFalse(timeline.last().breakBefore)
        assertEquals(10_000L, timeline.last().epochMillis)
    }

    @Test
    fun `elapsed realtime reset creates a hard trend break`() {
        val timeline = buildTripTrendTimelineV06(
            samples = listOf(
                TripTrendSampleV06(
                    timestamp = 10_000L,
                    value = 10.0,
                    capturedAtElapsedRealtimeNanos = 50_000_000_000L,
                ),
                TripTrendSampleV06(
                    timestamp = 20_000L,
                    value = 80.0,
                    capturedAtElapsedRealtimeNanos = 2_000_000_000L,
                ),
            ),
            longGapMs = 120_000L,
        )

        assertEquals(120_000L, timeline.last().timelineMillis)
        assertTrue(timeline.last().breakBefore)
    }
}
