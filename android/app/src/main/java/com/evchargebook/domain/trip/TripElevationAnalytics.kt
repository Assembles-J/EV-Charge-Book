package com.evchargebook.domain.trip

import com.evchargebook.data.entity.TripPointEntity
import com.evchargebook.domain.TripContinuityRules
import kotlin.math.abs

/**
 * Presentation analytics derived from already-persisted TripPoint altitude facts.
 *
 * This deliberately does not change Trip/GPS acceptance rules. It only avoids turning
 * obviously weak vertical fixes, tiny altitude jitter, or long GPS gaps into cumulative
 * climb/descent claims.
 */
data class TripElevationSummary(
    val startAltitudeMeters: Double,
    val endAltitudeMeters: Double,
    val minAltitudeMeters: Double,
    val maxAltitudeMeters: Double,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double,
    val trustedSampleCount: Int,
    val skippedLongGapCount: Int
) {
    val hasCumulativeEstimate: Boolean get() = trustedSampleCount >= 2
}

object TripElevationAnalytics {
    const val MAX_VERTICAL_ACCURACY_METERS = 30.0
    const val MIN_SIGNIFICANT_ALTITUDE_CHANGE_METERS = 3.0

    fun summarize(points: List<TripPointEntity>): TripElevationSummary? {
        val samples = points
            .asSequence()
            .sortedBy { it.capturedAtEpochMillis }
            .mapNotNull { point ->
                val altitude = point.altitudeMeters?.takeIf { it.isFinite() } ?: return@mapNotNull null
                val verticalAccuracy = point.verticalAccuracyMeters
                if (verticalAccuracy != null &&
                    (!verticalAccuracy.isFinite() || verticalAccuracy < 0.0 || verticalAccuracy > MAX_VERTICAL_ACCURACY_METERS)
                ) {
                    return@mapNotNull null
                }
                ElevationSample(
                    capturedAtEpochMillis = point.capturedAtEpochMillis,
                    altitudeMeters = altitude,
                    verticalAccuracyMeters = verticalAccuracy
                )
            }
            .toList()

        if (samples.isEmpty()) return null

        var gainMeters = 0.0
        var lossMeters = 0.0
        var skippedLongGapCount = 0
        var previous = samples.first()
        var altitudeAnchor = samples.first()

        samples.drop(1).forEach { current ->
            val deltaSeconds = ((current.capturedAtEpochMillis - previous.capturedAtEpochMillis) / 1000)
                .coerceAtLeast(0L)
            if (deltaSeconds >= TripContinuityRules.LONG_GAP_SECONDS) {
                skippedLongGapCount += 1
                altitudeAnchor = current
                previous = current
                return@forEach
            }

            val altitudeDelta = current.altitudeMeters - altitudeAnchor.altitudeMeters
            val significanceThreshold = maxOf(
                MIN_SIGNIFICANT_ALTITUDE_CHANGE_METERS,
                altitudeAnchor.verticalAccuracyMeters ?: 0.0,
                current.verticalAccuracyMeters ?: 0.0
            )
            if (abs(altitudeDelta) >= significanceThreshold) {
                if (altitudeDelta > 0.0) gainMeters += altitudeDelta else lossMeters += -altitudeDelta
                altitudeAnchor = current
            }
            previous = current
        }

        return TripElevationSummary(
            startAltitudeMeters = samples.first().altitudeMeters,
            endAltitudeMeters = samples.last().altitudeMeters,
            minAltitudeMeters = samples.minOf { it.altitudeMeters },
            maxAltitudeMeters = samples.maxOf { it.altitudeMeters },
            elevationGainMeters = gainMeters,
            elevationLossMeters = lossMeters,
            trustedSampleCount = samples.size,
            skippedLongGapCount = skippedLongGapCount
        )
    }

    private data class ElevationSample(
        val capturedAtEpochMillis: Long,
        val altitudeMeters: Double,
        val verticalAccuracyMeters: Double?
    )
}
