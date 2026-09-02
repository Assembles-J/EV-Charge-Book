package com.evchargebook.domain.trip

import com.evchargebook.data.entity.TripPointEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class TripElevationMonotonicTimeTest {
    @Test
    fun `wall clock rollback does not create false elevation gap`() {
        val summary = TripElevationAnalytics.summarize(
            listOf(
                point(epoch = 20_000L, elapsed = 5_000_000_000L, altitude = 10.0),
                point(epoch = 10_000L, elapsed = 9_000_000_000L, altitude = 20.0),
            )
        )!!

        assertEquals(0, summary.skippedLongGapCount)
        assertEquals(10.0, summary.elevationGainMeters, 0.001)
    }

    @Test
    fun `elapsed realtime reset prevents elevation bridging`() {
        val summary = TripElevationAnalytics.summarize(
            listOf(
                point(epoch = 10_000L, elapsed = 50_000_000_000L, altitude = 10.0),
                point(epoch = 20_000L, elapsed = 2_000_000_000L, altitude = 80.0),
            )
        )!!

        assertEquals(1, summary.skippedLongGapCount)
        assertEquals(0.0, summary.elevationGainMeters, 0.001)
    }

    private fun point(epoch: Long, elapsed: Long, altitude: Double) = TripPointEntity(
        tripId = 1L,
        capturedAtEpochMillis = epoch,
        capturedAtElapsedRealtimeNanos = elapsed,
        latitude = 31.0,
        longitude = 121.0,
        altitudeMeters = altitude,
        verticalAccuracyMeters = 2.0,
        provider = "gps",
    )
}
