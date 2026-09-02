package com.evchargebook.ui.vehicle

import com.evchargebook.data.entity.VehicleCatalogEntity
import com.evchargebook.data.entity.VehicleEntity

/**
 * Resolves a user vehicle against the managed catalog without reintroducing an APK-owned model
 * whitelist.
 *
 * Modern vehicles use catalogVehicleId exactly. Legacy local vehicles created before managed
 * catalog linkage may have a null catalogVehicleId; for those only, use conservative brand/model
 * matching against the current managed catalog at render time. This never rewrites vehicle history
 * or silently mutates catalog identity.
 */
internal object ManagedVehicleCatalogResolver {
    fun resolveCatalogVehicle(
        vehicle: VehicleEntity?,
        catalog: List<VehicleCatalogEntity>,
    ): VehicleCatalogEntity? {
        vehicle ?: return null

        exactCatalogVehicle(vehicle, catalog)?.let { return it }
        if (!vehicle.catalogVehicleId.isNullOrBlank()) return null

        return legacyCandidates(vehicle, catalog)
            .sortedWith(catalogPreference())
            .firstOrNull()
    }

    fun resolveHeroArtworkKey(
        vehicle: VehicleEntity?,
        catalog: List<VehicleCatalogEntity>,
    ): String? {
        vehicle ?: return null

        val exactCatalogId = vehicle.catalogVehicleId?.trim()?.takeIf { it.isNotEmpty() }
        if (exactCatalogId != null) {
            return exactCatalogVehicle(vehicle, catalog)
                ?.heroArtworkKey
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }

        return legacyCandidates(vehicle, catalog)
            .filter { it.heroArtworkKey?.isNotBlank() == true }
            .sortedWith(catalogPreference())
            .mapNotNull { it.heroArtworkKey?.trim()?.takeIf(String::isNotEmpty) }
            .firstOrNull()
    }

    private fun exactCatalogVehicle(
        vehicle: VehicleEntity,
        catalog: List<VehicleCatalogEntity>,
    ): VehicleCatalogEntity? {
        val exactCatalogId = vehicle.catalogVehicleId
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        return catalog.firstOrNull { it.catalogId.trim().lowercase() == exactCatalogId }
    }

    private fun legacyCandidates(
        vehicle: VehicleEntity,
        catalog: List<VehicleCatalogEntity>,
    ): Sequence<VehicleCatalogEntity> {
        val vehicleBrand = normalize(vehicle.brand)
        val vehicleModel = normalize(vehicle.model)
        if (vehicleBrand.isEmpty() || vehicleModel.isEmpty()) return emptySequence()

        return catalog.asSequence().filter { item ->
            normalize(item.brand) == vehicleBrand && modelMatches(vehicleModel, item)
        }
    }

    private fun catalogPreference() =
        compareByDescending<VehicleCatalogEntity> { it.isActive }
            .thenByDescending { it.modelYear ?: Int.MIN_VALUE }
            .thenByDescending { it.sourceUpdatedAtEpochMillis }

    private fun modelMatches(vehicleModel: String, item: VehicleCatalogEntity): Boolean {
        val series = normalize(item.series)
        val modelName = normalize(item.modelName)
        return when {
            modelName.isNotEmpty() && (
                vehicleModel == modelName ||
                    vehicleModel.contains(modelName) ||
                    modelName.contains(vehicleModel)
                ) -> true
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
