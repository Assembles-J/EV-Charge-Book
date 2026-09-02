package com.evchargebook.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trip_points",
    foreignKeys = [
        ForeignKey(
            entity = TripSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tripId"]), Index(value = ["capturedAtEpochMillis"])]
)
data class TripPointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tripId: Long,
    val capturedAtEpochMillis: Long,
    val capturedAtElapsedRealtimeNanos: Long? = null,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val speedMps: Double? = null,
    val bearingDegrees: Double? = null,
    val horizontalAccuracyMeters: Double? = null,
    val verticalAccuracyMeters: Double? = null,
    val speedAccuracyMps: Double? = null,
    val provider: String? = null
)
