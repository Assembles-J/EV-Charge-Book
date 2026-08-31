package com.evchargebook.data.backup

import com.evchargebook.data.entity.VehicleEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupNicknameTest {
    @Test
    fun `vehicle nickname survives backup round trip`() {
        val payload = BackupPayload(
            schemaVersion = BackupCodec.CURRENT_SCHEMA_VERSION,
            exportedAt = 1234L,
            appVersion = "test",
            vehicles = listOf(
                VehicleEntity(
                    id = 1L,
                    catalogVehicleId = "future-brand-model-1",
                    brand = "Future Brand",
                    model = "Model One",
                    batteryCapacityKwh = 80.0,
                    rangeKm = 600,
                    nickname = "周末车",
                    isDefault = true,
                    createdAtEpochMillis = 100L,
                    syncId = "vehicle-sync-1",
                    updatedAtEpochMillis = 200L
                )
            ),
            chargingRecords = emptyList()
        )

        val restored = BackupCodec.decode(BackupCodec.encode(payload))

        assertEquals("周末车", restored.vehicles.single().nickname)
        assertEquals("Model One", restored.vehicles.single().model)
    }
}
