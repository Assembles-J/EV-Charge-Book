package com.evchargebook.ui.vehicle

import com.evchargebook.data.entity.VehicleEntity

/**
 * Maps known vehicle names to local artwork bundled with the app.
 *
 * Keep the matcher deliberately strict. A wrong vehicle image is worse than
 * falling back to the generic EV illustration in the dashboard hero.
 */
data class OfficialVehicleImage(
    val assetPath: String,
    val sourcePage: String,
    val sourceLabel: String
)

object OfficialVehicleImageCatalog {
    private val bydSeal = OfficialVehicleImage(
        assetPath = "vehicle_artwork/byd_seal_2025.webp.b64",
        sourcePage = "https://media.byd.com/section/models/pure-electric/seal/?lang=eng",
        sourceLabel = "BYD SEAL hero artwork"
    )

    fun resolve(vehicle: VehicleEntity?): OfficialVehicleImage? {
        vehicle ?: return null

        val brand = vehicle.brand.trim().lowercase()
        val model = vehicle.model.trim().lowercase()

        val isByd = brand.contains("比亚迪") || brand == "byd" || brand.contains("byd")
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

        return when {
            isByd && isBaseSeal -> bydSeal
            else -> null
        }
    }
}
