package com.evchargebook.domain

import com.evchargebook.data.entity.TripPointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripCompletenessSummaryTest {
    @Test
    fun summarizesProvidersWithoutLongGaps() {
        val summary = TripCompletenessAnalytics.summarize(
            listOf(
                point(1_000L, "gps"),
                point(5_000L, "gps"),
                point(9_000L, "network")
            )
        )

        assertEquals(3, summary.acceptedPointCount)
        assertEquals(2, summary.gpsPointCount)
        assertEquals(1, summary.networkPointCount)
        assertEquals(0, summary.otherProviderPointCount)
        assertEquals(0, summary.longGapCount)
        assertEquals(0L, summary.longestGapSeconds)
        assertEquals(0L, summary.cumulativeLongGapSeconds)
        assertFalse(summary.hasLongGap)
    }

    @Test
    fun countsMultipleLongGapsAndKeepsLongest() {
        val summary = TripCompletenessAnalytics.summarize(
            listOf(
                point(1_000L, "gps"),
                point(121_000L, "gps"),
                point(125_000L, "gps"),
                point(305_000L, "network")
            )
        )

        assertEquals(2, summary.longGapCount)
        assertEquals(180L, summary.longestGapSeconds)
        assertEquals(300L, summary.cumulativeLongGapSeconds)
        assertTrue(summary.hasLongGap)
    }

    @Test
    fun treatsOneHundredNineteenSecondsAsContinuousAndOneTwentyAsGap() {
        val below = TripCompletenessAnalytics.summarize(
            listOf(point(0L, "gps"), point(119_999L, "gps"))
        )
        val boundary = TripCompletenessAnalytics.summarize(
            listOf(point(0L, "gps"), point(120_000L, "gps"))
        )

        assertEquals(0, below.longGapCount)
        assertEquals(1, boundary.longGapCount)
    }

    @Test
    fun sortsPointsBeforeCalculatingGaps() {
        val summary = TripCompletenessAnalytics.summarize(
            listOf(
                point(305_000L, "network"),
                point(1_000L, null),
                point(5_000L, "gps")
            )
        )

        assertEquals(1, summary.gpsPointCount)
        assertEquals(1, summary.networkPointCount)
        assertEquals(1, summary.otherProviderPointCount)
        assertEquals(1, summary.longGapCount)
        assertEquals(300L, summary.longestGapSeconds)
    }

    private fun point(time: Long, provider: String?) = TripPointEntity(
        tripId = 1L,
        capturedAtEpochMillis = time,
        latitude = 31.0,
        longitude = 121.0,
        provider = provider
    )
}
