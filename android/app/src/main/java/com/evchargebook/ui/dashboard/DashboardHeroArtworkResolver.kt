package com.evchargebook.ui.dashboard

import com.evchargebook.data.entity.VehicleCatalogEntity
import com.evchargebook.data.entity.VehicleEntity

/**
 * Resolves Dashboard Hero semantics from the managed catalog without reintroducing an APK-owned
 * model whitelist.
 *
 * Modern vehicles use catalogVehicleId exactly. Legacy local vehicles created before managed
 * catalog linkage may have a null catalogVehicleId; for those only, use conservative brand/model
 * matching against the current managed catalog at render time. This does not rewrite vehicle
 * history or silently mutate catalog identity.
 */
internal object DashboardHeroArtworkResolver {
    fun resolve(
        vehicle: VehicleEntity?,
        catalog: List<VehicleCatalogEntity>,
    ): String? {
        vehicle ?: return null

        val exactCatalogId = vehicle.catalogVehicleId?.trim()?.takeIf { it.isNotEmpty() }
        if (exactCatalogId != null) {
            return catalog.firstOrNull { it.catalogId == exactCatalogId }
                ?.heroArtworkKey
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }

        val vehicleBrand = normalize(vehicle.brand)
        val vehicleModel = normalize(vehicle.model)
        if (vehicleBrand.isEmpty() || vehicleModel.isEmpty()) return null

        return catalog.asSequence()
            .filter { item ->
                normalize(item.brand) == vehicleBrand &&
                    item.heroArtworkKey?.isNotBlank() == true &&
                    modelMatches(vehicleModel, item)
            }
            .sortedWith(
                compareByDescending<VehicleCatalogEntity> { it.isActive }
                    .thenByDescending { it.modelYear ?: Int.MIN_VALUE }
                    .thenByDescending { it.sourceUpdatedAtEpochMillis }
            )
            .mapNotNull { it.heroArtworkKey?.trim()?.takeIf(String::isNotEmpty) }
            .firstOrNull()
    }

    private fun modelMatches(vehicleModel: String, item: VehicleCatalogEntity): Boolean {
        val series = normalize(item.series)
        val modelName = normalize(item.modelName)
        return when {
            modelName.isNotEmpty() && (vehicleModel == modelName || vehicleModel.contains(modelName) || modelName.contains(vehicleModel)) -> true
            series.isNotEmpty() && vehicleModel.contains(series) -> true
            else -> false
        }
    }

    private fun normalize(value: String): String = value
        .trim()
        .lowercase()
        .replace(" ", "")
        .replace("-", "")
        .replace("_", "")
}
