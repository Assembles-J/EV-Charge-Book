package com.evchargebook.domain

enum class TripCaptureTimeAuthority {
    ELAPSED_REALTIME,
    EPOCH_FALLBACK,
    ELAPSED_REALTIME_REBASE,
}

data class TripCaptureTimeDelta(
    val deltaMillis: Long?,
    val authority: TripCaptureTimeAuthority,
    val requiresRebase: Boolean = false,
    val rejectReason: String? = null,
) {
    val accepted: Boolean get() = rejectReason == null

    fun deltaSecondsOrNull(): Long? = deltaMillis?.div(1_000L)

    fun breaksContinuity(longGapMillis: Long): Boolean =
        requiresRebase || (deltaMillis != null && deltaMillis >= longGapMillis)
}

/**
 * Chooses a stable capture-time delta between two persisted locations.
 *
 * Android's elapsedRealtimeNanos is monotonic only within one boot. New Trip points therefore use
 * it whenever both locations belong to the same monotonic timeline, while legacy points without
 * it fall back to epoch time. If elapsed realtime moves backwards but epoch time moves forward, the
 * device most likely rebooted (or the monotonic clock was reset), so the current point is accepted
 * only as a new baseline and the segment across that boundary must remain disconnected.
 */
object TripCaptureTimeRules {
    fun between(
        previousEpochMillis: Long,
        previousElapsedRealtimeNanos: Long?,
        currentEpochMillis: Long,
        currentElapsedRealtimeNanos: Long?,
    ): TripCaptureTimeDelta {
        val previousElapsed = previousElapsedRealtimeNanos?.takeIf { it > 0L }
        val currentElapsed = currentElapsedRealtimeNanos?.takeIf { it > 0L }

        if (previousElapsed != null && currentElapsed != null) {
            return when {
                currentElapsed > previousElapsed -> TripCaptureTimeDelta(
                    deltaMillis = (currentElapsed - previousElapsed) / NANOS_PER_MILLI,
                    authority = TripCaptureTimeAuthority.ELAPSED_REALTIME,
                )
                currentElapsed == previousElapsed -> TripCaptureTimeDelta(
                    deltaMillis = null,
                    authority = TripCaptureTimeAuthority.ELAPSED_REALTIME,
                    rejectReason = "duplicate_elapsed_realtime",
                )
                currentEpochMillis > previousEpochMillis -> TripCaptureTimeDelta(
                    deltaMillis = null,
                    authority = TripCaptureTimeAuthority.ELAPSED_REALTIME_REBASE,
                    requiresRebase = true,
                )
                else -> TripCaptureTimeDelta(
                    deltaMillis = null,
                    authority = TripCaptureTimeAuthority.ELAPSED_REALTIME,
                    rejectReason = "out_of_order_elapsed_realtime",
                )
            }
        }

        val epochDelta = currentEpochMillis - previousEpochMillis
        return if (epochDelta > 0L) {
            TripCaptureTimeDelta(
                deltaMillis = epochDelta,
                authority = TripCaptureTimeAuthority.EPOCH_FALLBACK,
            )
        } else {
            TripCaptureTimeDelta(
                deltaMillis = null,
                authority = TripCaptureTimeAuthority.EPOCH_FALLBACK,
                rejectReason = "non_monotonic_epoch_fallback",
            )
        }
    }

    private const val NANOS_PER_MILLI = 1_000_000L
}
