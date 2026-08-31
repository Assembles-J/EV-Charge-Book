package com.evchargebook.data.repository

import androidx.room.withTransaction
import com.evchargebook.data.database.AppDatabase
import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.data.entity.ChargingSessionEntity
import com.evchargebook.data.entity.ChargingSessionStatus
import com.evchargebook.data.entity.VehicleStateEntity
import com.evchargebook.data.entity.VehicleStateUpdateSource
import com.evchargebook.domain.ChargingRecordRules
import kotlinx.coroutines.flow.Flow

/** Inputs known when the user explicitly chooses `开始充电`. */
data class StartChargingSessionRequest(
    val vehicleId: Long,
    val startedAtEpochMillis: Long = System.currentTimeMillis(),
    val startSoc: Int? = null,
    val targetSoc: Int? = null,
    val chargerType: String? = null,
    val unitPricePerKwh: Double? = null,
    val location: String? = null,
    val remark: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracyMeters: Double? = null,
)

/**
 * Final completion facts. The completion editor must pass its final visible values, including the
 * session defaults it kept. Null coordinates intentionally mean "no coordinates" so manually
 * replacing an address never keeps stale coordinates behind the scenes.
 */
data class CompleteChargingSessionRequest(
    val sessionId: String,
    val startSoc: Int,
    val endSoc: Int,
    val meterEnergyKwh: Double,
    val vehicleEnergyKwh: Double? = null,
    val totalCost: Double,
    val endedAtEpochMillis: Long = System.currentTimeMillis(),
    val odometerKm: Double? = null,
    val chargerType: String? = null,
    val location: String? = null,
    val remark: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracyMeters: Double? = null,
)

private data class ChargingVehicleStateFact<T>(
    val value: T,
    val timestamp: Long,
    val source: VehicleStateUpdateSource,
)

private fun <T> latestChargingFact(vararg facts: ChargingVehicleStateFact<T>?): ChargingVehicleStateFact<T>? =
    facts.filterNotNull().maxByOrNull { it.timestamp }

/**
 * Transaction boundary for the optional charging lifecycle.
 *
 * A session is not a completed charging record. It persists known start facts so process death does
 * not erase a user-started charge. Completion atomically inserts exactly one historical record and
 * marks the session completed; retries return the already-created record id.
 */
class ChargingSessionRepository(private val database: AppDatabase) {
    private val vehicleDao = database.vehicleDao()
    private val chargingRecordDao = database.chargingRecordDao()
    private val chargingSessionDao = database.chargingSessionDao()
    private val tripDao = database.tripDao()
    private val vehicleStateDao = database.vehicleStateDao()

    fun observeActiveForVehicle(vehicleId: Long): Flow<ChargingSessionEntity?> =
        chargingSessionDao.observeActiveForVehicle(vehicleId)

    suspend fun getActiveForVehicle(vehicleId: Long): ChargingSessionEntity? =
        chargingSessionDao.getActiveForVehicle(vehicleId)

    suspend fun start(request: StartChargingSessionRequest): String {
        validateStart(request)
        return database.withTransaction {
            val vehicle = vehicleDao.getAll().firstOrNull { it.id == request.vehicleId && !it.isArchived }
                ?: error("车辆不可用")
            require(vehicle.id == request.vehicleId)
            require(chargingSessionDao.getActiveForVehicle(request.vehicleId) == null) {
                "这辆车已有进行中的充电"
            }

            val now = System.currentTimeMillis()
            val session = ChargingSessionEntity(
                vehicleId = request.vehicleId,
                startedAtEpochMillis = request.startedAtEpochMillis,
                startSoc = request.startSoc,
                targetSoc = request.targetSoc,
                chargerType = request.chargerType.cleanText(),
                unitPricePerKwh = request.unitPricePerKwh,
                location = request.location.cleanText(),
                remark = request.remark.cleanText(),
                latitude = request.latitude,
                longitude = request.longitude,
                locationAccuracyMeters = request.locationAccuracyMeters,
                status = ChargingSessionStatus.ACTIVE,
                updatedAtEpochMillis = now,
            )
            chargingSessionDao.insert(session)
            session.id
        }
    }

