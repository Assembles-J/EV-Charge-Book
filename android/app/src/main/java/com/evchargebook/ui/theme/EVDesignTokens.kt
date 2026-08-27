package com.evchargebook.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * EV Charge Book v0.5 design tokens.
 *
 * Dark First visual foundation.
 * Keep tokens independent from MaterialTheme so screens can share a stable product language.
 */
object EVDesignTokens {
    object Dark {
        val background = Color(0xFF090D0C)
        val surface = Color(0xFF121716)
        val surfaceElevated = Color(0xFF19201D)
        val primaryText = Color(0xFFEAF3EE)
        val secondaryText = Color(0xFF9AA6A0)
        val outline = Color(0xFF26302C)
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
