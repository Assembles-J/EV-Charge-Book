package com.evchargebook.domain

data class MonthlyComparison(
    val current: MonthlyChargingBucket,
    val previous: MonthlyChargingBucket,
    val costChangeRate: Double?,
    val energyChangeRate: Double?,
    val countChangeRate: Double?
)

object MonthlyChargingComparison {
    fun compare(buckets: List<MonthlyChargingBucket>): MonthlyComparison? {
        if (buckets.size < 2) return null
        val previous = buckets[buckets.lastIndex - 1]
        val current = buckets.last()
        return MonthlyComparison(
            current = current,
            previous = previous,
            costChangeRate = changeRate(current.cost, previous.cost),
            energyChangeRate = changeRate(current.energyKwh, previous.energyKwh),
            countChangeRate = changeRate(current.chargingCount.toDouble(), previous.chargingCount.toDouble())
        )
    }

    private fun changeRate(current: Double, previous: Double): Double? =
        if (previous > 0.0) (current - previous) / previous else null
}
