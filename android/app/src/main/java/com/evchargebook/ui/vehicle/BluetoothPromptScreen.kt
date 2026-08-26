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
import com.evchargebook.bluetooth.BluetoothPromptSettings
import com.evchargebook.bluetooth.PairedBluetoothDevice
import com.evchargebook.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothPromptScreen(settings: BluetoothPromptSettings, devices: List<PairedBluetoothDevice>, onSave: (Boolean, String?, String?) -> Unit, onBack: () -> Unit) {
    val enabled = settings.enabled && settings.deviceAddress != null
    Scaffold(topBar = { TopAppBar(title = { Text("车载蓝牙提示") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Bluetooth, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(MaterialTheme.spacing.sm)); Column(Modifier.weight(1f)) { Text("连接后提醒开始行程", style = MaterialTheme.typography.titleMedium); Text("只监听你选中的已配对设备，不读取车辆数据，也不会自动开始行程。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked = enabled, onCheckedChange = { checked -> onSave(checked, settings.deviceAddress, settings.deviceName) }, enabled = settings.deviceAddress != null) }
                    if (settings.deviceAddress == null) Text("先选择一个已配对设备后才能启用。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { HorizontalDivider(); Text("已配对设备", style = MaterialTheme.typography.titleMedium) }
            if (devices.isEmpty()) item { Text("没有可用设备。请先到系统蓝牙设置完成配对，然后返回这里。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(devices, key = { it.address }) { device ->
                val selected = device.address == settings.deviceAddress
                ListItem(
                    headlineContent = { Text(device.name) },
                    supportingContent = { Text(if (selected) "已选择 · ${device.address}" else device.address) },
                    trailingContent = { RadioButton(selected = selected, onClick = { onSave(true, device.address, device.name) }) },
                    modifier = Modifier.fillMaxWidth().clickable { onSave(true, device.address, device.name) }
                )
                HorizontalDivider()
            }
        }
    }
}
