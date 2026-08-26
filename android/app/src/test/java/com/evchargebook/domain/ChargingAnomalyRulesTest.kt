package com.evchargebook.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargingAnomalyRulesTest {
    @Test
    fun `warns on extreme unit price`() {
        val warnings = ChargingAnomalyRules.evaluate(20, 80, 10.0, 80.0, 80.0)
        assertTrue(warnings.any { it.code == "HIGH_UNIT_PRICE" })
    }

    @Test
    fun `warns when energy greatly exceeds battery capacity`() {
        val warnings = ChargingAnomalyRules.evaluate(0, 100, 120.0, 100.0, 80.0)
        assertTrue(warnings.any { it.code == "ENERGY_OVER_CAPACITY" })
    }

    @Test
    fun `warns on flat soc with meaningful energy`() {
        val warnings = ChargingAnomalyRules.evaluate(50, 50, 10.0, 10.0, 80.0)
        assertTrue(warnings.any { it.code == "FLAT_SOC_WITH_ENERGY" })
    }

    @Test
    fun `normal record has no warnings`() {
        val warnings = ChargingAnomalyRules.evaluate(20, 80, 45.0, 50.0, 80.0)
        assertEquals(emptyList<ChargingInputWarning>(), warnings)
    }
}
