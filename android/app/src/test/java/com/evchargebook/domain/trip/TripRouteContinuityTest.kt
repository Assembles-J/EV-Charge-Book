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
    fun `isolated point after a long gap does not crush default map fit`() {
        val mainRouteStart = TripGeoPoint(31.2000, 121.4000, 0L, 10.0)
        val mainRouteEnd = TripGeoPoint(31.2010, 121.4010, 4_000L, 12.0)
        val isolatedOutlier = TripGeoPoint(32.5000, 123.0000, 180_000L, null)

        val continuity = TripRouteContinuityBuilder.build(
            listOf(mainRouteStart, mainRouteEnd, isolatedOutlier)
        )

        assertEquals(listOf(mainRouteStart, mainRouteEnd), continuity.defaultFitPoints)
        assertFalse(continuity.defaultFitPoints.contains(isolatedOutlier))
        assertEquals(listOf(mainRouteStart, mainRouteEnd, isolatedOutlier), continuity.fullRouteFitPoints)
        assertEquals(1, continuity.gaps.size)
    }

    @Test
    fun `tiny distant fragment stays out of default fit but remains in full route`() {
        val mainRoute = listOf(
            TripGeoPoint(31.2000, 121.4000, 0L),
            TripGeoPoint(31.2010, 121.4010, 4_000L),
            TripGeoPoint(31.2020, 121.4020, 8_000L),
            TripGeoPoint(31.2030, 121.4030, 12_000L),
        )
        val tinyDistantFragment = listOf(
            TripGeoPoint(32.5000, 123.0000, 180_000L),
            TripGeoPoint(32.5010, 123.0010, 184_000L),
        )

        val continuity = TripRouteContinuityBuilder.build(mainRoute + tinyDistantFragment)

        assertEquals(mainRoute, continuity.defaultFitPoints)
        assertEquals(mainRoute + tinyDistantFragment, continuity.fullRouteFitPoints)
        assertEquals(
            TripGeoBounds(
                minLatitude = 31.2000,
                maxLatitude = 31.2030,
                minLongitude = 121.4000,
                maxLongitude = 121.4030,
            ),
            continuity.defaultBounds,
        )
        assertTrue(continuity.fullRouteBounds!!.maxLatitude > 32.0)
    }

    @Test
    fun `three point distant fragment stays out when the main route clearly dominates`() {
        val mainRoute = (0..8).map { index ->
            TripGeoPoint(
                latitude = 31.2000 + index * 0.001,
                longitude = 121.4000 + index * 0.001,
                capturedAtEpochMillis = index * 4_000L,
            )
        }
        val tinyDistantFragment = listOf(
            TripGeoPoint(32.5000, 123.0000, 180_000L),
            TripGeoPoint(32.5010, 123.0010, 184_000L),
            TripGeoPoint(32.5020, 123.0020, 188_000L),
        )

        val continuity = TripRouteContinuityBuilder.build(mainRoute + tinyDistantFragment)

        assertEquals(mainRoute, continuity.defaultFitPoints)
        assertEquals(mainRoute + tinyDistantFragment, continuity.fullRouteFitPoints)
    }

    @Test
    fun `multiple similarly substantial continuous segments remain visible by default`() {
        val first = listOf(
            TripGeoPoint(31.2000, 121.4000, 0L),
            TripGeoPoint(31.2010, 121.4010, 4_000L),
            TripGeoPoint(31.2020, 121.4020, 8_000L),
        )
        val second = listOf(
            TripGeoPoint(31.2500, 121.4500, 180_000L),
            TripGeoPoint(31.2510, 121.4510, 184_000L),
            TripGeoPoint(31.2520, 121.4520, 188_000L),
        )

        val continuity = TripRouteContinuityBuilder.build(first + second)

        assertEquals(first + second, continuity.defaultFitPoints)
        assertEquals(first + second, continuity.fullRouteFitPoints)
    }
}
