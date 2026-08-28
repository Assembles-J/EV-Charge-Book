package com.evchargebook.domain.trip

import com.evchargebook.data.entity.TripSessionEntity
import com.evchargebook.domain.TripValidityRules

data class TripEnergySummary(
    val completedTripCount: Int,
    val eligibleTripCount: Int,
    val excludedTripCount: Int,
    val distanceKm: Double,
    val estimatedEnergyKwh: Double,
    val weightedAverageKwhPer100Km: Double?
)

object TripEnergyAnalytics {
    fun summarize(
        trips: List<TripSessionEntity>,
        startInclusiveEpochMillis: Long? = null,
        endExclusiveEpochMillis: Long? = null
    ): TripEnergySummary {
        val completed = trips.filter { trip ->
            if (!TripValidityRules.isEligibleForAnalytics(trip)) return@filter false
            val occurredAt = trip.endedAtEpochMillis ?: trip.startedAtEpochMillis
            (startInclusiveEpochMillis == null || occurredAt >= startInclusiveEpochMillis) &&
                (endExclusiveEpochMillis == null || occurredAt < endExclusiveEpochMillis)
        }
        val eligible = completed.filter { trip ->
            trip.distanceMeters.isFinite() && trip.distanceMeters > 0.0 &&
                trip.consumedEnergyKwh?.let { it.isFinite() && it > 0.0 } == true
        }
        val distanceKm = eligible.sumOf { it.distanceMeters } / 1000.0
        val estimatedEnergyKwh = eligible.sumOf { it.consumedEnergyKwh ?: 0.0 }
        val weightedAverage = if (distanceKm > 0.0 && estimatedEnergyKwh > 0.0) {
            estimatedEnergyKwh / distanceKm * 100.0
        } else {
            null
        }
        return TripEnergySummary(
            completedTripCount = completed.size,
            eligibleTripCount = eligible.size,
            excludedTripCount = completed.size - eligible.size,
            distanceKm = distanceKm,
            estimatedEnergyKwh = estimatedEnergyKwh,
            weightedAverageKwhPer100Km = weightedAverage
        )
    }
}
