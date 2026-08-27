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
    const val MAX_LOCATION_AGE_MILLIS = 15_000L

    fun isFreshLocation(ageMillis: Long): Boolean = ageMillis in 0..MAX_LOCATION_AGE_MILLIS

    fun decide(
        deltaSeconds: Long?,
        previousProvider: String?,
        currentProvider: String?
    ): TripContinuityDecision {
        if (deltaSeconds == null) {
            return TripContinuityDecision(
                acceptPoint = true,
                countDistance = false,
                countDuration = false,
                speedEligibleForAggregate = false,
                reason = "首个定位点仅建立基线"
            )
        }

        if (deltaSeconds < 0) {
            return TripContinuityDecision(false, false, false, false, "时间倒序")
        }

        if (deltaSeconds >= LONG_GAP_SECONDS) {
            return TripContinuityDecision(
                acceptPoint = true,
                countDistance = false,
                countDuration = false,
                speedEligibleForAggregate = false,
                reason = "GPS 长时间中断后重新建立基线"
            )
        }

        val previousIsGps = previousProvider.equals("gps", ignoreCase = true)
        val currentIsNetwork = currentProvider.equals("network", ignoreCase = true)
        if (deltaSeconds <= PROVIDER_DEDUP_SECONDS && previousIsGps && currentIsNetwork) {
            return TripContinuityDecision(
                acceptPoint = false,
                countDistance = false,
                countDuration = false,
                speedEligibleForAggregate = false,
                reason = "近期已有 GPS 点，忽略重复网络定位"
            )
        }

        val previousIsNetwork = previousProvider.equals("network", ignoreCase = true)
        val currentIsGps = currentProvider.equals("gps", ignoreCase = true)
        if (deltaSeconds <= PROVIDER_DEDUP_SECONDS && previousIsNetwork && currentIsGps) {
            return TripContinuityDecision(
                acceptPoint = true,
                countDistance = false,
                countDuration = false,
                speedEligibleForAggregate = true,
                reason = "GPS 恢复，替代近期网络定位作为新基线"
            )
        }

        return TripContinuityDecision(
            acceptPoint = true,
            countDistance = true,
            countDuration = true,
            speedEligibleForAggregate = true
        )
    }
}
