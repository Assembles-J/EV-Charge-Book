package com.evchargebook.domain

enum class TripGpsHealthStatus {
    WAITING,
    GOOD,
    DEGRADED,
    LOST,
    LONG_GAP
}

data class TripGpsHealthSnapshot(
    val status: TripGpsHealthStatus,
    val secondsSinceLastCallback: Long?,
    val secondsSinceLastAcceptedPoint: Long?,
    val message: String
)

object TripGpsHealth {
    const val DEGRADED_AFTER_SECONDS = 15L
    const val LOST_AFTER_SECONDS = 30L
    const val LONG_GAP_AFTER_SECONDS = 120L

    fun evaluate(
        nowEpochMillis: Long,
        trackingStartedAtEpochMillis: Long,
        lastCallbackAtEpochMillis: Long?,
        lastAcceptedPointAtEpochMillis: Long?
    ): TripGpsHealthSnapshot {
        val callbackAge = lastCallbackAtEpochMillis?.let { ageSeconds(nowEpochMillis, it) }
        val acceptedAge = lastAcceptedPointAtEpochMillis?.let { ageSeconds(nowEpochMillis, it) }
        val effectiveAge = acceptedAge ?: callbackAge ?: ageSeconds(nowEpochMillis, trackingStartedAtEpochMillis)

        val status = when {
            lastCallbackAtEpochMillis == null && effectiveAge < DEGRADED_AFTER_SECONDS -> TripGpsHealthStatus.WAITING
            effectiveAge >= LONG_GAP_AFTER_SECONDS -> TripGpsHealthStatus.LONG_GAP
            effectiveAge >= LOST_AFTER_SECONDS -> TripGpsHealthStatus.LOST
            effectiveAge >= DEGRADED_AFTER_SECONDS -> TripGpsHealthStatus.DEGRADED
            else -> TripGpsHealthStatus.GOOD
        }

        val message = when (status) {
            TripGpsHealthStatus.WAITING -> "正在等待首次定位"
            TripGpsHealthStatus.GOOD -> "GPS 正常"
            TripGpsHealthStatus.DEGRADED -> "GPS 更新变慢"
            TripGpsHealthStatus.LOST -> "GPS 暂时中断"
            TripGpsHealthStatus.LONG_GAP -> "GPS 已长时间中断"
        }

        return TripGpsHealthSnapshot(status, callbackAge, acceptedAge, message)
    }

    private fun ageSeconds(nowEpochMillis: Long, thenEpochMillis: Long): Long =
        ((nowEpochMillis - thenEpochMillis).coerceAtLeast(0L) / 1000L)
}
