package com.evchargebook.data.repository

import androidx.room.withTransaction
import com.evchargebook.data.backup.BackupCodec
import com.evchargebook.data.backup.BackupPayload
import com.evchargebook.data.database.AppDatabase
import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.data.entity.VehicleEntity
import com.evchargebook.domain.ChargingRecordRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ChargingRepository(private val database: AppDatabase) {
    private val vehicleDao = database.vehicleDao()
    private val chargingRecordDao = database.chargingRecordDao()

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

    suspend fun exportBackup(appVersion: String): String {
        val payload = BackupPayload(
            schemaVersion = BackupCodec.CURRENT_SCHEMA_VERSION,
            exportedAt = System.currentTimeMillis(),
            appVersion = appVersion,
            vehicles = vehicleDao.getAll(),
            chargingRecords = chargingRecordDao.getAll()
        )
        val encoded = BackupCodec.encode(payload)
        val verified = BackupCodec.decode(encoded)
        require(verified.vehicles.size == payload.vehicles.size) { "车辆备份数量校验失败" }
        require(verified.chargingRecords.size == payload.chargingRecords.size) { "充电记录备份数量校验失败" }
        return encoded
    }

    suspend fun restoreBackup(content: String) {
        val payload = BackupCodec.decode(content)
        database.withTransaction {
            chargingRecordDao.deleteAll()
            vehicleDao.deleteAll()
            vehicleDao.insertAll(payload.vehicles)
            chargingRecordDao.insertAll(payload.chargingRecords)

            require(vehicleDao.getAll().size == payload.vehicles.size) { "车辆恢复数量校验失败" }
            require(chargingRecordDao.getAll().size == payload.chargingRecords.size) { "充电记录恢复数量校验失败" }
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
