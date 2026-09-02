package com.evchargebook.ui.records

import com.evchargebook.domain.charge.ChargeBillingField
import com.evchargebook.domain.charge.ChargeCalculationIssue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeBillingEditorStateTest {
    @Test
    fun `preset price keeps calculated cost live across repeated energy typing`() {
        val initial = ChargeBillingEditor.create(
            totalCostText = "",
            unitPriceText = "1.25",
            meterEnergyText = "",
            authoritativeBillingFields = setOf(ChargeBillingField.UNIT_PRICE)
        )

        val first = ChargeBillingEditor.edit(initial, ChargeBillingField.METER_ENERGY, "3")
        val second = ChargeBillingEditor.edit(first, ChargeBillingField.METER_ENERGY, "30")

        assertEquals("30", second.meterEnergyText)
        assertEquals("1.25", second.unitPriceText)
        assertEquals("37.5", second.totalCostText)
        assertEquals(37.5, second.totalCost!!, 0.000001)
        assertFalse(ChargeBillingField.TOTAL_COST in second.calculationInput.authoritativeBillingFields)
        assertFalse(second.issues.contains(ChargeCalculationIssue.BILLING_CONFLICT))
    }

    @Test
    fun `price edit keeps energy when displayed cost was calculated`() {
        val initial = ChargeBillingEditor.create(
            totalCostText = "",
            unitPriceText = "1.25",
            meterEnergyText = "",
            authoritativeBillingFields = setOf(ChargeBillingField.UNIT_PRICE)
        )
        val withEnergy = ChargeBillingEditor.edit(initial, ChargeBillingField.METER_ENERGY, "32")
        val result = ChargeBillingEditor.edit(withEnergy, ChargeBillingField.UNIT_PRICE, "1.10")

        assertEquals("1.10", result.unitPriceText)
        assertEquals("32", result.meterEnergyText)
        assertEquals("35.2", result.totalCostText)
        assertEquals(35.2, result.totalCost!!, 0.000001)
        assertFalse(ChargeBillingField.TOTAL_COST in result.calculationInput.authoritativeBillingFields)
        assertTrue(ChargeBillingField.UNIT_PRICE in result.calculationInput.authoritativeBillingFields)
        assertTrue(ChargeBillingField.METER_ENERGY in result.calculationInput.authoritativeBillingFields)
        assertFalse(result.issues.contains(ChargeCalculationIssue.BILLING_CONFLICT))
    }

    @Test
    fun `edited raw text is preserved while dependant field is reformatted`() {
        val initial = ChargeBillingEditor.create(
            totalCostText = "45.76",
            unitPriceText = "1.22",
            meterEnergyText = "37.508",
            authoritativeBillingFields = setOf(
                ChargeBillingField.TOTAL_COST,
                ChargeBillingField.UNIT_PRICE
            )
        )

        val result = ChargeBillingEditor.edit(initial, ChargeBillingField.UNIT_PRICE, "1.10")

        assertEquals("1.10", result.unitPriceText)
        assertEquals("45.76", result.totalCostText)
        assertEquals("41.6", result.meterEnergyText)
        assertEquals(45.76 / 1.10, result.meterEnergyKwh!!, 0.000001)
    }

    @Test
    fun `cost edit holds visible price and recalculates energy`() {
        val initial = ChargeBillingEditor.create(
            totalCostText = "40",
            unitPriceText = "1.25",
            meterEnergyText = "32",
            authoritativeBillingFields = setOf(
                ChargeBillingField.UNIT_PRICE,
                ChargeBillingField.METER_ENERGY
            )
        )

        val result = ChargeBillingEditor.edit(initial, ChargeBillingField.TOTAL_COST, "45")

        assertEquals("45", result.totalCostText)
        assertEquals("1.25", result.unitPriceText)
        assertEquals("36", result.meterEnergyText)
        assertTrue(ChargeBillingField.TOTAL_COST in result.calculationInput.authoritativeBillingFields)
        assertFalse(ChargeBillingField.METER_ENERGY in result.calculationInput.authoritativeBillingFields)
    }

    @Test
    fun `authoritative cost and price survive energy edit and expose one conflict`() {
        val initial = ChargeBillingEditor.create(
            totalCostText = "45.76",
            unitPriceText = "1.22",
            meterEnergyText = "37.508",
            authoritativeBillingFields = setOf(
                ChargeBillingField.TOTAL_COST,
                ChargeBillingField.UNIT_PRICE
            )
        )

        val result = ChargeBillingEditor.edit(initial, ChargeBillingField.METER_ENERGY, "40")

        assertEquals("45.76", result.totalCostText)
        assertEquals("1.22", result.unitPriceText)
        assertEquals("40", result.meterEnergyText)
        assertTrue(result.issues.contains(ChargeCalculationIssue.BILLING_CONFLICT))
    }

    @Test
    fun `clearing an edited field keeps the raw blank without erasing unrelated text`() {
        val initial = ChargeBillingEditor.create(
            totalCostText = "45.76",
            unitPriceText = "1.22",
            meterEnergyText = "37.508",
            authoritativeBillingFields = setOf(
                ChargeBillingField.TOTAL_COST,
                ChargeBillingField.UNIT_PRICE
            )
        )

        val result = ChargeBillingEditor.edit(initial, ChargeBillingField.TOTAL_COST, "")

        assertEquals("", result.totalCostText)
        assertEquals("1.22", result.unitPriceText)
        assertEquals("37.508", result.meterEnergyText)
        assertNull(result.totalCost)
    }

    @Test
    fun `intermediate decimal text remains untouched`() {
        val initial = ChargeBillingEditor.create(
            totalCostText = "",
            unitPriceText = "1.25",
            meterEnergyText = "",
            authoritativeBillingFields = setOf(ChargeBillingField.UNIT_PRICE)
        )

        val result = ChargeBillingEditor.edit(initial, ChargeBillingField.METER_ENERGY, "1.")

        assertEquals("1.", result.meterEnergyText)
        assertEquals("1.25", result.unitPriceText)
        assertEquals("1.25", result.totalCostText)
        assertEquals(1.0, result.meterEnergyKwh!!, 0.000001)
    }

    @Test
    fun `display rounding does not replace full precision calculation state`() {
        val initial = ChargeBillingEditor.create(
            totalCostText = "45.76",
            unitPriceText = "1.22",
            meterEnergyText = "",
            authoritativeBillingFields = setOf(
                ChargeBillingField.TOTAL_COST,
                ChargeBillingField.UNIT_PRICE
            )
        )

        val result = ChargeBillingEditor.edit(initial, ChargeBillingField.TOTAL_COST, "45.76")

        assertEquals("37.508", result.meterEnergyText)
        assertEquals(45.76 / 1.22, result.meterEnergyKwh!!, 0.0000001)
    }
}
