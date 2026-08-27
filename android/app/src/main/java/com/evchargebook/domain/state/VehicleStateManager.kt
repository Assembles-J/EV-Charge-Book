package com.evchargebook.domain.state

import com.evchargebook.data.entity.VehicleStateEntity
import com.evchargebook.data.repository.VehicleStateRepository

class VehicleStateManager(
    private val repository: VehicleStateRepository
) {
    suspend fun updateAfterCharge(
        vehicleId: Long,
        endSoc: Int,
        mileage: Int? = null
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
        mileage: Int?
    ) {
        repository.save(
            VehicleStateEntity(
                vehicleId = vehicleId,
                currentSoc = endSoc,
                currentMileage = mileage,
                updateSource = "TRIP_END"
            )
        )
    }

    suspend fun manualUpdate(
        vehicleId: Long,
        soc: Int?,
        mileage: Int?
    ) {
        repository.save(
            VehicleStateEntity(
                vehicleId = vehicleId,
                currentSoc = soc,
                currentMileage = mileage,
                updateSource = "MANUAL_UPDATE"
            )
        )
    }
}
