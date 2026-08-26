package com.evchargebook.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "charging_records",
    indices = [
        Index(value = ["vehicleId"]),
        Index(value = ["chargeTimeEpochMillis"])
    ]
)
data class ChargingRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val vehicleId: Long,
    val chargeTimeEpochMillis: Long,
    val energyKwh: Double,
    val cost: Double,
    val startSoc: Int,
    val endSoc: Int,
    val chargerType: String? = null,
    val location: String? = null,
    val remark: String? = null,
    val odometerKm: Double? = null
) {
    val pricePerKwh: Double
        get() = if (energyKwh > 0.0) cost / energyKwh else 0.0
}
