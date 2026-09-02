package com.evchargebook.ui.vehicle

import com.evchargebook.data.entity.VehicleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OfficialVehicleImageCatalogTest {

    @Test
    fun `managed artwork key resolves without model whitelist`() {
        val resolved = OfficialVehicleImageCatalog.resolve(
            vehicle("future-brand-model-1", "未来品牌", "未来车型"),
            preferredArtworkKey = "future-brand-model-hero"
        )

        assertNotNull(resolved)
        assertEquals("future-brand-model-hero", resolved?.key)
        assertNull(resolved?.remoteFallbackUrl)
    }

    @Test
    fun `model names never select artwork without catalog key`() {
        assertNull(OfficialVehicleImageCatalog.resolve(vehicle("byd-seal-2025-650", "比亚迪", "海豹 2025款")))
        assertNull(OfficialVehicleImageCatalog.resolve(vehicle(null, "Tesla", "Model 3")))
        assertNull(OfficialVehicleImageCatalog.resolve(vehicle(null, "任意品牌", "任意车型")))
    }

    private fun vehicle(catalogId: String?, brand: String, model: String) = VehicleEntity(
        catalogVehicleId = catalogId,
        brand = brand,
        model = model,
        batteryCapacityKwh = 60.0,
        rangeKm = 500
    )
}
