package com.evchargebook.domain

import kotlin.math.max

/**
 * Renderer-independent playback sample built from persisted TripPoint timestamps.
 *
 * The playback layer may interpolate only inside a short real interval. A real
 * LONG_GAP remains a hard discontinuity and is never bridged as continuous movement.
 */
data class TripPlaybackSample(
    val capturedAtEpochMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val bearingDegrees: Double? = null
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

    fun durationMillis(samples: List<TripPlaybackSample>): Long {
        if (!isChronological(samples) || samples.size < 2) return 0L
        return max(0L, samples.last().capturedAtEpochMillis - samples.first().capturedAtEpochMillis)
    }

    fun frameAt(
        samples: List<TripPlaybackSample>,
        requestedElapsedMillis: Long
    ): TripPlaybackFrame? {
        if (samples.isEmpty() || !isChronological(samples)) return null

        val startTime = samples.first().capturedAtEpochMillis
        val totalMillis = durationMillis(samples)
        val elapsedMillis = requestedElapsedMillis.coerceIn(0L, totalMillis)
        val targetTime = startTime + elapsedMillis

        if (samples.size == 1 || elapsedMillis == 0L) {
            return frameAtSample(samples, 0, elapsedMillis, totalMillis)
        }

        if (elapsedMillis >= totalMillis) {
            return frameAtSample(samples, samples.lastIndex, totalMillis, totalMillis)
        }

        for (index in 0 until samples.lastIndex) {
            val current = samples[index]
            val next = samples[index + 1]

            if (targetTime == next.capturedAtEpochMillis) {
                return frameAtSample(samples, index + 1, elapsedMillis, totalMillis)
            }
            if (targetTime > next.capturedAtEpochMillis) continue

            val deltaMillis = next.capturedAtEpochMillis - current.capturedAtEpochMillis
            if (deltaMillis <= 0L) {
                return frameAtSample(samples, index, elapsedMillis, totalMillis)
            }

            val longGapMillis = TripContinuityRules.LONG_GAP_SECONDS * 1_000L
            if (deltaMillis >= longGapMillis) {
                return TripPlaybackFrame(
                    elapsedMillis = elapsedMillis,
                    totalMillis = totalMillis,
                    currentSampleIndex = index,
                    nextSampleIndex = index + 1,
                    latitude = current.latitude,
                    longitude = current.longitude,
                    bearingDegrees = current.bearingDegrees,
                    segmentFraction = 0.0,
                    isLongGap = true,
                    longGapStartElapsedMillis = current.capturedAtEpochMillis - startTime,
                    longGapEndElapsedMillis = next.capturedAtEpochMillis - startTime
                )
            }

            val fraction = ((targetTime - current.capturedAtEpochMillis).toDouble() / deltaMillis.toDouble())
                .coerceIn(0.0, 1.0)
            return TripPlaybackFrame(
                elapsedMillis = elapsedMillis,
                totalMillis = totalMillis,
                currentSampleIndex = index,
                nextSampleIndex = index + 1,
                latitude = lerp(current.latitude, next.latitude, fraction),
                longitude = lerp(current.longitude, next.longitude, fraction),
                bearingDegrees = interpolateBearing(current.bearingDegrees, next.bearingDegrees, fraction),
                segmentFraction = fraction,
                isLongGap = false
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
        samples.zipWithNext().all { (current, next) ->
            next.capturedAtEpochMillis >= current.capturedAtEpochMillis
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
