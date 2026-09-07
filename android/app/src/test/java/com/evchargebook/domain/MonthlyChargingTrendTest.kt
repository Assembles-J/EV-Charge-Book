package com.evchargebook.domain

import com.evchargebook.data.entity.ChargingRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class MonthlyChargingTrendTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun `returns continuous months including empty buckets`() {
        val records = listOf(
            record(id = 1, epochMillis = 1_767_225_600_000L, energy = 20.0, cost = 10.0),
            record(id = 2, epochMillis = 1_767_312_000_000L, energy = 30.0, cost = 15.0)
        )

        val buckets = MonthlyChargingTrend.summarize(
            records = records,
            currentMonth = YearMonth.of(2026, 1),
            zoneId = zone,
            monthCount = 3
        )

        assertEquals(listOf(11, 12, 1), buckets.map { it.month })
        assertEquals(0, buckets[0].chargingCount)
        assertEquals(0, buckets[1].chargingCount)
        assertEquals(2, buckets[2].chargingCount)
        assertEquals(50.0, buckets[2].energyKwh, 0.0001)
        assertEquals(25.0, buckets[2].cost, 0.0001)
        assertEquals(0.5, buckets[2].averagePricePerKwh!!, 0.0001)
    }

    @Test
    fun `cross month charging belongs to month where charging started`() {
        val startedAt = LocalDateTime.of(2026, 8, 31, 23, 30)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        val endedAt = LocalDateTime.of(2026, 9, 1, 7, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        val buckets = MonthlyChargingTrend.summarize(
            records = listOf(
                record(
                    id = 1,
                    epochMillis = startedAt,
                    endedAtEpochMillis = endedAt,
                    energy = 42.0,
                    cost = 18.0,
                )
            ),
            currentMonth = YearMonth.of(2026, 9),
            zoneId = zone,
            monthCount = 2,
        )

        assertEquals(listOf(8, 9), buckets.map { it.month })
        assertEquals(1, buckets[0].chargingCount)
        assertEquals(42.0, buckets[0].energyKwh, 0.0001)
        assertEquals(18.0, buckets[0].cost, 0.0001)
        assertEquals(0, buckets[1].chargingCount)
    }

    @Test
    fun `empty month has no average price`() {
        val bucket = MonthlyChargingTrend.summarize(
            records = emptyList(),
            currentMonth = YearMonth.of(2026, 8),
            zoneId = zone,
            monthCount = 1
        ).single()

        assertEquals(0.0, bucket.cost, 0.0001)
        assertEquals(0.0, bucket.energyKwh, 0.0001)
        assertNull(bucket.averagePricePerKwh)
    }

    private fun record(
        id: Long,
        epochMillis: Long,
        energy: Double,
        cost: Double,
        endedAtEpochMillis: Long? = null,
    ) = ChargingRecordEntity(
        id = id,
        vehicleId = 1,
        chargeTimeEpochMillis = epochMillis,
        endedAtEpochMillis = endedAtEpochMillis,
        energyKwh = energy,
        cost = cost,
        startSoc = 20,
        endSoc = 80
    )
}
