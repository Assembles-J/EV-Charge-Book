package com.evchargebook.domain

import com.evchargebook.data.entity.ChargingRecordEntity

object ChargingRecordRules {
    fun validate(startSoc: Int, endSoc: Int, energyKwh: Double, cost: Double) {
        require(startSoc in 0..100) { "起始 SOC 必须在 0~100 之间" }
        require(endSoc in 0..100) { "结束 SOC 必须在 0~100 之间" }
        require(endSoc >= startSoc) { "结束 SOC 不能低于起始 SOC" }
        require(energyKwh > 0.0) { "充电量必须大于 0" }
        require(cost >= 0.0) { "费用不能小于 0" }
    }
}

data class ChargingSummary(
    val monthCost: Double,
    val monthEnergy: Double,
    val chargingCount: Int,
    val totalCost: Double,
    val totalEnergy: Double
) {
    val averagePrice: Double get() = if (totalEnergy > 0) totalCost / totalEnergy else 0.0
}

object ChargingStatistics {
    fun summarize(records: List<ChargingRecordEntity>, monthStart: Long, nextMonthStart: Long): ChargingSummary {
        val monthly = records.filter { it.chargeTimeEpochMillis in monthStart until nextMonthStart }
        return ChargingSummary(
            monthCost = monthly.sumOf { it.cost },
            monthEnergy = monthly.sumOf { it.energyKwh },
            chargingCount = monthly.size,
            totalCost = records.sumOf { it.cost },
            totalEnergy = records.sumOf { it.energyKwh }
        )
    }
}
