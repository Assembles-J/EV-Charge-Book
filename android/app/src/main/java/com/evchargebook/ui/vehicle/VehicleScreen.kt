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
import androidx.compose.ui.text.font.FontWeight
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
            vehicle?.let { current -> item { CurrentVehicleCockpit(current, onEdit) } }

            if (vehicles.size > 1) {
                item { SettingsSectionTitle("切换车辆") }
                items(vehicles.filter { it.id != vehicle?.id }, key = { it.id }) { item ->
                    VehicleRow(
                        vehicle = item,
                        canArchive = true,
                        onSelect = { onSelect(item.id) },
                        onArchive = { archiveCandidate = item }
                    )
                }
            }

            item { SettingsSectionTitle("连接与数据") }
            item { SettingsRow(Icons.Default.Bluetooth, "车载蓝牙", "连接指定设备时提醒开始行程", onBluetoothPrompt) }
            item { SettingsRow(Icons.Default.UploadFile, "导出备份", "完整 JSON，可用于恢复车辆、充电记录和行程", onExportBackup) }
            item { SettingsRow(Icons.Default.TableView, "导出分析 CSV", "当前车辆充电账本，可用于 Excel / Python 分析", onExportCsv) }
            item { SettingsRow(Icons.Default.Download, "恢复备份", "从本地 JSON 备份恢复数据", onImportBackup) }
        }
    }

    archiveCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { archiveCandidate = null },
            title = { Text("归档 ${candidate.brand} ${candidate.model}？") },
            text = { Text("车辆将不再出现在切换列表中，但历史充电记录仍会保留。") },
            confirmButton = { TextButton(onClick = { onArchive(candidate); archiveCandidate = null }) { Text("归档") } },
            dismissButton = { TextButton(onClick = { archiveCandidate = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun CurrentVehicleCockpit(vehicle: VehicleEntity, onEdit: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(Modifier.padding(MaterialTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(8.dp), color = MaterialTheme.colorScheme.inversePrimary, shape = MaterialTheme.shapes.extraSmall) {}
                Spacer(Modifier.width(MaterialTheme.spacing.xs))
                Text("VEHICLE / ACTIVE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = .60f))
            }

            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs)) {
                Text("${vehicle.brand} ${vehicle.model}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("当前车辆", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = .58f))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = .12f))

            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg)) {
                VehicleMetric("电池", "${one(vehicle.batteryCapacityKwh)} kWh", Modifier.weight(1f))
                VehicleMetric("标称续航", "${vehicle.rangeKm} km", Modifier.weight(1f))
            }

            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.inverseOnSurface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = .22f))
            ) {
                Icon(Icons.Default.Edit, null)
                Spacer(Modifier.width(MaterialTheme.spacing.xs))
                Text("编辑当前车辆")
            }
        }
    }
}

@Composable
private fun VehicleMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = .52f))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.inverseOnSurface)
    }
}

@Composable
private fun VehicleRow(vehicle: VehicleEntity, canArchive: Boolean, onSelect: () -> Unit, onArchive: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${vehicle.brand} ${vehicle.model}", style = MaterialTheme.typography.titleMedium)
                Text("${one(vehicle.batteryCapacityKwh)} kWh · ${vehicle.rangeKm} km", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onSelect) { Text("切换") }
            if (canArchive) IconButton(onClick = onArchive) { Icon(Icons.Default.Archive, "归档车辆") }
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
        Row(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
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
