package com.evchargebook.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "auto_trip_detection_sessions",
    indices = [
        Index("vehicleId"),
        Index("deviceAddressHash"),
        Index("state"),
        Index(value = ["deviceAddressHash", "connectionEpoch"], unique = true),
    ],
)
data class AutoTripDetectionSessionEntity(
    @PrimaryKey val id: String,
    val vehicleId: Long,
    val deviceAddressHash: String,
    val deviceNameSnapshot: String?,
    val connectionEpoch: String,
    val state: String,
    val connectedAtEpochMillis: Long,
    val ignoredAtEpochMillis: Long? = null,
    val closedAtEpochMillis: Long? = null,
    val tripId: Long? = null,
    val updatedAtEpochMillis: Long,
)
