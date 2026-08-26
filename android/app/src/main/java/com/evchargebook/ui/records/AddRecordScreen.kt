package com.evchargebook.ui.records

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.evchargebook.ui.theme.spacing
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecordScreen(
    onBack: () -> Unit,
    onSave: (location: String?, startSoc: Int, endSoc: Int, energyKwh: Double, cost: Double, chargerType: String, remark: String?, chargeTime: Long) -> Unit
) {
    val context = LocalContext.current
    val calendar = remember { Calendar.getInstance() }
    var chargeTime by remember { mutableLongStateOf(calendar.timeInMillis) }
    var location by remember { mutableStateOf("") }
    var remark by remember { mutableStateOf("") }
    var startSoc by remember { mutableStateOf("") }
    var endSoc by remember { mutableStateOf("") }
    var energy by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var chargerType by remember { mutableStateOf("公共慢充") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val dateText = remember(chargeTime) { SimpleDateFormat("yyyy年M月d日", Locale.SIMPLIFIED_CHINESE).format(chargeTime) }
    val timeText = remember(chargeTime) { SimpleDateFormat("HH:mm", Locale.SIMPLIFIED_CHINESE).format(chargeTime) }

    Scaffold(topBar = { TopAppBar(title = { Text("添加充电记录") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(MaterialTheme.spacing.md).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
            Text("记录本次充电", style = MaterialTheme.typography.titleLarge)
            Text("每次记录都会即时更新你的月度与累计统计。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                OutlinedButton(onClick = { DatePickerDialog(context, { _, year, month, day -> calendar.set(year, month, day); chargeTime = calendar.timeInMillis }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show() }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(MaterialTheme.spacing.xs)); Text(dateText) }
                OutlinedButton(onClick = { TimePickerDialog(context, { _, hour, minute -> calendar.set(Calendar.HOUR_OF_DAY, hour); calendar.set(Calendar.MINUTE, minute); chargeTime = calendar.timeInMillis }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show() }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Schedule, null); Spacer(Modifier.width(MaterialTheme.spacing.xs)); Text(timeText) }
            }
            OutlinedTextField(location, { location = it }, label = { Text("充电地点（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("充电方式", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) { listOf("家充", "公共慢充", "公共快充").forEach { type -> FilterChip(selected = chargerType == type, onClick = { chargerType = type }, label = { Text(type) }) } }
            Text("电池与费用", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) { AddNumberField(startSoc, { startSoc = it.filter(Char::isDigit) }, "起始 SOC", "%", Modifier.weight(1f), KeyboardType.Number); AddNumberField(endSoc, { endSoc = it.filter(Char::isDigit) }, "结束 SOC", "%", Modifier.weight(1f), KeyboardType.Number) }
            AddNumberField(energy, { energy = it }, "充电量", "kWh", Modifier.fillMaxWidth(), KeyboardType.Decimal)
            AddNumberField(cost, { cost = it }, "总费用", "元", Modifier.fillMaxWidth(), KeyboardType.Decimal)
            OutlinedTextField(remark, { remark = it }, label = { Text("备注（可选）") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                val start = startSoc.toIntOrNull(); val end = endSoc.toIntOrNull(); val energyValue = energy.toDoubleOrNull(); val costValue = cost.toDoubleOrNull()
                errorMessage = when { start == null || start !in 0..100 -> "请输入 0~100 的起始 SOC"; end == null || end !in 0..100 -> "请输入 0~100 的结束 SOC"; end < start -> "结束 SOC 不能低于起始 SOC"; energyValue == null || energyValue <= 0 -> "充电量必须大于 0"; costValue == null || costValue < 0 -> "费用不能小于 0"; else -> null }
                if (errorMessage == null) onSave(location.trim().takeIf { it.isNotEmpty() }, start!!, end!!, energyValue!!, costValue!!, chargerType, remark.trim().takeIf { it.isNotEmpty() }, chargeTime)
            }, modifier = Modifier.fillMaxWidth()) { Text("保存记录") }
        }
    }
}

@Composable private fun AddNumberField(value: String, onChange: (String) -> Unit, label: String, suffix: String, modifier: Modifier, keyboardType: KeyboardType) { OutlinedTextField(value, onChange, label = { Text(label) }, suffix = { Text(suffix) }, keyboardOptions = KeyboardOptions(keyboardType = keyboardType), modifier = modifier, singleLine = true) }
