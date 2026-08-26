package com.evchargebook.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonthlyChargingComparisonTest {
    @Test
    fun `compares latest two months`() {
        val comparison = MonthlyChargingComparison.compare(
            listOf(
                bucket(2026, 7, cost = 100.0, energy = 50.0, count = 4),
                bucket(2026, 8, cost = 120.0, energy = 60.0, count = 2)
            )
        )!!

        assertEquals(0.2, comparison.costChangeRate!!, 0.0001)
        assertEquals(0.2, comparison.energyChangeRate!!, 0.0001)
        assertEquals(-0.5, comparison.countChangeRate!!, 0.0001)
    }

    @Test
    fun `does not invent percentage when previous month is zero`() {
        val comparison = MonthlyChargingComparison.compare(
            listOf(
                bucket(2026, 7, cost = 0.0, energy = 0.0, count = 0),
                bucket(2026, 8, cost = 30.0, energy = 10.0, count = 1)
            )
        )!!

        assertNull(comparison.costChangeRate)
        assertNull(comparison.energyChangeRate)
        assertNull(comparison.countChangeRate)
    }

    @Test
    fun `requires at least two months`() {
        assertNull(MonthlyChargingComparison.compare(listOf(bucket(2026, 8, 10.0, 5.0, 1))))
    }

    private fun bucket(year: Int, month: Int, cost: Double, energy: Double, count: Int) =
        MonthlyChargingBucket(year, month, cost, energy, count)
}
