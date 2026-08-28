package com.evchargebook.domain.state

import com.evchargebook.data.entity.VehicleStateEntity
import com.evchargebook.data.entity.VehicleStateUpdateSource
import com.evchargebook.data.repository.VehicleStateRepository

class VehicleStateManager(
    private val repository: VehicleStateRepository
) {
    suspend fun updateAfterCharge(vehicleId: Long, endSoc: Int, odometerKm: Double? = null) {
        require(endSoc in 0..100) { "结束 SOC 必须在 0 到 100 之间" }
        update(
            vehicleId = vehicleId,
            currentSoc = endSoc,
            currentMileage = odometerKm,
            source = VehicleStateUpdateSource.CHARGE_RECORD
        )
    }

    suspend fun updateAfterTrip(vehicleId: Long, endSoc: Int?, endMileageKm: Double? = null) {
        require(endSoc == null || endSoc in 0..100) { "结束 SOC 必须在 0 到 100 之间" }
        update(
            vehicleId = vehicleId,
            currentSoc = endSoc,
            currentMileage = endMileageKm,
            source = VehicleStateUpdateSource.TRIP_END
        )
    }

    suspend fun manualUpdate(vehicleId: Long, currentSoc: Int?, currentMileageKm: Double? = null) {
        require(currentSoc == null || currentSoc in 0..100) { "SOC 必须在 0 到 100 之间" }
        update(
            vehicleId = vehicleId,
            currentSoc = currentSoc,
            currentMileage = currentMileageKm,
            source = VehicleStateUpdateSource.MANUAL_UPDATE
        )
    }

    private suspend fun update(
        vehicleId: Long,
        currentSoc: Int?,
        currentMileage: Double?,
        source: VehicleStateUpdateSource
    ) {
        val existing = repository.get(vehicleId)
        repository.save(
            VehicleStateEntity(
                vehicleId = vehicleId,
                currentSoc = currentSoc ?: existing?.currentSoc,
                currentMileage = currentMileage ?: existing?.currentMileage,
                updatedAtEpochMillis = System.currentTimeMillis(),
                updateSource = source.name
            )
        )
    }
}
