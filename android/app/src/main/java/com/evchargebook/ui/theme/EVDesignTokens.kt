package com.evchargebook.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Product-level visual tokens for EV Charge Book.
 *
 * Theme-sensitive UI should consume MaterialTheme.colorScheme. These raw palette values
 * only define the app light/dark schemes and intentional media-only overlays in one place.
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

    /**
     * Fixed dark media colors used only on top of vehicle artwork where contrast must stay
     * predictable regardless of the surrounding app theme.
     */
    object Media {
        val stageTop = Color(0xFF07110D)
        val stageMiddle = Color(0xFF0A1712)
        val stageBottom = Color(0xFF06100C)
        val primaryText = Color(0xFFF4F8F6)
        val controlSurface = Color(0x6607110F)
        val controlOutline = Color.White.copy(alpha = 0.14f)
        val scrimStrong = Color(0x98020806)
        val scrimSoft = Color(0x20020806)
        val scrimLower = Color(0x10020806)
        val scrimBottom = Color(0x4806100C)
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
