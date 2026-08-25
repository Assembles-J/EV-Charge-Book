package com.evchargebook.ui.records

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecordScreen(
    onBack: () -> Unit,
    onSave: (location: String?, startSoc: Int, endSoc: Int, energyKwh: Double, cost: Double) -> Unit
) {
    var location by remember { mutableStateOf("") }
    var startSoc by remember { mutableStateOf("") }
    var endSoc by remember { mutableStateOf("") }
    var energy by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("添加充电记录", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("记录本次充电", style = MaterialTheme.typography.titleMedium)
            Text("保存时间默认使用当前时间。日期选择器会在后续版本补齐。", style = MaterialTheme.typography.bodySmall)

            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("充电地点（可选）") },
                placeholder = { Text("如：自家车位、某某充电站") },
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )

            DividerText("电池状态")

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = startSoc,
                    onValueChange = { startSoc = it.filter(Char::isDigit) },
                    label = { Text("起始 SOC") },
                    suffix = { Text("%") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = endSoc,
                    onValueChange = { endSoc = it.filter(Char::isDigit) },
                    label = { Text("结束 SOC") },
                    suffix = { Text("%") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            DividerText("费用详情")

            OutlinedTextField(
                value = energy,
                onValueChange = { energy = it },
                label = { Text("充电量") },
                suffix = { Text("kWh") },
                leadingIcon = { Icon(Icons.Default.ElectricBolt, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = cost,
                onValueChange = { cost = it },
                label = { Text("总费用") },
                prefix = { Text("¥ ") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = {
                    val start = startSoc.toIntOrNull()
                    val end = endSoc.toIntOrNull()
                    val energyValue = energy.toDoubleOrNull()
                    val costValue = cost.toDoubleOrNull()
                    errorMessage = when {
                        start == null || start !in 0..100 -> "请输入 0~100 的起始 SOC"
                        end == null || end !in 0..100 -> "请输入 0~100 的结束 SOC"
                        end < start -> "结束 SOC 不能低于起始 SOC"
                        energyValue == null || energyValue <= 0 -> "充电量必须大于 0"
                        costValue == null || costValue < 0 -> "费用不能小于 0"
                        else -> null
                    }
                    if (errorMessage == null) {
                        onSave(location.trim().takeIf { it.isNotEmpty() }, start!!, end!!, energyValue!!, costValue!!)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("保存记录", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun DividerText(text: String) {
    Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.width(8.dp))
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}
