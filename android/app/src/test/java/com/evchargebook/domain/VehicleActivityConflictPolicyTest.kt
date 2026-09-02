package com.evchargebook.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleActivityConflictPolicyTest {
    @Test
    fun `charging is blocked only by a trip for the same vehicle`() {
        assertEquals(
            "这辆车有进行中的行程，请先结束行程",
            VehicleActivityConflictPolicy.chargingStartBlockReason(
                vehicleId = 7L,
                activeTripVehicleId = 7L,
            ),
        )
        assertNull(
            VehicleActivityConflictPolicy.chargingStartBlockReason(
                vehicleId = 7L,
                activeTripVehicleId = 8L,
            )
        )
        assertNull(
            VehicleActivityConflictPolicy.chargingStartBlockReason(
                vehicleId = 7L,
                activeTripVehicleId = null,
            )
        )
    }

    @Test
    fun `trip is blocked only by active charging for the same vehicle`() {
        assertEquals(
            "这辆车正在充电，请先结束或取消充电",
            VehicleActivityConflictPolicy.tripStartBlockReason(
                vehicleId = 11L,
                activeChargingVehicleId = 11L,
            ),
        )
        assertNull(
            VehicleActivityConflictPolicy.tripStartBlockReason(
                vehicleId = 11L,
                activeChargingVehicleId = 12L,
            )
        )
        assertNull(
            VehicleActivityConflictPolicy.tripStartBlockReason(
                vehicleId = 11L,
                activeChargingVehicleId = null,
            )
        )
    }
}
