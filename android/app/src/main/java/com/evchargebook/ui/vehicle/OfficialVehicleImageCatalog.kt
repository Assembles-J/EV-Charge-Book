package com.evchargebook.ui.vehicle

import androidx.annotation.DrawableRes
import com.evchargebook.R
import com.evchargebook.data.entity.VehicleEntity

data class OfficialVehicleImage(
    @DrawableRes val drawableRes: Int,
    val sourceLabel: String
)

object OfficialVehicleImageCatalog {
    private val bydSeal = OfficialVehicleImage(
        R.drawable.byd_seal_2025,
        "BYD Seal bundled hero artwork"
    )

    private val leapmotorC16 = OfficialVehicleImage(
        R.drawable.leapmotor_c16_2026,
        "Leapmotor C16 bundled hero artwork"
    )


    private val xiaomiSu7 = OfficialVehicleImage(R.drawable.xiaomi_su7_2024, "Xiaomi SU7 bundled hero artwork")
    private val teslaModel3 = OfficialVehicleImage(R.drawable.tesla_model_3, "Tesla Model 3 bundled hero artwork")


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
        val isBaseSeal = (model == "海豹" || model.startsWith("海豹 20") || model.startsWith("海豹20") || model == "seal" || model.startsWith("seal 20")) && !model.contains("06") && !model.contains("07") && !model.contains("5 dm") && !model.contains("6 dm")
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
