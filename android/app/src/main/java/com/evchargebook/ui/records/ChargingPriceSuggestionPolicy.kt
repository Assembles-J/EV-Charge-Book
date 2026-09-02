package com.evchargebook.ui.records

import java.util.Locale

internal data class ChargingPriceMemory(
    val pricePerKwh: Double,
    val chargerType: String?,
    val location: String?,
    val timestampEpochMillis: Long,
    /** True only for a price explicitly stored on a charging session, not cost / energy. */
    val isStoredTariff: Boolean,
)

internal object ChargingPriceSuggestionPolicy {
    fun autoFillPrice(
        chargerType: String,
        location: String,
        memories: List<ChargingPriceMemory>,
    ): Double? {
        val typeKey = normalizeType(chargerType) ?: return null
        val locationKey = normalizeLocation(location) ?: return null
        return memories
            .asSequence()
            .filter { it.isStoredTariff && it.pricePerKwh >= 0.0 }
            .filter { normalizeType(it.chargerType) == typeKey }
            .filter { normalizeLocation(it.location) == locationKey }
            .sortedByDescending { it.timestampEpochMillis }
            .map { it.pricePerKwh }
            .firstOrNull()
    }

    fun suggestionPrices(
        chargerType: String,
        location: String,
        memories: List<ChargingPriceMemory>,
        limit: Int = 4,
    ): List<Double> {
        if (limit <= 0) return emptyList()
        val typeKey = normalizeType(chargerType)
        val locationKey = normalizeLocation(location)
        val valid = memories
            .asSequence()
            .filter { it.pricePerKwh >= 0.0 }
            .sortedByDescending { it.timestampEpochMillis }
            .toList()

        val tiers = listOf(
            valid.filter {
                it.isStoredTariff &&
                    typeKey != null && normalizeType(it.chargerType) == typeKey &&
                    locationKey != null && normalizeLocation(it.location) == locationKey
            },
            valid.filter {
                it.isStoredTariff && typeKey != null && normalizeType(it.chargerType) == typeKey
            },
            valid.filter { it.isStoredTariff },
            valid.filter {
                !it.isStoredTariff && typeKey != null && normalizeType(it.chargerType) == typeKey
            },
            valid.filter { !it.isStoredTariff },
        )

        val result = mutableListOf<Double>()
        val seen = mutableSetOf<Long>()
        for (tier in tiers) {
            for (memory in tier) {
                val key = (memory.pricePerKwh * 1000.0).toLong()
                if (seen.add(key)) {
                    result += memory.pricePerKwh
                    if (result.size == limit) return result
                }
            }
        }
        return result
    }

    internal fun normalizeLocation(value: String?): String? = value
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.lowercase(Locale.ROOT)
        ?.takeIf { it.isNotEmpty() }

    private fun normalizeType(value: String?): String? = value
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf { it.isNotEmpty() }
}
