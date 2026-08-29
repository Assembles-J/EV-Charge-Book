package com.evchargebook.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripPlaybackTimelineTest {
    @Test
    fun `short interval interpolates between real samples`() {
        val samples = listOf(
            sample(time = 1_000L, latitude = 30.0, longitude = 120.0, bearing = 350.0),
            sample(time = 11_000L, latitude = 31.0, longitude = 121.0, bearing = 10.0)
        )

        val frame = TripPlaybackTimeline.frameAt(samples, 5_000L)!!

        assertFalse(frame.isLongGap)
        assertEquals(0, frame.currentSampleIndex)
        assertEquals(1, frame.nextSampleIndex)
        assertEquals(30.5, frame.latitude, 0.000001)
        assertEquals(120.5, frame.longitude, 0.000001)
        assertEquals(0.5, frame.segmentFraction, 0.000001)
        assertEquals(0.0, frame.bearingDegrees!!, 0.000001)
    }

    @Test
    fun `elapsed time clamps to start and end`() {
        val samples = listOf(
            sample(time = 1_000L, latitude = 30.0, longitude = 120.0),
            sample(time = 6_000L, latitude = 31.0, longitude = 121.0)
        )

        val before = TripPlaybackTimeline.frameAt(samples, -500L)!!
        val after = TripPlaybackTimeline.frameAt(samples, 50_000L)!!

        assertEquals(0L, before.elapsedMillis)
        assertEquals(30.0, before.latitude, 0.0)
        assertEquals(5_000L, after.elapsedMillis)
        assertEquals(31.0, after.latitude, 0.0)
        assertEquals(1, after.currentSampleIndex)
    }

    @Test
    fun `long gap never interpolates across missing interval`() {
        val longGapMillis = TripContinuityRules.LONG_GAP_SECONDS * 1_000L
        val samples = listOf(
            sample(time = 10_000L, latitude = 30.0, longitude = 120.0),
            sample(time = 10_000L + longGapMillis + 30_000L, latitude = 31.0, longitude = 121.0)
        )

        val frame = TripPlaybackTimeline.frameAt(samples, 60_000L)!!

        assertTrue(frame.isLongGap)
        assertEquals(30.0, frame.latitude, 0.0)
        assertEquals(120.0, frame.longitude, 0.0)
        assertEquals(0.0, frame.segmentFraction, 0.0)
        assertEquals(0L, frame.longGapStartElapsedMillis)
        assertEquals(longGapMillis + 30_000L, frame.longGapEndElapsedMillis)
    }

    @Test
    fun `exact next timestamp after long gap jumps to real sample`() {
        val longGapMillis = TripContinuityRules.LONG_GAP_SECONDS * 1_000L
        val samples = listOf(
            sample(time = 10_000L, latitude = 30.0, longitude = 120.0),
            sample(time = 10_000L + longGapMillis, latitude = 31.0, longitude = 121.0)
        )

        val frame = TripPlaybackTimeline.frameAt(samples, longGapMillis)!!

        assertFalse(frame.isLongGap)
        assertEquals(1, frame.currentSampleIndex)
        assertEquals(31.0, frame.latitude, 0.0)
        assertEquals(121.0, frame.longitude, 0.0)
    }

    @Test
    fun `non chronological history is rejected instead of reordered`() {
        val samples = listOf(
            sample(time = 2_000L, latitude = 30.0, longitude = 120.0),
            sample(time = 1_000L, latitude = 31.0, longitude = 121.0)
        )

        assertFalse(TripPlaybackTimeline.isChronological(samples))
        assertEquals(0L, TripPlaybackTimeline.durationMillis(samples))
        assertNull(TripPlaybackTimeline.frameAt(samples, 500L))
    }

    @Test
    fun `playback advancement applies speed and clamps to duration`() {
        assertEquals(
            9_000L,
            TripPlaybackTimeline.advanceElapsed(
                currentElapsedMillis = 1_000L,
                realDeltaMillis = 2_000L,
                speedMultiplier = 4f,
                totalMillis = 20_000L
            )
        )
        assertEquals(
            20_000L,
            TripPlaybackTimeline.advanceElapsed(
                currentElapsedMillis = 19_000L,
                realDeltaMillis = 2_000L,
                speedMultiplier = 8f,
                totalMillis = 20_000L
            )
        )
        assertEquals(
            2_000L,
            TripPlaybackTimeline.advanceElapsed(
                currentElapsedMillis = 1_000L,
                realDeltaMillis = 1_000L,
                speedMultiplier = Float.NaN,
                totalMillis = 20_000L
            )
        )
    }

    @Test
    fun `single sample stays at zero duration`() {
        val samples = listOf(sample(time = 1_000L, latitude = 30.0, longitude = 120.0))

        val frame = TripPlaybackTimeline.frameAt(samples, 10_000L)!!

        assertEquals(0L, TripPlaybackTimeline.durationMillis(samples))
        assertEquals(0L, frame.elapsedMillis)
        assertEquals(0L, frame.totalMillis)
        assertEquals(30.0, frame.latitude, 0.0)
    }

    private fun sample(
        time: Long,
        latitude: Double,
        longitude: Double,
        bearing: Double? = null
    ) = TripPlaybackSample(
        capturedAtEpochMillis = time,
        latitude = latitude,
        longitude = longitude,
        bearingDegrees = bearing
    )
}
