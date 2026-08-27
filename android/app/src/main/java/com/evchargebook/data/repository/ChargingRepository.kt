package com.evchargebook.data.repository

import android.bluetooth.BluetoothAdapter
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import com.evchargebook.bluetooth.BluetoothPromptPreferences
import com.evchargebook.bluetooth.BluetoothPromptSettings
import com.evchargebook.bluetooth.PairedBluetoothDevice
import com.evchargebook.data.backup.BackupCodec
import com.evchargebook.data.backup.BackupPayload
import com.evchargebook.data.database.AppDatabase
import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.data.entity.TripPointEntity
import com.evchargebook.data.entity.TripSessionEntity
import com.evchargebook.data.entity.TripStatus
import com.evchargebook.data.entity.VehicleCatalogEntity
import com.evchargebook.data.entity.VehicleEntity
import com.evchargebook.data.entity.VehicleStateEntity
import com.evchargebook.data.entity.VehicleStateUpdateSource
import com.evchargebook.domain.ChargingRecordRules
import com.evchargebook.domain.TripRules
import com.evchargebook.domain.trip.TripEnergyCalculator
import com.evchargebook.trip.TripTrackingService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private val Context.vehicleSelectionDataStore by preferencesDataStore(name = "vehicle_selection")
private val selectedVehicleIdKey = longPreferencesKey("selected_vehicle_id")

private data class VehicleStateFact<T>(
    val value: T,
    val timestamp: Long,
    val source: VehicleStateUpdateSource
)

private fun <T> latestFact(vararg facts: VehicleStateFact<T>?): VehicleStateFact<T>? =
    facts.filterNotNull().maxByOrNull { it.timestamp }

@OptIn(ExperimentalCoroutinesApi::class)
class ChargingRepository(private val database: AppDatabase, private val context: Context) {
    private val vehicleDao = database.vehicleDao()
    private val vehicleCatalogDao = database.vehicleCatalogDao()
    private val chargingRecordDao = database.chargingRecordDao()
    private val tripDao = database.tripDao()
    private val vehicleStateDao = database.vehicleStateDao()
    private val bluetoothPreferences = BluetoothPromptPreferences(context)

    val vehicles: Flow<List<VehicleEntity>> = vehicleDao.observeActive()
    val catalogVehicles: Flow<List<VehicleCatalogEntity>> = vehicleCatalogDao.observeAll()
    val bluetoothSettings: Flow<BluetoothPromptSettings> = bluetoothPreferences.settings
    private val selectedVehicleId: Flow<Long?> = context.vehicleSelectionDataStore.data.map { it[selectedVehicleIdKey] }
    val vehicle: Flow<VehicleEntity?> = combine(vehicles, selectedVehicleId) { vehicles, selectedId ->
        vehicles.firstOrNull { it.id == selectedId } ?: vehicles.firstOrNull { it.isDefault } ?: vehicles.firstOrNull()
    }
    val vehicleState: Flow<VehicleStateEntity?> = vehicle.flatMapLatest { selected ->
        selected?.let { vehicleStateDao.observe(it.id) } ?: flowOf(null)
    }
    val chargingRecords: Flow<List<ChargingRecordEntity>> = vehicle.flatMapLatest { selected ->
        selected?.let { chargingRecordDao.observeForVehicle(it.id) } ?: flowOf(emptyList())
    }
    val trips: Flow<List<TripSessionEntity>> = vehicle.flatMapLatest { selected ->
        selected?.let { tripDao.observeForVehicle(it.id) } ?: flowOf(emptyList())
    }
    val activeTrip: Flow<TripSessionEntity?> = tripDao.observeActive()

    fun observeTripPoints(tripId: Long): Flow<List<TripPointEntity>> = tripDao.observePoints(tripId)

    suspend fun ensureDefaultVehicle() {
        seedVehicleCatalog()
        val activeVehicles = vehicles.first()
        if (activeVehicles.isEmpty()) {
            val id = vehicleDao.insert(VehicleEntity(brand = "零跑", model = "C16 2026款", batteryCapacityKwh = 67.7, rangeKm = 520, isDefault = true))
            ensureVehicleState(id)
            selectVehicle(id)
        } else {
            val defaultVehicle = activeVehicles.firstOrNull { it.isDefault } ?: activeVehicles.first()
            if (!defaultVehicle.isDefault) vehicleDao.setDefault(defaultVehicle.id)
            if (selectedVehicleId.first() !in activeVehicles.map { it.id }) selectVehicle(defaultVehicle.id)
            activeVehicles.forEach { ensureVehicleState(it.id) }
        }
    }

