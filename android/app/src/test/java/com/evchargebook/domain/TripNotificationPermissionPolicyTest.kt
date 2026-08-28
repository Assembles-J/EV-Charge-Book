package com.evchargebook.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripNotificationPermissionPolicyTest {
    @Test
    fun `requests once on android 13 plus when missing`() {
        assertTrue(
            TripNotificationPermissionPolicy.shouldRequest(
                apiLevel = 33,
                permissionGranted = false,
                alreadyRequestedForTrip = false
            )
        )
    }

    @Test
    fun `does not request on older android`() {
        assertFalse(
            TripNotificationPermissionPolicy.shouldRequest(
                apiLevel = 32,
                permissionGranted = false,
                alreadyRequestedForTrip = false
            )
        )
    }

    @Test
    fun `does not request when already granted`() {
        assertFalse(
            TripNotificationPermissionPolicy.shouldRequest(
                apiLevel = 35,
                permissionGranted = true,
                alreadyRequestedForTrip = false
            )
        )
    }

    @Test
    fun `does not nag again after Trip prompt was already shown`() {
        assertFalse(
            TripNotificationPermissionPolicy.shouldRequest(
                apiLevel = 35,
                permissionGranted = false,
                alreadyRequestedForTrip = true
            )
        )
    }
}
