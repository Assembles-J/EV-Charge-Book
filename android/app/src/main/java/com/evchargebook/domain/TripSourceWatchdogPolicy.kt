package com.evchargebook.domain

/**
 * A delivered location callback is not evidence of a new fix: OEMs may redeliver the same cached
 * Location or release an old batch after the device wakes. Keep callback-delivery tolerance and
 * persisted TripPoint truth separate from source-health / failover decisions.
 */
object TripSourceWatchdogPolicy {
    // Less than the source's 12s silence deadline. Delayed points remain available to the
    // tracking pipeline; they simply cannot postpone fallback indefinitely.
    const val MAX_WATCHDOG_FIX_AGE_MILLIS = 10_000L
    private const val NANOS_PER_MILLI = 1_000_000L

    fun refreshesWatchdog(
        previousFreshFixElapsedRealtimeNanos: Long?,
        fixElapsedRealtimeNanos: Long,
        nowElapsedRealtimeNanos: Long,
    ): Boolean {
        if (fixElapsedRealtimeNanos <= 0L || fixElapsedRealtimeNanos > nowElapsedRealtimeNanos) return false
        if (previousFreshFixElapsedRealtimeNanos != null &&
            fixElapsedRealtimeNanos <= previousFreshFixElapsedRealtimeNanos
        ) return false
        return nowElapsedRealtimeNanos - fixElapsedRealtimeNanos <=
            MAX_WATCHDOG_FIX_AGE_MILLIS * NANOS_PER_MILLI
    }
}
