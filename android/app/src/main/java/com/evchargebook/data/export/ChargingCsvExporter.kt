package com.evchargebook.data.export

import com.evchargebook.data.entity.ChargingRecordEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object ChargingCsvExporter {
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun encode(records: List<ChargingRecordEntity>, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val header = listOf(
            "record_id",
            "vehicle_id",
            "charge_time",
            "location",
            "charger_type",
            "start_soc",
            "end_soc",
            "energy_kwh",
            "cost",
            "price_per_kwh",
            "odometer_km",
            "latitude",
            "longitude",
            "location_accuracy_m",
            "remark"
        )

        val rows = records.sortedBy { it.chargeTimeEpochMillis }.map { record ->
            val price = if (record.energyKwh > 0.0) record.cost / record.energyKwh else null
            listOf(
                record.id.toString(),
                record.vehicleId.toString(),
                timeFormatter.format(Instant.ofEpochMilli(record.chargeTimeEpochMillis).atZone(zoneId)),
                record.location.orEmpty(),
                record.chargerType.orEmpty(),
                record.startSoc.toString(),
                record.endSoc.toString(),
                decimal(record.energyKwh),
                decimal(record.cost),
                price?.let(::decimal).orEmpty(),
                record.odometerKm?.let(::decimal).orEmpty(),
                record.latitude?.let(::decimal).orEmpty(),
                record.longitude?.let(::decimal).orEmpty(),
                record.locationAccuracyMeters?.let(::decimal).orEmpty(),
                record.remark.orEmpty()
            )
        }

        return buildString {
            append('\uFEFF')
            appendLine(header.joinToString(",") { csvCell(it) })
            rows.forEach { row -> appendLine(row.joinToString(",") { csvCell(it) }) }
        }
    }

    private fun csvCell(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.any { it == ',' || it == '\"' || it == '\n' || it == '\r' }) "\"$escaped\"" else escaped
    }

    private fun decimal(value: Double): String = String.format(Locale.US, "%.6f", value).trimEnd('0').trimEnd('.')
}
