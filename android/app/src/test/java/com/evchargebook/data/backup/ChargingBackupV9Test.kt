package com.evchargebook.data.backup

import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.data.entity.ChargingSessionEntity
import com.evchargebook.data.entity.ChargingSessionStatus
import com.evchargebook.data.entity.VehicleEntity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargingBackupV9Test {
    @Test
    fun `current round trip preserves active charging session and completion facts`() {
        val vehicle = VehicleEntity(
            id = 1L,
            brand = "Test",
            model = "EV",
            batteryCapacityKwh = 80.0,
            rangeKm = 600,
            syncId = "vehicle-1",
            createdAtEpochMillis = 100L,
            updatedAtEpochMillis = 100L,
        )
        val record = ChargingRecordEntity(
            id = 7L,
            vehicleId = 1L,
            chargeTimeEpochMillis = 1_000L,
            endedAtEpochMillis = 4_600_000L,
            energyKwh = 40.0,
            vehicleEnergyKwh = 36.5,
            cost = 48.0,
            startSoc = 40,
            endSoc = 85,
            syncId = "charge-7",
            updatedAtEpochMillis = 4_600_000L,
        )
        val active = ChargingSessionEntity(
            id = "session-active",
            vehicleId = 1L,
            startedAtEpochMillis = 5_000_000L,
            startSoc = 30,
            targetSoc = 90,
            chargerType = "家充",
            unitPricePerKwh = 0.65,
            location = "家",
            latitude = 31.0,
            longitude = 121.0,
            locationAccuracyMeters = 8.0,
            status = ChargingSessionStatus.ACTIVE,
            updatedAtEpochMillis = 5_000_000L,
        )

        val decoded = BackupCodec.decode(
            BackupCodec.encode(
                BackupPayload(
                    schemaVersion = BackupCodec.CURRENT_SCHEMA_VERSION,
                    exportedAt = 6_000_000L,
                    appVersion = "test",
                    vehicles = listOf(vehicle),
                    chargingRecords = listOf(record),
                    chargingSessions = listOf(active),
                )
            )
        )

        assertEquals(10, decoded.schemaVersion)
        assertEquals(1, decoded.chargingSessions.size)
        assertEquals(active, decoded.chargingSessions.single())
        assertEquals(4_600_000L, decoded.chargingRecords.single().endedAtEpochMillis)
        assertEquals(36.5, decoded.chargingRecords.single().vehicleEnergyKwh!!, 0.000001)
    }

    @Test
    fun `v10 round trip preserves pending completion facts without fake billing values`() {
        val vehicle = VehicleEntity(
            id = 1L,
            brand = "Test",
            model = "EV",
            batteryCapacityKwh = 80.0,
            rangeKm = 600,
            syncId = "vehicle-1",
            createdAtEpochMillis = 100L,
            updatedAtEpochMillis = 100L,
        )
        val pending = ChargingSessionEntity(
            id = "session-pending",
            vehicleId = 1L,
            startedAtEpochMillis = 10_000L,
            startSoc = 25,
            targetSoc = 80,
            chargerType = "家充",
            unitPricePerKwh = 0.61,
            location = "家",
            status = ChargingSessionStatus.PENDING_DETAILS,
            endedAtEpochMillis = 20_000L,
            endSoc = 82,
            odometerKm = 12_345.6,
            pendingMeterEnergyKwh = null,
            pendingTotalCost = null,
            pendingVehicleEnergyKwh = null,
            updatedAtEpochMillis = 20_000L,
        )

        val decoded = BackupCodec.decode(
            BackupCodec.encode(
                BackupPayload(
                    schemaVersion = BackupCodec.CURRENT_SCHEMA_VERSION,
                    exportedAt = 21_000L,
                    appVersion = "test",
                    vehicles = listOf(vehicle),
                    chargingRecords = emptyList(),
                    chargingSessions = listOf(pending),
                )
            )
        )

        assertEquals(pending, decoded.chargingSessions.single())
        assertNull(decoded.chargingSessions.single().pendingMeterEnergyKwh)
        assertNull(decoded.chargingSessions.single().pendingTotalCost)
    }

    @Test
    fun `schema 8 backup without session or completion fields remains readable`() {
        val vehicle = VehicleEntity(
            id = 1L,
            brand = "Legacy",
            model = "EV",
            batteryCapacityKwh = 60.0,
            rangeKm = 450,
            syncId = "legacy-vehicle",
            createdAtEpochMillis = 100L,
            updatedAtEpochMillis = 100L,
        )
        val record = ChargingRecordEntity(
            id = 2L,
            vehicleId = 1L,
            chargeTimeEpochMillis = 2_000L,
            energyKwh = 20.0,
            cost = 24.0,
            startSoc = 50,
            endSoc = 80,
            syncId = "legacy-charge",
            updatedAtEpochMillis = 2_000L,
        )
        val root = JSONObject(
            BackupCodec.encode(
                BackupPayload(
                    schemaVersion = BackupCodec.CURRENT_SCHEMA_VERSION,
                    exportedAt = 3_000L,
                    appVersion = "legacy-fixture",
                    vehicles = listOf(vehicle),
                    chargingRecords = listOf(record),
                )
            )
        )
        root.put("schemaVersion", 8)
        root.remove("chargingSessions")
        root.getJSONArray("chargingRecords").getJSONObject(0).apply {
            remove("endedAtEpochMillis")
            remove("vehicleEnergyKwh")
        }

        val decoded = BackupCodec.decode(root.toString())

        assertTrue(decoded.chargingSessions.isEmpty())
        assertNull(decoded.chargingRecords.single().endedAtEpochMillis)
        assertNull(decoded.chargingRecords.single().vehicleEnergyKwh)
    }
}
