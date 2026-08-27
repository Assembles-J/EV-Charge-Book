package com.evchargebook.domain

import com.evchargebook.data.entity.TripPointEntity

data class TripCompletenessSummary(
    val acceptedPointCount: Int,
    val gpsPointCount: Int,
    val networkPointCount: Int,
    val otherProviderPointCount: Int,
    val longGapCount: Int,
    val longestGapSeconds: Long,
    val cumulativeLongGapSeconds: Long
) {
    val hasLongGap: Boolean get() = longGapCount > 0
}

object TripCompletenessAnalytics {
    fun summarize(points: List<TripPointEntity>): TripCompletenessSummary {
        val ordered = points.sortedBy { it.capturedAtEpochMillis }
        var longGapCount = 0
        var longestGapSeconds = 0L
        var cumulativeLongGapSeconds = 0L

        ordered.zipWithNext().forEach { (previous, current) ->
            val deltaSeconds = ((current.capturedAtEpochMillis - previous.capturedAtEpochMillis) / 1000)
                .coerceAtLeast(0L)
            if (deltaSeconds >= TripContinuityRules.LONG_GAP_SECONDS) {
                longGapCount += 1
                longestGapSeconds = maxOf(longestGapSeconds, deltaSeconds)
                cumulativeLongGapSeconds += deltaSeconds
            }
        }

        val gpsPointCount = ordered.count { it.provider.equals("gps", ignoreCase = true) }
        val networkPointCount = ordered.count { it.provider.equals("network", ignoreCase = true) }
        val otherProviderPointCount = ordered.size - gpsPointCount - networkPointCount

        return TripCompletenessSummary(
            acceptedPointCount = ordered.size,
            gpsPointCount = gpsPointCount,
            networkPointCount = networkPointCount,
            otherProviderPointCount = otherProviderPointCount,
            longGapCount = longGapCount,
            longestGapSeconds = longestGapSeconds,
            cumulativeLongGapSeconds = cumulativeLongGapSeconds
        )
    }
}
