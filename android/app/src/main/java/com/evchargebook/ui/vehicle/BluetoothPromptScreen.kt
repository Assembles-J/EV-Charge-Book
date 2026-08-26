package com.evchargebook.ui.vehicle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.evchargebook.bluetooth.BluetoothPromptSettings
import com.evchargebook.bluetooth.PairedBluetoothDevice
import com.evchargebook.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothPromptScreen(settings: BluetoothPromptSettings, devices: List<PairedBluetoothDevice>, onSave: (Boolean, String?, String?) -> Unit, onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("车载蓝牙提示") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            item { Text("仅在你指定的已配对设备连接时提醒你。不会扫描附近设备、读取车辆数据或自动开始行程。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { SwitchPreference("启用连接提示", settings.enabled && settings.deviceAddress != null) { enabled -> onSave(enabled, settings.deviceAddress, settings.deviceName) } }
            item { Text("选择已配对设备", style = MaterialTheme.typography.titleMedium) }
            if (devices.isEmpty()) item { Text("没有可用的已配对设备。请先在系统蓝牙设置中完成配对。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(devices, key = { it.address }) { device ->
                ListItem(headlineContent = { Text(device.name) }, supportingContent = { Text(device.address) }, trailingContent = { RadioButton(selected = device.address == settings.deviceAddress, onClick = { onSave(true, device.address, device.name) }) }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable private fun SwitchPreference(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, modifier = Modifier.weight(1f)); Switch(checked = checked, onCheckedChange = onCheckedChange) } }
