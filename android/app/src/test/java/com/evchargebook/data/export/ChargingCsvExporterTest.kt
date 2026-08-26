package com.evchargebook.data.export

import com.evchargebook.data.entity.ChargingRecordEntity
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class ChargingCsvExporterTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun `exports derived price and optional fields`() {
        val csv = ChargingCsvExporter.encode(
            listOf(
                ChargingRecordEntity(
                    id = 7,
                    vehicleId = 2,
                    chargeTimeEpochMillis = 0L,
                    energyKwh = 20.0,
                    cost = 10.0,
                    startSoc = 20,
                    endSoc = 80,
                    chargerType = "家充",
                    location = "公司地库",
                    odometerKm = 1234.5,
                    latitude = 31.2,
                    longitude = 121.5,
                    locationAccuracyMeters = 8.0,
                    remark = "夜间"
                )
            ),
            utc
        )

        assertTrue(csv.startsWith("\uFEFFrecord_id"))
        assertTrue(csv.contains("1970-01-01 00:00:00"))
        assertTrue(csv.contains(",0.5,1234.5,31.2,121.5,8,夜间"))
    }

    @Test
    fun `escapes commas quotes and newlines`() {
        val csv = ChargingCsvExporter.encode(
            listOf(
                ChargingRecordEntity(
                    id = 1,
                    vehicleId = 1,
                    chargeTimeEpochMillis = 0L,
                    energyKwh = 1.0,
                    cost = 0.0,
                    startSoc = 10,
                    endSoc = 20,
                    location = "A,B \"站\"",
                    remark = "line1\nline2"
                )
            ),
            utc
        )

        assertTrue(csv.contains("\"A,B \"\"站\"\"\""))
        assertTrue(csv.contains("\"line1\nline2\""))
    }
}
