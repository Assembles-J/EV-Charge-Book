package com.evchargebook.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Theme-aware semantic colors for dashboard/cockpit data surfaces.
 *
 * Normal cards and metrics must follow the active Material color scheme. Fixed dark colors
 * belong only to media overlays such as the vehicle artwork stage.
 */
data class CockpitColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val outline: Color,
    val accent: Color,
    val warning: Color,
    val danger: Color
)

internal fun cockpitColorsFor(colorScheme: ColorScheme) = CockpitColors(
    background = colorScheme.background,
    surface = colorScheme.surfaceContainerLow,
    surfaceElevated = colorScheme.surfaceContainerHigh,
    primaryText = colorScheme.onSurface,
    secondaryText = colorScheme.onSurfaceVariant,
    outline = colorScheme.outlineVariant,
    accent = colorScheme.primary,
    warning = EVDesignTokens.Energy.warning,
    danger = colorScheme.error
)

val LocalCockpitColors = staticCompositionLocalOf {
    CockpitColors(
        background = EVDesignTokens.Dark.background,
        surface = EVDesignTokens.Dark.surfaceLow,
        surfaceElevated = EVDesignTokens.Dark.surfaceElevated,
        primaryText = EVDesignTokens.Dark.primaryText,
        secondaryText = EVDesignTokens.Dark.secondaryText,
        outline = EVDesignTokens.Dark.outlineVariant,
        accent = EVDesignTokens.Energy.green,
        warning = EVDesignTokens.Energy.warning,
        danger = EVDesignTokens.Energy.danger
    )
}
