package com.evchargebook.domain.charge

import com.evchargebook.data.entity.ChargingRecordEntity

data class ChargeDefaults(
    val startSoc: Int?,
    val endSoc: Int,
    val chargerType: String,
    val pricePerKwh: Double?,
    val location: String?
)

object ChargeDefaultResolver {
    private const val FALLBACK_END_SOC = 100
    const val FALLBACK_CHARGER_TYPE = "公共慢充"

    fun resolve(
        currentSoc: Int?,
        records: List<ChargingRecordEntity>,
        chargerType: String? = null
    ): ChargeDefaults {
        val latest = records.firstOrNull { !it.isDeleted }
        val resolvedType = chargerType ?: latest?.chargerType ?: FALLBACK_CHARGER_TYPE
        val sameType = records.firstOrNull { !it.isDeleted && it.chargerType == resolvedType }
        val priceSource = sameType ?: latest

        return ChargeDefaults(
            startSoc = currentSoc ?: latest?.endSoc,
            endSoc = latest?.endSoc ?: FALLBACK_END_SOC,
            chargerType = resolvedType,
            pricePerKwh = priceSource?.takeIf { it.energyKwh > 0.0 }?.pricePerKwh,
            location = sameType?.location ?: latest?.location
        )
    }

    fun priceForType(records: List<ChargingRecordEntity>, chargerType: String): Double? {
        return records
            .firstOrNull { !it.isDeleted && it.chargerType == chargerType && it.energyKwh > 0.0 }
            ?.pricePerKwh
    }
}
