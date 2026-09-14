package com.evchargebook.ui.trip

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripMapGestureArbitrationTest {
    @Test
    fun `multi touch always stays with the map`() {
        assertTrue(
            TripMapGestureArbitration.shouldDisallowParentIntercept(
                pointerCount = 2,
                deltaX = 0f,
                deltaY = 24f,
                touchSlopPx = 8f,
            )
        )
    }

    @Test
    fun `small undecided movement stays with the map until touch slop is crossed`() {
        assertTrue(
            TripMapGestureArbitration.shouldDisallowParentIntercept(
                pointerCount = 1,
                deltaX = 3f,
                deltaY = 4f,
                touchSlopPx = 8f,
            )
        )
    }

    @Test
    fun `horizontal single finger drag stays with the map`() {
        assertTrue(
            TripMapGestureArbitration.shouldDisallowParentIntercept(
                pointerCount = 1,
                deltaX = 30f,
                deltaY = 5f,
                touchSlopPx = 8f,
            )
        )
    }

    @Test
    fun `vertical single finger drag releases the parent detail scroll`() {
        assertFalse(
            TripMapGestureArbitration.shouldDisallowParentIntercept(
                pointerCount = 1,
                deltaX = 4f,
                deltaY = 30f,
                touchSlopPx = 8f,
            )
        )
    }
}
