package com.evchargebook.domain

import kotlin.math.max

/**
 * Renderer-independent playback sample built from persisted TripPoint timestamps.
 *
 * Epoch time remains a human-readable fact. elapsedRealtimeNanos is the preferred interval clock
 * when available, so wall-clock corrections cannot make an otherwise valid drive unplayable.
 */
data class TripPlaybackSample(
    val capturedAtEpochMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val bearingDegrees: Double? = null,
    val capturedAtElapsedRealtimeNanos: Long? = null,
)

data class TripPlaybackFrame(
    val elapsedMillis: Long,
    val totalMillis: Long,
    val currentSampleIndex: Int,
    val nextSampleIndex: Int?,
    val latitude: Double,
    val longitude: Double,
    val bearingDegrees: Double?,
    val segmentFraction: Double,
    val isLongGap: Boolean,
    val longGapStartElapsedMillis: Long? = null,
    val longGapEndElapsedMillis: Long? = null
)

object TripPlaybackTimeline {
    val speedPresets: List<Float> = listOf(1f, 2f, 4f, 8f)
    private val longGapMillis = TripContinuityRules.LONG_GAP_SECONDS * 1_000L

    fun durationMillis(samples: List<TripPlaybackSample>): Long {
        val timeline = buildTimeline(samples) ?: return 0L
        return timeline.offsets.lastOrNull() ?: 0L
    }

    fun frameAt(
        samples: List<TripPlaybackSample>,
        requestedElapsedMillis: Long
    ): TripPlaybackFrame? {
        if (samples.isEmpty()) return null
        val timeline = buildTimeline(samples) ?: return null
        val totalMillis = timeline.offsets.lastOrNull() ?: 0L
        val elapsedMillis = requestedElapsedMillis.coerceIn(0L, totalMillis)

        if (samples.size == 1 || elapsedMillis == 0L) {
            return frameAtSample(samples, 0, elapsedMillis, totalMillis)
        }
        if (elapsedMillis >= totalMillis) {
            return frameAtSample(samples, samples.lastIndex, totalMillis, totalMillis)
        }

        for (index in 0 until samples.lastIndex) {
            val segmentStart = timeline.offsets[index]
            val segmentEnd = timeline.offsets[index + 1]
            if (elapsedMillis == segmentEnd) {
                return frameAtSample(samples, index + 1, elapsedMillis, totalMillis)
            }
            if (elapsedMillis > segmentEnd) continue

            val segment = timeline.segments[index]
            if (segment.breaksContinuity) {
                return TripPlaybackFrame(
                    elapsedMillis = elapsedMillis,
                    totalMillis = totalMillis,
                    currentSampleIndex = index,
                    nextSampleIndex = index + 1,
                    latitude = samples[index].latitude,
                    longitude = samples[index].longitude,
                    bearingDegrees = samples[index].bearingDegrees,
                    segmentFraction = 0.0,
                    isLongGap = true,
                    longGapStartElapsedMillis = segmentStart,
                    longGapEndElapsedMillis = segmentEnd,
                )
            }

            val deltaMillis = (segmentEnd - segmentStart).coerceAtLeast(1L)
            val fraction = ((elapsedMillis - segmentStart).toDouble() / deltaMillis.toDouble())
                .coerceIn(0.0, 1.0)
            return TripPlaybackFrame(
                elapsedMillis = elapsedMillis,
                totalMillis = totalMillis,
                currentSampleIndex = index,
                nextSampleIndex = index + 1,
                latitude = lerp(samples[index].latitude, samples[index + 1].latitude, fraction),
                longitude = lerp(samples[index].longitude, samples[index + 1].longitude, fraction),
                bearingDegrees = interpolateBearing(samples[index].bearingDegrees, samples[index + 1].bearingDegrees, fraction),
                segmentFraction = fraction,
                isLongGap = false,
            )
        }

        return frameAtSample(samples, samples.lastIndex, elapsedMillis, totalMillis)
    }

