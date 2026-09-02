package com.evchargebook.ui.records

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargingPriceSuggestionPolicyTest {
    @Test
    fun `same normalized location and type may auto fill fresh stored session tariff`() {
        val memories = listOf(
            memory(1.20, "家充", " 上海  奉贤 ", 100, storedTariff = true),
            memory(1.35, "家充", "上海 奉贤", 200, storedTariff = true),
        )

        val result = ChargingPriceSuggestionPolicy.autoFillPrice(
            chargerType = " 家充 ",
            location = "上海   奉贤",
            memories = memories,
            nowEpochMillis = 300,
            maxAgeMillis = 500,
        )

        assertEquals(1.35, result!!, 0.000001)
    }

    @Test
    fun `same type at another location is suggestion only`() {
        val memories = listOf(
            memory(0.72, "家充", "公司地库", 200, storedTariff = true),
        )

        val auto = ChargingPriceSuggestionPolicy.autoFillPrice(
            chargerType = "家充",
            location = "家",
            memories = memories,
            nowEpochMillis = 300,
            maxAgeMillis = 500,
        )
        val suggestions = ChargingPriceSuggestionPolicy.suggestionPrices(
            chargerType = "家充",
            location = "家",
            memories = memories,
        )

        assertNull(auto)
        assertEquals(listOf(0.72), suggestions)
    }

    @Test
    fun `effective record price is never an automatic tariff`() {
        val memories = listOf(
            memory(1.18, "公共快充", "站点 A", 300, storedTariff = false),
        )

        val auto = ChargingPriceSuggestionPolicy.autoFillPrice(
            chargerType = "公共快充",
            location = "站点 A",
            memories = memories,
            nowEpochMillis = 400,
            maxAgeMillis = 500,
        )
        val suggestions = ChargingPriceSuggestionPolicy.suggestionPrices(
            chargerType = "公共快充",
            location = "站点 A",
            memories = memories,
        )

        assertNull(auto)
        assertEquals(listOf(1.18), suggestions)
    }

    @Test
    fun `stored tariff older than thirty days is suggestion only`() {
        val now = 100L * DAY_MILLIS
        val staleTimestamp = now - 31L * DAY_MILLIS
        val memories = listOf(
            memory(0.61, "家充", "家", staleTimestamp, storedTariff = true),
        )

        val auto = ChargingPriceSuggestionPolicy.autoFillPrice(
            chargerType = "家充",
            location = "家",
            memories = memories,
            nowEpochMillis = now,
        )
        val suggestions = ChargingPriceSuggestionPolicy.suggestionPrices(
            chargerType = "家充",
            location = "家",
            memories = memories,
        )

        assertNull(auto)
        assertEquals(listOf(0.61), suggestions)
    }

    @Test
    fun `stored tariff at freshness boundary may auto fill`() {
        val now = 100L * DAY_MILLIS
        val boundaryTimestamp = now - ChargingPriceSuggestionPolicy.DEFAULT_AUTO_FILL_MAX_AGE_MILLIS
        val memories = listOf(
            memory(0.58, "家充", "家", boundaryTimestamp, storedTariff = true),
        )

        val auto = ChargingPriceSuggestionPolicy.autoFillPrice(
            chargerType = "家充",
            location = "家",
            memories = memories,
            nowEpochMillis = now,
        )

        assertEquals(0.58, auto!!, 0.000001)
    }

    @Test
    fun `future timestamp never becomes silent auto fill authority`() {
        val now = 100L * DAY_MILLIS
        val memories = listOf(
            memory(0.66, "家充", "家", now + DAY_MILLIS, storedTariff = true),
        )

        val auto = ChargingPriceSuggestionPolicy.autoFillPrice(
            chargerType = "家充",
            location = "家",
            memories = memories,
            nowEpochMillis = now,
        )

        assertNull(auto)
    }

    @Test
    fun `suggestions prioritize exact stored tariff then type tariff then effective prices`() {
        val memories = listOf(
            memory(1.40, "家充", "家", 100, storedTariff = true),
            memory(1.10, "家充", "公司", 400, storedTariff = true),
            memory(0.95, "公共快充", "其他", 500, storedTariff = true),
            memory(1.30, "家充", "家", 600, storedTariff = false),
        )

        val suggestions = ChargingPriceSuggestionPolicy.suggestionPrices(
            chargerType = "家充",
            location = "家",
            memories = memories,
        )

        assertEquals(listOf(1.40, 1.10, 0.95, 1.30), suggestions)
    }

    @Test
    fun `suggestions deduplicate prices across source tiers`() {
        val memories = listOf(
            memory(1.20, "家充", "家", 300, storedTariff = true),
            memory(1.20, "家充", "公司", 200, storedTariff = true),
            memory(1.20, "家充", "家", 100, storedTariff = false),
        )

        val suggestions = ChargingPriceSuggestionPolicy.suggestionPrices(
            chargerType = "家充",
            location = "家",
            memories = memories,
        )

        assertEquals(listOf(1.20), suggestions)
    }

    @Test
    fun `location normalization trims collapses whitespace and ignores case`() {
        assertEquals(
            ChargingPriceSuggestionPolicy.normalizeLocation("  Garage   A  "),
            ChargingPriceSuggestionPolicy.normalizeLocation("garage a"),
        )
        assertTrue(ChargingPriceSuggestionPolicy.normalizeLocation("   ") == null)
    }

    private fun memory(
        price: Double,
        type: String?,
        location: String?,
        timestamp: Long,
        storedTariff: Boolean,
    ) = ChargingPriceMemory(
        pricePerKwh = price,
        chargerType = type,
        location = location,
        timestampEpochMillis = timestamp,
        isStoredTariff = storedTariff,
    )

    private companion object {
        const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}
