package com.evchargebook.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object TripDiagnosticEventType {
    const val SERVICE_START = "SERVICE_START"
    const val SERVICE_REDELIVERED = "SERVICE_REDELIVERED"
    const val SERVICE_DESTROY = "SERVICE_DESTROY"
    const val LOCATION_REGISTRATION_FAILED = "LOCATION_REGISTRATION_FAILED"
    const val LOCATION_SOURCE = "LOCATION_SOURCE"
    const val LOCATION_CALLBACK_GAP = "LOCATION_CALLBACK_GAP"
    const val GPS_HEALTH_TRANSITION = "GPS_HEALTH_TRANSITION"
    const val POWER_STATE = "POWER_STATE"
    const val PROVIDER_DISABLED = "PROVIDER_DISABLED"
    const val PERMISSION_MISSING = "PERMISSION_MISSING"
    const val LOCATION_REJECTED = "LOCATION_REJECTED"
}

@Entity(
    tableName = "trip_diagnostic_events",
    foreignKeys = [
        ForeignKey(
            entity = TripSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["tripId"]),
        Index(value = ["occurredAtEpochMillis"]),
        Index(value = ["type"])
    ]
)
data class TripDiagnosticEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tripId: Long,
    val occurredAtEpochMillis: Long,
    val type: String,
    val provider: String? = null,
    val detail: String? = null
)