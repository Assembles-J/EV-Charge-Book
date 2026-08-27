package com.evchargebook.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicle_state")
data class VehicleStateEntity(
    @PrimaryKey
    val vehicleId: Long,
    val currentSoc: Int? = null,
    val currentMileage: Int? = null,
    val updatedAtEpochMillis: Long = System.currentTimeMillis(),
    val updateSource: String = "MANUAL_UPDATE"
)
