package com.evchargebook.ui.vehicle

import com.evchargebook.data.entity.VehicleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `bundled C16 falls back when managed hero key is missing`() {
        val resolved = OfficialVehicleImageCatalog.resolve(
            vehicle("leap-c16-2026-reev-67", "零跑", "C16 2026款")
        )

        assertNotNull(resolved)
        assertEquals("leapmotor-c16-2026", resolved?.key)
        assertTrue(resolved?.remoteFallbackUrl?.endsWith("/leapmotor_c16_2026.webp") == true)
    }

    @Test
    fun `managed hero key stays authoritative for bundled vehicle`() {
        val resolved = OfficialVehicleImageCatalog.resolve(
            vehicle("leap-c16-2026-reev-67", "零跑", "C16 2026款"),
            preferredArtworkKey = "leapmotor-c16-special"
        )

        assertEquals("leapmotor-c16-special", resolved?.key)
        assertNull(resolved?.remoteFallbackUrl)
    }

    @Test
    fun `unknown model never selects artwork from display name`() {
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
