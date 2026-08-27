package com.evchargebook.domain.charge

/**
 * Calculates EV charging energy values.
 *
 * receivedEnergy is the energy stored by the vehicle based on SOC change.
 * chargedEnergy is the charger/meter input and may be different because of losses.
 */
object ChargeEnergyCalculator {

    fun calculateReceivedEnergy(
        batteryCapacityKwh: Double,
        startSoc: Int,
        endSoc: Int
    ): Double {
        require(endSoc >= startSoc) { "Charging end SOC must not be lower than start SOC" }
        return batteryCapacityKwh * (endSoc - startSoc) / 100.0
    }

    fun calculateEnergyLoss(
        chargedEnergyKwh: Double?,
        receivedEnergyKwh: Double
    ): Double? {
        return chargedEnergyKwh?.let { it - receivedEnergyKwh }
    }
}
