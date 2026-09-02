package com.evchargebook.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripPlaybackMonotonicTimeTest {
    @Test
    fun `playback remains chronological when wall clock moves backwards`() {
        val samples = listOf(
            TripPlaybackSample(
                capturedAtEpochMillis = 20_000L,
                latitude = 30.0,
                longitude = 120.0,
                capturedAtElapsedRealtimeNanos = 5_000_000_000L,
            ),
            TripPlaybackSample(
                capturedAtEpochMillis = 10_000L,
                latitude = 31.0,
                longitude = 121.0,
                capturedAtElapsedRealtimeNanos = 9_000_000_000L,
            ),
        )

        assertTrue(TripPlaybackTimeline.isChronological(samples))
        assertEquals(4_000L, TripPlaybackTimeline.durationMillis(samples))
        val frame = TripPlaybackTimeline.frameAt(samples, 2_000L)!!
        assertFalse(frame.isLongGap)
        assertEquals(30.5, frame.latitude, 0.000001)
    }

    @Test
    fun `elapsed realtime reset is represented as disconnected playback gap`() {
        val samples = listOf(
            TripPlaybackSample(
                capturedAtEpochMillis = 10_000L,
                latitude = 30.0,
                longitude = 120.0,
                capturedAtElapsedRealtimeNanos = 50_000_000_000L,
            ),
            TripPlaybackSample(
                capturedAtEpochMillis = 20_000L,
                latitude = 31.0,
                longitude = 121.0,
                capturedAtElapsedRealtimeNanos = 2_000_000_000L,
            ),
        )

        val total = TripPlaybackTimeline.durationMillis(samples)
        assertEquals(TripContinuityRules.LONG_GAP_SECONDS * 1_000L, total)
        val frame = TripPlaybackTimeline.frameAt(samples, total / 2)!!
        assertTrue(frame.isLongGap)
        assertEquals(30.0, frame.latitude, 0.0)
    }
}
