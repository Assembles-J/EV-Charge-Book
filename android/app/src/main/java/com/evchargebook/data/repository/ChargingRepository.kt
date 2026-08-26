package com.evchargebook.data.repository

import android.content.Context
import androidx.room.withTransaction
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.evchargebook.data.backup.BackupCodec
import com.evchargebook.data.backup.BackupPayload
import com.evchargebook.data.database.AppDatabase
import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.data.entity.VehicleEntity
import com.evchargebook.domain.ChargingRecordRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi

private val Context.vehicleSelectionDataStore by preferencesDataStore(name = "vehicle_selection")
private val selectedVehicleIdKey = longPreferencesKey("selected_vehicle_id")

@OptIn(ExperimentalCoroutinesApi::class)
class ChargingRepository(private val database: AppDatabase, private val context: Context) {
    private val vehicleDao = database.vehicleDao()
    private val chargingRecordDao = database.chargingRecordDao()

    val vehicles: Flow<List<VehicleEntity>> = vehicleDao.observeActive()
    private val selectedVehicleId: Flow<Long?> = context.vehicleSelectionDataStore.data.map { it[selectedVehicleIdKey] }
    val vehicle: Flow<VehicleEntity?> = combine(vehicles, selectedVehicleId) { vehicles, selectedId ->
        vehicles.firstOrNull { it.id == selectedId } ?: vehicles.firstOrNull { it.isDefault } ?: vehicles.firstOrNull()
    }
    val chargingRecords: Flow<List<ChargingRecordEntity>> = vehicle.flatMapLatest { selected ->
        selected?.let { chargingRecordDao.observeForVehicle(it.id) } ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }

    suspend fun ensureDefaultVehicle() {
        val activeVehicles = vehicles.first()
        if (activeVehicles.isEmpty()) {
            val id = vehicleDao.insert(
                VehicleEntity(
                    brand = "零跑",
                    model = "C16 2026款",
                    batteryCapacityKwh = 67.7,
                    rangeKm = 520,
                    isDefault = true
                )
            )
            selectVehicle(id)
        } else {
            val defaultVehicle = activeVehicles.firstOrNull { it.isDefault } ?: activeVehicles.first()
            if (!defaultVehicle.isDefault) vehicleDao.setDefault(defaultVehicle.id)
            if (selectedVehicleId.first() !in activeVehicles.map { it.id }) selectVehicle(defaultVehicle.id)
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

    suspend fun addVehicle(brand: String, model: String, battery: Double, range: Int) {
        val vehicle = VehicleEntity(
            brand = brand.trim(), model = model.trim(), batteryCapacityKwh = battery, rangeKm = range
        )
        validateVehicle(vehicle)
        val id = vehicleDao.insert(vehicle)
        selectVehicle(id)
    }

    suspend fun selectVehicle(vehicleId: Long) {
        require(vehicles.first().any { it.id == vehicleId }) { "车辆不可用" }
        database.withTransaction { vehicleDao.setDefault(vehicleId) }
        context.vehicleSelectionDataStore.edit { it[selectedVehicleIdKey] = vehicleId }
    }

    suspend fun archiveVehicle(vehicleId: Long) {
        database.withTransaction {
            val activeVehicles = vehicleDao.observeActive().first()
            require(activeVehicles.size > 1) { "请至少保留一辆车辆" }
            val vehicle = activeVehicles.firstOrNull { it.id == vehicleId } ?: error("车辆不可用")
            vehicleDao.update(vehicle.copy(isArchived = true, isDefault = false))
            val replacement = activeVehicles.first { it.id != vehicleId }
            vehicleDao.setDefault(replacement.id)
            context.vehicleSelectionDataStore.edit { it[selectedVehicleIdKey] = replacement.id }
        }
    }

    private fun validateVehicle(vehicle: VehicleEntity) {
        require(vehicle.brand.isNotBlank()) { "品牌不能为空" }
        require(vehicle.model.isNotBlank()) { "车型不能为空" }
        require(vehicle.batteryCapacityKwh > 0) { "电池容量必须大于 0" }
        require(vehicle.rangeKm > 0) { "续航必须大于 0" }
    }
}
