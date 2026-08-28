package com.evchargebook.domain.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripRouteGeometryTest {
    @Test
    fun `normalizes a route into preview coordinates`() {
        val geometry = TripRouteGeometryBuilder.build(
            listOf(
                TripGeoPoint(31.2000, 121.4000),
                TripGeoPoint(31.2100, 121.4200),
                TripGeoPoint(31.2200, 121.4100)
            )
        )

        assertNotNull(geometry)
        geometry!!
        assertEquals(3, geometry.points.size)
        assertEquals(1, geometry.segments.size)
        assertEquals(0, geometry.gapCount)
        assertTrue(geometry.isDrawable)
        assertEquals(0f, geometry.points.first().x, 0.0001f)
        assertEquals(1f, geometry.points.first().y, 0.0001f)
        assertEquals(0f, geometry.points.last().y, 0.0001f)
        assertTrue(geometry.points.all { it.x in 0f..1f && it.y in 0f..1f })
    }

    @Test
    fun `keeps first and last points when downsampling`() {
        val source = (0 until 1000).map { index ->
            TripGeoPoint(30.0 + index * 0.0001, 120.0 + index * 0.0001)
        }

        val geometry = TripRouteGeometryBuilder.build(source, maxPoints = 100)!!

        assertEquals(100, geometry.points.size)
        assertEquals(source.first().latitude, geometry.minLatitude, 0.000001)
        assertEquals(source.last().latitude, geometry.maxLatitude, 0.000001)
        assertEquals(source.first().longitude, geometry.minLongitude, 0.000001)
        assertEquals(source.last().longitude, geometry.maxLongitude, 0.000001)
    }

    @Test
    fun `single point route remains non drawable`() {
        val geometry = TripRouteGeometryBuilder.build(listOf(TripGeoPoint(31.2, 121.4)))!!

        assertEquals(1, geometry.points.size)
        assertTrue(!geometry.isDrawable)
    }

    @Test
    fun `long GPS gap creates disconnected route segments`() {
        val geometry = TripRouteGeometryBuilder.build(
            listOf(
                TripGeoPoint(31.2000, 121.4000, 0L),
                TripGeoPoint(31.2010, 121.4010, 4_000L),
                TripGeoPoint(31.2500, 121.4500, 180_000L),
                TripGeoPoint(31.2510, 121.4510, 184_000L)
            )
        )!!

        assertEquals(2, geometry.segments.size)
        assertEquals(1, geometry.gapCount)
        assertEquals(2, geometry.segments[0].size)
        assertEquals(2, geometry.segments[1].size)
        assertTrue(geometry.isDrawable)
    }

    @Test
    fun `gap below threshold stays continuous`() {
        val geometry = TripRouteGeometryBuilder.build(
            listOf(
                TripGeoPoint(31.2000, 121.4000, 0L),
                TripGeoPoint(31.2010, 121.4010, 119_999L)
            )
        )!!

        assertEquals(1, geometry.segments.size)
        assertEquals(0, geometry.gapCount)
    }

    @Test
    fun `trusted speed metadata survives normalization and gap segmentation`() {
        val geometry = TripRouteGeometryBuilder.build(
            listOf(
                TripGeoPoint(31.2000, 121.4000, 0L, 3.0),
                TripGeoPoint(31.2010, 121.4010, 4_000L, 12.0),
                TripGeoPoint(31.2500, 121.4500, 180_000L, null),
                TripGeoPoint(31.2510, 121.4510, 184_000L, 25.0)
            )
        )!!

        assertEquals(3.0, geometry.segments[0][0].speedMps!!, 0.0001)
        assertEquals(12.0, geometry.segments[0][1].speedMps!!, 0.0001)
        assertNull(geometry.segments[1][0].speedMps)
        assertEquals(25.0, geometry.segments[1][1].speedMps!!, 0.0001)
    }
}
