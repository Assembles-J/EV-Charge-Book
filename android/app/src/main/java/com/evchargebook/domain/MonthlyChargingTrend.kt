package com.evchargebook.domain

import com.evchargebook.data.entity.ChargingRecordEntity
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class MonthlyChargingBucket(
    val year: Int,
    val month: Int,
    val cost: Double,
    val energyKwh: Double,
    val chargingCount: Int
) {
    val averagePricePerKwh: Double?
        get() = if (energyKwh > 0.0) cost / energyKwh else null
}

object MonthlyChargingTrend {
    fun summarize(
        records: List<ChargingRecordEntity>,
        currentMonth: YearMonth,
        zoneId: ZoneId,
        monthCount: Int = 6
    ): List<MonthlyChargingBucket> {
        if (monthCount <= 0) return emptyList()

        val months = (monthCount - 1 downTo 0).map { currentMonth.minusMonths(it.toLong()) }
        val grouped = records.groupBy { record ->
            YearMonth.from(Instant.ofEpochMilli(record.chargeTimeEpochMillis).atZone(zoneId))
        }

        return months.map { month ->
            val monthRecords = grouped[month].orEmpty()
            MonthlyChargingBucket(
                year = month.year,
                month = month.monthValue,
                cost = monthRecords.sumOf { it.cost },
                energyKwh = monthRecords.sumOf { it.energyKwh },
                chargingCount = monthRecords.size
            )
        }
    }
}
