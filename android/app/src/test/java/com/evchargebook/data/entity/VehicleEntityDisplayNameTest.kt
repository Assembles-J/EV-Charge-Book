package com.evchargebook.data.entity

import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleEntityDisplayNameTest {
    @Test
    fun `nickname wins over catalog model for display`() {
        val vehicle = VehicleEntity(
            catalogVehicleId = "future-brand-model-1",
            brand = "Future Brand",
            model = "Model One",
            batteryCapacityKwh = 80.0,
            rangeKm = 600,
            nickname = "通勤车"
        )

        assertEquals("通勤车", vehicle.displayName)
    }

    @Test
    fun `blank nickname falls back to immutable model snapshot`() {
        val vehicle = VehicleEntity(
            catalogVehicleId = "future-brand-model-1",
            brand = "Future Brand",
            model = "Model One",
            batteryCapacityKwh = 80.0,
            rangeKm = 600,
            nickname = "   "
        )

        assertEquals("Model One", vehicle.displayName)
    }
}
