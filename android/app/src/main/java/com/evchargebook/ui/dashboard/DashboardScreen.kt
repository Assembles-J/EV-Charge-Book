package com.evchargebook.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
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
    Scaffold(floatingActionButton = { ExtendedFloatingActionButton(onClick = onAddClick, icon = { Icon(Icons.Default.Add, null) }, text = { Text("记录充电") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
        ) {
            item { DashboardHeader(state.vehicle, state.vehicles, onSelectVehicle) }
            item { EnergyHero(state) }
            item { Text("本月能耗", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            item { MetricsRow(state) }
            item { Text("最近充电", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            if (state.chargingRecords.isEmpty()) item { EmptyState("开始记录你的第一次充电", "记录电量、费用与地点，账本会自动汇总。", "新增充电", onAddClick) }
            else items(state.chargingRecords.take(3), key = { it.id }) { RecentRecord(record = it) }
        }
    }
}

@Composable private fun DashboardHeader(selectedVehicle: VehicleEntity?, vehicles: List<VehicleEntity>, onSelectVehicle: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.large) { Icon(Icons.Default.DirectionsCar, null, Modifier.padding(MaterialTheme.spacing.sm), MaterialTheme.colorScheme.onTertiaryContainer) }
        Spacer(Modifier.width(MaterialTheme.spacing.sm))
        Column(Modifier.weight(1f)) { Text("EV Charge Book", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary); Box { TextButton(onClick = { expanded = true }, contentPadding = PaddingValues()) { Text(selectedVehicle?.let { "${it.brand} ${it.model}" } ?: "我的电动车", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Icon(Icons.Default.ArrowDropDown, "切换车辆") }; DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { vehicles.forEach { vehicle -> DropdownMenuItem(text = { Text("${vehicle.brand} ${vehicle.model}") }, onClick = { onSelectVehicle(vehicle.id); expanded = false }) } } } }
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) { Text("本地账本", Modifier.padding(horizontal = MaterialTheme.spacing.sm, vertical = MaterialTheme.spacing.xs), style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable private fun EnergyHero(state: MainUiState) {
    val capacity = state.vehicle?.batteryCapacityKwh ?: 0.0
    val equivalent = if (capacity > 0) state.monthEnergy / capacity else 0.0
    val progress by animateFloatAsState(equivalent.coerceIn(0.0, 1.0).toFloat(), label = "energy-progress")
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseSurface)) {
        Row(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(MaterialTheme.spacing.xl * 4)) {
                CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.inversePrimary, trackColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.18f), strokeWidth = MaterialTheme.spacing.xs)
                Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.inversePrimary)
            }
            Spacer(Modifier.width(MaterialTheme.spacing.md))
            Column(Modifier.weight(1f)) {
                Text("本月已注入", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.72f))
                Text("${one(state.monthEnergy)} kWh", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.inverseOnSurface)
                Text(if (capacity > 0) "约 ${one(equivalent)} 次满充等效" else "完善车辆资料后显示满充等效", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.78f))
            }
        }
    }
}

@Composable private fun MetricsRow(state: MainUiState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        MetricTile("本月电费", "¥ ${two(state.monthCost)}", "${state.chargingCount} 次充电", Icons.Default.ReceiptLong, Modifier.weight(1f))
        MetricTile("平均电价", "¥ ${two(state.averagePrice)}", "每 kWh", Icons.Default.Bolt, Modifier.weight(1f))
    }
}

@Composable private fun MetricTile(title: String, value: String, caption: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    ElevatedCard(modifier, colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(MaterialTheme.spacing.md)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.height(MaterialTheme.spacing.sm))
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable private fun RecentRecord(record: ChargingRecordEntity) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) { Icon(Icons.Default.Bolt, null, Modifier.padding(MaterialTheme.spacing.sm), MaterialTheme.colorScheme.onPrimaryContainer) }
            Spacer(Modifier.width(MaterialTheme.spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(record.location ?: "未命名充电地点", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${formatTime(record.chargeTimeEpochMillis)} · ${record.chargerType ?: "未标记方式"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) { Text("¥ ${two(record.cost)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("${one(record.energyKwh)} kWh", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun formatTime(epochMillis: Long) = DateTimeFormatter.ofPattern("M月d日 HH:mm").withLocale(Locale.SIMPLIFIED_CHINESE).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epochMillis))
private fun one(value: Double) = String.format(Locale.US, "%.1f", value)
private fun two(value: Double) = String.format(Locale.US, "%.2f", value)
