package com.evchargebook.data.export

import com.evchargebook.data.entity.TripDiagnosticEventEntity
import com.evchargebook.data.entity.TripPointEntity
import com.evchargebook.data.entity.TripSessionEntity
import com.evchargebook.domain.TripCaptureTimeRules
import com.evchargebook.domain.TripContinuityRules
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Human-readable, diff-friendly Trip diagnostics export intended for physical-device debugging.
 *
 * Epoch timestamps remain raw, human-readable facts. For interval/gap analysis the export prefers
 * persisted elapsedRealtimeNanos when both adjacent points have it, and explicitly labels legacy
 * epoch fallback or a monotonic-clock rebase. No missing GPS point or route is synthesized.
 */
object TripDiagnosticExporter {
    private const val SHORT_GAP_ESTIMATE_CANDIDATE_METERS = 3_000.0
    private val LONG_GAP_MILLIS = TripContinuityRules.LONG_GAP_SECONDS * 1_000L

    fun toCsv(
        trip: TripSessionEntity,
        points: List<TripPointEntity>,
        events: List<TripDiagnosticEventEntity>,
        environment: Map<String, String> = emptyMap(),
    ): String = buildString {
        appendLine("# EV Charge Book Trip GPS diagnostics")
        appendLine("# tripId=${trip.id}")
        appendLine("# vehicleId=${trip.vehicleId}")
        appendLine("# status=${trip.status}")
        appendLine("# startedAtEpochMillis=${trip.startedAtEpochMillis}")
        appendLine("# endedAtEpochMillis=${trip.endedAtEpochMillis ?: ""}")
        appendLine("# distanceMeters=${number(trip.distanceMeters)}")
        appendLine("# elapsedSeconds=${trip.elapsedSeconds}")
        appendLine("# movingSeconds=${trip.movingSeconds ?: ""}")
        appendLine("# stoppedSeconds=${trip.stoppedSeconds ?: ""}")
        appendLine("# averageSpeedMps=${nullableNumber(trip.averageSpeedMps)}")
        appendLine("# maxSpeedMps=${nullableNumber(trip.maxSpeedMps)}")
        environment.toSortedMap().forEach { (key, value) ->
            appendLine("# env.${safeHeader(key)}=${safeHeader(value)}")
        }
        appendLine("# points=${points.size}")
        appendLine("# diagnosticEvents=${events.size}")
        appendDiagnostics(points, events)
    }

    fun toCsv(
        tripId: Long,
        points: List<TripPointEntity>,
        events: List<TripDiagnosticEventEntity>,
    ): String = buildString {
        appendLine("# EV Charge Book Trip GPS diagnostics")
        appendLine("# tripId=$tripId")
        appendLine("# points=${points.size}")
        appendLine("# diagnosticEvents=${events.size}")
        appendDiagnostics(points, events)
    }

    private fun StringBuilder.appendDiagnostics(
        points: List<TripPointEntity>,
        events: List<TripDiagnosticEventEntity>,
    ) {
        val orderedPoints = captureOrdered(points)

        appendLine()
        appendLine("[events]")
        appendLine("occurredAtEpochMillis,type,provider,detail")
        events.sortedBy { it.occurredAtEpochMillis }.forEach { event ->
            append(event.occurredAtEpochMillis).append(',')
            append(csv(event.type)).append(',')
            append(csv(event.provider)).append(',')
            appendLine(csv(event.detail))
        }

        appendLine()
        appendLine("[longGaps]")
        appendLine("fromCapturedAtEpochMillis,toCapturedAtEpochMillis,deltaMs,timeAuthority,timeDecision,straightLineMeters,under3KmEstimateCandidate")
        orderedPoints.zipWithNext().forEach { (from, to) ->
            val timing = captureDelta(from, to)
            val breaks = !timing.accepted || timing.breaksContinuity(LONG_GAP_MILLIS)
            if (!breaks) return@forEach
            val straightLineMeters = straightLineMeters(from, to)
            append(from.capturedAtEpochMillis).append(',')
            append(to.capturedAtEpochMillis).append(',')
            append(timing.deltaMillis ?: "").append(',')
            append(csv(timing.authority.name)).append(',')
            append(csv(timeDecision(timing.requiresRebase, timing.rejectReason))).append(',')
            append(String.format(Locale.US, "%.1f", straightLineMeters)).append(',')
            appendLine(straightLineMeters <= SHORT_GAP_ESTIMATE_CANDIDATE_METERS)
        }

        appendLine()
        appendLine("[points]")
        appendLine("capturedAtEpochMillis,capturedAtElapsedRealtimeNanos,deltaMs,timeAuthority,timeDecision,latitude,longitude,altitudeMeters,speedMps,bearingDegrees,horizontalAccuracyMeters,verticalAccuracyMeters,speedAccuracyMps,provider")
        appendPoints(orderedPoints)
    }

