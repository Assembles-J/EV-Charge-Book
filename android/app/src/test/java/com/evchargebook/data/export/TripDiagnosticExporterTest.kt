package com.evchargebook.data.export

import com.evchargebook.data.entity.TripDiagnosticEventEntity
import com.evchargebook.data.entity.TripDiagnosticEventType
import com.evchargebook.data.entity.TripPointEntity
import com.evchargebook.data.entity.TripSessionEntity
import com.evchargebook.data.entity.TripStatus
import org.junit.Assert.assertTrue
import org.junit.Test

class TripDiagnosticExporterTest {
    @Test
    fun `exports trip summary events point deltas and environment without interpolation`() {
        val trip = TripSessionEntity(
            id = 42L,
            vehicleId = 7L,
            startedAtEpochMillis = 1_000L,
            endedAtEpochMillis = 9_000L,
            distanceMeters = 1234.5,
            elapsedSeconds = 8L,
            movingSeconds = 7L,
            stoppedSeconds = 1L,
            status = TripStatus.COMPLETED,
        )
        val points = listOf(
            TripPointEntity(
                id = 1L,
                tripId = 42L,
                capturedAtEpochMillis = 2_000L,
                latitude = 31.0,
                longitude = 121.0,
                provider = "fused",
            ),
            TripPointEntity(
                id = 2L,
                tripId = 42L,
                capturedAtEpochMillis = 8_500L,
                latitude = 31.001,
                longitude = 121.001,
                provider = "gps",
            ),
        )
        val events = listOf(
            TripDiagnosticEventEntity(
                id = 1L,
                tripId = 42L,
                occurredAtEpochMillis = 8_000L,
                type = TripDiagnosticEventType.LOCATION_CALLBACK_GAP,
                provider = "gps",
                detail = "callbackGapMs=6500",
            )
        )

        val csv = TripDiagnosticExporter.toCsv(
            trip = trip,
            points = points,
            events = events,
            environment = mapOf(
                "appVersion" to "0.7-test",
                "manufacturer" to "Example",
                "model" to "Device X",
                "sdkInt" to "36",
            ),
        )

        assertTrue(csv.contains("# tripId=42"))
        assertTrue(csv.contains("# status=COMPLETED"))
        assertTrue(csv.contains("# env.appVersion=0.7-test"))
        assertTrue(csv.contains("# env.manufacturer=Example"))
        assertTrue(csv.contains("# env.model=Device X"))
        assertTrue(csv.contains("# env.sdkInt=36"))
        assertTrue(csv.contains("LOCATION_CALLBACK_GAP"))
        assertTrue(csv.contains("8500,6500,"))
        assertTrue(csv.contains("\"gps\""))
    }

    @Test
    fun `long gap under three kilometres is exported only as an estimate candidate`() {
        val points = listOf(
            TripPointEntity(
                id = 1L,
                tripId = 42L,
                capturedAtEpochMillis = 1_000L,
                latitude = 31.0000,
                longitude = 121.0000,
                provider = "gps",
            ),
            TripPointEntity(
                id = 2L,
                tripId = 42L,
                capturedAtEpochMillis = 121_000L,
                latitude = 31.0010,
                longitude = 121.0010,
                provider = "gps",
            ),
        )

        val csv = TripDiagnosticExporter.toCsv(42L, points, emptyList())

        assertTrue(csv.contains("[longGaps]"))
        assertTrue(csv.contains("1000,121000,120000,"))
        assertTrue(csv.contains(",true\n"))
    }
}
