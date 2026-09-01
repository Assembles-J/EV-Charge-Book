package com.evchargebook.ui.dashboard

import com.evchargebook.data.entity.VehicleCatalogEntity
import com.evchargebook.data.entity.VehicleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardHeroArtworkResolverTest {
    private val c16 = VehicleCatalogEntity(
        catalogId = "leap-c16-2026-reev-67",
        source = "managed-v1",
        brandId = "leapmotor",
        brand = "零跑",
        series = "C16",
        modelName = "C16 2026款",
        modelYear = 2026,
        trimName = "增程版",
        powertrainType = "REEV",
        heroArtworkKey = "leapmotor-c16-2026",
        isActive = true,
    )

    @Test
    fun `exact managed catalog identity remains authoritative`() {
        val vehicle = vehicle(catalogVehicleId = c16.catalogId, model = "自定义旧名称")
        assertEquals("leapmotor-c16-2026", DashboardHeroArtworkResolver.resolve(vehicle, listOf(c16)))
    }

    @Test
    fun `legacy C16 without catalog identity resolves from managed brand and series`() {
        val vehicle = vehicle(catalogVehicleId = null, model = "C16")
        assertEquals("leapmotor-c16-2026", DashboardHeroArtworkResolver.resolve(vehicle, listOf(c16)))
    }

    @Test
    fun `legacy model never crosses brand boundary`() {
        val vehicle = vehicle(catalogVehicleId = null, brand = "其他品牌", model = "C16")
        assertNull(DashboardHeroArtworkResolver.resolve(vehicle, listOf(c16)))
    }

    private fun vehicle(
        catalogVehicleId: String?,
        brand: String = "零跑",
        model: String,
    ) = VehicleEntity(
        id = 1L,
        catalogVehicleId = catalogVehicleId,
        brand = brand,
        model = model,
        batteryCapacityKwh = 67.7,
        rangeKm = 520,
    )
}