    suspend fun exportBackup(appVersion: String): String {
        val payload = BackupPayload(
            schemaVersion = BackupCodec.CURRENT_SCHEMA_VERSION,
            exportedAt = System.currentTimeMillis(),
            appVersion = appVersion,
            vehicles = vehicleDao.getAll(),
            chargingRecords = chargingRecordDao.getAll(),
            tripSessions = tripDao.getAllSessions(),
            tripPoints = tripDao.getAllPoints()
        )
        val encoded = BackupCodec.encode(payload)
        val verified = BackupCodec.decode(encoded)
        require(verified.vehicles.size == payload.vehicles.size) { "车辆备份数量校验失败" }
        require(verified.chargingRecords.size == payload.chargingRecords.size) { "充电记录备份数量校验失败" }
        require(verified.tripSessions.size == payload.tripSessions.size) { "行程备份数量校验失败" }
        require(verified.tripPoints.size == payload.tripPoints.size) { "轨迹点备份数量校验失败" }
        return encoded
    }

    suspend fun restoreBackup(content: String) {
        val payload = BackupCodec.decode(content)
        database.withTransaction {
            require(tripDao.getActive() == null) { "请先结束当前行程，再恢复备份" }
            tripDao.deleteAllSessions()
            chargingRecordDao.deleteAll()
            vehicleStateDao.deleteAll()
            vehicleDao.deleteAll()
            vehicleDao.insertAll(payload.vehicles)
            chargingRecordDao.insertAll(payload.chargingRecords)
            tripDao.insertSessions(payload.tripSessions)
            tripDao.insertPoints(payload.tripPoints)
            payload.vehicles.forEach { rebuildVehicleStateFromEvents(it.id) }
            require(vehicleDao.getAll().size == payload.vehicles.size) { "车辆恢复数量校验失败" }
            require(chargingRecordDao.getAll().size == payload.chargingRecords.size) { "充电记录恢复数量校验失败" }
            require(tripDao.getAllSessions().size == payload.tripSessions.size) { "行程恢复数量校验失败" }
            require(tripDao.getAllPoints().size == payload.tripPoints.size) { "轨迹点恢复数量校验失败" }
        }
    }

    suspend fun startTrip(vehicleId: Long, startedAtEpochMillis: Long = System.currentTimeMillis()): Long {
        val tripId = database.withTransaction {
            val selectedVehicle = vehicleDao.observeActive().first().firstOrNull { it.id == vehicleId }
                ?: error("当前车辆不可用")
            TripRules.requireCanStart(tripDao.getActive() != null)
            val state = vehicleStateDao.get(vehicleId)
            tripDao.insertSession(
                TripSessionEntity(
                    vehicleId = selectedVehicle.id,
                    startedAtEpochMillis = startedAtEpochMillis,
                    startSoc = state?.currentSoc,
                    startMileageKm = state?.currentMileage,
                    status = TripStatus.RECORDING
                )
            )
        }
        startTrackingOrInterrupt(tripId)
        return tripId
    }

    suspend fun resumeTrip(tripId: Long) {
        database.withTransaction {
            val trip = tripDao.getSession(tripId) ?: error("行程不存在")
            require(trip.status in setOf(TripStatus.RECORDING, TripStatus.INTERRUPTED)) { "只有未完成行程可以恢复" }
            val active = tripDao.getActive()
            require(active == null || active.id == tripId) { "已有其他进行中的行程" }
            if (trip.status == TripStatus.INTERRUPTED) tripDao.updateSession(trip.copy(status = TripStatus.RECORDING))
        }
        startTrackingOrInterrupt(tripId)
    }

    private suspend fun startTrackingOrInterrupt(tripId: Long) {
        try {
            TripTrackingService.start(context, tripId)
        } catch (error: Throwable) {
            database.withTransaction {
                tripDao.getSession(tripId)?.let { session ->
                    if (session.status == TripStatus.RECORDING) tripDao.updateSession(session.copy(status = TripStatus.INTERRUPTED))
                }
            }
            throw error
        }
    }

