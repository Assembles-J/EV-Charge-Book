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

    /** Legacy/test-friendly wall-clock entry point. Runtime tracking should use evaluateMonotonic. */
    fun evaluate(
        nowEpochMillis: Long,
        trackingStartedAtEpochMillis: Long,
        lastCallbackAtEpochMillis: Long?,
        lastAcceptedPointAtEpochMillis: Long?
    ): TripGpsHealthSnapshot = evaluateIntervals(
        nowMillis = nowEpochMillis,
        trackingStartedAtMillis = trackingStartedAtEpochMillis,
        lastCallbackAtMillis = lastCallbackAtEpochMillis,
        lastAcceptedPointAtMillis = lastAcceptedPointAtEpochMillis,
    )

    /**
     * Runtime health entry point using SystemClock.elapsedRealtime milliseconds.
     *
     * Health is about callback/acceptance age, not civil time. A network/user wall-clock correction
     * must therefore not manufacture a DEGRADED/LOST/LONG_GAP transition.
     */
    fun evaluateMonotonic(
        nowElapsedRealtimeMillis: Long,
        trackingStartedAtElapsedRealtimeMillis: Long,
        lastCallbackAtElapsedRealtimeMillis: Long?,
        lastAcceptedPointAtElapsedRealtimeMillis: Long?,
    ): TripGpsHealthSnapshot = evaluateIntervals(
        nowMillis = nowElapsedRealtimeMillis,
        trackingStartedAtMillis = trackingStartedAtElapsedRealtimeMillis,
        lastCallbackAtMillis = lastCallbackAtElapsedRealtimeMillis,
        lastAcceptedPointAtMillis = lastAcceptedPointAtElapsedRealtimeMillis,
    )

    private fun evaluateIntervals(
        nowMillis: Long,
        trackingStartedAtMillis: Long,
        lastCallbackAtMillis: Long?,
        lastAcceptedPointAtMillis: Long?,
    ): TripGpsHealthSnapshot {
        val callbackAge = lastCallbackAtMillis?.let { ageSeconds(nowMillis, it) }
        val acceptedAge = lastAcceptedPointAtMillis?.let { ageSeconds(nowMillis, it) }
        val effectiveAge = acceptedAge ?: callbackAge ?: ageSeconds(nowMillis, trackingStartedAtMillis)

        val status = when {
            lastCallbackAtMillis == null && effectiveAge < DEGRADED_AFTER_SECONDS -> TripGpsHealthStatus.WAITING
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

    private fun ageSeconds(nowMillis: Long, thenMillis: Long): Long =
        ((nowMillis - thenMillis).coerceAtLeast(0L) / 1000L)
}
