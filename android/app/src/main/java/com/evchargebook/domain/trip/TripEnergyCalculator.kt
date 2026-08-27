package com.evchargebook.domain.trip

data class TripEnergyEstimate(
    val consumedEnergyKwh: Double?,
    val averageConsumptionKwhPer100Km: Double?
)

object TripEnergyCalculator {
    fun estimate(
        batteryCapacityKwh: Double?,
        startSoc: Int?,
        endSoc: Int?,
        distanceMeters: Double
    ): TripEnergyEstimate {
        val consumed = if (
            batteryCapacityKwh != null && batteryCapacityKwh > 0.0 &&
            startSoc != null && endSoc != null &&
            startSoc in 0..100 && endSoc in 0..100 &&
            endSoc < startSoc
        ) {
            batteryCapacityKwh * (startSoc - endSoc) / 100.0
        } else {
            null
        }

        val average = if (consumed != null && distanceMeters > 0.0) {
            consumed / (distanceMeters / 1000.0) * 100.0
        } else {
            null
        }

        return TripEnergyEstimate(
            consumedEnergyKwh = consumed,
            averageConsumptionKwhPer100Km = average
        )
    }
}
