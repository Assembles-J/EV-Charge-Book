package com.evchargebook.ui.trip

import kotlin.math.abs
import kotlin.math.hypot

internal object TripMapGestureArbitration {
    fun shouldDisallowParentIntercept(
        pointerCount: Int,
        deltaX: Float,
        deltaY: Float,
        touchSlopPx: Float,
    ): Boolean {
        if (pointerCount >= 2) return true
        if (hypot(deltaX.toDouble(), deltaY.toDouble()) <= touchSlopPx) return true
        return abs(deltaX) >= abs(deltaY)
    }
}
