package com.evchargebook.ui.vehicle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.evchargebook.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleEditScreen(initialBrand: String, initialModel: String, initialBatteryCapacity: String, initialRange: String, title: String = "编辑车辆", onSave: (String, String, Double, Int) -> Unit, onBack: () -> Unit) {
    var brand by remember { mutableStateOf(initialBrand) }
    var model by remember { mutableStateOf(initialModel) }
    var battery by remember { mutableStateOf(initialBatteryCapacity) }
    var range by remember { mutableStateOf(initialRange) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg)) {
            Spacer(Modifier.height(MaterialTheme.spacing.xs))
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                Text("车辆信息", style = MaterialTheme.typography.titleMedium)
                Text("用于区分账本数据，并参与电池容量与续航相关的展示。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(brand, { brand = it }, label = { Text("品牌") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(model, { model = it }, label = { Text("车型") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                Text("电池与续航", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    OutlinedTextField(battery, { battery = it }, label = { Text("电池容量") }, suffix = { Text("kWh") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(range, { range = it.filter(Char::isDigit) }, label = { Text("标称续航") }, suffix = { Text("km") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                val capacity = battery.toDoubleOrNull(); val rangeKm = range.toIntOrNull()
                error = when {
                    brand.isBlank() -> "请输入车辆品牌"
                    model.isBlank() -> "请输入车型"
                    capacity == null || capacity <= 0 -> "电池容量需要大于 0"
                    rangeKm == null || rangeKm <= 0 -> "标称续航需要大于 0"
                    else -> null
                }
                if (error == null) onSave(brand.trim(), model.trim(), capacity!!, rangeKm!!)
            }, modifier = Modifier.fillMaxWidth()) { Text(if (title == "添加车辆") "添加车辆" else "保存车辆") }
            Spacer(Modifier.height(MaterialTheme.spacing.lg))
        }
    }
}
