package com.evchargebook.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.evchargebook.ui.theme.spacing

@Composable
fun EmptyState(title: String, message: String, actionLabel: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = MaterialTheme.spacing.xl, vertical = MaterialTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                Icons.Default.ElectricBolt,
                contentDescription = null,
                modifier = Modifier.padding(MaterialTheme.spacing.md)
            )
        }
        Spacer(Modifier.height(MaterialTheme.spacing.md))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(MaterialTheme.spacing.xs))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(MaterialTheme.spacing.lg))
        Button(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
fun ResponsiveMetricGrid(
    itemCount: Int,
    itemContent: @Composable (index: Int, modifier: Modifier) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 360.dp || LocalDensity.current.fontScale >= 1.3f
        val columns = if (compact) 2 else 3

        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            var start = 0
            while (start < itemCount) {
                val end = minOf(start + columns, itemCount)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
                ) {
                    for (index in start until end) {
                        itemContent(index, Modifier.weight(1f))
                    }
                    repeat(columns - (end - start)) { Spacer(Modifier.weight(1f)) }
                }
                start = end
            }
        }
    }
}
