package com.evchargebook.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicle_state")
data class VehicleStateEntity(
    @PrimaryKey
    val vehicleId: Long,
    val currentSoc: Int? = null,
    val currentMileage: Double? = null,
    val updatedAtEpochMillis: Long = System.currentTimeMillis(),
    val updateSource: String = VehicleStateUpdateSource.UNKNOWN.name
)

enum class VehicleStateUpdateSource {
    UNKNOWN,
    MIGRATION,
    CHARGE_RECORD,
    CHARGE_PENDING,
    TRIP_END,
    MANUAL_UPDATE,
    IMPORT
}
