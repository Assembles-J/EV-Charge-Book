package com.evchargebook.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Stable product colors for cockpit surfaces.
 *
 * Screens should consume these tokens instead of Material inverse colors
 * so cockpit UI remains consistent across theme changes.
 */
val MaterialTheme.cockpitColors: CockpitColors
    @Composable
    get() = LocalCockpitColors.current

val CockpitColors.backgroundColor: Color
    get() = background

val CockpitColors.primaryTextColor: Color
    get() = primaryText

val CockpitColors.accentColor: Color
    get() = accent
