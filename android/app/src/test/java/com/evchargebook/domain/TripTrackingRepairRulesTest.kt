package com.evchargebook.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripTrackingRepairRulesTest {
    @Test
    fun `missing location permission takes priority`() {
        assertEquals(
            TripTrackingRepairReason.LOCATION_PERMISSION_MISSING,
            TripTrackingRepairRules.evaluate(
                hasLocationPermission = false,
                hasUsableLocationProvider = false
            )
        )
    }

    @Test
    fun `enabled permission without provider requires system location repair`() {
        assertEquals(
            TripTrackingRepairReason.LOCATION_PROVIDER_DISABLED,
            TripTrackingRepairRules.evaluate(
                hasLocationPermission = true,
                hasUsableLocationProvider = false
            )
        )
    }

    @Test
    fun `healthy location state needs no repair`() {
        assertNull(
            TripTrackingRepairRules.evaluate(
                hasLocationPermission = true,
                hasUsableLocationProvider = true
            )
        )
    }
}
