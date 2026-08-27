package com.evchargebook.domain.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripEnergyCalculatorTest {
    @Test
    fun `calculates trip energy and consumption from soc delta`() {
        val result = TripEnergyCalculator.estimate(
            batteryCapacityKwh = 81.9,
            startSoc = 82,
            endSoc = 65,
            distanceMeters = 80_000.0
        )

        assertEquals(13.923, result.consumedEnergyKwh!!, 0.001)
        assertEquals(17.40375, result.averageConsumptionKwhPer100Km!!, 0.0001)
    }

    @Test
    fun `does not invent consumption when start soc is unknown`() {
        val result = TripEnergyCalculator.estimate(
            batteryCapacityKwh = 81.9,
            startSoc = null,
            endSoc = 65,
            distanceMeters = 80_000.0
        )

        assertNull(result.consumedEnergyKwh)
        assertNull(result.averageConsumptionKwhPer100Km)
    }

    @Test
    fun `unchanged soc is unknown rather than zero consumption`() {
        val result = TripEnergyCalculator.estimate(
            batteryCapacityKwh = 67.7,
            startSoc = 80,
            endSoc = 80,
            distanceMeters = 3_000.0
        )

        assertNull(result.consumedEnergyKwh)
        assertNull(result.averageConsumptionKwhPer100Km)
    }

    @Test
    fun `soc gain from regen or rounding is not forced into consumption`() {
        val result = TripEnergyCalculator.estimate(
            batteryCapacityKwh = 67.7,
            startSoc = 80,
            endSoc = 81,
            distanceMeters = 5_000.0
        )

        assertNull(result.consumedEnergyKwh)
        assertNull(result.averageConsumptionKwhPer100Km)
    }
}
