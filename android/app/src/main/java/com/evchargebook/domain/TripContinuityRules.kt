package com.evchargebook.domain

data class TripContinuityDecision(
    val acceptPoint: Boolean,
    val countDistance: Boolean,
    val countDuration: Boolean,
    val speedEligibleForAggregate: Boolean,
    val reason: String? = null
)

object TripContinuityRules {
    const val LONG_GAP_SECONDS = 120L
    const val PROVIDER_DEDUP_SECONDS = 8L

    // Some Android/OEM builds batch otherwise valid foreground-location callbacks while the screen
    // is locked or the device enters a light idle state. Keep callback delivery tolerance wider
    // than route continuity: the original capture timestamps still decide whether a segment can
    // contribute distance/duration, and >= LONG_GAP_SECONDS remains a hard route break.
    const val MAX_LOCATION_AGE_MILLIS = 10 * 60_000L

    fun isFreshLocation(ageMillis: Long): Boolean = ageMillis in 0..MAX_LOCATION_AGE_MILLIS

    fun decide(
        deltaSeconds: Long?,
        previousProvider: String?,
        currentProvider: String?
    ): TripContinuityDecision {
        if (deltaSeconds == null) {
            return TripContinuityDecision(true, false, false, false, "首个定位点仅建立基线")
        }
        if (deltaSeconds < 0) {
            return TripContinuityDecision(false, false, false, false, "时间倒序")
        }
        if (deltaSeconds >= LONG_GAP_SECONDS) {
            return TripContinuityDecision(true, false, false, false, "GPS 长时间中断后重新建立基线")
        }

        val previousIsGps = previousProvider.equals("gps", ignoreCase = true)
        val currentIsNetwork = currentProvider.equals("network", ignoreCase = true)
        if (deltaSeconds <= PROVIDER_DEDUP_SECONDS && previousIsGps && currentIsNetwork) {
            return TripContinuityDecision(false, false, false, false, "近期已有 GPS 点，忽略重复网络定位")
        }

        val previousIsNetwork = previousProvider.equals("network", ignoreCase = true)
        val currentIsGps = currentProvider.equals("gps", ignoreCase = true)
        if (deltaSeconds <= PROVIDER_DEDUP_SECONDS && previousIsNetwork && currentIsGps) {
            return TripContinuityDecision(true, false, false, true, "GPS 恢复，替代近期网络定位作为新基线")
        }

        return TripContinuityDecision(true, true, true, true)
    }
}
