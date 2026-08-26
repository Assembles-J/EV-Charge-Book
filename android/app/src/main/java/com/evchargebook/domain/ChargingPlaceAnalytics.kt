package com.evchargebook.domain

import com.evchargebook.data.entity.ChargingRecordEntity

data class ChargingPlaceSummary(
    val displayName: String,
    val chargingCount: Int,
    val energyKwh: Double,
    val cost: Double,
    val averagePricePerKwh: Double?,
    val latestChargeTimeEpochMillis: Long
)

object ChargingPlaceAnalytics {
    private val whitespace = Regex("\\s+")

    fun summarize(records: List<ChargingRecordEntity>): List<ChargingPlaceSummary> {
        return records
            .mapNotNull { record ->
                val normalized = normalize(record.location) ?: return@mapNotNull null
                normalized to record
            }
            .groupBy({ it.first }, { it.second })
            .map { (name, items) ->
                val energy = items.sumOf { it.energyKwh.coerceAtLeast(0.0) }
                val cost = items.sumOf { it.cost.coerceAtLeast(0.0) }
                ChargingPlaceSummary(
                    displayName = name,
                    chargingCount = items.size,
                    energyKwh = energy,
                    cost = cost,
                    averagePricePerKwh = if (energy > 0.0) cost / energy else null,
                    latestChargeTimeEpochMillis = items.maxOf { it.chargeTimeEpochMillis }
                )
            }
            .sortedWith(
                compareByDescending<ChargingPlaceSummary> { it.chargingCount }
                    .thenByDescending { it.latestChargeTimeEpochMillis }
                    .thenBy { it.displayName }
            )
    }

    fun normalize(raw: String?): String? {
        val value = raw?.trim()?.replace(whitespace, " ").orEmpty()
        return value.takeIf { it.isNotEmpty() }
    }
}
