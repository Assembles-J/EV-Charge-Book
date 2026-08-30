package com.evchargebook.domain.trip

import com.evchargebook.data.entity.TripPointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripElevationAnalyticsTest {
    @Test
    fun `summarizes climb descent min and max from trusted altitude points`() {
        val summary = TripElevationAnalytics.summarize(
            listOf(
                point(seconds = 0, altitude = 10.0, verticalAccuracy = 2.0),
                point(seconds = 10, altitude = 12.0, verticalAccuracy = 2.0),
                point(seconds = 20, altitude = 16.0, verticalAccuracy = 2.0),
                point(seconds = 30, altitude = 11.0, verticalAccuracy = 2.0)
            )
        )!!

        assertEquals(10.0, summary.startAltitudeMeters, 0.001)
        assertEquals(11.0, summary.endAltitudeMeters, 0.001)
        assertEquals(10.0, summary.minAltitudeMeters, 0.001)
        assertEquals(16.0, summary.maxAltitudeMeters, 0.001)
        assertEquals(6.0, summary.elevationGainMeters, 0.001)
        assertEquals(5.0, summary.elevationLossMeters, 0.001)
        assertTrue(summary.hasCumulativeEstimate)
    }

    @Test
    fun `does not bridge cumulative elevation across long gps gap`() {
        val summary = TripElevationAnalytics.summarize(
            listOf(
                point(seconds = 0, altitude = 20.0),
                point(seconds = 20, altitude = 30.0),
                point(seconds = 160, altitude = 80.0),
                point(seconds = 180, altitude = 70.0)
            )
        )!!

        assertEquals(10.0, summary.elevationGainMeters, 0.001)
        assertEquals(10.0, summary.elevationLossMeters, 0.001)
        assertEquals(1, summary.skippedLongGapCount)
    }

    @Test
    fun `continuous small altitude samples do not become a false long gap`() {
        val summary = TripElevationAnalytics.summarize(
            listOf(
                point(seconds = 0, altitude = 100.0, verticalAccuracy = 4.0),
                point(seconds = 30, altitude = 101.0, verticalAccuracy = 4.0),
                point(seconds = 60, altitude = 102.0, verticalAccuracy = 4.0),
                point(seconds = 90, altitude = 103.0, verticalAccuracy = 4.0),
                point(seconds = 120, altitude = 104.0, verticalAccuracy = 4.0),
                point(seconds = 150, altitude = 105.0, verticalAccuracy = 4.0)
            )
        )!!

        assertEquals(0, summary.skippedLongGapCount)
        assertEquals(4.0, summary.elevationGainMeters, 0.001)
    }

    @Test
    fun `rejects weak vertical accuracy from elevation analytics`() {
        val summary = TripElevationAnalytics.summarize(
            listOf(
                point(seconds = 0, altitude = 100.0, verticalAccuracy = 4.0),
                point(seconds = 10, altitude = 180.0, verticalAccuracy = 80.0),
                point(seconds = 20, altitude = 108.0, verticalAccuracy = 4.0)
            )
        )!!

        assertEquals(100.0, summary.minAltitudeMeters, 0.001)
        assertEquals(108.0, summary.maxAltitudeMeters, 0.001)
        assertEquals(8.0, summary.elevationGainMeters, 0.001)
        assertEquals(2, summary.trustedSampleCount)
    }

    @Test
    fun `uses reported vertical accuracy as the jitter threshold`() {
        val summary = TripElevationAnalytics.summarize(
            listOf(
                point(seconds = 0, altitude = 100.0, verticalAccuracy = 8.0),
                point(seconds = 10, altitude = 106.0, verticalAccuracy = 8.0),
                point(seconds = 20, altitude = 109.0, verticalAccuracy = 8.0)
            )
        )!!

        assertEquals(9.0, summary.elevationGainMeters, 0.001)
    }

    @Test
    fun `median filter suppresses isolated altitude spikes on a flat drive`() {
        val altitudes = listOf(100.0, 112.0, 99.0, 111.0, 100.0, 102.0, 98.0, 101.0, 100.0)
        val summary = TripElevationAnalytics.summarize(
            altitudes.mapIndexed { index, altitude ->
                point(seconds = index * 5L, altitude = altitude, verticalAccuracy = 4.0)
            }
        )!!

        assertTrue(summary.maxAltitudeMeters - summary.minAltitudeMeters <= 3.0)
        assertTrue(summary.elevationGainMeters <= 4.0)
        assertTrue(summary.elevationLossMeters <= 4.0)
    }

    @Test
    fun `median filter keeps a sustained climb visible`() {
        val altitudes = listOf(100.0, 102.0, 104.0, 106.0, 108.0, 110.0, 112.0, 114.0, 116.0)
        val summary = TripElevationAnalytics.summarize(
            altitudes.mapIndexed { index, altitude ->
                point(seconds = index * 5L, altitude = altitude, verticalAccuracy = 3.0)
            }
        )!!

        assertEquals(100.0, summary.startAltitudeMeters, 0.001)
        assertEquals(116.0, summary.endAltitudeMeters, 0.001)
        assertEquals(16.0, summary.elevationGainMeters, 0.001)
        assertEquals(0.0, summary.elevationLossMeters, 0.001)
    }

    @Test
    fun `returns null when no altitude facts exist`() {
        assertNull(
            TripElevationAnalytics.summarize(
                listOf(
                    TripPointEntity(
                        tripId = 1,
                        capturedAtEpochMillis = 1_000,
                        latitude = 31.0,
                        longitude = 121.0
                    )
                )
            )
        )
    }

    private fun point(
        seconds: Long,
        altitude: Double,
        verticalAccuracy: Double? = null
    ) = TripPointEntity(
        tripId = 1,
        capturedAtEpochMillis = seconds * 1_000,
        latitude = 31.0,
        longitude = 121.0,
        altitudeMeters = altitude,
        verticalAccuracyMeters = verticalAccuracy,
        provider = "gps"
    )
}
