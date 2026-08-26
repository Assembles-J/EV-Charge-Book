package com.evchargebook.data.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncIdentityTest {
    @Test
    fun `new vehicles receive stable distinct sync ids`() {
        val first = VehicleEntity(brand = "A", model = "One", batteryCapacityKwh = 60.0, rangeKm = 500)
        val second = VehicleEntity(brand = "A", model = "Two", batteryCapacityKwh = 60.0, rangeKm = 500)

        assertTrue(first.syncId.isNotBlank())
        assertNotEquals(first.syncId, second.syncId)
        assertEquals(first.syncId, first.copy(model = "Edited").syncId)
        assertTrue(first.updatedAtEpochMillis > 0)
    }

    @Test
    fun `new charging records receive sync identity and are not tombstones`() {
        val first = record()
        val second = record()

        assertTrue(first.syncId.isNotBlank())
        assertNotEquals(first.syncId, second.syncId)
        assertFalse(first.isDeleted)
        assertEquals(first.syncId, first.copy(cost = 20.0).syncId)
        assertTrue(first.updatedAtEpochMillis > 0)
    }

    private fun record() = ChargingRecordEntity(
        vehicleId = 1,
        chargeTimeEpochMillis = 1_000,
        energyKwh = 20.0,
        cost = 10.0,
        startSoc = 20,
        endSoc = 80
    )
}
