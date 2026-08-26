package com.evchargebook.domain

import com.evchargebook.data.entity.ChargingRecordEntity

enum class ChargerCategory {
    HOME,
    PUBLIC_SLOW,
    PUBLIC_FAST,
    OTHER
}

data class ChargerCategorySummary(
    val category: ChargerCategory,
    val chargingCount: Int,
    val energyKwh: Double,
    val cost: Double,
    val countShare: Double,
    val energyShare: Double,
    val costShare: Double
)

object ChargerTypeAnalytics {
    fun summarize(records: List<ChargingRecordEntity>): List<ChargerCategorySummary> {
        val totalCount = records.size
        val totalEnergy = records.sumOf { it.energyKwh.coerceAtLeast(0.0) }
        val totalCost = records.sumOf { it.cost.coerceAtLeast(0.0) }
        val grouped = records.groupBy { categoryOf(it.chargerType) }

        return ChargerCategory.entries.map { category ->
            val items = grouped[category].orEmpty()
            val energy = items.sumOf { it.energyKwh.coerceAtLeast(0.0) }
            val cost = items.sumOf { it.cost.coerceAtLeast(0.0) }
            ChargerCategorySummary(
                category = category,
                chargingCount = items.size,
                energyKwh = energy,
                cost = cost,
                countShare = if (totalCount > 0) items.size.toDouble() / totalCount else 0.0,
                energyShare = if (totalEnergy > 0.0) energy / totalEnergy else 0.0,
                costShare = if (totalCost > 0.0) cost / totalCost else 0.0
            )
        }
    }

    fun categoryOf(raw: String?): ChargerCategory {
        val value = raw?.trim().orEmpty()
        return when {
            value.contains("家充") || value.contains("家用") -> ChargerCategory.HOME
            value.contains("快") -> ChargerCategory.PUBLIC_FAST
            value.contains("慢") -> ChargerCategory.PUBLIC_SLOW
            else -> ChargerCategory.OTHER
        }
    }
}
