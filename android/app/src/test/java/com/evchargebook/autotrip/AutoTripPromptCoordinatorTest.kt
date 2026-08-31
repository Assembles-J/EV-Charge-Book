package com.evchargebook.autotrip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoTripPromptCoordinatorTest {
    @Test
    fun `device address hash is normalized before persistence`() {
        val lower = AutoTripPromptCoordinator.hashDeviceAddress("aa:bb:cc:dd:ee:ff")
        val upper = AutoTripPromptCoordinator.hashDeviceAddress("AA:BB:CC:DD:EE:FF")

        assertEquals(lower, upper)
        assertFalse(lower.contains("AA:BB"))
        assertEquals(64, lower.length)
    }

    @Test
    fun `notification id is stable for same session`() {
        val first = AutoTripNotificationController.notificationId("session-123")
        val second = AutoTripNotificationController.notificationId("session-123")

        assertEquals(first, second)
        assertTrue(first >= 31_000)
    }
}
