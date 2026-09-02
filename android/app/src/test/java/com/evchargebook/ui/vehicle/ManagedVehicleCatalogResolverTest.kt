package com.evchargebook.ui.vehicle

import com.evchargebook.data.entity.VehicleCatalogEntity
import com.evchargebook.data.entity.VehicleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManagedVehicleCatalogResolverTest {
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
        batteryCapacityKwh = 67.7,
        rangeKm = 520,
        isActive = true,
    )

    @Test
    fun `exact managed catalog identity remains authoritative`() {
        val vehicle = vehicle(catalogVehicleId = c16.catalogId, model = "自定义旧名称")

        assertEquals(c16, ManagedVehicleCatalogResolver.resolveCatalogVehicle(vehicle, listOf(c16)))
        assertEquals(
            "leapmotor-c16-2026",
            ManagedVehicleCatalogResolver.resolveHeroArtworkKey(vehicle, listOf(c16)),
        )
    }

    @Test
    fun `legacy C16 backup without catalog identity resolves from managed brand and model`() {
        val vehicle = vehicle(catalogVehicleId = null, model = "C16 2026款")

        assertEquals(c16, ManagedVehicleCatalogResolver.resolveCatalogVehicle(vehicle, listOf(c16)))
        assertEquals(
            "leapmotor-c16-2026",
            ManagedVehicleCatalogResolver.resolveHeroArtworkKey(vehicle, listOf(c16)),
        )
    }

    @Test
    fun `legacy exact model beats newer series-only match`() {
        val newer = c16.copy(
            catalogId = "leap-c16-2027-reev",
            modelName = "C16 2027款",
            modelYear = 2027,
            heroArtworkKey = "leapmotor-c16-2027",
        )
        val vehicle = vehicle(catalogVehicleId = null, model = "C16 2026款")

        assertEquals(c16, ManagedVehicleCatalogResolver.resolveCatalogVehicle(vehicle, listOf(newer, c16)))
        assertEquals(
            "leapmotor-c16-2026",
            ManagedVehicleCatalogResolver.resolveHeroArtworkKey(vehicle, listOf(newer, c16)),
        )
    }

    @Test
    fun `legacy model never crosses brand boundary`() {
        val vehicle = vehicle(catalogVehicleId = null, brand = "其他品牌", model = "C16 2026款")

        assertNull(ManagedVehicleCatalogResolver.resolveCatalogVehicle(vehicle, listOf(c16)))
        assertNull(ManagedVehicleCatalogResolver.resolveHeroArtworkKey(vehicle, listOf(c16)))
    }

    @Test
    fun `known catalog identity with missing hero key does not borrow another model artwork`() {
        val withoutHero = c16.copy(heroArtworkKey = null)
        val another = c16.copy(
            catalogId = "leap-c16-2027-reev",
            modelName = "C16 2027款",
            modelYear = 2027,
            heroArtworkKey = "leapmotor-c16-2027",
        )
        val vehicle = vehicle(catalogVehicleId = withoutHero.catalogId, model = "C16 2026款")

        assertEquals(withoutHero, ManagedVehicleCatalogResolver.resolveCatalogVehicle(vehicle, listOf(withoutHero, another)))
        assertNull(ManagedVehicleCatalogResolver.resolveHeroArtworkKey(vehicle, listOf(withoutHero, another)))
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
        batteryCapacityKwh = 81.9,
        rangeKm = 630,
    )
}
