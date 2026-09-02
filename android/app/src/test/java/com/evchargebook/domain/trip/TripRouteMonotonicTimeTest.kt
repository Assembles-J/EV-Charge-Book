package com.evchargebook.domain.trip

import org.junit.Assert.assertEquals
import org.junit.Test

class TripRouteMonotonicTimeTest {
    @Test
    fun `wall clock rollback does not split route when elapsed realtime advances`() {
        val geometry = TripRouteGeometryBuilder.build(
            listOf(
                TripGeoPoint(
                    latitude = 31.0,
                    longitude = 121.0,
                    capturedAtEpochMillis = 20_000L,
                    capturedAtElapsedRealtimeNanos = 5_000_000_000L,
                ),
                TripGeoPoint(
                    latitude = 31.01,
                    longitude = 121.01,
                    capturedAtEpochMillis = 10_000L,
                    capturedAtElapsedRealtimeNanos = 9_000_000_000L,
                ),
            )
        )!!

        assertEquals(1, geometry.segments.size)
        assertEquals(0, geometry.gapCount)
    }

    @Test
    fun `elapsed realtime reset forces disconnected route baseline`() {
        val geometry = TripRouteGeometryBuilder.build(
            listOf(
                TripGeoPoint(
                    latitude = 31.0,
                    longitude = 121.0,
                    capturedAtEpochMillis = 10_000L,
                    capturedAtElapsedRealtimeNanos = 50_000_000_000L,
                ),
                TripGeoPoint(
                    latitude = 31.01,
                    longitude = 121.01,
                    capturedAtEpochMillis = 20_000L,
                    capturedAtElapsedRealtimeNanos = 2_000_000_000L,
                ),
            )
        )!!

        assertEquals(2, geometry.segments.size)
        assertEquals(1, geometry.gapCount)
    }
}