    suspend fun updateActive(session: ChargingSessionEntity) {
        validateSessionFacts(session)
        database.withTransaction {
            val stored = chargingSessionDao.get(session.id) ?: error("充电会话不存在")
            require(stored.status == ChargingSessionStatus.ACTIVE) { "只有进行中的充电可以修改" }
            require(stored.vehicleId == session.vehicleId) { "充电车辆不能在会话中被替换" }
            chargingSessionDao.update(
                session.copy(
                    status = ChargingSessionStatus.ACTIVE,
                    endedAtEpochMillis = null,
                    completedRecordId = null,
                    chargerType = session.chargerType.cleanText(),
                    location = session.location.cleanText(),
                    remark = session.remark.cleanText(),
                    updatedAtEpochMillis = System.currentTimeMillis(),
                )
            )
        }
    }

    suspend fun cancel(sessionId: String, endedAtEpochMillis: Long = System.currentTimeMillis()) {
        database.withTransaction {
            val session = chargingSessionDao.get(sessionId) ?: error("充电会话不存在")
            when (session.status) {
                ChargingSessionStatus.CANCELLED -> return@withTransaction
                ChargingSessionStatus.COMPLETED -> error("已完成的充电不能取消")
                ChargingSessionStatus.ACTIVE -> Unit
                else -> error("未知充电会话状态")
            }
            require(endedAtEpochMillis >= session.startedAtEpochMillis) { "结束时间不能早于开始时间" }
            chargingSessionDao.update(
                session.copy(
                    status = ChargingSessionStatus.CANCELLED,
                    endedAtEpochMillis = endedAtEpochMillis,
                    completedRecordId = null,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                )
            )
        }
    }

    suspend fun complete(request: CompleteChargingSessionRequest): Long {
        return database.withTransaction {
            val session = chargingSessionDao.get(request.sessionId) ?: error("充电会话不存在")
            if (session.status == ChargingSessionStatus.COMPLETED) {
                return@withTransaction session.completedRecordId
                    ?: error("已完成充电缺少关联记录")
            }
            require(session.status == ChargingSessionStatus.ACTIVE) { "只有进行中的充电可以完成" }
            validateCompletion(session, request)

            val now = System.currentTimeMillis()
            val recordId = chargingRecordDao.insert(
                ChargingRecordEntity(
                    vehicleId = session.vehicleId,
                    chargeTimeEpochMillis = session.startedAtEpochMillis,
                    endedAtEpochMillis = request.endedAtEpochMillis,
                    energyKwh = request.meterEnergyKwh,
                    vehicleEnergyKwh = request.vehicleEnergyKwh,
                    cost = request.totalCost,
                    startSoc = request.startSoc,
                    endSoc = request.endSoc,
                    chargerType = request.chargerType.cleanText(),
                    location = request.location.cleanText(),
                    remark = request.remark.cleanText(),
                    odometerKm = request.odometerKm,
                    latitude = request.latitude,
                    longitude = request.longitude,
                    locationAccuracyMeters = request.locationAccuracyMeters,
                    updatedAtEpochMillis = now,
                )
            )

            chargingSessionDao.update(
                session.copy(
                    status = ChargingSessionStatus.COMPLETED,
                    endedAtEpochMillis = request.endedAtEpochMillis,
                    completedRecordId = recordId,
                    updatedAtEpochMillis = now,
                )
            )
            rebuildVehicleStateFromEvents(session.vehicleId)
            recordId
        }
    }

    private fun validateStart(request: StartChargingSessionRequest) {
        require(request.startedAtEpochMillis > 0L) { "开始时间无效" }
        require(request.startSoc == null || request.startSoc in 0..100) { "开始 SOC 必须在 0 到 100 之间" }
        require(request.targetSoc == null || request.targetSoc in 0..100) { "目标 SOC 必须在 0 到 100 之间" }
        if (request.startSoc != null && request.targetSoc != null) {
            require(request.targetSoc >= request.startSoc) { "目标 SOC 不能低于开始 SOC" }
        }
        require(request.unitPricePerKwh == null || request.unitPricePerKwh >= 0.0) { "电价不能小于 0" }
        require((request.latitude == null) == (request.longitude == null)) { "定位坐标不完整" }
        require(request.locationAccuracyMeters == null || request.locationAccuracyMeters >= 0.0) { "定位精度不能小于 0" }
    }

