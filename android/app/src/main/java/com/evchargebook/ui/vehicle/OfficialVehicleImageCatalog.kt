package com.evchargebook.ui.vehicle

import com.evchargebook.data.entity.VehicleEntity

data class OfficialVehicleImage(
    val key: String,
    val remoteFallbackUrl: String,
    val sourceLabel: String
)

object OfficialVehicleImageCatalog {
    private const val RAW_HERO_BASE =
        "https://raw.githubusercontent.com/Assembles-J/EV-Charge-Book/main/hero-assets/remote"

    private fun remote(key: String, fileName: String, label: String) = OfficialVehicleImage(
        key = key,
        remoteFallbackUrl = "$RAW_HERO_BASE/$fileName",
        sourceLabel = label
    )

    private val bydSeal = remote(
        key = "byd-seal-2025",
        fileName = "byd_seal_2025.webp",
        label = "BYD Seal remote hero artwork"
    )
    private val leapmotorC16 = remote(
        key = "leapmotor-c16-2026",
        fileName = "leapmotor_c16_2026.webp",
        label = "Leapmotor C16 remote hero artwork"
    )
    private val xiaomiSu7 = remote(
        key = "xiaomi-su7-2024",
        fileName = "xiaomi_su7_2024.webp",
        label = "Xiaomi SU7 remote hero artwork"
    )
    private val xiaomiSu7Ultra = remote(
        key = "xiaomi-su7-ultra-2024",
        fileName = "xiaomi_su7_ultra_2024.webp",
        label = "Xiaomi SU7 Ultra remote hero artwork"
    )
    private val xiaomiYu7 = remote(
        key = "xiaomi-yu7-2025",
        fileName = "xiaomi_yu7_2025.webp",
        label = "Xiaomi YU7 remote hero artwork"
    )
    private val teslaModel3 = remote(
        key = "tesla-model-3",
        fileName = "tesla_model_3.webp",
        label = "Tesla Model 3 remote hero artwork"
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
        val isBaseSeal = (model == "海豹" || model.startsWith("海豹 20") || model.startsWith("海豹20") || model == "seal" || model.startsWith("seal 20")) &&
            !model.contains("06") && !model.contains("07") && !model.contains("5 dm") && !model.contains("6 dm")
        val isLeapmotor = brand.contains("零跑") || brand.contains("leapmotor") || brand == "leap"
        val isC16 = model == "c16" || model.startsWith("c16 ") || model.startsWith("c16 20")
        val isXiaomi = brand.contains("小米") || brand.contains("xiaomi")
        val isSu7Ultra = model.contains("su7 ultra") || model.contains("su7ultra")
        val isSu7 = model == "su7" || model.startsWith("su7 ") || model.startsWith("su7 20")
        val isYu7 = model == "yu7" || model.startsWith("yu7 ") || model.startsWith("yu7 20")
        val isTesla = brand.contains("特斯拉") || brand.contains("tesla")
        val isModel3 = model == "model 3" || model.startsWith("model 3 ") || model.startsWith("model3")

        return when {
            isByd && isBaseSeal -> bydSeal
            isLeapmotor && isC16 -> leapmotorC16
            isXiaomi && isSu7Ultra -> xiaomiSu7Ultra
            isXiaomi && isYu7 -> xiaomiYu7
            isXiaomi && isSu7 -> xiaomiSu7
            isTesla && isModel3 -> teslaModel3
            else -> null
        }
    }
}
