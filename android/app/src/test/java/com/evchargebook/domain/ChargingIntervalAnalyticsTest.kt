package com.evchargebook.domain

import com.evchargebook.data.entity.ChargingRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChargingIntervalAnalyticsTest {
    @Test
    fun `calculates weighted replenishment metrics from consecutive odometer records`() {
        val records = listOf(
            record(id = 1, time = 1_000, odometer = 10_000.0, energy = 20.0, cost = 10.0),
            record(id = 2, time = 2_000, odometer = 10_100.0, energy = 18.0, cost = 9.0),
            record(id = 3, time = 3_000, odometer = 10_250.0, energy = 30.0, cost = 15.0)
        )

        val summary = ChargingIntervalAnalytics.summarize(records)

        assertEquals(2, summary.samples.size)
        assertEquals(250.0, summary.totalDistanceKm, 0.0001)
        assertEquals(48.0, summary.totalEnergyKwh, 0.0001)
        assertEquals(24.0, summary.totalCost, 0.0001)
        assertEquals(19.2, summary.energyPer100Km!!, 0.0001)
        assertEquals(9.6, summary.costPer100Km!!, 0.0001)
        assertEquals(ChargingEstimateConfidence.HIGH, summary.samples.first().confidence)
    }

    @Test
    fun `excludes odometer rollback interval and counts it invalid`() {
        val records = listOf(
            record(id = 1, time = 1_000, odometer = 10_000.0),
            record(id = 2, time = 2_000, odometer = 9_900.0),
            record(id = 3, time = 3_000, odometer = 10_100.0, energy = 20.0, cost = 12.0)
        )

        val summary = ChargingIntervalAnalytics.summarize(records)

        assertEquals(1, summary.invalidIntervalCount)
        assertEquals(1, summary.samples.size)
        assertEquals(200.0, summary.totalDistanceKm, 0.0001)
    }

    @Test
    fun `ignores records without odometer and keeps metrics unavailable without interval`() {
        val records = listOf(
            record(id = 1, time = 1_000, odometer = null),
            record(id = 2, time = 2_000, odometer = 10_000.0)
        )

        val summary = ChargingIntervalAnalytics.summarize(records)

        assertEquals(0, summary.samples.size)
        assertNull(summary.energyPer100Km)
        assertNull(summary.costPer100Km)
    }

    @Test
    fun `classifies estimate confidence from charge end SOC alignment`() {
        val records = listOf(
            record(id = 1, time = 1_000, odometer = 10_000.0, endSoc = 80),
            record(id = 2, time = 2_000, odometer = 10_100.0, endSoc = 84),
            record(id = 3, time = 3_000, odometer = 10_200.0, endSoc = 95),
            record(id = 4, time = 4_000, odometer = 10_300.0, endSoc = 70)
        )

        val samples = ChargingIntervalAnalytics.summarize(records).samples

        assertEquals(4, samples[0].endSocDeltaPoints)
        assertEquals(ChargingEstimateConfidence.HIGH, samples[0].confidence)
        assertEquals(11, samples[1].endSocDeltaPoints)
        assertEquals(ChargingEstimateConfidence.MEDIUM, samples[1].confidence)
        assertEquals(25, samples[2].endSocDeltaPoints)
        assertEquals(ChargingEstimateConfidence.LOW, samples[2].confidence)
    }

    private fun record(
        id: Long,
        time: Long,
        odometer: Double?,
        energy: Double = 10.0,
        cost: Double = 5.0,
        endSoc: Int = 80
    ) = ChargingRecordEntity(
        id = id,
        vehicleId = 1,
        chargeTimeEpochMillis = time,
        energyKwh = energy,
        cost = cost,
        startSoc = 20,
        endSoc = endSoc,
        odometerKm = odometer
    )
}