    private fun validateSessionFacts(session: ChargingSessionEntity) {
        require(session.status == ChargingSessionStatus.ACTIVE) { "只有进行中的充电可以修改" }
        require(session.startedAtEpochMillis > 0L) { "开始时间无效" }
        require(session.startSoc == null || session.startSoc in 0..100) { "开始 SOC 必须在 0 到 100 之间" }
        require(session.targetSoc == null || session.targetSoc in 0..100) { "目标 SOC 必须在 0 到 100 之间" }
        if (session.startSoc != null && session.targetSoc != null) {
            require(session.targetSoc >= session.startSoc) { "目标 SOC 不能低于开始 SOC" }
        }
        require(session.unitPricePerKwh == null || session.unitPricePerKwh >= 0.0) { "电价不能小于 0" }
        require((session.latitude == null) == (session.longitude == null)) { "定位坐标不完整" }
        require(session.locationAccuracyMeters == null || session.locationAccuracyMeters >= 0.0) { "定位精度不能小于 0" }
    }

    private fun validateCompletion(session: ChargingSessionEntity, request: CompleteChargingSessionRequest) {
        ChargingRecordRules.validate(
            request.startSoc,
            request.endSoc,
            request.meterEnergyKwh,
            request.totalCost,
            request.odometerKm,
        )
        require(request.endedAtEpochMillis > session.startedAtEpochMillis) { "结束时间必须晚于开始时间" }
        require(request.vehicleEnergyKwh == null || request.vehicleEnergyKwh >= 0.0) { "车辆侧充电量不能小于 0" }
        require(request.vehicleEnergyKwh == null || request.vehicleEnergyKwh <= request.meterEnergyKwh + 1e-6) {
            "车辆侧充电量不能高于桩端 / 电表电量"
        }
        require((request.latitude == null) == (request.longitude == null)) { "定位坐标不完整" }
        require(request.locationAccuracyMeters == null || request.locationAccuracyMeters >= 0.0) { "定位精度不能小于 0" }
    }

    private suspend fun rebuildVehicleStateFromEvents(vehicleId: Long) {
        val existing = vehicleStateDao.get(vehicleId)
        val manualState = existing?.takeIf { it.updateSource == VehicleStateUpdateSource.MANUAL_UPDATE.name }

        val latestCharge = chargingRecordDao.getLatestForVehicle(vehicleId)
        val latestTripSoc = tripDao.getLatestCompletedWithSocForVehicle(vehicleId)
        val socFact = latestChargingFact(
            latestCharge?.let {
                ChargingVehicleStateFact(it.endSoc, it.endedAtEpochMillis ?: it.chargeTimeEpochMillis, VehicleStateUpdateSource.CHARGE_RECORD)
            },
            latestTripSoc?.let {
                ChargingVehicleStateFact(it.endSoc!!, it.endedAtEpochMillis!!, VehicleStateUpdateSource.TRIP_END)
            },
            manualState?.currentSoc?.let {
                ChargingVehicleStateFact(it, manualState.updatedAtEpochMillis, VehicleStateUpdateSource.MANUAL_UPDATE)
            }
        )

        val latestChargeMileage = chargingRecordDao.getLatestWithOdometerForVehicle(vehicleId)
        val latestTripMileage = tripDao.getLatestCompletedWithMileageForVehicle(vehicleId)
        val mileageFact = latestChargingFact(
            latestChargeMileage?.odometerKm?.let {
                ChargingVehicleStateFact(
                    it,
                    latestChargeMileage.endedAtEpochMillis ?: latestChargeMileage.chargeTimeEpochMillis,
                    VehicleStateUpdateSource.CHARGE_RECORD,
                )
            },
            latestTripMileage?.endMileageKm?.let {
                ChargingVehicleStateFact(it, latestTripMileage.endedAtEpochMillis!!, VehicleStateUpdateSource.TRIP_END)
            },
            manualState?.currentMileage?.let {
                ChargingVehicleStateFact(it, manualState.updatedAtEpochMillis, VehicleStateUpdateSource.MANUAL_UPDATE)
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
                updateSource = source.name,
            )
        )
    }
}

private fun String?.cleanText(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
