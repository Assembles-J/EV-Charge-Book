package com.evchargebook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.evchargebook.ui.theme.spacing

@Composable
fun EmptyState(title: String, message: String, actionLabel: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(MaterialTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.ElectricBolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(MaterialTheme.spacing.md))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(MaterialTheme.spacing.xs))
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(MaterialTheme.spacing.lg))
        Button(onClick = onAction) { Text(actionLabel) }
    }
}