    private fun StringBuilder.appendPoints(points: List<TripPointEntity>) {
        var previous: TripPointEntity? = null
        points.forEach { point ->
            val timing = previous?.let { captureDelta(it, point) }
            append(point.capturedAtEpochMillis).append(',')
            append(point.capturedAtElapsedRealtimeNanos ?: "").append(',')
            append(timing?.deltaMillis ?: "").append(',')
            append(csv(timing?.authority?.name)).append(',')
            append(csv(timing?.let { timeDecision(it.requiresRebase, it.rejectReason) })).append(',')
            append(number(point.latitude)).append(',')
            append(number(point.longitude)).append(',')
            append(nullableNumber(point.altitudeMeters)).append(',')
            append(nullableNumber(point.speedMps)).append(',')
            append(nullableNumber(point.bearingDegrees)).append(',')
            append(nullableNumber(point.horizontalAccuracyMeters)).append(',')
            append(nullableNumber(point.verticalAccuracyMeters)).append(',')
            append(nullableNumber(point.speedAccuracyMps)).append(',')
            appendLine(csv(point.provider))
            previous = point
        }
    }

    private fun captureOrdered(points: List<TripPointEntity>): List<TripPointEntity> =
        if (points.isNotEmpty() && points.all { it.id > 0L }) points.sortedBy { it.id } else points

    private fun captureDelta(from: TripPointEntity, to: TripPointEntity) =
        TripCaptureTimeRules.between(
            previousEpochMillis = from.capturedAtEpochMillis,
            previousElapsedRealtimeNanos = from.capturedAtElapsedRealtimeNanos,
            currentEpochMillis = to.capturedAtEpochMillis,
            currentElapsedRealtimeNanos = to.capturedAtElapsedRealtimeNanos,
        )

    private fun timeDecision(rebase: Boolean, rejectReason: String?): String? = when {
        !rejectReason.isNullOrBlank() -> "rejected:$rejectReason"
        rebase -> "rebase"
        else -> null
    }

    private fun straightLineMeters(from: TripPointEntity, to: TripPointEntity): Double {
        val earthRadiusMeters = 6_371_000.0
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val deltaLat = lat2 - lat1
        val deltaLon = Math.toRadians(to.longitude - from.longitude)
        val haversine = sin(deltaLat / 2.0) * sin(deltaLat / 2.0) +
            cos(lat1) * cos(lat2) * sin(deltaLon / 2.0) * sin(deltaLon / 2.0)
        val angularDistance = 2.0 * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
        return earthRadiusMeters * angularDistance
    }

    private fun safeHeader(value: String): String = value.replace('\n', ' ').replace('\r', ' ')

    private fun csv(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        return "\"${value.replace("\"", "\"\"")}\""
    }

    private fun number(value: Double): String = String.format(Locale.US, "%.8f", value)

    private fun nullableNumber(value: Double?): String =
        value?.takeIf { it.isFinite() }?.let { String.format(Locale.US, "%.4f", it) }.orEmpty()
}
