package com.evchargebook.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TripNotificationProgressTest {
    @Test
    fun `formats sub-hour trip progress compactly`() {
        assertEquals("12分34秒 · 5.42 km", TripNotificationProgress.format(754, 5_420.0))
    }

    @Test
    fun `formats long trip without seconds noise`() {
        assertEquals("1小时23分 · 860 m", TripNotificationProgress.format(5_020, 860.0))
    }

    @Test
    fun `sanitizes invalid values`() {
        assertEquals("0分0秒 · 0 m", TripNotificationProgress.format(-3, Double.NaN))
    }
}
