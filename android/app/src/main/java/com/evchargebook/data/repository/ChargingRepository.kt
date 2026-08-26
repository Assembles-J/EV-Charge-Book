package com.evchargebook.data.repository

import com.evchargebook.data.dao.ChargingRecordDao
import com.evchargebook.data.dao.VehicleDao
import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.data.entity.VehicleEntity
import com.evchargebook.domain.ChargingRecordRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ChargingRepository(
    private val vehicleDao: VehicleDao,
    private val chargingRecordDao: ChargingRecordDao
) {
    val vehicle: Flow<VehicleEntity?> = vehicleDao.observePrimaryVehicle()
    val chargingRecords: Flow<List<ChargingRecordEntity>> = chargingRecordDao.observeAll()

    suspend fun ensureDefaultVehicle() {
        if (vehicle.first() == null) {
            vehicleDao.insert(
                VehicleEntity(
                    brand = "零跑",
                    model = "C16 2026款",
                    batteryCapacityKwh = 67.7,
                    rangeKm = 520
                )
            )
        }
    }

    suspend fun addChargingRecord(
        vehicleId: Long,
        startSoc: Int,
        endSoc: Int,
        energyKwh: Double,
        cost: Double,
        location: String?,
        chargerType: String? = null,
        remark: String? = null,
        chargeTimeEpochMillis: Long = System.currentTimeMillis(),
        odometerKm: Double? = null
    ) {
        ChargingRecordRules.validate(startSoc, endSoc, energyKwh, cost, odometerKm)

        chargingRecordDao.insert(
            ChargingRecordEntity(
                vehicleId = vehicleId,
                chargeTimeEpochMillis = chargeTimeEpochMillis,
                startSoc = startSoc,
                endSoc = endSoc,
                energyKwh = energyKwh,
                cost = cost,
                location = location?.trim()?.takeIf { it.isNotEmpty() },
                chargerType = chargerType,
                remark = remark?.trim()?.takeIf { it.isNotEmpty() },
                odometerKm = odometerKm
            )
        )
    }

    suspend fun deleteChargingRecord(record: ChargingRecordEntity) {
        chargingRecordDao.delete(record)
    }

    suspend fun updateChargingRecord(record: ChargingRecordEntity) {
        ChargingRecordRules.validate(record.startSoc, record.endSoc, record.energyKwh, record.cost, record.odometerKm)
        chargingRecordDao.update(
            record.copy(
                location = record.location?.trim()?.takeIf { it.isNotEmpty() },
                remark = record.remark?.trim()?.takeIf { it.isNotEmpty() }
            )
        )
    }

    suspend fun saveVehicle(vehicle: VehicleEntity) {
        require(vehicle.brand.isNotBlank()) { "品牌不能为空" }
        require(vehicle.model.isNotBlank()) { "车型不能为空" }
        require(vehicle.batteryCapacityKwh > 0) { "电池容量必须大于 0" }
        require(vehicle.rangeKm > 0) { "续航必须大于 0" }

        if (vehicle.id == 0L) {
            vehicleDao.insert(vehicle)
        } else {
            vehicleDao.update(vehicle)
        }
    }
}
