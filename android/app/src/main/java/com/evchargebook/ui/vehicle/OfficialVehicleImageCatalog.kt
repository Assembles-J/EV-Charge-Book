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

    fun resolve(vehicle: VehicleEntity?): OfficialVehicleImage? {
        vehicle ?: return null

        if (vehicle.catalogVehicleId?.trim()?.lowercase() == "byd-seal-2025-650") {
            return bydSeal
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

        return if (isByd && isBaseSeal) bydSeal else null
    }
}
