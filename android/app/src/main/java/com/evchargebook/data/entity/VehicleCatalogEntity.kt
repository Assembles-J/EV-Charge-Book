package com.evchargebook.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicle_catalog")
data class VehicleCatalogEntity(
    @PrimaryKey val catalogId: String,
    val source: String,
    @ColumnInfo(defaultValue = "''")
    val brandId: String = "",
    val brand: String,
    val series: String,
    val modelName: String,
    val modelYear: Int? = null,
    val trimName: String? = null,
    val powertrainType: String,
    val batteryCapacityKwh: Double? = null,
    val rangeKm: Int? = null,
    val rangeStandard: String? = null,
    val heroArtworkKey: String? = null,
    val brandLogoLightUrl: String? = null,
    @ColumnInfo(defaultValue = "0")
    val brandLogoLightVersion: Int = 0,
    val brandLogoDarkUrl: String? = null,
    @ColumnInfo(defaultValue = "0")
    val brandLogoDarkVersion: Int = 0,
    val isActive: Boolean = true,
    val sourceUpdatedAtEpochMillis: Long = 0L
)
