package com.evchargebook.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Product-level colors for EV cockpit surfaces.
 *
 * Keep this separate from Material inverse colors so dashboard,
 * trip and charging cockpit surfaces remain stable across theme changes.
 */
data class CockpitColors(
    val background: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accent: Color,
    val warning: Color,
    val danger: Color
)

val LocalCockpitColors = staticCompositionLocalOf {
    CockpitColors(
        background = Color(0xFF11171B),
        primaryText = Color(0xFFF4F8F6),
        secondaryText = Color(0xFFB7C2BD),
        accent = Color(0xFF45E6A8),
        warning = Color(0xFFFFC56B),
        danger = Color(0xFFFFB4AB)
    )
}
