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
    private data class LegacyCandidate(
        val catalogVehicle: VehicleCatalogEntity,
        val matchRank: Int,
    )

    fun resolveCatalogVehicle(
        vehicle: VehicleEntity?,
        catalog: List<VehicleCatalogEntity>,
    ): VehicleCatalogEntity? {
        vehicle ?: return null

        exactCatalogVehicle(vehicle, catalog)?.let { return it }
        if (!vehicle.catalogVehicleId.isNullOrBlank()) return null

        return legacyCandidates(vehicle, catalog)
            .sortedWith(legacyPreference())
            .map { it.catalogVehicle }
            .firstOrNull()
    }

    fun resolveHeroArtworkKey(
        vehicle: VehicleEntity?,
        catalog: List<VehicleCatalogEntity>,
    ): String? {
        vehicle ?: return null

        if (!vehicle.catalogVehicleId.isNullOrBlank()) {
            return exactCatalogVehicle(vehicle, catalog)
                ?.heroArtworkKey
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }

        return legacyCandidates(vehicle, catalog)
            .filter { it.catalogVehicle.heroArtworkKey?.isNotBlank() == true }
            .sortedWith(legacyPreference())
            .mapNotNull { it.catalogVehicle.heroArtworkKey?.trim()?.takeIf(String::isNotEmpty) }
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
    ): Sequence<LegacyCandidate> {
        val vehicleBrand = normalize(vehicle.brand)
        val vehicleModel = normalize(vehicle.model)
        if (vehicleBrand.isEmpty() || vehicleModel.isEmpty()) return emptySequence()

        return catalog.asSequence().mapNotNull { item ->
            if (normalize(item.brand) != vehicleBrand) return@mapNotNull null
            val matchRank = modelMatchRank(vehicleModel, item) ?: return@mapNotNull null
            LegacyCandidate(item, matchRank)
        }
    }

    private fun legacyPreference() =
        compareByDescending<LegacyCandidate> { it.matchRank }
            .thenByDescending { it.catalogVehicle.isActive }
            .thenByDescending { it.catalogVehicle.modelYear ?: Int.MIN_VALUE }
            .thenByDescending { it.catalogVehicle.sourceUpdatedAtEpochMillis }

    private fun modelMatchRank(vehicleModel: String, item: VehicleCatalogEntity): Int? {
        val series = normalize(item.series)
        val modelName = normalize(item.modelName)
        return when {
            modelName.isNotEmpty() && vehicleModel == modelName -> 3
            modelName.isNotEmpty() && (
                vehicleModel.contains(modelName) ||
                    modelName.contains(vehicleModel)
                ) -> 2
            series.isNotEmpty() && vehicleModel.contains(series) -> 1
            else -> null
        }
    }

    private fun normalize(value: String): String = value
        .trim()
        .lowercase()
        .replace(" ", "")
        .replace("-", "")
        .replace("_", "")
}
