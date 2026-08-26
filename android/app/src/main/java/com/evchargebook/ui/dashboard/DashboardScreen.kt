package com.evchargebook.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.data.entity.VehicleEntity
import com.evchargebook.ui.components.EmptyState
import com.evchargebook.ui.theme.spacing
import com.evchargebook.viewmodel.MainUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(state: MainUiState, onAddClick: () -> Unit, onSelectVehicle: (Long) -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddClick,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("记录充电") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
        ) {
            item { DashboardHeader(state.vehicle, state.vehicles, onSelectVehicle) }
            item { EnergySummary(state) }
            item { MetricsRow(state) }
            item { SectionHeader("最近充电", if (state.chargingRecords.isEmpty()) null else "${state.chargingRecords.size} 笔记录") }
            if (state.chargingRecords.isEmpty()) {
                item { EmptyState("开始记录第一次充电", "记录电量、费用和地点，之后会自动汇总。", "新增充电", onAddClick) }
            } else {
                items(state.chargingRecords.take(3), key = { it.id }) { RecentRecord(it) }
            }
            item { Spacer(Modifier.height(64.dp)) }
        }
    }
}

@Composable
private fun DashboardHeader(selectedVehicle: VehicleEntity?, vehicles: List<VehicleEntity>, onSelectVehicle: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.xs)) {
        Text("总览", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(MaterialTheme.spacing.xxs))
        Box {
            TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(0.dp)) {
                Text(
                    selectedVehicle?.let { "${it.brand} ${it.model}" } ?: "选择车辆",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(Icons.Default.ArrowDropDown, "切换车辆")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                vehicles.forEach { vehicle ->
                    DropdownMenuItem(
                        text = { Text("${vehicle.brand} ${vehicle.model}") },
                        onClick = {
                            onSelectVehicle(vehicle.id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EnergySummary(state: MainUiState) {
    val capacity = state.vehicle?.batteryCapacityKwh ?: 0.0
    val equivalent = if (capacity > 0) state.monthEnergy / capacity else 0.0

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.inverseSurface
    ) {
        Column(Modifier.padding(MaterialTheme.spacing.lg)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "ENERGY / MONTH",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.58f)
                    )
                    Spacer(Modifier.height(MaterialTheme.spacing.xxs))
                    Text(
                        state.vehicle?.let { "${it.brand} ${it.model}" } ?: "EV Charge Book",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.82f)
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.inversePrimary.copy(alpha = 0.14f),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.inversePrimary.copy(alpha = 0.36f))
                ) {
                    Row(
                        Modifier.padding(horizontal = MaterialTheme.spacing.sm, vertical = MaterialTheme.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(7.dp),
                            color = MaterialTheme.colorScheme.inversePrimary,
                            shape = MaterialTheme.shapes.extraLarge
                        ) {}
                        Spacer(Modifier.width(MaterialTheme.spacing.xs))
                        Text("本月", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.inversePrimary)
                    }
                }
            }

            Spacer(Modifier.height(MaterialTheme.spacing.lg))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    one(state.monthEnergy),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.inverseOnSurface
                )
                Spacer(Modifier.width(MaterialTheme.spacing.xs))
                Text(
                    "kWh",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.inversePrimary
                )
            }
            Spacer(Modifier.height(MaterialTheme.spacing.xs))
            Text(
                "¥ ${two(state.monthCost)} · ${state.chargingCount} 次充电",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.72f)
            )

            Spacer(Modifier.height(MaterialTheme.spacing.lg))
            LinearProgressIndicator(
                progress = { equivalent.coerceIn(0.0, 1.0).toFloat() },
                modifier = Modifier.fillMaxWidth().height(5.dp),
                color = MaterialTheme.colorScheme.inversePrimary,
                trackColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.10f)
            )
            Spacer(Modifier.height(MaterialTheme.spacing.xs))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (capacity > 0) "≈ ${one(equivalent)} 次满充" else "补全车辆电池容量后显示",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.58f)
                )
                if (capacity > 0) {
                    Text(
                        "${one(capacity)} kWh / 次",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.58f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricsRow(state: MainUiState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        MetricTile("本月电费", "¥ ${two(state.monthCost)}", "${state.chargingCount} 次", Icons.Default.ReceiptLong, Modifier.weight(1f))
        MetricTile("平均电价", "¥ ${two(state.averagePrice)}", "/ kWh", Icons.Default.Bolt, Modifier.weight(1f))
    }
}

@Composable
private fun MetricTile(
    title: String,
    value: String,
    caption: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(MaterialTheme.spacing.md)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(MaterialTheme.spacing.sm))
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(caption, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionHeader(title: String, meta: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (meta != null) Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RecentRecord(record: ChargingRecordEntity) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(record.location ?: "未命名充电地点", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(MaterialTheme.spacing.xxs))
                Text(
                    "${formatTime(record.chargeTimeEpochMillis)} · ${record.chargerType ?: "未标记方式"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(MaterialTheme.spacing.sm))
            Column(horizontalAlignment = Alignment.End) {
                Text("¥ ${two(record.cost)}", style = MaterialTheme.typography.titleMedium)
                Text("${one(record.energyKwh)} kWh", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
