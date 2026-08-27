package com.evchargebook.ui.vehicle

import com.evchargebook.data.entity.VehicleEntity

/**
 * Maps known vehicles to transparent hero artwork bundled with the APK.
 *
 * Prefer exact catalog IDs and keep name fallbacks deliberately strict. Showing
 * the wrong vehicle is worse than falling back to the generic EV illustration.
 */
data class OfficialVehicleImage(
    val assetPath: String,
    val sourceLabel: String
)

object OfficialVehicleImageCatalog {
    private val bydSeal = OfficialVehicleImage(
        assetPath = "vehicle_artwork/byd_seal_2025.webp.b64",
        sourceLabel = "BYD Seal bundled hero artwork"
    )

    private val leapmotorC16 = OfficialVehicleImage(
        assetPath = "vehicle_artwork/leapmotor_c16_2026.webp.b64",
        sourceLabel = "Leapmotor C16 bundled hero artwork"
    )

    private val xiaomiSu7 = OfficialVehicleImage(
        assetPath = "vehicle_artwork/xiaomi_su7_2024.webp.b64",
        sourceLabel = "Xiaomi SU7 bundled hero artwork"
    )

    private val teslaModel3 = OfficialVehicleImage(
        assetPath = "vehicle_artwork/tesla_model_3.webp.b64",
        sourceLabel = "Tesla Model 3 bundled hero artwork"
    )

    fun resolve(vehicle: VehicleEntity?): OfficialVehicleImage? {
        vehicle ?: return null

        when (vehicle.catalogVehicleId?.trim()?.lowercase()) {
            "byd-seal-2025-650" -> return bydSeal
            "leap-c16-2026-reev-67" -> return leapmotorC16
            "xiaomi-su7-2024-max" -> return xiaomiSu7
        }

        val brand = vehicle.brand.trim().lowercase()
        val model = vehicle.model.trim().lowercase()

        val isByd = brand.contains("比亚迪") || brand.contains("byd")
        val isBaseSeal = (
            model == "海豹" ||
                model.startsWith("海豹 20") ||
                model.startsWith("海豹20") ||
                model == "seal" ||
                model.startsWith("seal 20")
            ) &&
            !model.contains("06") &&
            !model.contains("07") &&
            !model.contains("5 dm") &&
            !model.contains("6 dm")

        val isLeapmotor = brand.contains("零跑") || brand.contains("leapmotor") || brand == "leap"
        val isC16 = model == "c16" || model.startsWith("c16 ") || model.startsWith("c16 20")

        val isXiaomi = brand.contains("小米") || brand.contains("xiaomi")
        val isSu7 = model == "su7" || model.startsWith("su7 ") || model.startsWith("su7 20")

        val isTesla = brand.contains("特斯拉") || brand.contains("tesla")
        val isModel3 = model == "model 3" || model.startsWith("model 3 ") || model.startsWith("model3")

        return when {
            isByd && isBaseSeal -> bydSeal
            isLeapmotor && isC16 -> leapmotorC16
            isXiaomi && isSu7 -> xiaomiSu7
            isTesla && isModel3 -> teslaModel3
            else -> null
        }
    }
}
