package com.evchargebook.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.ui.theme.EVDesignTokens
import com.evchargebook.ui.theme.spacing
import com.evchargebook.viewmodel.MainUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DashboardScreen(state: MainUiState, onAddClick: () -> Unit, onSelectVehicle: (Long) -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = EVDesignTokens.Energy.green,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "记录充电")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                horizontal = MaterialTheme.spacing.md,
                vertical = MaterialTheme.spacing.md
            ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
        ) {
            item { HeroVehicleCard(state.vehicle) }
            item { EnergyCockpitCard(state) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("最近充电", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "RECENT CHARGING",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.chargingRecords.isNotEmpty()) {
                        Text(
                            "${state.chargingRecords.size} 笔",
                            style = MaterialTheme.typography.bodyMedium,
                            color = EVDesignTokens.Energy.green
                        )
                    }
                }
            }

            if (state.chargingRecords.isEmpty()) {
                item { CompactChargingEmptyState(onAddClick) }
            } else {
                items(state.chargingRecords.take(3), key = { it.id }) { record ->
                    DashboardChargeItem(record)
                }
            }
            item { Spacer(Modifier.height(56.dp)) }
        }
    }
}

@Composable
private fun CompactChargingEmptyState(onAddClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(EVDesignTokens.Energy.green.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Bolt, null, tint = EVDesignTokens.Energy.green)
            }
            Spacer(Modifier.size(MaterialTheme.spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text("暂无充电记录", style = MaterialTheme.typography.titleMedium)
                Text(
                    "记录第一次充电后，这里会显示最近活动。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onAddClick) { Text("新增") }
        }
    }
}

@Composable
private fun DashboardChargeItem(record: ChargingRecordEntity) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(EVDesignTokens.Energy.green.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Bolt,
                    contentDescription = null,
                    tint = EVDesignTokens.Energy.green,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.size(MaterialTheme.spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    record.location ?: "未命名充电地点",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(MaterialTheme.spacing.xxs))
                Text(
                    "${formatTime(record.chargeTimeEpochMillis)} · ${record.chargerType ?: "未标记方式"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "¥ ${two(record.cost)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${one(record.energyKwh)} kWh",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EVDesignTokens.Energy.green
                )
            }
        }
    }
}

private fun formatTime(epochMillis: Long) = DateTimeFormatter.ofPattern("M月d日 HH:mm")
    .withLocale(Locale.SIMPLIFIED_CHINESE)
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(epochMillis))

private fun one(value: Double) = String.format(Locale.US, "%.1f", value)
private fun two(value: Double) = String.format(Locale.US, "%.2f", value)
