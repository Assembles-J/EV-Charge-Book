package com.evchargebook.data.entity

/**
 * Vehicle profile.
 */
data class VehicleEntity(
    val id: Long = 0,
    val brand: String,
    val model: String,
    val batteryCapacityKwh: Double,
    val rangeKm: Int
)
