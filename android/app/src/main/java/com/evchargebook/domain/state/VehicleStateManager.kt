package com.evchargebook.domain.state

import com.evchargebook.data.entity.VehicleStateEntity
import com.evchargebook.data.repository.VehicleStateRepository

class VehicleStateManager(
    private val repository: VehicleStateRepository
) {
    suspend fun updateAfterCharge(
        vehicleId: Long,
        endSoc: Int,
        mileage: Double? = null
    ) {
        repository.save(
            VehicleStateEntity(
                vehicleId = vehicleId,
                currentSoc = endSoc,
                currentMileage = mileage,
                updateSource = "CHARGE_RECORD"
            )
        )
    }

    suspend fun updateAfterTrip(
        vehicleId: Long,
        endSoc: Int,
        endMileage: Double?
    ) {
        repository.save(
            VehicleStateEntity(
                vehicleId = vehicleId,
                currentSoc = endSoc,
                currentMileage = endMileage,
                updateSource = "TRIP_END"
            )
        )
    }
}
