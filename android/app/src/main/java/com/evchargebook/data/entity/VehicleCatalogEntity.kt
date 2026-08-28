package com.evchargebook.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicle_catalog")
data class VehicleCatalogEntity(
    @PrimaryKey val catalogId: String,
    val source: String,
    val brand: String,
    val series: String,
    val modelName: String,
    val modelYear: Int? = null,
    val trimName: String? = null,
    val powertrainType: String,
    val batteryCapacityKwh: Double? = null,
    val rangeKm: Int? = null,
    val heroArtworkKey: String? = null,
    val isActive: Boolean = true,
    val sourceUpdatedAtEpochMillis: Long = 0L
)
