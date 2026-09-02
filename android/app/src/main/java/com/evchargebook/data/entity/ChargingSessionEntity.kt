package com.evchargebook.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

object ChargingSessionStatus {
    const val ACTIVE = "ACTIVE"
    const val PENDING_DETAILS = "PENDING_DETAILS"
    const val COMPLETED = "COMPLETED"
    const val CANCELLED = "CANCELLED"

    val all: Set<String> = setOf(ACTIVE, PENDING_DETAILS, COMPLETED, CANCELLED)
}

/**
 * Durable local lifecycle state for the optional `开始充电` flow.
 *
 * This is deliberately separate from [ChargingRecordEntity]: a charging record is a completed
 * historical fact, while a session may survive process death with completion facts still unknown.
 *
 * `PENDING_DETAILS` means physical charging has ended and the end facts are durable, but billing /
 * meter facts are not complete enough to create a final historical charging record yet.
 */
@Entity(
    tableName = "charging_sessions",
    indices = [
        Index(value = ["vehicleId"]),
        Index(value = ["status"]),
        Index(value = ["startedAtEpochMillis"]),
    ]
)
data class ChargingSessionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val vehicleId: Long,
    val startedAtEpochMillis: Long,
    val startSoc: Int? = null,
    val targetSoc: Int? = null,
    val chargerType: String? = null,
    val unitPricePerKwh: Double? = null,
    val location: String? = null,
    val remark: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracyMeters: Double? = null,
    val status: String = ChargingSessionStatus.ACTIVE,
    val endedAtEpochMillis: Long? = null,
    /** Actual end SOC captured when physical charging ends. */
    val endSoc: Int? = null,
    /** Optional odometer captured when physical charging ends. */
    val odometerKm: Double? = null,
    /** Optional partial billing facts retained while waiting for final meter data. */
    val pendingMeterEnergyKwh: Double? = null,
    val pendingTotalCost: Double? = null,
    /** Explicit measured vehicle-side energy only; never store the SOC-based estimate here. */
    val pendingVehicleEnergyKwh: Double? = null,
    val completedRecordId: Long? = null,
    val updatedAtEpochMillis: Long = System.currentTimeMillis(),
)
