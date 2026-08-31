package com.evchargebook.domain.charge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeCalculationEngineTest {
    @Test
    fun `editing total cost keeps unit price and recalculates meter energy`() {
        val result = ChargeCalculationEngine.editBilling(
            input = ChargeCalculationInput(
                totalCost = 40.0,
                unitPrice = 1.22,
                meterEnergyKwh = 32.0
            ),
            field = ChargeBillingField.TOTAL_COST,
            value = 45.76
        )

        assertEquals(45.76, result.input.totalCost!!, 0.000001)
        assertEquals(1.22, result.input.unitPrice!!, 0.000001)
        assertEquals(45.76 / 1.22, result.input.meterEnergyKwh!!, 0.000001)
        assertFalse(result.issues.contains(ChargeCalculationIssue.BILLING_CONFLICT))
    }

    @Test
    fun `editing unit price keeps total cost and recalculates meter energy`() {
        val result = ChargeCalculationEngine.editBilling(
            input = ChargeCalculationInput(
                totalCost = 45.76,
                unitPrice = 1.22,
                meterEnergyKwh = 45.76 / 1.22
            ),
            field = ChargeBillingField.UNIT_PRICE,
            value = 1.10
        )

        assertEquals(45.76, result.input.totalCost!!, 0.000001)
        assertEquals(1.10, result.input.unitPrice!!, 0.000001)
        assertEquals(45.76 / 1.10, result.input.meterEnergyKwh!!, 0.000001)
        assertFalse(result.issues.contains(ChargeCalculationIssue.BILLING_CONFLICT))
    }

    @Test
    fun `editing meter energy preserves higher priority cost and price and reports conflict`() {
        val result = ChargeCalculationEngine.editBilling(
            input = ChargeCalculationInput(
                totalCost = 45.76,
                unitPrice = 1.22,
                meterEnergyKwh = 45.76 / 1.22
            ),
            field = ChargeBillingField.METER_ENERGY,
            value = 40.0
        )

        assertEquals(45.76, result.input.totalCost!!, 0.000001)
        assertEquals(1.22, result.input.unitPrice!!, 0.000001)
        assertEquals(40.0, result.input.meterEnergyKwh!!, 0.000001)
        assertTrue(result.issues.contains(ChargeCalculationIssue.BILLING_CONFLICT))
    }

    @Test
    fun `meter energy can fill missing unit price without changing known total cost`() {
        val result = ChargeCalculationEngine.editBilling(
            input = ChargeCalculationInput(totalCost = 45.0),
            field = ChargeBillingField.METER_ENERGY,
            value = 30.0
        )

        assertEquals(45.0, result.input.totalCost!!, 0.000001)
        assertEquals(1.5, result.input.unitPrice!!, 0.000001)
        assertEquals(30.0, result.input.meterEnergyKwh!!, 0.000001)
    }

    @Test
    fun `meter energy can fill missing total cost from known unit price`() {
        val result = ChargeCalculationEngine.editBilling(
            input = ChargeCalculationInput(unitPrice = 1.25),
            field = ChargeBillingField.METER_ENERGY,
            value = 32.0
        )

        assertEquals(40.0, result.input.totalCost!!, 0.000001)
        assertEquals(1.25, result.input.unitPrice!!, 0.000001)
        assertEquals(32.0, result.input.meterEnergyKwh!!, 0.000001)
    }

    @Test
    fun `derives duration average power and charging loss from compatible facts`() {
        val result = ChargeCalculationEngine.calculate(
            ChargeCalculationInput(
                meterEnergyKwh = 37.60,
                vehicleEnergyKwh = 34.36,
                startTimeEpochMillis = 1_000L,
                endTimeEpochMillis = 3_601_000L
            )
        )

        assertEquals(3_600_000L, result.durationMillis)
        assertEquals(37.60, result.averagePowerKw!!, 0.000001)
        assertEquals(3.24, result.lossEnergyKwh!!, 0.000001)
        assertEquals(3.24 / 37.60, result.lossRate!!, 0.000001)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun `vehicle energy above meter energy is inconsistent rather than fake zero loss`() {
        val result = ChargeCalculationEngine.calculate(
            ChargeCalculationInput(
                meterEnergyKwh = 30.0,
                vehicleEnergyKwh = 31.0
            )
        )

        assertNull(result.lossEnergyKwh)
        assertNull(result.lossRate)
        assertTrue(result.issues.contains(ChargeCalculationIssue.VEHICLE_ENERGY_EXCEEDS_METER))
    }

    @Test
    fun `end time must be after start time for duration and average power`() {
        val result = ChargeCalculationEngine.calculate(
            ChargeCalculationInput(
                meterEnergyKwh = 30.0,
                startTimeEpochMillis = 5_000L,
                endTimeEpochMillis = 5_000L
            )
        )

        assertNull(result.durationMillis)
        assertNull(result.averagePowerKw)
        assertTrue(result.issues.contains(ChargeCalculationIssue.END_NOT_AFTER_START))
    }

    @Test
    fun `repeated higher priority edits do not accumulate rounding drift`() {
        val initial = ChargeCalculationInput(
            totalCost = 45.76,
            unitPrice = 1.22,
            meterEnergyKwh = 45.76 / 1.22
        )
        val cheaper = ChargeCalculationEngine.editBilling(
            initial,
            ChargeBillingField.UNIT_PRICE,
            1.10
        )
        val restored = ChargeCalculationEngine.editBilling(
            cheaper.input,
            ChargeBillingField.UNIT_PRICE,
            1.22
        )

        assertEquals(45.76, restored.input.totalCost!!, 0.000001)
        assertEquals(45.76 / 1.22, restored.input.meterEnergyKwh!!, 0.000001)
        assertFalse(restored.issues.contains(ChargeCalculationIssue.BILLING_CONFLICT))
    }
}
