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
import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.domain.ChargingRecordRules
import com.evchargebook.ui.theme.spacing
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordEditScreen(
    record: ChargingRecordEntity,
    records: List<ChargingRecordEntity>,
    onSave: (ChargingRecordEntity) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val calendar = remember { Calendar.getInstance().apply { timeInMillis = record.chargeTimeEpochMillis } }
    var chargeTime by remember { mutableLongStateOf(record.chargeTimeEpochMillis) }
    var location by remember { mutableStateOf(record.location.orEmpty()) }
    var remark by remember { mutableStateOf(record.remark.orEmpty()) }
    var energy by remember { mutableStateOf(record.energyKwh.toString()) }
    var cost by remember { mutableStateOf(record.cost.toString()) }
    var startSoc by remember { mutableStateOf(record.startSoc.toString()) }
    var endSoc by remember { mutableStateOf(record.endSoc.toString()) }
    var odometer by remember { mutableStateOf(record.odometerKm?.toString().orEmpty()) }
    var chargerType by remember { mutableStateOf(record.chargerType ?: "公共慢充") }
    var error by remember { mutableStateOf<String?>(null) }
    val dateText = remember(chargeTime) { SimpleDateFormat("yyyy年M月d日", Locale.SIMPLIFIED_CHINESE).format(chargeTime) }
    val timeText = remember(chargeTime) { SimpleDateFormat("HH:mm", Locale.SIMPLIFIED_CHINESE).format(chargeTime) }
    val previousOdometer = remember(records, record.id, record.vehicleId, chargeTime) {
        ChargingRecordRules.previousOdometerKm(records, record.vehicleId, chargeTime, record.id)
    }
    val odometerValue = odometer.toDoubleOrNull()
    val odometerWarning = ChargingRecordRules.odometerWarning(previousOdometer, odometerValue)

    Scaffold(topBar = { TopAppBar(title = { Text("编辑充电记录") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回记录") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(MaterialTheme.spacing.md).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
            Text("补全本次充电信息", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                OutlinedButton(onClick = { DatePickerDialog(context, { _, year, month, day -> calendar.set(year, month, day); chargeTime = calendar.timeInMillis }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show() }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(MaterialTheme.spacing.xs)); Text(dateText) }
                OutlinedButton(onClick = { TimePickerDialog(context, { _, hour, minute -> calendar.set(Calendar.HOUR_OF_DAY, hour); calendar.set(Calendar.MINUTE, minute); chargeTime = calendar.timeInMillis }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show() }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Schedule, null); Spacer(Modifier.width(MaterialTheme.spacing.xs)); Text(timeText) }
            }
            OutlinedTextField(location, { location = it }, label = { Text("充电地点") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("充电方式", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) { listOf("家充", "公共慢充", "公共快充").forEach { type -> FilterChip(selected = chargerType == type, onClick = { chargerType = type }, label = { Text(type) }) } }
            Text("车辆里程（可选）", style = MaterialTheme.typography.titleSmall)
            NumberField(odometer, { odometer = it }, "当前总里程", "km", Modifier.fillMaxWidth())
            previousOdometer?.let { Text("上一条有效里程：${formatEditKm(it)} km", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            odometerWarning?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
            Text("电池与费用", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                NumberField(startSoc, { startSoc = it }, "起始 SOC", "%", Modifier.weight(1f))
                NumberField(endSoc, { endSoc = it }, "结束 SOC", "%", Modifier.weight(1f))
            }
            NumberField(energy, { energy = it }, "充电量", "kWh", Modifier.fillMaxWidth())
            NumberField(cost, { cost = it }, "总费用", "元", Modifier.fillMaxWidth())
            OutlinedTextField(remark, { remark = it }, label = { Text("备注（可选）") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Button(onClick = {
                val start = startSoc.toIntOrNull(); val end = endSoc.toIntOrNull(); val energyValue = energy.toDoubleOrNull(); val costValue = cost.toDoubleOrNull(); val odometerKm = odometer.toDoubleOrNull()
                error = when {
                    start == null || start !in 0..100 -> "起始 SOC 需要是 0 到 100"
                    end == null || end !in 0..100 -> "结束 SOC 需要是 0 到 100"
                    end < start -> "结束 SOC 不能低于起始 SOC"
                    energyValue == null || energyValue <= 0 -> "充电量必须大于 0"
                    costValue == null || costValue < 0 -> "费用不能小于 0"
                    odometer.isNotBlank() && (odometerKm == null || odometerKm < 0) -> "里程需要是大于等于 0 的数字"
                    else -> null
                }
                if (error == null) onSave(record.copy(location = location, remark = remark, startSoc = start!!, endSoc = end!!, energyKwh = energyValue!!, cost = costValue!!, chargerType = chargerType, chargeTimeEpochMillis = chargeTime, odometerKm = odometerKm))
            }, modifier = Modifier.fillMaxWidth()) { Text("保存修改") }
        }
    }
}

@Composable private fun NumberField(value: String, onChange: (String) -> Unit, label: String, suffix: String, modifier: Modifier) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, suffix = { Text(suffix) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = modifier, singleLine = true)
}
private fun formatEditKm(value: Double) = if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)