    fun advanceElapsed(
        currentElapsedMillis: Long,
        realDeltaMillis: Long,
        speedMultiplier: Float,
        totalMillis: Long
    ): Long {
        if (totalMillis <= 0L) return 0L
        val safeSpeed = speedMultiplier.takeIf { it.isFinite() && it > 0f } ?: 1f
        val safeDelta = realDeltaMillis.coerceAtLeast(0L)
        val advanced = currentElapsedMillis.toDouble() + safeDelta.toDouble() * safeSpeed.toDouble()
        return advanced.toLong().coerceIn(0L, totalMillis)
    }

    fun isChronological(samples: List<TripPlaybackSample>): Boolean =
        buildTimeline(samples) != null

    private data class PlaybackSegment(
        val durationMillis: Long,
        val breaksContinuity: Boolean,
    )

    private data class PlaybackTimeline(
        val offsets: List<Long>,
        val segments: List<PlaybackSegment>,
    )

    private fun buildTimeline(samples: List<TripPlaybackSample>): PlaybackTimeline? {
        if (samples.isEmpty()) return PlaybackTimeline(emptyList(), emptyList())
        if (samples.size == 1) return PlaybackTimeline(listOf(0L), emptyList())

        val offsets = mutableListOf(0L)
        val segments = mutableListOf<PlaybackSegment>()
        var offset = 0L

        samples.zipWithNext().forEach { (current, next) ->
            val timing = TripCaptureTimeRules.between(
                previousEpochMillis = current.capturedAtEpochMillis,
                previousElapsedRealtimeNanos = current.capturedAtElapsedRealtimeNanos,
                currentEpochMillis = next.capturedAtEpochMillis,
                currentElapsedRealtimeNanos = next.capturedAtElapsedRealtimeNanos,
            )
            if (!timing.accepted) return null

            val duration = when {
                timing.requiresRebase -> max(
                    longGapMillis,
                    (next.capturedAtEpochMillis - current.capturedAtEpochMillis).coerceAtLeast(0L),
                )
                else -> timing.deltaMillis ?: 0L
            }
            val breaks = timing.breaksContinuity(longGapMillis)
            offset += duration
            segments += PlaybackSegment(durationMillis = duration, breaksContinuity = breaks)
            offsets += offset
        }
        return PlaybackTimeline(offsets = offsets, segments = segments)
    }

    private fun frameAtSample(
        samples: List<TripPlaybackSample>,
        index: Int,
        elapsedMillis: Long,
        totalMillis: Long
    ): TripPlaybackFrame {
        val sample = samples[index]
        return TripPlaybackFrame(
            elapsedMillis = elapsedMillis,
            totalMillis = totalMillis,
            currentSampleIndex = index,
            nextSampleIndex = samples.getOrNull(index + 1)?.let { index + 1 },
            latitude = sample.latitude,
            longitude = sample.longitude,
            bearingDegrees = sample.bearingDegrees,
            segmentFraction = 0.0,
            isLongGap = false
        )
    }

    private fun lerp(start: Double, end: Double, fraction: Double): Double =
        start + (end - start) * fraction

    private fun interpolateBearing(start: Double?, end: Double?, fraction: Double): Double? {
        if (start == null && end == null) return null
        if (start == null) return end
        if (end == null) return start
        if (!start.isFinite() || !end.isFinite()) return start.takeIf { it.isFinite() } ?: end.takeIf { it.isFinite() }

        val normalizedStart = normalizeDegrees(start)
        val normalizedEnd = normalizeDegrees(end)
        val delta = ((normalizedEnd - normalizedStart + 540.0) % 360.0) - 180.0
        return normalizeDegrees(normalizedStart + delta * fraction)
    }

    private fun normalizeDegrees(value: Double): Double {
        val normalized = value % 360.0
        return if (normalized < 0.0) normalized + 360.0 else normalized
    }
}
