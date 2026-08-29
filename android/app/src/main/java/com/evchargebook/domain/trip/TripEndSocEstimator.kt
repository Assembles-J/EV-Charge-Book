package com.evchargebook.domain.trip

import kotlin.math.roundToInt

/**
 * Seeds the completion-screen end SOC when no trustworthy end SOC measurement is available.
 *
 * This is only an initial UI value. Saving the completion screen is the explicit user confirmation.
 * The estimator never pretends to be a BMS measurement.
 */
object TripEndSocEstimator {
    const val DEFAULT_CONSUMPTION_KWH_PER_100_KM = 15.0

    fun estimate(
        startSoc: Int?,
        distanceMeters: Double,
        batteryCapacityKwh: Double?,
        averageConsumptionKwhPer100Km: Double? = null
    ): Int? {
        val start = startSoc?.takeIf { it in 0..100 } ?: return null
        val capacity = batteryCapacityKwh?.takeIf { it.isFinite() && it > 0.0 } ?: return start
        if (!distanceMeters.isFinite() || distanceMeters <= 0.0) return start

        val consumption = averageConsumptionKwhPer100Km
            ?.takeIf { it.isFinite() && it in 5.0..40.0 }
            ?: DEFAULT_CONSUMPTION_KWH_PER_100_KM
        val distanceKm = distanceMeters / 1000.0
        val consumedKwh = distanceKm / 100.0 * consumption
        val dropPercent = consumedKwh / capacity * 100.0
        return (start - dropPercent.roundToInt()).coerceIn(0, 100)
    }
}
