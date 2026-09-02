package com.evchargebook.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Theme-aware colors used only by the vehicle artwork stage.
 *
 * Unlike normal cards, Hero media needs its own palette so the photo/scrim/title treatment can
 * remain legible while still having a real light and dark presentation.
 */
data class HeroMediaColors(
    val stageTop: Color,
    val stageMiddle: Color,
    val stageBottom: Color,
    val primaryText: Color,
    val controlSurface: Color,
    val controlOutline: Color,
    val scrimStrong: Color,
    val scrimSoft: Color,
    val scrimLower: Color,
    val scrimBottom: Color,
    val darkSurface: Boolean,
)

/**
 * Product-level visual tokens for EV Charge Book.
 *
 * Theme-sensitive normal UI should consume MaterialTheme.colorScheme. These raw palette values
 * define the app light/dark schemes, product accents and the intentional Hero media treatment in
 * one place so screens do not invent their own Color(0x...) values.
 */
object EVDesignTokens {
    object Light {
        val background = Color(0xFFF4F7F5)
        val surface = Color(0xFFFFFFFF)
        val surfaceLow = Color(0xFFF0F4F2)
        val surfaceHigh = Color(0xFFE7EDE9)
        val surfaceHighest = Color(0xFFDDE6E1)
        val primaryText = Color(0xFF101512)
        val secondaryText = Color(0xFF5F6D66)
        val outline = Color(0xFFC9D5CF)
        val outlineVariant = Color(0xFFDCE5E0)
    }

    object Dark {
        val background = Color(0xFF090D0C)
        val surfaceLowest = Color(0xFF060908)
        val surfaceLow = Color(0xFF0D1311)
        val surface = Color(0xFF121716)
        val surfaceElevated = Color(0xFF19201D)
        val surfaceHighest = Color(0xFF202925)
        val primaryText = Color(0xFFEAF3EE)
        val secondaryText = Color(0xFF9AA6A0)
        val outline = Color(0xFF26302C)
        val outlineVariant = Color(0xFF1B2521)
    }

    object Media {
        val dark = HeroMediaColors(
            stageTop = Color(0xFF07110D),
            stageMiddle = Color(0xFF0A1712),
            stageBottom = Color(0xFF06100C),
            primaryText = Color(0xFFF4F8F6),
            controlSurface = Color(0x6607110F),
            controlOutline = Color.White.copy(alpha = 0.14f),
            scrimStrong = Color(0x98020806),
            scrimSoft = Color(0x20020806),
            scrimLower = Color(0x10020806),
            scrimBottom = Color(0x4806100C),
            darkSurface = true,
        )

        val light = HeroMediaColors(
            stageTop = Color(0xFFF2F6F4),
            stageMiddle = Color(0xFFE8EFEB),
            stageBottom = Color(0xFFF7F9F8),
            primaryText = Light.primaryText,
            controlSurface = Color.White.copy(alpha = 0.78f),
            controlOutline = Color.Black.copy(alpha = 0.10f),
            // A pale scrim guarantees dark title/status chrome remains readable even when the
            // light-specific vehicle artwork has mixed brightness near the top edge.
            scrimStrong = Color(0xB8F4F7F5),
            scrimSoft = Color(0x42F4F7F5),
            scrimLower = Color(0x18F4F7F5),
            scrimBottom = Color(0x8AF4F7F5),
            darkSurface = false,
        )

        fun forTheme(darkTheme: Boolean): HeroMediaColors = if (darkTheme) dark else light
    }

    object Energy {
        val green = Color(0xFF32F080)
        val success = Color(0xFF23D18B)
        val warning = Color(0xFFFFB020)
        val danger = Color(0xFFFF4D4F)
    }

    object Radius {
        const val small = 10
        const val medium = 16
        const val large = 24
    }
}
