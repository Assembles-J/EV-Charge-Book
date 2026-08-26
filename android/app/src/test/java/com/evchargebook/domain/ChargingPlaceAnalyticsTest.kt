package com.evchargebook.domain

import com.evchargebook.data.entity.ChargingRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChargingPlaceAnalyticsTest {
    @Test
    fun `normalizes whitespace and aggregates exact place text`() {
        val records = listOf(
            record(1, "  家里车位  ", 20.0, 10.0, 1_000),
            record(2, "家里车位", 30.0, 18.0, 2_000),
            record(3, "公司   地库", 10.0, 8.0, 3_000),
            record(4, "公司 地库", 20.0, 12.0, 4_000)
        )

        val summaries = ChargingPlaceAnalytics.summarize(records)

        assertEquals(2, summaries.size)
        val home = summaries.first { it.displayName == "家里车位" }
        assertEquals(2, home.chargingCount)
        assertEquals(50.0, home.energyKwh, 0.0001)
        assertEquals(28.0, home.cost, 0.0001)
        assertEquals(0.56, home.averagePricePerKwh!!, 0.0001)
        assertEquals(2_000L, home.latestChargeTimeEpochMillis)
    }

    @Test
    fun `keeps different address text separate and ignores blanks`() {
        val records = listOf(
            record(1, "上海站 P1", 10.0, 5.0, 1_000),
            record(2, "上海站 P2", 10.0, 5.0, 2_000),
            record(3, "   ", 10.0, 5.0, 3_000),
            record(4, null, 10.0, 5.0, 4_000)
        )

        val summaries = ChargingPlaceAnalytics.summarize(records)

        assertEquals(2, summaries.size)
        assertEquals(setOf("上海站 P1", "上海站 P2"), summaries.map { it.displayName }.toSet())
        assertNull(ChargingPlaceAnalytics.normalize("  "))
        assertNull(ChargingPlaceAnalytics.normalize(null))
    }

    @Test
    fun `sorts by count then recency`() {
        val records = listOf(
            record(1, "A", 10.0, 5.0, 1_000),
            record(2, "A", 10.0, 5.0, 2_000),
            record(3, "B", 10.0, 5.0, 5_000),
            record(4, "B", 10.0, 5.0, 6_000),
            record(5, "C", 10.0, 5.0, 9_000)
        )

        val summaries = ChargingPlaceAnalytics.summarize(records)

        assertEquals(listOf("B", "A", "C"), summaries.map { it.displayName })
    }

    private fun record(
        id: Long,
        location: String?,
        energy: Double,
        cost: Double,
        time: Long
    ) = ChargingRecordEntity(
        id = id,
        vehicleId = 1,
        chargeTimeEpochMillis = time,
        energyKwh = energy,
        cost = cost,
        startSoc = 20,
        endSoc = 80,
        location = location
    )
}
