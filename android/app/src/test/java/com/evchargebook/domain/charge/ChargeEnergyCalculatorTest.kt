package com.evchargebook.domain.charge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChargeEnergyCalculatorTest {
    @Test
    fun `calculates received energy and loss`() {
        val result = ChargeEnergyCalculator.estimate(
            batteryCapacityKwh = 81.9,
            startSoc = 58,
            endSoc = 100,
            chargedEnergyKwh = 36.8
        )

        assertEquals(34.398, result.receivedEnergyKwh!!, 0.001)
        assertEquals(2.402, result.lossEnergyKwh!!, 0.001)
        assertEquals(2.402 / 36.8, result.lossRate!!, 0.0001)
    }

    @Test
    fun `does not invent energy without soc inputs`() {
        val result = ChargeEnergyCalculator.estimate(
            batteryCapacityKwh = 81.9,
            startSoc = null,
            endSoc = 100,
            chargedEnergyKwh = 36.8
        )

        assertNull(result.receivedEnergyKwh)
        assertNull(result.lossEnergyKwh)
    }
}
