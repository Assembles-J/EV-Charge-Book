package com.evchargebook.domain

import com.evchargebook.data.entity.ChargingRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChargingBusinessRulesTest {
    @Test fun `rejects an SOC decrease`() {
        val error = runCatching { ChargingRecordRules.validate(80, 60, 10.0, 10.0) }.exceptionOrNull()
        assertEquals("结束 SOC 不能低于起始 SOC", error?.message)
    }

    @Test fun `rejects a negative odometer`() {
        val error = runCatching { ChargingRecordRules.validate(20, 80, 10.0, 10.0, -1.0) }.exceptionOrNull()
        assertEquals("里程不能小于 0", error?.message)
    }

    @Test fun `finds the latest earlier odometer for the same vehicle`() {
        val records = listOf(
            record(id = 1, vehicleId = 1, time = 100, odometer = 1000.0),
            record(id = 2, vehicleId = 1, time = 200, odometer = 1200.0),
            record(id = 3, vehicleId = 2, time = 250, odometer = 9999.0),
            record(id = 4, vehicleId = 1, time = 400, odometer = 1600.0)
        )
        val previous = ChargingRecordRules.previousOdometerKm(records, 1, 300)
            ?: error("expected a previous odometer")
        assertEquals(1200.0, previous, 0.0)
    }

    @Test fun `warns without blocking when odometer is lower than previous reading`() {
        assertEquals(
            "当前里程低于上一条记录（1200 km），请确认是否录入正确",
            ChargingRecordRules.odometerWarning(1200.0, 1190.0)
        )
        assertNull(ChargingRecordRules.odometerWarning(1200.0, 1210.0))
        assertNull(ChargingRecordRules.odometerWarning(null, 1210.0))
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

    private fun record(
        id: Long = 0,
        vehicleId: Long = 1,
        time: Long,
        energy: Double = 10.0,
        cost: Double = 20.0,
        odometer: Double? = null
    ) = ChargingRecordEntity(
        id = id,
        vehicleId = vehicleId,
        chargeTimeEpochMillis = time,
        startSoc = 20,
        endSoc = 50,
        energyKwh = energy,
        cost = cost,
        odometerKm = odometer
    )
}
