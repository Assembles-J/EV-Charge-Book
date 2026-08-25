package com.evchargebook.data.entity

/**
 * Charging history record.
 * Room entity will be completed in next step.
 */
data class ChargingRecordEntity(
    val id: Long = 0,
    val energyKwh: Double,
    val cost: Double,
    val startSoc: Int,
    val endSoc: Int,
    val location: String? = null
)
