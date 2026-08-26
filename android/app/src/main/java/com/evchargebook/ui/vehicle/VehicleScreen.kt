package com.evchargebook.ui.vehicle

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.VehicleEntity
import com.evchargebook.ui.theme.spacing
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleScreen(
    vehicle: VehicleEntity?,
    vehicles: List<VehicleEntity>,
    onSelect: (Long) -> Unit,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
    onArchive: (VehicleEntity) -> Unit,
    onBluetoothPrompt: () -> Unit,
    onExportBackup: () -> Unit,
    onExportCsv: () -> Unit,
    onImportBackup: () -> Unit
) {
    var archiveCandidate by remember { mutableStateOf<VehicleEntity?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("车辆", style = MaterialTheme.typography.headlineSmall) },
                actions = { IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "添加车辆") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
        ) {
            item {
                Text(
                    "当前车辆",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(vehicles, key = { it.id }) { item ->
                VehicleRow(
                    vehicle = item,
                    selected = item.id == vehicle?.id,
                    canArchive = vehicles.size > 1,
                    onSelect = { onSelect(item.id) },
                    onEdit = { if (item.id == vehicle?.id) onEdit() else onSelect(item.id) },
                    onArchive = { archiveCandidate = item }
                )
            }

            item { SettingsSectionTitle("连接与数据") }
            item {
                SettingsRow(
                    icon = Icons.Default.Bluetooth,
                    title = "车载蓝牙",
                    subtitle = "连接指定设备时提醒开始行程",
                    onClick = onBluetoothPrompt
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Default.UploadFile,
                    title = "导出备份",
                    subtitle = "完整 JSON，可用于恢复车辆、充电记录和行程",
                    onClick = onExportBackup
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Default.TableView,
                    title = "导出分析 CSV",
                    subtitle = "当前车辆充电账本，可用于 Excel / Python 分析",
                    onClick = onExportCsv
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Default.Download,
                    title = "恢复备份",
                    subtitle = "从本地 JSON 备份恢复数据",
                    onClick = onImportBackup
                )
            }
        }
    }

    archiveCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { archiveCandidate = null },
            title = { Text("归档 ${candidate.brand} ${candidate.model}？") },
            text = { Text("车辆将不再出现在切换列表中，但历史充电记录仍会保留。") },
            confirmButton = {
                TextButton(onClick = { onArchive(candidate); archiveCandidate = null }) { Text("归档") }
            },
            dismissButton = { TextButton(onClick = { archiveCandidate = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun VehicleRow(
    vehicle: VehicleEntity,
    selected: Boolean,
    canArchive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${vehicle.brand} ${vehicle.model}", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(MaterialTheme.spacing.xxs))
                    Text(
                        "${one(vehicle.batteryCapacityKwh)} kWh · ${vehicle.rangeKm} km",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (selected) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text("当前", Modifier.padding(horizontal = MaterialTheme.spacing.xs, vertical = MaterialTheme.spacing.xxs), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            if (selected || canArchive) {
                Spacer(Modifier.height(MaterialTheme.spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                    if (selected) TextButton(onClick = onEdit) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(4.dp)); Text("编辑") }
                    if (canArchive) TextButton(onClick = onArchive) { Icon(Icons.Default.Archive, null); Spacer(Modifier.width(4.dp)); Text("归档") }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(MaterialTheme.spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun one(value: Double) = String.format(Locale.US, "%.1f", value)
