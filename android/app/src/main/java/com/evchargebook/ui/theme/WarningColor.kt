package com.evchargebook.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Compatibility extension for existing screens.
 *
 * Kept as a separate file while the v0.5 design token migration is in progress.
 */
private val LocalWarningColor = staticCompositionLocalOf {
    Color(0xFFFFB020)
}

val MaterialTheme.warningColor: Color
    @Composable
    get() = LocalWarningColor.current
