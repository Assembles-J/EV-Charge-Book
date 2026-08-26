package com.evchargebook.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object TripStatus {
    const val RECORDING = "RECORDING"
    const val INTERRUPTED = "INTERRUPTED"
    const val COMPLETED = "COMPLETED"
}

@Entity(
    tableName = "trip_sessions",
    indices = [Index(value = ["vehicleId"]), Index(value = ["startedAtEpochMillis"]), Index(value = ["status"])]
)
data class TripSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val vehicleId: Long,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long? = null,
    val distanceMeters: Double = 0.0,
    val elapsedSeconds: Long = 0,
    val movingSeconds: Long? = null,
    val stoppedSeconds: Long? = null,
    val averageSpeedMps: Double? = null,
    val maxSpeedMps: Double? = null,
    val startLatitude: Double? = null,
    val startLongitude: Double? = null,
    val endLatitude: Double? = null,
    val endLongitude: Double? = null,
    val startAltitudeMeters: Double? = null,
    val endAltitudeMeters: Double? = null,
    val minAltitudeMeters: Double? = null,
    val maxAltitudeMeters: Double? = null,
    val status: String = TripStatus.RECORDING
)
