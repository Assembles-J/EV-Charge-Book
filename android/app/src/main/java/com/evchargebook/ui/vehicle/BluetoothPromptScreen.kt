package com.evchargebook.ui.vehicle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evchargebook.bluetooth.BluetoothPromptPreferences
import com.evchargebook.bluetooth.BluetoothPromptSettings
import com.evchargebook.bluetooth.PairedBluetoothDevice
import com.evchargebook.ui.theme.spacing
import kotlinx.coroutines.launch

private val BluetoothHeroBrush = Brush.linearGradient(
    listOf(Color(0xFF06100B), Color(0xFF0B2117), Color(0xFF07120D))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothPromptScreen(
    settings: BluetoothPromptSettings,
    devices: List<PairedBluetoothDevice>,
    onSave: (Boolean, String?, String?) -> Unit,
    onBack: () -> Unit
) {
    val enabled = settings.enabled && settings.deviceAddress != null
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("车载蓝牙", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("TRIP TRIGGER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg)
        ) {
            item {
                BluetoothStatusCockpit(
                    settings = settings,
                    enabled = enabled,
                    onSave = onSave,
                    onAutoStartChange = { checked ->
                        scope.launch {
                            BluetoothPromptPreferences(context.applicationContext)
                                .saveAutoStartOnConnect(checked)
                        }
                    },
                )
            }
            item {
                Column {
                    Text("已配对设备", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (settings.autoStartOnConnect) {
                            "选择车辆蓝牙；连接后将尝试自动开始行程。"
                        } else {
                            "选择车辆蓝牙，连接时提醒你确认是否开始行程。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (devices.isEmpty()) {
                item {
                    Text(
                        "没有可用设备。请先到系统蓝牙设置完成配对，然后返回这里。",
                        modifier = Modifier.padding(vertical = MaterialTheme.spacing.md),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(devices, key = { it.address }) { device ->
                BluetoothDeviceRow(device, device.address == settings.deviceAddress) {
                    onSave(true, device.address, device.name)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun BluetoothStatusCockpit(
    settings: BluetoothPromptSettings,
    enabled: Boolean,
    onSave: (Boolean, String?, String?) -> Unit,
    onAutoStartChange: (Boolean) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxWidth(), color = Color.Transparent, shape = MaterialTheme.shapes.extraLarge) {
        Column(
            Modifier.background(BluetoothHeroBrush).padding(MaterialTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(8.dp).background(
                        if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .35f),
                        CircleShape
                    )
                )
                Spacer(Modifier.width(MaterialTheme.spacing.xs))
                Text(
                    if (enabled) "BLUETOOTH / ARMED" else "BLUETOOTH / READY",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = enabled,
                    onCheckedChange = { checked -> onSave(checked, settings.deviceAddress, settings.deviceName) },
                    enabled = settings.deviceAddress != null
                )
            }

            Surface(Modifier.size(52.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .10f)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Bluetooth, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                }
            }

            Text(settings.deviceName ?: "尚未选择车辆蓝牙", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    settings.deviceAddress == null -> "先在下方选择一个已配对设备。"
                    !enabled -> "设备已选择，但蓝牙行程检测当前关闭。"
                    settings.autoStartOnConnect -> "连接到此设备后，将尝试直接开始本次行程。"
                    else -> "连接到此设备后，会提醒你确认是否开始行程。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .28f))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "蓝牙连接后自动开始行程",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (enabled) {
                            "开启后无需再次确认；已有行程或系统限制会阻止重复启动。"
                        } else {
                            "先选择车辆蓝牙并开启连接检测。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(MaterialTheme.spacing.sm))
                Switch(
                    checked = settings.autoStartOnConnect && enabled,
                    onCheckedChange = onAutoStartChange,
                    enabled = enabled,
                )
            }

            Text(
                if (settings.autoStartOnConnect && enabled) {
                    "自动开始只对当前车辆绑定的蓝牙生效；不会跨车辆启动行程。"
                } else {
                    "默认仅做连接提醒，不读取车辆数据，也不会自动开始行程。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BluetoothDeviceRow(device: PairedBluetoothDevice, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = MaterialTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(Modifier.size(40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = if (selected) .16f else .08f)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Bluetooth, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(MaterialTheme.spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(device.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(
                if (selected) "当前目标 · ${device.address}" else device.address,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}
