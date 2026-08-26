package com.evchargebook.ui.vehicle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.evchargebook.data.entity.VehicleEntity
import com.evchargebook.ui.theme.spacing
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleScreen(vehicle: VehicleEntity?, vehicles: List<VehicleEntity>, onSelect: (Long) -> Unit, onAdd: () -> Unit, onEdit: () -> Unit, onArchive: (VehicleEntity) -> Unit, onBluetoothPrompt: () -> Unit, onExportBackup: () -> Unit, onImportBackup: () -> Unit) {
    var archiveCandidate by remember { mutableStateOf<VehicleEntity?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text("我的车辆") }) }, floatingActionButton = { ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Default.Add, null) }, text = { Text("添加车辆") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            item { Text("当前车辆决定总览、记录与统计的数据范围。归档不会删除历史充电记录。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(vehicles, key = { it.id }) { item -> VehicleRow(item, item.id == vehicle?.id, vehicles.size > 1, { onSelect(item.id) }, { if (item.id == vehicle?.id) onEdit() else onSelect(item.id) }, { archiveCandidate = item }) }
            item { HorizontalDivider(Modifier.padding(vertical = MaterialTheme.spacing.sm)); Text("车载蓝牙", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text("连接指定车载设备时提醒你主动开始行程。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); OutlinedButton(onClick = onBluetoothPrompt, modifier = Modifier.fillMaxWidth()) { Text("配置连接提示") } }
            item { HorizontalDivider(Modifier.padding(vertical = MaterialTheme.spacing.sm)); Text("本地备份", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text("备份包含所有车辆及其充电记录。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(MaterialTheme.spacing.sm)); Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) { OutlinedButton(onClick = onExportBackup, modifier = Modifier.weight(1f)) { Text("导出备份") }; OutlinedButton(onClick = onImportBackup, modifier = Modifier.weight(1f)) { Text("恢复备份") } } }
        }
    }
    archiveCandidate?.let { candidate -> AlertDialog(onDismissRequest = { archiveCandidate = null }, title = { Text("归档 ${candidate.brand} ${candidate.model}？") }, text = { Text("车辆将不再出现在切换列表中，但历史充电记录仍会保留。") }, confirmButton = { TextButton(onClick = { onArchive(candidate); archiveCandidate = null }) { Text("归档") } }, dismissButton = { TextButton(onClick = { archiveCandidate = null }) { Text("取消") } }) }
}

@Composable private fun VehicleRow(vehicle: VehicleEntity, selected: Boolean, canArchive: Boolean, onSelect: () -> Unit, onEdit: () -> Unit, onArchive: () -> Unit) {
    ElevatedCard(onClick = onSelect, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DirectionsCar, null, tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(MaterialTheme.spacing.sm)); Column(Modifier.weight(1f)) { Row(verticalAlignment = Alignment.CenterVertically) { Text("${vehicle.brand} ${vehicle.model}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); if (selected) { Spacer(Modifier.width(MaterialTheme.spacing.xs)); AssistChip(onClick = onSelect, label = { Text("当前") }) } }; Text("${one(vehicle.batteryCapacityKwh)} kWh · ${vehicle.rangeKm} km", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "编辑车辆") }; if (canArchive) IconButton(onClick = onArchive) { Icon(Icons.Default.Archive, "归档车辆") }
        }
    }
}
private fun one(value: Double) = String.format(Locale.US, "%.1f", value)
