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
    private const val MIN_REASONABLE_CONSUMPTION_KWH_PER_100_KM = 5.0
    private const val MAX_REASONABLE_CONSUMPTION_KWH_PER_100_KM = 40.0
    private const val HISTORY_SAMPLE_LIMIT = 5

    /**
     * Uses the newest plausible completed-Trip values. Callers should pass history in newest-first
     * order; invalid/outlier values are ignored before the small-sample average is calculated.
     */
    fun historicalAverageConsumptionKwhPer100Km(values: Iterable<Double?>): Double? {
        val samples = values.asSequence()
            .mapNotNull { value ->
                value?.takeIf {
                    it.isFinite() && it in MIN_REASONABLE_CONSUMPTION_KWH_PER_100_KM..MAX_REASONABLE_CONSUMPTION_KWH_PER_100_KM
                }
            }
            .take(HISTORY_SAMPLE_LIMIT)
            .toList()
        return samples.takeIf { it.isNotEmpty() }?.average()
    }

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
            ?.takeIf {
                it.isFinite() && it in MIN_REASONABLE_CONSUMPTION_KWH_PER_100_KM..MAX_REASONABLE_CONSUMPTION_KWH_PER_100_KM
            }
            ?: DEFAULT_CONSUMPTION_KWH_PER_100_KM
        val distanceKm = distanceMeters / 1000.0
        val consumedKwh = distanceKm / 100.0 * consumption
        val dropPercent = consumedKwh / capacity * 100.0
        return (start - dropPercent.roundToInt()).coerceIn(0, 100)
    }
}
