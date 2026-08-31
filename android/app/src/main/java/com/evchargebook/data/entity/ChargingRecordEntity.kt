package com.evchargebook.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "charging_records",
    indices = [
        Index(value = ["vehicleId"]),
        Index(value = ["chargeTimeEpochMillis"]),
        Index(value = ["syncId"], unique = true)
    ]
)
data class ChargingRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val vehicleId: Long,
    /** Start/occurrence time. Legacy records may only know this one timestamp. */
    val chargeTimeEpochMillis: Long,
    /** Explicit completion time when known. Legacy/manual records may leave it unknown. */
    val endedAtEpochMillis: Long? = null,
    /** Charger/meter billed energy. */
    val energyKwh: Double,
    /** Explicit vehicle-side received energy when the user has a trustworthy external value. */
    val vehicleEnergyKwh: Double? = null,
    val cost: Double,
    val startSoc: Int,
    val endSoc: Int,
    val chargerType: String? = null,
    val location: String? = null,
    val remark: String? = null,
    val odometerKm: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracyMeters: Double? = null,
    @ColumnInfo(defaultValue = "''")
    val syncId: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0")
    val updatedAtEpochMillis: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val isDeleted: Boolean = false
) {
    val pricePerKwh: Double
        get() = if (energyKwh > 0.0) cost / energyKwh else 0.0
}
