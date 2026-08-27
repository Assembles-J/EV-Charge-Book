package com.evchargebook.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Stable visual tokens for EV cockpit surfaces.
 *
 * Do not use Material inverse colors for vehicle-style panels because they
 * intentionally change with Material theme semantics. Cockpit surfaces are
 * product identity and should remain predictable across light/dark modes.
 */
data class CockpitColors(
    val background: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accent: Color,
    val warning: Color,
    val danger: Color
)

private val LightCockpitColors = CockpitColors(
    background = Color(0xFF11171B),
    primaryText = Color(0xFFF4F8F6),
    secondaryText = Color(0xFFA9B5B0),
    accent = Color(0xFF45E6A8),
    warning = Color(0xFFFFC56B),
    danger = Color(0xFFFF8A80)
)

private val DarkCockpitColors = CockpitColors(
    background = Color(0xFF080C0F),
    primaryText = Color(0xFFF4F8F6),
    secondaryText = Color(0xFFB9C8C2),
    accent = Color(0xFF45E6A8),
    warning = Color(0xFFFFC56B),
    danger = Color(0xFFFF8A80)
)

val LocalCockpitColors = staticCompositionLocalOf { LightCockpitColors }

val MaterialTheme.cockpit: CockpitColors
    @Composable get() = LocalCockpitColors.current
