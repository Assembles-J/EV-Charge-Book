package com.evchargebook.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val brand: String,
    val model: String,
    val batteryCapacityKwh: Double,
    val rangeKm: Int
)
