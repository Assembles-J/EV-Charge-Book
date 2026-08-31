package com.evchargebook.ui.vehicle

import kotlin.test.Test
import kotlin.test.assertEquals

class HeroArtworkManifestRepositoryVariantTest {
    @Test
    fun `dark UI prefers dark then legacy base`() {
        assertEquals(
            listOf("xiaomi-su7-2024-dark", "xiaomi-su7-2024"),
            HeroArtworkManifestRepository.candidateKeys("xiaomi-su7-2024", preferLight = false),
        )
    }

    @Test
    fun `light UI prefers light then dark then legacy base`() {
        assertEquals(
            listOf("xiaomi-su7-2024-light", "xiaomi-su7-2024-dark", "xiaomi-su7-2024"),
            HeroArtworkManifestRepository.candidateKeys("xiaomi-su7-2024", preferLight = true),
        )
    }

    @Test
    fun `variant input is normalized back to semantic base`() {
        assertEquals(
            listOf("byd-seal-2026-light", "byd-seal-2026-dark", "byd-seal-2026"),
            HeroArtworkManifestRepository.candidateKeys("byd-seal-2026-dark", preferLight = true),
        )
    }

    @Test
    fun `resolve uses variant-specific cache version and preserves manifest version`() {
        val entries = mapOf(
            "demo-light" to HeroArtworkManifestRepository.RemoteArtwork(
                version = 2,
                manifestVersion = 2,
                resolvedKey = "demo-light",
                url = "https://example.com/demo-light.webp",
            ),
            "demo-dark" to HeroArtworkManifestRepository.RemoteArtwork(
                version = 2,
                manifestVersion = 2,
                resolvedKey = "demo-dark",
                url = "https://example.com/demo-dark.webp",
            ),
        )

        val light = HeroArtworkManifestRepository.resolveFrom(entries, "demo", preferLight = true)
        val dark = HeroArtworkManifestRepository.resolveFrom(entries, "demo", preferLight = false)

        assertEquals("demo-light", light?.resolvedKey)
        assertEquals(22, light?.version)
        assertEquals(2, light?.manifestVersion)
        assertEquals("demo-dark", dark?.resolvedKey)
        assertEquals(21, dark?.version)
        assertEquals(2, dark?.manifestVersion)
    }
}
