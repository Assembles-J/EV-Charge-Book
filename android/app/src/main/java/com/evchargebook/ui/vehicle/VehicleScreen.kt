package com.evchargebook.ui.vehicle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evchargebook.data.database.AppDatabase
import com.evchargebook.data.entity.VehicleEntity
import com.evchargebook.ui.dashboard.HeroVehicleCard
import com.evchargebook.ui.theme.LocalAppThemeController
import com.evchargebook.ui.theme.spacing
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleScreen(
    vehicle: VehicleEntity?,
    vehicles: List<VehicleEntity>,
    currentSoc: Int? = null,
    currentMileageKm: Double? = null,
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
    val themeController = LocalAppThemeController.current
    val context = LocalContext.current
    val catalogVehicles by remember(context.applicationContext) {
        AppDatabase.getInstance(context.applicationContext)
            .vehicleCatalogDao()
            .observeAll()
    }.collectAsState(initial = emptyList())
    val heroArtworkKey = remember(vehicle, catalogVehicles) {
        ManagedVehicleCatalogResolver.resolveHeroArtworkKey(vehicle, catalogVehicles)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("车辆", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("MY EV GARAGE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = { IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "添加车辆") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg)
        ) {
            if (vehicle != null) {
                item {
                    HeroVehicleCard(
                        vehicle = vehicle,
                        currentSoc = currentSoc,
                        currentMileageKm = currentMileageKm,
                        artworkKey = heroArtworkKey,
                    )
                }
                item { VehicleSpecificationCard(vehicle) }
                item {
                    OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Edit, null)
                        Spacer(Modifier.width(MaterialTheme.spacing.xs))
                        Text("编辑车辆名称")
                    }
                }
            } else {
                item { EmptyGarage(onAdd) }
            }

            if (vehicles.size > 1) {
                item { SettingsSectionTitle("其他车辆", "VEHICLE SWITCHER") }
                items(vehicles.filter { it.id != vehicle?.id }, key = { it.id }) { item ->
                    VehicleRow(item, { onSelect(item.id) }) { archiveCandidate = item }
                }
            }

            item { SettingsSectionTitle("外观", "APPEARANCE") }
            item { ThemeSettingsRow(themeController.darkTheme, themeController.setDarkTheme) }

            item { SettingsSectionTitle("连接与数据", "CONNECTION & DATA") }
            item { SettingsRow(Icons.Default.Bluetooth, "车载蓝牙", "连接指定设备时提醒开始行程", onBluetoothPrompt) }
            item { SettingsRow(Icons.Default.UploadFile, "导出备份", "完整 JSON，可用于恢复车辆、充电记录和行程", onExportBackup) }
            item { SettingsRow(Icons.Default.TableView, "导出分析 CSV", "当前车辆充电账本，可用于 Excel / Python 分析", onExportCsv) }
            item { SettingsRow(Icons.Default.Download, "恢复备份", "从本地 JSON 备份恢复数据", onImportBackup) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    archiveCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { archiveCandidate = null },
            title = { Text("归档 ${candidate.displayName}？") },
            text = { Text("车辆将不再出现在切换列表中，但历史充电记录仍会保留。") },
            confirmButton = { TextButton(onClick = { onArchive(candidate); archiveCandidate = null }) { Text("归档") } },
            dismissButton = { TextButton(onClick = { archiveCandidate = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun VehicleSpecificationCard(vehicle: VehicleEntity) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        SettingsSectionTitle("车辆规格", "VEHICLE SPECS · READ ONLY")
        Text(
            "${vehicle.brand} ${vehicle.model} · 标准车型资料由车型库维护",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg)
            ) {
                VehicleSpecValue("电池容量", "${one(vehicle.batteryCapacityKwh)} kWh", Modifier.weight(1f))
                VehicleSpecValue("标称续航", "${vehicle.rangeKm} km", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun VehicleSpecValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ThemeSettingsRow(darkTheme: Boolean, onDarkThemeChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(Modifier.size(40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .10f)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (darkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(MaterialTheme.spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(if (darkTheme) "深色模式" else "浅色模式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text("Dark First · 选择会自动保存", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = darkTheme, onCheckedChange = onDarkThemeChange)
    }
}

@Composable
private fun EmptyGarage(onAdd: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(MaterialTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(7.dp), color = MaterialTheme.colorScheme.primary, shape = CircleShape) {}
                Spacer(Modifier.width(MaterialTheme.spacing.xs))
                Text("VEHICLE / EMPTY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Text("添加你的第一辆车", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("从后台车型库选择标准车型；车型参数只读，添加后可以修改车辆名称。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(MaterialTheme.spacing.xs))
                Text("选择车型")
            }
        }
    }
}

@Composable
private fun VehicleRow(vehicle: VehicleEntity, onSelect: () -> Unit, onArchive: () -> Unit) {
    Surface(onClick = onSelect, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
        Row(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(42.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .10f)) {
                Box(Modifier.padding(7.dp), contentAlignment = Alignment.Center) {
                    ManagedBrandLogo(catalogVehicleId = vehicle.catalogVehicleId, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(MaterialTheme.spacing.sm))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(vehicle.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${vehicle.brand} ${vehicle.model}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onSelect) { Text("切换") }
            IconButton(onClick = onArchive) { Icon(Icons.Default.Archive, "归档车辆", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String, eyebrow: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = MaterialTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(Modifier.size(40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .10f)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(MaterialTheme.spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun one(value: Double) = String.format(Locale.US, "%.1f", value)
