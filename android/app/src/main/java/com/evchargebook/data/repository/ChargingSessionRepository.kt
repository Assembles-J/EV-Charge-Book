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
 * Final completion facts when billing / meter data is already known at charge end.
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

/**
 * Physical charging has ended but final meter / billing data may still be unavailable.
 *
 * Optional billing values are retained only when the user already knows them. Unknown values stay
 * null; zero is never used as an "unknown" sentinel.
 */
data class DeferChargingCompletionRequest(
    val sessionId: String,
    val startSoc: Int,
    val endSoc: Int,
    val endedAtEpochMillis: Long = System.currentTimeMillis(),
    val odometerKm: Double? = null,
    val meterEnergyKwh: Double? = null,
    val totalCost: Double? = null,
    val vehicleEnergyKwh: Double? = null,
    val location: String? = null,
    val remark: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracyMeters: Double? = null,
)

/** Final billing facts supplied later for a `PENDING_DETAILS` session. */
data class BackfillChargingSessionRequest(
    val sessionId: String,
    val meterEnergyKwh: Double,
    val totalCost: Double,
    val vehicleEnergyKwh: Double? = null,
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
 * `ACTIVE` means physical charging is still in progress. `PENDING_DETAILS` means physical charging
 * has ended and end facts are durable, but billing facts are not complete enough for a historical
 * charging record. `COMPLETED` owns exactly one historical record id.
 */
class ChargingSessionRepository(private val database: AppDatabase) {
    private val vehicleDao = database.vehicleDao()
    private val chargingRecordDao = database.chargingRecordDao()
    private val chargingSessionDao = database.chargingSessionDao()
    private val tripDao = database.tripDao()
    private val vehicleStateDao = database.vehicleStateDao()

    fun observeActiveForVehicle(vehicleId: Long): Flow<ChargingSessionEntity?> =
        chargingSessionDao.observeActiveForVehicle(vehicleId)

    fun observePendingForVehicle(vehicleId: Long): Flow<List<ChargingSessionEntity>> =
        chargingSessionDao.observePendingForVehicle(vehicleId)

    suspend fun getActiveForVehicle(vehicleId: Long): ChargingSessionEntity? =
        chargingSessionDao.getActiveForVehicle(vehicleId)

    suspend fun getPendingForVehicle(vehicleId: Long): List<ChargingSessionEntity> =
        chargingSessionDao.getPendingForVehicle(vehicleId)

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
                    endSoc = null,
                    odometerKm = null,
                    pendingMeterEnergyKwh = null,
                    pendingTotalCost = null,
                    pendingVehicleEnergyKwh = null,
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
                ChargingSessionStatus.PENDING_DETAILS -> error("充电已结束并待补录；请补录或删除此次充电")
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

    /**
     * End physical charging without inventing missing meter facts.
     *
     * The pending session immediately becomes a vehicle-state fact, but it does not create a
     * `ChargingRecordEntity` and therefore cannot enter charging cost / energy statistics.
     */
    suspend fun deferCompletion(request: DeferChargingCompletionRequest) {
        database.withTransaction {
            val session = chargingSessionDao.get(request.sessionId) ?: error("充电会话不存在")
            when (session.status) {
                ChargingSessionStatus.PENDING_DETAILS -> return@withTransaction
                ChargingSessionStatus.COMPLETED -> error("本次充电已经完成")
                ChargingSessionStatus.CANCELLED -> error("已取消的充电不能结束")
                ChargingSessionStatus.ACTIVE -> Unit
                else -> error("未知充电会话状态")
            }
            validateDeferredCompletion(session, request)
            chargingSessionDao.update(
                session.copy(
                    startSoc = request.startSoc,
                    status = ChargingSessionStatus.PENDING_DETAILS,
                    endedAtEpochMillis = request.endedAtEpochMillis,
                    endSoc = request.endSoc,
                    odometerKm = request.odometerKm,
                    pendingMeterEnergyKwh = request.meterEnergyKwh,
                    pendingTotalCost = request.totalCost,
                    pendingVehicleEnergyKwh = request.vehicleEnergyKwh,
                    location = request.location.cleanText(),
                    remark = request.remark.cleanText(),
                    latitude = request.latitude,
                    longitude = request.longitude,
                    locationAccuracyMeters = request.locationAccuracyMeters,
                    completedRecordId = null,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                )
            )
            rebuildVehicleStateFromEvents(session.vehicleId)
        }
    }

    /** Delete an ended-but-unbilled session without creating a historical record. */
    suspend fun discardPending(sessionId: String) {
        database.withTransaction {
            val session = chargingSessionDao.get(sessionId) ?: error("充电会话不存在")
            if (session.status == ChargingSessionStatus.CANCELLED) return@withTransaction
            require(session.status == ChargingSessionStatus.PENDING_DETAILS) { "只有待补录充电可以删除" }
            chargingSessionDao.update(
                session.copy(
                    status = ChargingSessionStatus.CANCELLED,
                    completedRecordId = null,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                )
            )
            rebuildVehicleStateFromEvents(session.vehicleId)
        }
    }

    suspend fun complete(request: CompleteChargingSessionRequest): Long {
        return database.withTransaction {
            val session = chargingSessionDao.get(request.sessionId) ?: error("充电会话不存在")
            if (session.status == ChargingSessionStatus.COMPLETED) {
                return@withTransaction session.completedRecordId
                    ?: error("已完成充电缺少关联记录")
            }
            require(session.status == ChargingSessionStatus.ACTIVE) { "只有进行中的充电可以直接完成" }
            validateCompletion(session, request)

            val recordId = insertCompletedRecord(
                session = session,
                startSoc = request.startSoc,
                endSoc = request.endSoc,
                endedAtEpochMillis = request.endedAtEpochMillis,
                meterEnergyKwh = request.meterEnergyKwh,
                vehicleEnergyKwh = request.vehicleEnergyKwh,
                totalCost = request.totalCost,
                odometerKm = request.odometerKm,
                chargerType = request.chargerType,
                location = request.location,
                remark = request.remark,
                latitude = request.latitude,
                longitude = request.longitude,
                locationAccuracyMeters = request.locationAccuracyMeters,
            )
            recordId
        }
    }

    /** Finalize one pending session exactly once after delayed meter / billing data arrives. */
    suspend fun backfill(request: BackfillChargingSessionRequest): Long {
        return database.withTransaction {
            val session = chargingSessionDao.get(request.sessionId) ?: error("充电会话不存在")
            if (session.status == ChargingSessionStatus.COMPLETED) {
                return@withTransaction session.completedRecordId
                    ?: error("已完成充电缺少关联记录")
            }
            require(session.status == ChargingSessionStatus.PENDING_DETAILS) { "只有待补录充电可以补充电表数据" }
            val startSoc = session.startSoc ?: error("待补录充电缺少开始 SOC")
            val endSoc = session.endSoc ?: error("待补录充电缺少结束 SOC")
            val endedAt = session.endedAtEpochMillis ?: error("待补录充电缺少结束时间")
            val vehicleEnergy = request.vehicleEnergyKwh ?: session.pendingVehicleEnergyKwh
            validateBillingFacts(request.meterEnergyKwh, request.totalCost, vehicleEnergy)
            ChargingRecordRules.validate(startSoc, endSoc, request.meterEnergyKwh, request.totalCost, session.odometerKm)

            insertCompletedRecord(
                session = session,
                startSoc = startSoc,
                endSoc = endSoc,
                endedAtEpochMillis = endedAt,
                meterEnergyKwh = request.meterEnergyKwh,
                vehicleEnergyKwh = vehicleEnergy,
                totalCost = request.totalCost,
                odometerKm = session.odometerKm,
                chargerType = session.chargerType,
                location = session.location,
                remark = session.remark,
                latitude = session.latitude,
                longitude = session.longitude,
                locationAccuracyMeters = session.locationAccuracyMeters,
            )
        }
    }

    private suspend fun insertCompletedRecord(
        session: ChargingSessionEntity,
        startSoc: Int,
        endSoc: Int,
        endedAtEpochMillis: Long,
        meterEnergyKwh: Double,
        vehicleEnergyKwh: Double?,
        totalCost: Double,
        odometerKm: Double?,
        chargerType: String?,
        location: String?,
        remark: String?,
        latitude: Double?,
        longitude: Double?,
        locationAccuracyMeters: Double?,
    ): Long {
        validateBillingFacts(meterEnergyKwh, totalCost, vehicleEnergyKwh)
        val now = System.currentTimeMillis()
        val recordId = chargingRecordDao.insert(
            ChargingRecordEntity(
                vehicleId = session.vehicleId,
                chargeTimeEpochMillis = session.startedAtEpochMillis,
                endedAtEpochMillis = endedAtEpochMillis,
                energyKwh = meterEnergyKwh,
                vehicleEnergyKwh = vehicleEnergyKwh,
                cost = totalCost,
                startSoc = startSoc,
                endSoc = endSoc,
                chargerType = chargerType.cleanText(),
                location = location.cleanText(),
                remark = remark.cleanText(),
                odometerKm = odometerKm,
                latitude = latitude,
                longitude = longitude,
                locationAccuracyMeters = locationAccuracyMeters,
                updatedAtEpochMillis = now,
            )
        )

        chargingSessionDao.update(
            session.copy(
                startSoc = startSoc,
                status = ChargingSessionStatus.COMPLETED,
                endedAtEpochMillis = endedAtEpochMillis,
                endSoc = endSoc,
                odometerKm = odometerKm,
                pendingMeterEnergyKwh = meterEnergyKwh,
                pendingTotalCost = totalCost,
                pendingVehicleEnergyKwh = vehicleEnergyKwh,
                completedRecordId = recordId,
                chargerType = chargerType.cleanText(),
                location = location.cleanText(),
                remark = remark.cleanText(),
                latitude = latitude,
                longitude = longitude,
                locationAccuracyMeters = locationAccuracyMeters,
                updatedAtEpochMillis = now,
            )
        )
        rebuildVehicleStateFromEvents(session.vehicleId)
        return recordId
    }

    private fun validateStart(request: StartChargingSessionRequest) {
        require(request.startedAtEpochMillis > 0L) { "开始时间无效" }
        require(request.startSoc == null || request.startSoc in 0..100) { "开始 SOC 必须在 0 到 100 之间" }
        require(request.targetSoc == null || request.targetSoc in 0..100) { "目标 SOC 必须在 0 到 100 之间" }
        if (request.startSoc != null && request.targetSoc != null) {
            require(request.targetSoc >= request.startSoc) { "目标 SOC 不能低于开始 SOC" }
        }
        require(request.unitPricePerKwh == null || request.unitPricePerKwh >= 0.0) { "电价不能小于 0" }
        validateLocationFacts(request.latitude, request.longitude, request.locationAccuracyMeters)
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
        validateLocationFacts(session.latitude, session.longitude, session.locationAccuracyMeters)
    }

    private fun validateDeferredCompletion(
        session: ChargingSessionEntity,
        request: DeferChargingCompletionRequest,
    ) {
        require(request.startSoc in 0..100) { "开始 SOC 必须在 0 到 100 之间" }
        require(request.endSoc in 0..100) { "结束 SOC 必须在 0 到 100 之间" }
        require(request.endSoc >= request.startSoc) { "结束 SOC 不能低于开始 SOC" }
        require(request.endedAtEpochMillis > session.startedAtEpochMillis) { "结束时间必须晚于开始时间" }
        require(request.odometerKm == null || request.odometerKm >= 0.0) { "里程不能小于 0" }
        require(request.meterEnergyKwh == null || request.meterEnergyKwh > 0.0) { "电表电量必须大于 0" }
        require(request.totalCost == null || request.totalCost >= 0.0) { "费用不能小于 0" }
        if (request.vehicleEnergyKwh != null) {
            require(request.vehicleEnergyKwh >= 0.0) { "车辆侧充电量不能小于 0" }
            if (request.meterEnergyKwh != null) {
                require(request.vehicleEnergyKwh <= request.meterEnergyKwh + 1e-6) {
                    "车辆侧充电量不能高于桩端 / 电表电量"
                }
            }
        }
        validateLocationFacts(request.latitude, request.longitude, request.locationAccuracyMeters)
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
        validateBillingFacts(request.meterEnergyKwh, request.totalCost, request.vehicleEnergyKwh)
        validateLocationFacts(request.latitude, request.longitude, request.locationAccuracyMeters)
    }

    private fun validateBillingFacts(
        meterEnergyKwh: Double,
        totalCost: Double,
        vehicleEnergyKwh: Double?,
    ) {
        require(meterEnergyKwh > 0.0) { "充电量必须大于 0" }
        require(totalCost >= 0.0) { "费用不能小于 0" }
        require(vehicleEnergyKwh == null || vehicleEnergyKwh >= 0.0) { "车辆侧充电量不能小于 0" }
        require(vehicleEnergyKwh == null || vehicleEnergyKwh <= meterEnergyKwh + 1e-6) {
            "车辆侧充电量不能高于桩端 / 电表电量"
        }
    }

    private fun validateLocationFacts(
        latitude: Double?,
        longitude: Double?,
        accuracyMeters: Double?,
    ) {
        require((latitude == null) == (longitude == null)) { "定位坐标不完整" }
        require(accuracyMeters == null || accuracyMeters >= 0.0) { "定位精度不能小于 0" }
    }

    private suspend fun rebuildVehicleStateFromEvents(vehicleId: Long) {
        val existing = vehicleStateDao.get(vehicleId)
        val manualState = existing?.takeIf { it.updateSource == VehicleStateUpdateSource.MANUAL_UPDATE.name }

        val latestCharge = chargingRecordDao.getLatestForVehicle(vehicleId)
        val latestPendingSoc = chargingSessionDao.getLatestPendingWithEndSocForVehicle(vehicleId)
        val latestTripSoc = tripDao.getLatestCompletedWithSocForVehicle(vehicleId)
        val socFact = latestChargingFact(
            latestCharge?.let {
                ChargingVehicleStateFact(it.endSoc, it.endedAtEpochMillis ?: it.chargeTimeEpochMillis, VehicleStateUpdateSource.CHARGE_RECORD)
            },
            latestPendingSoc?.endSoc?.let {
                ChargingVehicleStateFact(
                    it,
                    latestPendingSoc.endedAtEpochMillis ?: latestPendingSoc.updatedAtEpochMillis,
                    VehicleStateUpdateSource.CHARGE_PENDING,
                )
            },
            latestTripSoc?.let {
                ChargingVehicleStateFact(it.endSoc!!, it.endedAtEpochMillis!!, VehicleStateUpdateSource.TRIP_END)
            },
            manualState?.currentSoc?.let {
                ChargingVehicleStateFact(it, manualState.updatedAtEpochMillis, VehicleStateUpdateSource.MANUAL_UPDATE)
            }
        )

        val latestChargeMileage = chargingRecordDao.getLatestWithOdometerForVehicle(vehicleId)
        val latestPendingMileage = chargingSessionDao.getLatestPendingWithOdometerForVehicle(vehicleId)
        val latestTripMileage = tripDao.getLatestCompletedWithMileageForVehicle(vehicleId)
        val mileageFact = latestChargingFact(
            latestChargeMileage?.odometerKm?.let {
                ChargingVehicleStateFact(
                    it,
                    latestChargeMileage.endedAtEpochMillis ?: latestChargeMileage.chargeTimeEpochMillis,
                    VehicleStateUpdateSource.CHARGE_RECORD,
                )
            },
            latestPendingMileage?.odometerKm?.let {
                ChargingVehicleStateFact(
                    it,
                    latestPendingMileage.endedAtEpochMillis ?: latestPendingMileage.updatedAtEpochMillis,
                    VehicleStateUpdateSource.CHARGE_PENDING,
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
