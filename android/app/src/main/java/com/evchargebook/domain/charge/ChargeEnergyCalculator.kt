package com.evchargebook.domain.charge

data class ChargeEnergyEstimate(
    val receivedEnergyKwh: Double?,
    val chargedEnergyKwh: Double?,
    val lossEnergyKwh: Double?,
    val lossRate: Double?
)

object ChargeEnergyCalculator {
    fun estimate(
        batteryCapacityKwh: Double?,
        startSoc: Int?,
        endSoc: Int?,
        chargedEnergyKwh: Double?
    ): ChargeEnergyEstimate {
        val received = if (
            batteryCapacityKwh != null && batteryCapacityKwh > 0.0 &&
            startSoc != null && endSoc != null &&
            startSoc in 0..100 && endSoc in 0..100 && endSoc >= startSoc
        ) {
            batteryCapacityKwh * (endSoc - startSoc) / 100.0
        } else {
            null
        }

        val loss = if (chargedEnergyKwh != null && chargedEnergyKwh >= 0.0 && received != null) {
            (chargedEnergyKwh - received).coerceAtLeast(0.0)
        } else {
            null
        }

        val lossRate = if (loss != null && chargedEnergyKwh != null && chargedEnergyKwh > 0.0) {
            loss / chargedEnergyKwh
        } else {
            null
        }

        return ChargeEnergyEstimate(
            receivedEnergyKwh = received,
            chargedEnergyKwh = chargedEnergyKwh,
            lossEnergyKwh = loss,
            lossRate = lossRate
        )
    }
}
