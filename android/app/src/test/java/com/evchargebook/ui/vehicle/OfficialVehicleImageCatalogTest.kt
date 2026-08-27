package com.evchargebook.ui.vehicle

import com.evchargebook.data.entity.VehicleEntity
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OfficialVehicleImageCatalogTest {

    @Test
    fun `supported catalog vehicles resolve bundled artwork`() {
        assertNotNull(OfficialVehicleImageCatalog.resolve(vehicle("byd-seal-2025-650", "比亚迪", "海豹 2025款")))
        assertNotNull(OfficialVehicleImageCatalog.resolve(vehicle("leap-c16-2026-reev-67", "零跑", "C16 2026款")))
        assertNotNull(OfficialVehicleImageCatalog.resolve(vehicle("xiaomi-su7-2024-max", "小米", "SU7 Max")))
    }

    @Test
    fun `strict name fallback resolves model 3`() {
        assertNotNull(OfficialVehicleImageCatalog.resolve(vehicle(null, "Tesla", "Model 3")))
    }

    @Test
    fun `nearby models never borrow wrong artwork`() {
        assertNull(OfficialVehicleImageCatalog.resolve(vehicle(null, "比亚迪", "海豹07 EV")))
        assertNull(OfficialVehicleImageCatalog.resolve(vehicle(null, "Tesla", "Model Y")))
    }

    private fun vehicle(catalogId: String?, brand: String, model: String) = VehicleEntity(
        catalogVehicleId = catalogId,
        brand = brand,
        model = model,
        batteryCapacityKwh = 60.0,
        rangeKm = 500
    )
}
