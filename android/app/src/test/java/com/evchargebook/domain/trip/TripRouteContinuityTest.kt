package com.evchargebook.domain.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripRouteContinuityTest {
    @Test
    fun `long gap preserves both real route segments and one explicit gap`() {
        val continuity = TripRouteContinuityBuilder.build(
            listOf(
                TripGeoPoint(31.2000, 121.4000, 0L, 10.0),
                TripGeoPoint(31.2010, 121.4010, 4_000L, 12.0),
                TripGeoPoint(31.2500, 121.4500, 180_000L, 15.0),
                TripGeoPoint(31.2510, 121.4510, 184_000L, 16.0),
            )
        )

        assertEquals(2, continuity.segments.size)
        assertEquals(2, continuity.segments[0].size)
        assertEquals(2, continuity.segments[1].size)
        assertEquals(1, continuity.gaps.size)
        assertEquals(continuity.segments[0].last(), continuity.gaps.single().from)
        assertEquals(continuity.segments[1].first(), continuity.gaps.single().to)
    }

    @Test
    fun `isolated point after a long gap does not crush map camera fit`() {
        val mainRouteStart = TripGeoPoint(31.2000, 121.4000, 0L, 10.0)
        val mainRouteEnd = TripGeoPoint(31.2010, 121.4010, 4_000L, 12.0)
        val isolatedOutlier = TripGeoPoint(32.5000, 123.0000, 180_000L, null)

        val continuity = TripRouteContinuityBuilder.build(
            listOf(mainRouteStart, mainRouteEnd, isolatedOutlier)
        )

        assertEquals(listOf(mainRouteStart, mainRouteEnd), continuity.cameraFitPoints)
        assertFalse(continuity.cameraFitPoints.contains(isolatedOutlier))
        assertEquals(1, continuity.gaps.size)
    }

    @Test
    fun `camera fit includes every drawable segment`() {
        val first = listOf(
            TripGeoPoint(31.2000, 121.4000, 0L),
            TripGeoPoint(31.2010, 121.4010, 4_000L),
        )
        val second = listOf(
            TripGeoPoint(31.2500, 121.4500, 180_000L),
            TripGeoPoint(31.2510, 121.4510, 184_000L),
        )

        val continuity = TripRouteContinuityBuilder.build(first + second)

        assertEquals(first + second, continuity.cameraFitPoints)
        assertTrue(continuity.cameraFitPoints.size >= 2)
    }
}
