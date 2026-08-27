package com.evchargebook.ui.vehicle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evchargebook.bluetooth.BluetoothPromptSettings
import com.evchargebook.bluetooth.PairedBluetoothDevice
import com.evchargebook.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothPromptScreen(
    settings: BluetoothPromptSettings,
    devices: List<PairedBluetoothDevice>,
    onSave: (Boolean, String?, String?) -> Unit,
    onBack: () -> Unit
) {
    val enabled = settings.enabled && settings.deviceAddress != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("车载蓝牙提示") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
        ) {
            item { BluetoothStatusCockpit(settings, enabled, onSave) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs)) {
                    Text("已配对设备", style = MaterialTheme.typography.titleMedium)
                    Text("选择车辆蓝牙；连接时 App 会提醒你确认是否开始行程。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (devices.isEmpty()) {
                item {
                    Surface(
                        Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Text(
                            "没有可用设备。请先到系统蓝牙设置完成配对，然后返回这里。",
                            modifier = Modifier.padding(MaterialTheme.spacing.md),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            items(devices, key = { it.address }) { device ->
                val selected = device.address == settings.deviceAddress
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onSave(true, device.address, device.name) },
                    shape = MaterialTheme.shapes.medium,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(device.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (selected) "当前目标 · ${device.address}" else device.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .70f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        RadioButton(selected = selected, onClick = { onSave(true, device.address, device.name) })
                    }
                }
            }
        }
    }
}

@Composable
private fun BluetoothStatusCockpit(
    settings: BluetoothPromptSettings,
    enabled: Boolean,
    onSave: (Boolean, String?, String?) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(Modifier.padding(MaterialTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(8.dp), color = if (enabled) MaterialTheme.colorScheme.inversePrimary else MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = .28f), shape = MaterialTheme.shapes.extraSmall) {}
                Spacer(Modifier.width(MaterialTheme.spacing.xs))
                Text(
                    if (enabled) "BLUETOOTH / ARMED" else "BLUETOOTH / READY",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = .60f)
                )
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = enabled,
                    onCheckedChange = { checked -> onSave(checked, settings.deviceAddress, settings.deviceName) },
                    enabled = settings.deviceAddress != null
                )
            }

            Icon(Icons.Default.Bluetooth, null, tint = MaterialTheme.colorScheme.inversePrimary)

            Text(
                settings.deviceName ?: "尚未选择车辆蓝牙",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                when {
                    settings.deviceAddress == null -> "先在下方选择一个已配对设备。"
                    enabled -> "连接到此设备后，会提醒你确认是否开始行程。"
                    else -> "设备已选择，但连接提醒当前关闭。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = .62f)
            )
            Text(
                "不会读取车辆数据，也不会自动开始行程。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = .46f)
            )
        }
    }
}
