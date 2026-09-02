package com.evchargebook.trip

import android.content.Context
import androidx.room.withTransaction
import com.evchargebook.autotrip.AutoTripDetectionState
import com.evchargebook.data.database.AppDatabase
import com.evchargebook.data.entity.TripSessionEntity
import com.evchargebook.data.entity.TripStatus
import com.evchargebook.domain.VehicleActivityConflictPolicy
import kotlinx.coroutines.flow.first

sealed interface TripStartSource {
    data object ManualUi : TripStartSource
    data class BluetoothPrompt(val sessionId: String) : TripStartSource
    data class BluetoothAuto(val sessionId: String) : TripStartSource
    data class VerifiedAuto(val sessionId: String) : TripStartSource
}

data class TripStartRequest(
    val vehicleId: Long,
    val source: TripStartSource,
    val requestedAtEpochMillis: Long = System.currentTimeMillis(),
)

sealed interface TripStartResult {
    data class Started(val tripId: Long) : TripStartResult
    data class AlreadyActive(val tripId: Long) : TripStartResult
    data class Blocked(val reason: String) : TripStartResult
    data class Failed(val tripId: Long, val reason: String, val cause: Throwable? = null) : TripStartResult
}

/**
 * Single authority for creating a new Trip.
 *
 * UI, Bluetooth prompt actions and automatic Bluetooth starts converge here so the active Trip
 * guard, same-vehicle charging guard, VehicleState snapshot and detection-session linkage are
 * evaluated in one Room transaction. Tracking service startup happens only after the Trip is
 * persisted; if Android rejects service startup, the Trip becomes INTERRUPTED and an associated
 * detection session becomes START_FAILED rather than silently retrying or creating another Trip.
 */
class TripStartCoordinator(
    private val database: AppDatabase,
    private val context: Context,
) {
    private val tripDao = database.tripDao()
    private val vehicleDao = database.vehicleDao()
    private val vehicleStateDao = database.vehicleStateDao()
    private val chargingSessionDao = database.chargingSessionDao()
    private val detectionDao = database.autoTripDetectionDao()

    suspend fun start(request: TripStartRequest): TripStartResult {
        val prepared = database.withTransaction {
            val active = tripDao.getActive()
            if (active != null) {
                return@withTransaction PreparedResult.Existing(active.id)
            }

            val vehicle = vehicleDao.observeActive().first().firstOrNull { it.id == request.vehicleId }
                ?: return@withTransaction PreparedResult.Blocked("当前车辆不可用")

            VehicleActivityConflictPolicy.tripStartBlockReason(
                vehicleId = vehicle.id,
                activeChargingVehicleId = chargingSessionDao.getActiveForVehicle(vehicle.id)?.vehicleId,
            )?.let { reason ->
                return@withTransaction PreparedResult.Blocked(reason)
            }

            val detectionSession = when (val source = request.source) {
                TripStartSource.ManualUi -> null

                is TripStartSource.BluetoothPrompt -> {
                    val session = detectionDao.getById(source.sessionId)
                        ?: return@withTransaction PreparedResult.Blocked("蓝牙行程提示已失效")
                    if (
                        session.closedAtEpochMillis != null ||
                        session.state != AutoTripDetectionState.BLUETOOTH_CANDIDATE.name
                    ) {
                        return@withTransaction PreparedResult.Blocked("蓝牙行程提示已过期，请重新开始")
                    }
                    if (session.vehicleId != vehicle.id) {
                        return@withTransaction PreparedResult.Blocked("蓝牙提示车辆与当前开始车辆不一致")
                    }
                    session
                }

                is TripStartSource.BluetoothAuto -> {
                    val session = detectionDao.getById(source.sessionId)
                        ?: return@withTransaction PreparedResult.Blocked("蓝牙自动行程检测已失效")
                    if (
                        session.closedAtEpochMillis != null ||
                        session.state != AutoTripDetectionState.BLUETOOTH_CANDIDATE.name
                    ) {
                        return@withTransaction PreparedResult.Blocked("蓝牙自动行程检测已过期")
                    }
                    if (session.vehicleId != vehicle.id) {
                        return@withTransaction PreparedResult.Blocked("蓝牙自动行程车辆不一致")
                    }
                    session
                }

                is TripStartSource.VerifiedAuto -> {
                    val session = detectionDao.getById(source.sessionId)
                        ?: return@withTransaction PreparedResult.Blocked("自动行程检测已失效")
                    if (
                        session.closedAtEpochMillis != null ||
                        session.state != AutoTripDetectionState.READY_TO_START.name
                    ) {
                        return@withTransaction PreparedResult.Blocked("自动行程尚未达到可信启动条件")
                    }
                    if (session.vehicleId != vehicle.id) {
                        return@withTransaction PreparedResult.Blocked("自动检测车辆与开始车辆不一致")
                    }
                    session
                }
            }

            val vehicleState = vehicleStateDao.get(vehicle.id)
            val tripId = tripDao.insertSession(
                TripSessionEntity(
                    vehicleId = vehicle.id,
                    startedAtEpochMillis = request.requestedAtEpochMillis,
                    startSoc = vehicleState?.currentSoc,
                    startSocSnapshot = vehicleState?.currentSoc,
                    startMileageKm = vehicleState?.currentMileage,
                    status = TripStatus.RECORDING,
                )
            )

            detectionSession?.let { session ->
                detectionDao.update(
                    session.copy(
                        state = AutoTripDetectionState.STARTING.name,
                        tripId = tripId,
                        updatedAtEpochMillis = request.requestedAtEpochMillis,
                    )
                )
            }
            PreparedResult.Created(tripId, detectionSession?.id)
        }

        val created = when (prepared) {
            is PreparedResult.Existing -> return TripStartResult.AlreadyActive(prepared.tripId)
            is PreparedResult.Blocked -> return TripStartResult.Blocked(prepared.reason)
            is PreparedResult.Created -> prepared
        }

        return try {
            TripTrackingService.start(context, created.tripId)
            if (created.detectionSessionId != null) {
                database.withTransaction {
                    detectionDao.getById(created.detectionSessionId)?.let { session ->
                        if (
                            session.tripId == created.tripId &&
                            session.state == AutoTripDetectionState.STARTING.name
                        ) {
                            detectionDao.update(
                                session.copy(
                                    state = AutoTripDetectionState.RECORDING.name,
                                    updatedAtEpochMillis = System.currentTimeMillis(),
                                )
                            )
                        }
                    }
                }
            }
            TripStartResult.Started(created.tripId)
        } catch (error: Throwable) {
            database.withTransaction {
                tripDao.getSession(created.tripId)?.let { trip ->
                    if (trip.status == TripStatus.RECORDING) {
                        tripDao.updateSession(trip.copy(status = TripStatus.INTERRUPTED))
                    }
                }
                created.detectionSessionId?.let { sessionId ->
                    detectionDao.getById(sessionId)?.let { session ->
                        if (session.tripId == created.tripId) {
                            detectionDao.update(
                                session.copy(
                                    state = AutoTripDetectionState.START_FAILED.name,
                                    updatedAtEpochMillis = System.currentTimeMillis(),
                                )
                            )
                        }
                    }
                }
            }
            TripStartResult.Failed(
                tripId = created.tripId,
                reason = error.message ?: "无法启动行程记录服务",
                cause = error,
            )
        }
    }

    private sealed interface PreparedResult {
        data class Created(val tripId: Long, val detectionSessionId: String?) : PreparedResult
        data class Existing(val tripId: Long) : PreparedResult
        data class Blocked(val reason: String) : PreparedResult
    }
}
