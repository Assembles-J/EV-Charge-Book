package com.evchargebook.domain

import java.util.Locale

data class ChargingInputWarning(
    val code: String,
    val message: String
)

object ChargingAnomalyRules {
    private const val HIGH_UNIT_PRICE_YUAN_PER_KWH = 5.0
    private const val MAX_ENERGY_TO_CAPACITY_RATIO = 1.35
    private const val LOW_SOC_DELTA_POINTS = 1
    private const val MIN_ENERGY_FOR_FLAT_SOC_KWH = 5.0

    fun evaluate(
        startSoc: Int?,
        endSoc: Int?,
        energyKwh: Double?,
        cost: Double?,
        batteryCapacityKwh: Double?
    ): List<ChargingInputWarning> {
        if (energyKwh == null || energyKwh <= 0.0 || cost == null || cost < 0.0) return emptyList()

        val warnings = mutableListOf<ChargingInputWarning>()
        val unitPrice = cost / energyKwh
        if (unitPrice > HIGH_UNIT_PRICE_YUAN_PER_KWH) {
            warnings += ChargingInputWarning(
                code = "HIGH_UNIT_PRICE",
                message = "本次单价约 ¥${two(unitPrice)}/kWh，明显偏高，请确认费用和充电量是否录反或多输了一位。"
            )
        }

        if (batteryCapacityKwh != null && batteryCapacityKwh > 0.0) {
            if (energyKwh > batteryCapacityKwh * MAX_ENERGY_TO_CAPACITY_RATIO) {
                warnings += ChargingInputWarning(
                    code = "ENERGY_OVER_CAPACITY",
                    message = "本次补能 ${one(energyKwh)} kWh 已超过车辆电池容量 ${one(batteryCapacityKwh)} kWh 的 135%，请确认充电量。"
                )
            }

            if (startSoc != null && endSoc != null && startSoc in 0..100 && endSoc in 0..100) {
                val socDelta = endSoc - startSoc
                val meaningfulEnergy = energyKwh >= maxOf(MIN_ENERGY_FOR_FLAT_SOC_KWH, batteryCapacityKwh * 0.10)
                if (socDelta in 0..LOW_SOC_DELTA_POINTS && meaningfulEnergy) {
                    warnings += ChargingInputWarning(
                        code = "FLAT_SOC_WITH_ENERGY",
                        message = "SOC 只变化 $socDelta 个百分点，但记录了 ${one(energyKwh)} kWh 补能，请确认起止 SOC。"
                    )
                }
            }
        }

        return warnings
    }

    private fun one(value: Double) = String.format(Locale.US, "%.1f", value)
    private fun two(value: Double) = String.format(Locale.US, "%.2f", value)
}
