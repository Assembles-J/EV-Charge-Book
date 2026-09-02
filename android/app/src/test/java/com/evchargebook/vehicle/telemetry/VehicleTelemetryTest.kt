package com.evchargebook.vehicle.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleTelemetryTest {
    @Test
    fun `fractional SOC is preserved and available fields are explicit`() {
        val snapshot = VehicleTelemetrySnapshot(
            vehicleId = 1L,
            providerId = "test-oem",
            receivedAtEpochMillis = 2_000L,
            socPercent = TelemetryValue(
                value = 74.6,
                capturedAtEpochMillis = 1_900L,
                resolution = 0.1,
            ),
            odometerKm = TelemetryValue(value = 12_841.2, resolution = 0.1),
        )

        assertEquals(74.6, snapshot.socPercent?.value ?: 0.0, 0.0001)
        assertEquals(
            setOf(VehicleTelemetryField.SOC_PERCENT, VehicleTelemetryField.ODOMETER_KM),
            snapshot.availableFields(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `SOC outside physical percent bounds is rejected`() {
        VehicleTelemetrySnapshot(
            vehicleId = 1L,
            providerId = "test-oem",
            receivedAtEpochMillis = 2_000L,
            socPercent = TelemetryValue(100.1),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `remaining range cannot masquerade as measured truth`() {
        VehicleTelemetrySnapshot(
            vehicleId = 1L,
            providerId = "test-oem",
            receivedAtEpochMillis = 2_000L,
            estimatedRangeKm = TelemetryValue(
                value = 394.0,
                semantic = TelemetryValueSemantic.PROVIDER_REPORTED,
            ),
        )
    }

    @Test
    fun `field capture time wins over snapshot and receive time`() {
        val value = TelemetryValue(value = 74.6, capturedAtEpochMillis = 1_700L)
        val snapshot = VehicleTelemetrySnapshot(
            vehicleId = 1L,
            providerId = "test-oem",
            snapshotCapturedAtEpochMillis = 1_800L,
            receivedAtEpochMillis = 1_900L,
            socPercent = value,
        )

        val freshness = VehicleTelemetryFreshness.evaluate(snapshot, value, nowEpochMillis = 2_000L)

        assertEquals(TelemetryTimeAuthority.FIELD_CAPTURE_TIME, freshness.authority)
        assertEquals(1_700L, freshness.observedAtEpochMillis)
        assertEquals(300L, freshness.ageMillis)
    }

    @Test
    fun `snapshot capture time is used when field timestamp is absent`() {
        val value = TelemetryValue(value = 12_841.2)
        val snapshot = VehicleTelemetrySnapshot(
            vehicleId = 1L,
            providerId = "test-oem",
            snapshotCapturedAtEpochMillis = 1_800L,
            receivedAtEpochMillis = 1_900L,
            odometerKm = value,
        )

        val freshness = VehicleTelemetryFreshness.evaluate(snapshot, value, nowEpochMillis = 2_000L)

        assertEquals(TelemetryTimeAuthority.SNAPSHOT_CAPTURE_TIME, freshness.authority)
        assertEquals(200L, freshness.ageMillis)
    }

    @Test
    fun `receive time fallback is explicitly marked when capture time is unknown`() {
        val value = TelemetryValue(value = VehiclePlugState.CONNECTED)
        val snapshot = VehicleTelemetrySnapshot(
            vehicleId = 1L,
            providerId = "obd",
            receivedAtEpochMillis = 1_900L,
            plugState = value,
        )

        val freshness = VehicleTelemetryFreshness.evaluate(snapshot, value, nowEpochMillis = 2_000L)

        assertEquals(TelemetryTimeAuthority.RECEIVE_TIME_ONLY, freshness.authority)
        assertEquals(100L, freshness.ageMillis)
    }

    @Test
    fun `OEM estimated range remains displayable as estimate`() {
        val snapshot = VehicleTelemetrySnapshot(
            vehicleId = 1L,
            providerId = "test-oem",
            receivedAtEpochMillis = 2_000L,
            estimatedRangeKm = TelemetryValue(
                value = 394.0,
                semantic = TelemetryValueSemantic.OEM_ESTIMATE,
            ),
        )

        assertTrue(snapshot.availableFields().contains(VehicleTelemetryField.ESTIMATED_RANGE_KM))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative provider refresh interval is rejected`() {
        VehicleTelemetryCapabilities(
            supportedFields = emptySet(),
            minimumRefreshIntervalMillis = -1L,
        )
    }
}