    suspend fun stopActiveTrip(
        endSoc: Int,
        endMileageKm: Double? = null,
        endedAtEpochMillis: Long = System.currentTimeMillis()
    ) {
        require(endSoc in 0..100) { "结束 SOC 必须在 0 到 100 之间" }
        database.withTransaction {
            val active = tripDao.getActive() ?: error("当前没有进行中的行程")
            require(endMileageKm == null || endMileageKm >= 0.0) { "结束里程不能小于 0" }
            if (active.startMileageKm != null && endMileageKm != null) {
                require(endMileageKm >= active.startMileageKm) { "结束里程不能低于开始里程" }
            }

            val selectedVehicle = vehicleDao.observeActive().first().firstOrNull { it.id == active.vehicleId }
                ?: error("行程车辆不可用")
            val derivedEndMileage = endMileageKm ?: active.startMileageKm?.plus(active.distanceMeters / 1000.0)
            val estimate = TripEnergyCalculator.estimate(
                batteryCapacityKwh = selectedVehicle.batteryCapacityKwh,
                startSoc = active.startSoc,
                endSoc = endSoc,
                distanceMeters = active.distanceMeters
            )
            tripDao.updateSession(
                active.copy(
                    endedAtEpochMillis = endedAtEpochMillis,
                    elapsedSeconds = TripRules.elapsedSeconds(active.startedAtEpochMillis, endedAtEpochMillis),
                    endSoc = endSoc,
                    endMileageKm = derivedEndMileage,
                    consumedEnergyKwh = estimate.consumedEnergyKwh,
                    averageConsumptionKwhPer100Km = estimate.averageConsumptionKwhPer100Km,
                    status = TripStatus.COMPLETED
                )
            )
            rebuildVehicleStateFromEvents(active.vehicleId)
        }
        TripTrackingService.stop(context)
    }

