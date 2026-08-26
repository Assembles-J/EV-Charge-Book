package com.evchargebook.domain

import com.evchargebook.data.entity.ChargingRecordEntity

data class ChargingIntervalSample(
    val previousRecordId: Long,
    val currentRecordId: Long,
    val distanceKm: Double,
    val replenishedEnergyKwh: Double,
    val replenishmentCost: Double,
    val energyPer100Km: Double,
    val costPer100Km: Double
)

data class ChargingIntervalSummary(
    val samples: List<ChargingIntervalSample>,
    val invalidIntervalCount: Int,
    val totalDistanceKm: Double,
    val totalEnergyKwh: Double,
    val totalCost: Double,
    val energyPer100Km: Double?,
    val costPer100Km: Double?
)

object ChargingIntervalAnalytics {
    fun summarize(records: List<ChargingRecordEntity>): ChargingIntervalSummary {
        val ordered = records
            .filter { it.odometerKm != null }
            .sortedBy { it.chargeTimeEpochMillis }

        if (ordered.size < 2) {
            return ChargingIntervalSummary(
                samples = emptyList(),
                invalidIntervalCount = 0,
                totalDistanceKm = 0.0,
                totalEnergyKwh = 0.0,
                totalCost = 0.0,
                energyPer100Km = null,
                costPer100Km = null
            )
        }

        val samples = mutableListOf<ChargingIntervalSample>()
        var invalid = 0

        ordered.zipWithNext().forEach { (previous, current) ->
            val previousOdometer = previous.odometerKm ?: return@forEach
            val currentOdometer = current.odometerKm ?: return@forEach
            val distance = currentOdometer - previousOdometer

            if (!distance.isFinite() || distance <= 0.0 || !current.energyKwh.isFinite() || current.energyKwh < 0.0 || !current.cost.isFinite() || current.cost < 0.0) {
                invalid += 1
                return@forEach
            }

            samples += ChargingIntervalSample(
                previousRecordId = previous.id,
                currentRecordId = current.id,
                distanceKm = distance,
                replenishedEnergyKwh = current.energyKwh,
                replenishmentCost = current.cost,
                energyPer100Km = current.energyKwh / distance * 100.0,
                costPer100Km = current.cost / distance * 100.0
            )
        }

        val totalDistance = samples.sumOf { it.distanceKm }
        val totalEnergy = samples.sumOf { it.replenishedEnergyKwh }
        val totalCost = samples.sumOf { it.replenishmentCost }

        return ChargingIntervalSummary(
            samples = samples,
            invalidIntervalCount = invalid,
            totalDistanceKm = totalDistance,
            totalEnergyKwh = totalEnergy,
            totalCost = totalCost,
            energyPer100Km = if (totalDistance > 0.0) totalEnergy / totalDistance * 100.0 else null,
            costPer100Km = if (totalDistance > 0.0) totalCost / totalDistance * 100.0 else null
        )
    }
}
