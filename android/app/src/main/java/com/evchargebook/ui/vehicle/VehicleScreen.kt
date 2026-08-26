package com.evchargebook.ui.vehicle

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.evchargebook.data.entity.VehicleEntity
import com.evchargebook.ui.theme.spacing
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun VehicleScreen(vehicle: VehicleEntity?, onEdit: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("我的车辆") }, actions = { IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "编辑车辆") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
            if (vehicle == null) Text("暂未配置车辆", style = MaterialTheme.typography.titleLarge)
            else {
                ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(MaterialTheme.spacing.lg)) { Icon(Icons.Default.DirectionsCar, null, tint = MaterialTheme.colorScheme.onPrimaryContainer); Spacer(Modifier.height(MaterialTheme.spacing.sm)); Text("${vehicle.brand} ${vehicle.model}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("你的当前座驾", color = MaterialTheme.colorScheme.onPrimaryContainer) } }
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) { VehicleMetric("电池容量", "${one(vehicle.batteryCapacityKwh)} kWh", Modifier.weight(1f)); VehicleMetric("标称续航", "${vehicle.rangeKm} km", Modifier.weight(1f)) }
                Text("车辆信息用于帮助你理解每次充电；所有数据只保存在本机。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(MaterialTheme.spacing.xs)); Text("编辑车辆资料") }
            }
        }
    }
}
@Composable private fun VehicleMetric(label: String, value: String, modifier: Modifier) { OutlinedCard(modifier) { Column(Modifier.padding(MaterialTheme.spacing.md)) { Text(label, style = MaterialTheme.typography.labelMedium); Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) } } }
private fun one(value: Double) = String.format(Locale.US, "%.1f", value)
