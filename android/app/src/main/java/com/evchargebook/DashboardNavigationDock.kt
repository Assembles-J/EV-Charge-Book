package com.evchargebook

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar as MaterialNavigationBar
import androidx.compose.material3.NavigationBarItem as MaterialNavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Dashboard-specific navigation shell.
 *
 * MainActivity lives in the same package, so this intentionally shadows the
 * Material3 NavigationBar call there while preserving its existing five-tab
 * semantics and state handling. The visual shell matches the approved
 * reference dock without duplicating navigation logic.
 */
@Composable
fun NavigationBar(
    containerColor: Color,
    tonalElevation: Dp,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = Color(0xFF081216),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        MaterialNavigationBar(
            modifier = Modifier.fillMaxWidth(),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            content = content
        )
    }
}

/**
 * Keeps Material3 interaction/accessibility behavior while enlarging the
 * supplied icon slot slightly. MainActivity already provides the target
 * selected/unselected colors and a transparent selected indicator.
 */
@Composable
fun RowScope.NavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: @Composable (() -> Unit)?,
    colors: NavigationBarItemColors
) {
    MaterialNavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Box(Modifier.scale(1.14f)) {
                icon()
            }
        },
        label = label,
        colors = colors
    )
}
