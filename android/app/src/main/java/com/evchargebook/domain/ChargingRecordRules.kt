package com.evchargebook.domain

import com.evchargebook.data.entity.ChargingRecordEntity

object ChargingRecordRules {
    fun validate(startSoc: Int, endSoc: Int, energyKwh: Double, cost: Double, odometerKm: Double? = null) {
        require(startSoc in 0..100) { "起始 SOC 必须在 0~100 之间" }
        require(endSoc in 0..100) { "结束 SOC 必须在 0~100 之间" }
        require(endSoc >= startSoc) { "结束 SOC 不能低于起始 SOC" }
        require(energyKwh > 0.0) { "充电量必须大于 0" }
        require(cost >= 0.0) { "费用不能小于 0" }
        require(odometerKm == null || odometerKm >= 0.0) { "里程不能小于 0" }
    }

    fun previousOdometerKm(
        records: List<ChargingRecordEntity>,
        vehicleId: Long,
        chargeTimeEpochMillis: Long,
        excludeRecordId: Long? = null
    ): Double? = records
        .asSequence()
        .filter { it.vehicleId == vehicleId }
        .filter { excludeRecordId == null || it.id != excludeRecordId }
        .filter { it.chargeTimeEpochMillis < chargeTimeEpochMillis }
        .filter { it.odometerKm != null }
        .maxByOrNull { it.chargeTimeEpochMillis }
        ?.odometerKm

    fun odometerWarning(previousOdometerKm: Double?, currentOdometerKm: Double?): String? {
        if (previousOdometerKm == null || currentOdometerKm == null) return null
        return if (currentOdometerKm < previousOdometerKm) {
            "当前里程低于上一条记录（${formatKm(previousOdometerKm)} km），请确认是否录入正确"
        } else {
            null
        }
    }

    private fun formatKm(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else "%.1f".format(value)
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
