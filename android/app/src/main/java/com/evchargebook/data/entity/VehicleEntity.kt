package com.evchargebook.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "vehicles",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val catalogVehicleId: String? = null,
    val brand: String,
    val model: String,
    val batteryCapacityKwh: Double,
    val rangeKm: Int,
    val isDefault: Boolean = false,
    val isArchived: Boolean = false,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "''")
    val syncId: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0")
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
)
