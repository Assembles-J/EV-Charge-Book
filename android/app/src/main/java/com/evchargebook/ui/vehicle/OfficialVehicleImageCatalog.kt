package com.evchargebook.ui.vehicle

import com.evchargebook.data.entity.VehicleEntity

data class OfficialVehicleImage(
    val key: String,
    val remoteFallbackUrl: String?,
    val sourceLabel: String
)

/**
 * Resolve the semantic Hero artwork key for a vehicle.
 *
 * Managed catalog metadata remains authoritative. The tiny bundled fallback table only mirrors
 * the APK seed catalog and exists to keep already-shipped models usable when a managed catalog row
 * temporarily loses its Hero key or the remote Hero manifest cannot be reached.
 *
 * New brands/models still do not require Android changes: once their managed catalog row provides
 * a Hero key, the runtime manifest/cache resolves them normally.
 */
object OfficialVehicleImageCatalog {
    private data class BundledFallback(
        val key: String,
        val url: String,
    )

    private const val GITHUB_RAW_BASE =
        "https://raw.githubusercontent.com/Assembles-J/EV-Charge-Book/main/hero-assets/remote"

    private val bundledFallbacks = mapOf(
        "leap-c16-2026-reev-67" to BundledFallback(
            key = "leapmotor-c16-2026",
            url = "$GITHUB_RAW_BASE/leapmotor_c16_2026.webp",
        ),
        "xiaomi-su7-2024-max" to BundledFallback(
            key = "xiaomi-su7-2024",
            url = "$GITHUB_RAW_BASE/xiaomi_su7_2024.webp",
        ),
        "byd-seal-2025-650" to BundledFallback(
            key = "byd-seal-2025",
            url = "$GITHUB_RAW_BASE/byd_seal_2025.webp",
        ),
    )

    fun resolve(vehicle: VehicleEntity?, preferredArtworkKey: String? = null): OfficialVehicleImage? {
        vehicle ?: return null

        val preferredKey = preferredArtworkKey
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() }
        val bundled = vehicle.catalogVehicleId
            ?.trim()
            ?.lowercase()
            ?.let(bundledFallbacks::get)
        val key = preferredKey ?: bundled?.key ?: return null

        return OfficialVehicleImage(
            key = key,
            remoteFallbackUrl = bundled
                ?.takeIf { it.key == key }
                ?.url,
            sourceLabel = if (preferredKey != null) {
                "Managed vehicle catalog Hero artwork"
            } else {
                "Bundled vehicle catalog Hero fallback"
            }
        )
    }
}
