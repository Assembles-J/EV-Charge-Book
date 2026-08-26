package com.evchargebook.domain

import com.evchargebook.data.entity.ChargingRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ChargerTypeAnalyticsTest {
    @Test
    fun `classifies known charger types`() {
        assertEquals(ChargerCategory.HOME, ChargerTypeAnalytics.categoryOf("家充"))
        assertEquals(ChargerCategory.PUBLIC_SLOW, ChargerTypeAnalytics.categoryOf("公共慢充"))
        assertEquals(ChargerCategory.PUBLIC_FAST, ChargerTypeAnalytics.categoryOf("公共快充"))
        assertEquals(ChargerCategory.PUBLIC_FAST, ChargerTypeAnalytics.categoryOf("超充"))
    }

    @Test
    fun `calculates charger type shares`() {
        val records = listOf(
            record(1, "家充", 20.0, 6.0),
            record(2, "公共慢充", 30.0, 15.0),
            record(3, "公共快充", 40.0, 36.0),
            record(4, "超充", 10.0, 9.0)
        )

        val summaries = ChargerTypeAnalytics.summarize(records)
        val summary = summaries.associateBy { it.category }
        val diagnostic = summaries.joinToString()

        assertEquals("summary=$diagnostic", 1, summary[ChargerCategory.HOME]!!.chargingCount)
        assertEquals("summary=$diagnostic", 1, summary[ChargerCategory.PUBLIC_SLOW]!!.chargingCount)
        assertEquals("summary=$diagnostic", 2, summary[ChargerCategory.PUBLIC_FAST]!!.chargingCount)
        assertEquals("summary=$diagnostic", 0, summary[ChargerCategory.OTHER]!!.chargingCount)
        assertEquals("summary=$diagnostic", 0.5, summary[ChargerCategory.PUBLIC_FAST]!!.countShare, 0.0001)
        assertEquals("summary=$diagnostic", 0.5, summary[ChargerCategory.PUBLIC_FAST]!!.energyShare, 0.0001)
    }

    @Test
    fun `keeps unknown or missing charger type as other`() {
        assertEquals(ChargerCategory.OTHER, ChargerTypeAnalytics.categoryOf(null))
        assertEquals(ChargerCategory.OTHER, ChargerTypeAnalytics.categoryOf("换电"))
    }

    private fun record(id: Long, type: String?, energy: Double, cost: Double) = ChargingRecordEntity(
        id = id,
        vehicleId = 1,
        chargeTimeEpochMillis = id * 1_000,
        energyKwh = energy,
        cost = cost,
        startSoc = 20,
        endSoc = 80,
        chargerType = type
    )
}
