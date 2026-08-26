package com.evchargebook.domain

import com.evchargebook.data.entity.ChargingRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ChargingBusinessRulesTest {
    @Test fun `rejects an SOC decrease`() {
        val error = runCatching { ChargingRecordRules.validate(80, 60, 10.0, 10.0) }.exceptionOrNull()
        assertEquals("结束 SOC 不能低于起始 SOC", error?.message)
    }

    @Test fun `summarizes only records inside the requested month`() {
        val records = listOf(record(time = 100, energy = 10.0, cost = 20.0), record(time = 250, energy = 5.0, cost = 15.0), record(time = 300, energy = 20.0, cost = 40.0))
        val summary = ChargingStatistics.summarize(records, monthStart = 200, nextMonthStart = 300)
        assertEquals(15.0, summary.monthCost, 0.0)
        assertEquals(5.0, summary.monthEnergy, 0.0)
        assertEquals(1, summary.chargingCount)
        assertEquals(75.0, summary.totalCost, 0.0)
        assertEquals(35.0, summary.totalEnergy, 0.0)
        assertEquals(75.0 / 35.0, summary.averagePrice, 0.0)
    }

    private fun record(time: Long, energy: Double, cost: Double) = ChargingRecordEntity(vehicleId = 1, chargeTimeEpochMillis = time, startSoc = 20, endSoc = 50, energyKwh = energy, cost = cost)
}
