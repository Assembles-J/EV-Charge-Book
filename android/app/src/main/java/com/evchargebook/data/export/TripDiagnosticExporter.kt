package com.evchargebook.data.export

import com.evchargebook.data.entity.TripDiagnosticEventEntity
import com.evchargebook.data.entity.TripPointEntity
import java.util.Locale

/**
 * Human-readable, diff-friendly Trip diagnostics export intended for physical-device debugging.
 *
 * The export keeps raw persisted facts. It does not interpolate missing GPS points or claim an
 * estimated route. A future analysis can therefore distinguish real capture gaps from rejected or
 * low-quality samples without contaminating the source data.
 */
object TripDiagnosticExporter {
    fun toCsv(
        tripId: Long,
        points: List<TripPointEntity>,
        events: List<TripDiagnosticEventEntity>,
    ): String = buildString {
        appendLine("# EV Charge Book Trip GPS diagnostics")
        appendLine("# tripId=$tripId")
        appendLine("# points=${points.size}")
        appendLine("# diagnosticEvents=${events.size}")
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
        appendLine("[points]")
        appendLine("capturedAtEpochMillis,deltaMs,latitude,longitude,altitudeMeters,speedMps,bearingDegrees,horizontalAccuracyMeters,verticalAccuracyMeters,speedAccuracyMps,provider")
        var previousTimestamp: Long? = null
        points.sortedBy { it.capturedAtEpochMillis }.forEach { point ->
            val deltaMs = previousTimestamp?.let { point.capturedAtEpochMillis - it }
            append(point.capturedAtEpochMillis).append(',')
            append(deltaMs ?: "").append(',')
            append(number(point.latitude)).append(',')
            append(number(point.longitude)).append(',')
            append(nullableNumber(point.altitudeMeters)).append(',')
            append(nullableNumber(point.speedMps)).append(',')
            append(nullableNumber(point.bearingDegrees)).append(',')
            append(nullableNumber(point.horizontalAccuracyMeters)).append(',')
            append(nullableNumber(point.verticalAccuracyMeters)).append(',')
            append(nullableNumber(point.speedAccuracyMps)).append(',')
            appendLine(csv(point.provider))
            previousTimestamp = point.capturedAtEpochMillis
        }
    }

    private fun csv(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        return "\"${value.replace("\"", "\"\"")}\""
    }

    private fun number(value: Double): String = String.format(Locale.US, "%.8f", value)

    private fun nullableNumber(value: Double?): String =
        value?.takeIf { it.isFinite() }?.let { String.format(Locale.US, "%.4f", it) }.orEmpty()
}
