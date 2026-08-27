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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.domain.ChargingAnomalyRules
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
    batteryCapacityKwh: Double? = null,
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
    val dateText = remember(chargeTime) { SimpleDateFormat("M月d日", Locale.SIMPLIFIED_CHINESE).format(chargeTime) }
    val timeText = remember(chargeTime) { SimpleDateFormat("HH:mm", Locale.SIMPLIFIED_CHINESE).format(chargeTime) }
    val previousOdometer = remember(records, record.id, record.vehicleId, chargeTime) { ChargingRecordRules.previousOdometerKm(records, record.vehicleId, chargeTime, record.id) }
    val odometerWarning = ChargingRecordRules.odometerWarning(previousOdometer, odometer.toDoubleOrNull())
    val anomalyWarnings = ChargingAnomalyRules.evaluate(
        startSoc = startSoc.toIntOrNull(),
        endSoc = endSoc.toIntOrNull(),
        energyKwh = energy.toDoubleOrNull(),
        cost = cost.toDoubleOrNull(),
        batteryCapacityKwh = batteryCapacityKwh
    )

    Scaffold(topBar = { TopAppBar(title = { Text("编辑充电记录") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg)) {
            Spacer(Modifier.height(MaterialTheme.spacing.xs))
            EditChargeSummary(startSoc, endSoc, energy, cost)
            EditSection("时间与地点") {
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    OutlinedButton(onClick = { DatePickerDialog(context, { _, y, m, d -> calendar.set(y, m, d); chargeTime = calendar.timeInMillis }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show() }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(MaterialTheme.spacing.xs)); Text(dateText) }
                    OutlinedButton(onClick = { TimePickerDialog(context, { _, h, minute -> calendar.set(Calendar.HOUR_OF_DAY, h); calendar.set(Calendar.MINUTE, minute); chargeTime = calendar.timeInMillis }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show() }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Schedule, null); Spacer(Modifier.width(MaterialTheme.spacing.xs)); Text(timeText) }
                }
                OutlinedTextField(location, { location = it }, label = { Text("充电地点") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            EditSection("充电数据") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val options = listOf("家充", "公共慢充", "公共快充")
                    options.forEachIndexed { index, option -> SegmentedButton(selected = chargerType == option, onClick = { chargerType = option }, shape = SegmentedButtonDefaults.itemShape(index, options.size)) { Text(option) } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    NumberField(startSoc, { startSoc = it.filter(Char::isDigit) }, "起始 SOC", "%", Modifier.weight(1f), KeyboardType.Number)
                    NumberField(endSoc, { endSoc = it.filter(Char::isDigit) }, "结束 SOC", "%", Modifier.weight(1f), KeyboardType.Number)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    NumberField(energy, { energy = it }, "充电量", "kWh", Modifier.weight(1f), KeyboardType.Decimal)
                    NumberField(cost, { cost = it }, "费用", "元", Modifier.weight(1f), KeyboardType.Decimal)
                }
                anomalyWarnings.forEach { warning -> Text(warning.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
            }
            EditSection("车辆与备注") {
                NumberField(odometer, { odometer = it }, "当前总里程", "km", Modifier.fillMaxWidth(), KeyboardType.Decimal)
                previousOdometer?.let { Text("上一条有效里程 ${formatEditKm(it)} km", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                odometerWarning?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
                OutlinedTextField(remark, { remark = it }, label = { Text("备注") }, placeholder = { Text("可选") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
                if (error == null) onSave(record.copy(location = location.trim().takeIf { it.isNotEmpty() }, remark = remark.trim().takeIf { it.isNotEmpty() }, startSoc = start!!, endSoc = end!!, energyKwh = energyValue!!, cost = costValue!!, chargerType = chargerType, chargeTimeEpochMillis = chargeTime, odometerKm = odometerKm))
            }, modifier = Modifier.fillMaxWidth()) { Text("保存修改") }
            Spacer(Modifier.height(MaterialTheme.spacing.lg))
        }
    }
}

@Composable
private fun EditChargeSummary(startSoc: String, endSoc: String, energy: String, cost: String) {
    val start = startSoc.toIntOrNull()
    val end = endSoc.toIntOrNull()
    val energyValue = energy.toDoubleOrNull()
    val costValue = cost.toDoubleOrNull()
    val delta = if (start != null && end != null && end >= start) end - start else null
    val unitPrice = if (energyValue != null && energyValue > 0 && costValue != null) costValue / energyValue else null

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(Modifier.padding(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(7.dp), color = MaterialTheme.colorScheme.inversePrimary, shape = MaterialTheme.shapes.extraSmall) {}
                Spacer(Modifier.width(MaterialTheme.spacing.xs))
                Text("CHARGE / EDIT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = .58f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
                EditMetric("SOC", delta?.let { "+$it%" } ?: "--", Modifier.weight(1f))
                EditMetric("补能", energyValue?.let { String.format(Locale.US, "%.1f kWh", it) } ?: "--", Modifier.weight(1f))
                EditMetric("均价", unitPrice?.let { String.format(Locale.US, "¥ %.2f", it) } ?: "--", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EditMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = .48f))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.inverseOnSurface)
    }
}

@Composable private fun EditSection(title: String, content: @Composable ColumnScope.() -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) { Text(title, style = MaterialTheme.typography.titleMedium); content() } }
@Composable private fun NumberField(value: String, onChange: (String) -> Unit, label: String, suffix: String, modifier: Modifier, keyboardType: KeyboardType) { OutlinedTextField(value, onChange, label = { Text(label) }, suffix = { Text(suffix) }, keyboardOptions = KeyboardOptions(keyboardType = keyboardType), modifier = modifier, singleLine = true) }
private fun formatEditKm(value: Double) = if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)
