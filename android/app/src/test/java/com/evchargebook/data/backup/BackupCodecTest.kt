package com.evchargebook.data.backup

import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.data.entity.VehicleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupCodecTest {
    @Test
    fun encodeDecode_preservesChargingCoordinates() {
        val payload = BackupPayload(
            schemaVersion = BackupCodec.CURRENT_SCHEMA_VERSION,
            exportedAt = 1234L,
            appVersion = "test",
            vehicles = listOf(VehicleEntity(id = 1, brand = "Test", model = "EV", batteryCapacityKwh = 60.0, rangeKm = 500)),
            chargingRecords = listOf(
                ChargingRecordEntity(
                    id = 2,
                    vehicleId = 1,
                    chargeTimeEpochMillis = 1000L,
                    energyKwh = 20.0,
                    cost = 15.0,
                    startSoc = 20,
                    endSoc = 80,
                    latitude = 31.230416,
                    longitude = 121.473701,
                    locationAccuracyMeters = 12.5
                )
            )
        )

        val decoded = BackupCodec.decode(BackupCodec.encode(payload)).chargingRecords.single()
        assertEquals(31.230416, decoded.latitude!!, 0.000001)
        assertEquals(121.473701, decoded.longitude!!, 0.000001)
        assertEquals(12.5, decoded.locationAccuracyMeters!!, 0.01)
    }

    @Test
    fun decode_acceptsLegacyBackupWithoutCoordinates() {
        val legacy = """
            {
              "schemaVersion": 3,
              "exportedAt": 1234,
              "appVersion": "0.2",
              "vehicles": [{
                "id": 1,
                "brand": "Test",
                "model": "EV",
                "batteryCapacityKwh": 60.0,
                "rangeKm": 500,
                "isDefault": true,
                "isArchived": false,
                "createdAtEpochMillis": 1
              }],
              "chargingRecords": [{
                "id": 2,
                "vehicleId": 1,
                "chargeTimeEpochMillis": 1000,
                "energyKwh": 20.0,
                "cost": 15.0,
                "startSoc": 20,
                "endSoc": 80
              }]
            }
        """.trimIndent()

        val decoded = BackupCodec.decode(legacy).chargingRecords.single()
        assertNull(decoded.latitude)
        assertNull(decoded.longitude)
        assertNull(decoded.locationAccuracyMeters)
    }
}
