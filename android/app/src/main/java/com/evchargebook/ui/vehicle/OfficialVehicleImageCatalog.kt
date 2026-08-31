package com.evchargebook.ui.vehicle

import com.evchargebook.data.entity.VehicleEntity

data class OfficialVehicleImage(
    val key: String,
    val remoteFallbackUrl: String?,
    val sourceLabel: String
)

/**
 * Android no longer owns a supported-model whitelist.
 *
 * A managed catalog row provides the Hero artwork key. The runtime manifest/cache resolves
 * that key to the current asset. Adding a brand or model therefore does not require a new APK.
 */
object OfficialVehicleImageCatalog {
    fun resolve(vehicle: VehicleEntity?, preferredArtworkKey: String? = null): OfficialVehicleImage? {
        vehicle ?: return null
        val key = preferredArtworkKey
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        return OfficialVehicleImage(
            key = key,
            remoteFallbackUrl = null,
            sourceLabel = "Managed vehicle catalog Hero artwork"
        )
    }
}