    suspend fun deleteTrip(trip: TripSessionEntity) {
        require(trip.status == TripStatus.COMPLETED) { "进行中的行程不能删除" }
        database.withTransaction {
            tripDao.deleteSession(trip)
            rebuildVehicleStateFromEvents(trip.vehicleId)
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
        odometerKm: Double? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        locationAccuracyMeters: Double? = null
    ) {
        ChargingRecordRules.validate(startSoc, endSoc, energyKwh, cost, odometerKm)
        require((latitude == null) == (longitude == null)) { "定位坐标不完整" }
        database.withTransaction {
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
                    odometerKm = odometerKm,
                    latitude = latitude,
                    longitude = longitude,
                    locationAccuracyMeters = locationAccuracyMeters
                )
            )
            rebuildVehicleStateFromEvents(vehicleId)
        }
    }

    suspend fun deleteChargingRecord(record: ChargingRecordEntity) {
        database.withTransaction {
            chargingRecordDao.markDeleted(record.id, System.currentTimeMillis())
            rebuildVehicleStateFromEvents(record.vehicleId)
        }
    }

    suspend fun updateChargingRecord(record: ChargingRecordEntity) {
        ChargingRecordRules.validate(record.startSoc, record.endSoc, record.energyKwh, record.cost, record.odometerKm)
        require((record.latitude == null) == (record.longitude == null)) { "定位坐标不完整" }
        database.withTransaction {
            chargingRecordDao.update(
                record.copy(
                    location = record.location?.trim()?.takeIf { it.isNotEmpty() },
                    remark = record.remark?.trim()?.takeIf { it.isNotEmpty() },
                    updatedAtEpochMillis = System.currentTimeMillis()
                )
            )
            rebuildVehicleStateFromEvents(record.vehicleId)
        }
    }

    suspend fun saveVehicle(vehicle: VehicleEntity) {
        validateVehicle(vehicle)
        val updated = vehicle.copy(updatedAtEpochMillis = System.currentTimeMillis())
        if (vehicle.id == 0L) vehicleDao.insert(updated) else vehicleDao.update(updated)
    }

    suspend fun addVehicle(brand: String, model: String, battery: Double, range: Int, catalogVehicleId: String? = null) {
        val vehicle = VehicleEntity(catalogVehicleId = catalogVehicleId, brand = brand.trim(), model = model.trim(), batteryCapacityKwh = battery, rangeKm = range)
        validateVehicle(vehicle)
        val id = vehicleDao.insert(vehicle)
        ensureVehicleState(id)
        selectVehicle(id)
    }

    suspend fun selectVehicle(vehicleId: Long) {
        require(vehicles.first().any { it.id == vehicleId }) { "车辆不可用" }
        database.withTransaction { vehicleDao.setDefault(vehicleId) }
        context.vehicleSelectionDataStore.edit { it[selectedVehicleIdKey] = vehicleId }
    }

    suspend fun archiveVehicle(vehicleId: Long) {
        database.withTransaction {
            val activeTrip = tripDao.getActive()
            require(activeTrip?.vehicleId != vehicleId) { "请先结束这辆车的当前行程" }
            val activeVehicles = vehicleDao.observeActive().first()
            require(activeVehicles.size > 1) { "请至少保留一辆车辆" }
            val vehicle = activeVehicles.firstOrNull { it.id == vehicleId } ?: error("车辆不可用")
            vehicleDao.update(vehicle.copy(isArchived = true, isDefault = false, updatedAtEpochMillis = System.currentTimeMillis()))
            val replacement = activeVehicles.first { it.id != vehicleId }
            vehicleDao.setDefault(replacement.id)
            context.vehicleSelectionDataStore.edit { it[selectedVehicleIdKey] = replacement.id }
        }
    }

    fun pairedBluetoothDevices(): List<PairedBluetoothDevice> = runCatching {
        BluetoothAdapter.getDefaultAdapter()?.bondedDevices.orEmpty().map { PairedBluetoothDevice(it.address, it.name ?: "未命名设备") }.sortedBy { it.name }
    }.getOrDefault(emptyList())

    suspend fun saveBluetoothPrompt(enabled: Boolean, deviceAddress: String?, deviceName: String?) = bluetoothPreferences.save(enabled, deviceAddress, deviceName)

    private suspend fun ensureVehicleState(vehicleId: Long) {
        if (vehicleStateDao.get(vehicleId) == null) {
            rebuildVehicleStateFromEvents(vehicleId)
        }
    }

    private suspend fun rebuildVehicleStateFromEvents(vehicleId: Long) {
        val existing = vehicleStateDao.get(vehicleId)
        val manualState = existing?.takeIf { it.updateSource == VehicleStateUpdateSource.MANUAL_UPDATE.name }

        val latestCharge = chargingRecordDao.getLatestForVehicle(vehicleId)
        val latestTripSoc = tripDao.getLatestCompletedWithSocForVehicle(vehicleId)
        val socFact = latestFact(
            latestCharge?.let {
                VehicleStateFact(it.endSoc, it.chargeTimeEpochMillis, VehicleStateUpdateSource.CHARGE_RECORD)
            },
            latestTripSoc?.let {
                VehicleStateFact(it.endSoc!!, it.endedAtEpochMillis!!, VehicleStateUpdateSource.TRIP_END)
            },
            manualState?.currentSoc?.let {
                VehicleStateFact(it, manualState.updatedAtEpochMillis, VehicleStateUpdateSource.MANUAL_UPDATE)
            }
        )

        val latestChargeMileage = chargingRecordDao.getLatestWithOdometerForVehicle(vehicleId)
        val latestTripMileage = tripDao.getLatestCompletedWithMileageForVehicle(vehicleId)
        val mileageFact = latestFact(
            latestChargeMileage?.odometerKm?.let {
                VehicleStateFact(it, latestChargeMileage.chargeTimeEpochMillis, VehicleStateUpdateSource.CHARGE_RECORD)
            },
            latestTripMileage?.endMileageKm?.let {
                VehicleStateFact(it, latestTripMileage.endedAtEpochMillis!!, VehicleStateUpdateSource.TRIP_END)
            },
            manualState?.currentMileage?.let {
                VehicleStateFact(it, manualState.updatedAtEpochMillis, VehicleStateUpdateSource.MANUAL_UPDATE)
            }
        )

        val updatedAt = maxOf(socFact?.timestamp ?: 0L, mileageFact?.timestamp ?: 0L)
            .takeIf { it > 0L }
            ?: System.currentTimeMillis()
        val source = when {
            socFact == null && mileageFact == null -> VehicleStateUpdateSource.UNKNOWN
            socFact == null -> mileageFact!!.source
            mileageFact == null -> socFact.source
            mileageFact.timestamp > socFact.timestamp -> mileageFact.source
            else -> socFact.source
        }

        vehicleStateDao.upsert(
            VehicleStateEntity(
                vehicleId = vehicleId,
                currentSoc = socFact?.value,
                currentMileage = mileageFact?.value,
                updatedAtEpochMillis = updatedAt,
                updateSource = source.name
            )
        )
    }

    private fun validateVehicle(vehicle: VehicleEntity) {
        require(vehicle.brand.isNotBlank()) { "品牌不能为空" }
        require(vehicle.model.isNotBlank()) { "车型不能为空" }
        require(vehicle.batteryCapacityKwh > 0) { "电池容量必须大于 0" }
        require(vehicle.rangeKm > 0) { "续航必须大于 0" }
    }

    private suspend fun seedVehicleCatalog() {
        val json = context.assets.open("vehicle_catalog.json").bufferedReader().use { it.readText() }
        val items = JSONArray(json).let { array ->
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                VehicleCatalogEntity(
                    catalogId = item.getString("catalogId"), source = item.getString("source"), brand = item.getString("brand"), series = item.getString("series"), modelName = item.getString("modelName"),
                    modelYear = item.optInt("modelYear").takeIf { it != 0 }, trimName = item.optString("trimName").takeIf { it.isNotBlank() }, powertrainType = item.getString("powertrainType"),
                    batteryCapacityKwh = item.optDouble("batteryCapacityKwh").takeIf { !it.isNaN() }, rangeKm = item.optInt("rangeKm").takeIf { it != 0 }
                )
            }
        }
        vehicleCatalogDao.insertAll(items)
    }
}
