package com.evchargebook.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Stable product-level colors for vehicle cockpit surfaces.
 *
 * These tokens intentionally do not depend on Material inverse colors.
 * Cockpit panels are part of EV Charge Book visual identity and should stay
 * consistent across light/dark system themes.
 */
data class CockpitThemeColors(
    val background: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accent: Color,
    val warning: Color,
    val danger: Color
)

private val DefaultCockpitThemeColors = CockpitThemeColors(
    background = Color(0xFF11171B),
    primaryText = Color(0xFFF4F8F6),
    secondaryText = Color(0xFFA9B5B0),
    accent = Color(0xFF45E6A8),
    warning = Color(0xFFFFC56B),
    danger = Color(0xFFFF8A80)
)

val LocalCockpitThemeColors = staticCompositionLocalOf {
    DefaultCockpitThemeColors
}

val MaterialTheme.cockpitColors: CockpitThemeColors
    @Composable get() = LocalCockpitThemeColors.current
