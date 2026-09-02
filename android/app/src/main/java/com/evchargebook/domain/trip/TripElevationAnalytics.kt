package com.evchargebook.domain.trip

import com.evchargebook.data.entity.TripPointEntity
import com.evchargebook.domain.TripCaptureTimeRules
import com.evchargebook.domain.TripContinuityRules
import kotlin.math.abs

/**
 * Presentation analytics derived from already-persisted TripPoint altitude facts.
 *
 * Raw TripPoint altitude remains untouched. The presentation layer applies conservative vertical
 * accuracy filtering plus a small median filter inside continuous GPS segments so isolated GNSS
 * altitude spikes do not become user-facing min/max or climb/descent claims.
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

data class TripElevationSample(
    val capturedAtEpochMillis: Long,
    val capturedAtElapsedRealtimeNanos: Long?,
    val altitudeMeters: Double,
    val verticalAccuracyMeters: Double?
)

object TripElevationAnalytics {
    const val MAX_VERTICAL_ACCURACY_METERS = 20.0
    const val MIN_SIGNIFICANT_ALTITUDE_CHANGE_METERS = 4.0
    private const val MEDIAN_WINDOW_RADIUS = 2
    private const val MIN_SAMPLES_FOR_MEDIAN_FILTER = 7
    private val LONG_GAP_MILLIS = TripContinuityRules.LONG_GAP_SECONDS * 1_000L

    fun filteredSeries(points: List<TripPointEntity>): List<TripElevationSample> {
        // DAO order is insertion/capture order. Do not re-sort by wall-clock time: a user or network
        // time correction may legitimately move epoch time backwards within one monotonic Trip.
        val trusted = points
            .asSequence()
            .mapNotNull { point ->
                val altitude = point.altitudeMeters?.takeIf { it.isFinite() } ?: return@mapNotNull null
                val verticalAccuracy = point.verticalAccuracyMeters
                if (verticalAccuracy != null &&
                    (!verticalAccuracy.isFinite() || verticalAccuracy < 0.0 || verticalAccuracy > MAX_VERTICAL_ACCURACY_METERS)
                ) {
                    return@mapNotNull null
                }
                TripElevationSample(
                    capturedAtEpochMillis = point.capturedAtEpochMillis,
                    capturedAtElapsedRealtimeNanos = point.capturedAtElapsedRealtimeNanos,
                    altitudeMeters = altitude,
                    verticalAccuracyMeters = verticalAccuracy
                )
            }
            .toList()

        if (trusted.size < MIN_SAMPLES_FOR_MEDIAN_FILTER) return trusted

        return splitContinuousSegments(trusted).flatMap { segment ->
            if (segment.size < MIN_SAMPLES_FOR_MEDIAN_FILTER) segment else medianFilter(segment)
        }
    }

    fun summarize(points: List<TripPointEntity>): TripElevationSummary? {
        val samples = filteredSeries(points)
        if (samples.isEmpty()) return null

        var gainMeters = 0.0
        var lossMeters = 0.0
        var skippedLongGapCount = 0
        var previous = samples.first()
        var altitudeAnchor = samples.first()

        samples.drop(1).forEach { current ->
            if (breaksContinuity(previous, current)) {
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

    private fun splitContinuousSegments(samples: List<TripElevationSample>): List<List<TripElevationSample>> {
        if (samples.isEmpty()) return emptyList()
        val result = mutableListOf<MutableList<TripElevationSample>>()
        var current = mutableListOf(samples.first())
        result += current
        samples.zipWithNext().forEach { (previous, next) ->
            if (breaksContinuity(previous, next)) {
                current = mutableListOf()
                result += current
            }
            current += next
        }
        return result.filter { it.isNotEmpty() }
    }

    private fun breaksContinuity(previous: TripElevationSample, current: TripElevationSample): Boolean {
        val timing = TripCaptureTimeRules.between(
            previousEpochMillis = previous.capturedAtEpochMillis,
            previousElapsedRealtimeNanos = previous.capturedAtElapsedRealtimeNanos,
            currentEpochMillis = current.capturedAtEpochMillis,
            currentElapsedRealtimeNanos = current.capturedAtElapsedRealtimeNanos,
        )
        return !timing.accepted || timing.breaksContinuity(LONG_GAP_MILLIS)
    }

    private fun medianFilter(segment: List<TripElevationSample>): List<TripElevationSample> =
        segment.mapIndexed { index, sample ->
            val altitudeWindow = List(MEDIAN_WINDOW_RADIUS * 2 + 1) { offset ->
                val sourceIndex = (index + offset - MEDIAN_WINDOW_RADIUS).coerceIn(0, segment.lastIndex)
                segment[sourceIndex].altitudeMeters
            }
            val accuracyWindow = List(MEDIAN_WINDOW_RADIUS * 2 + 1) { offset ->
                val sourceIndex = (index + offset - MEDIAN_WINDOW_RADIUS).coerceIn(0, segment.lastIndex)
                segment[sourceIndex].verticalAccuracyMeters
            }.filterNotNull()

            sample.copy(
                altitudeMeters = median(altitudeWindow),
                verticalAccuracyMeters = accuracyWindow.takeIf { it.isNotEmpty() }?.let(::median)
            )
        }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
    }
}
