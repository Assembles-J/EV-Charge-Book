package com.evchargebook.ui.records

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.domain.ChargingAnomalyRules
import com.evchargebook.domain.ChargingRecordRules
import com.evchargebook.domain.charge.ChargeEnergyCalculator
import com.evchargebook.ui.theme.spacing
import com.evchargebook.ui.theme.warningColor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private data class EditChargerTypeOption(val label: String, val icon: ImageVector)

private val EditChargerTypeOptions = listOf(
    EditChargerTypeOption("家充", Icons.Default.Home),
    EditChargerTypeOption("公共慢充", Icons.Default.EvStation),
    EditChargerTypeOption("公共快充", Icons.Default.Bolt),
    EditChargerTypeOption("超充", Icons.Default.Speed),
    EditChargerTypeOption("闪充", Icons.Default.ElectricBolt)
)

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
    val focusManager = LocalFocusManager.current
    val calendar = remember { Calendar.getInstance().apply { timeInMillis = record.chargeTimeEpochMillis } }
    var chargeTime by remember { mutableLongStateOf(record.chargeTimeEpochMillis) }
    var location by remember { mutableStateOf(record.location.orEmpty()) }
    var remark by remember { mutableStateOf(record.remark.orEmpty()) }
    var energy by remember { mutableStateOf(record.energyKwh.toEditableEditNumber()) }
    var pricePerKwh by remember { mutableStateOf(record.pricePerKwh.toEditableEditNumber()) }
    var cost by remember { mutableStateOf(record.cost.toEditableEditNumber()) }
    var costEditedManually by remember { mutableStateOf(false) }
    var startSoc by remember { mutableStateOf(record.startSoc.toString()) }
    var endSoc by remember { mutableStateOf(record.endSoc.toString()) }
    var odometer by remember { mutableStateOf(record.odometerKm?.toEditableEditNumber().orEmpty()) }
    var chargerType by remember { mutableStateOf(record.chargerType ?: "公共慢充") }
    var error by remember { mutableStateOf<String?>(null) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    fun updateCalculatedCost(newEnergy: String = energy, newPrice: String = pricePerKwh) {
        if (!costEditedManually) {
            val amount = newEnergy.toDoubleOrNull()
            val price = newPrice.toDoubleOrNull()
            if (amount != null && amount > 0.0 && price != null && price >= 0.0) {
                cost = (amount * price).toEditableEditNumber()
            }
        }
    }

    val isDirty = chargeTime != record.chargeTimeEpochMillis ||
        location != record.location.orEmpty() || remark != record.remark.orEmpty() ||
        energy != record.energyKwh.toEditableEditNumber() || cost != record.cost.toEditableEditNumber() ||
        pricePerKwh != record.pricePerKwh.toEditableEditNumber() ||
        startSoc != record.startSoc.toString() || endSoc != record.endSoc.toString() ||
        odometer != record.odometerKm?.toEditableEditNumber().orEmpty() || chargerType != (record.chargerType ?: "公共慢充")

    fun requestBack() {
        if (isDirty) showDiscardConfirm = true else onBack()
    }

    BackHandler(enabled = isDirty) { showDiscardConfirm = true }

    val dateText = remember(chargeTime) { SimpleDateFormat("M月d日", Locale.SIMPLIFIED_CHINESE).format(chargeTime) }
    val timeText = remember(chargeTime) { SimpleDateFormat("HH:mm", Locale.SIMPLIFIED_CHINESE).format(chargeTime) }
    val previousOdometer = remember(records, record.id, record.vehicleId, chargeTime) {
        ChargingRecordRules.previousOdometerKm(records, record.vehicleId, chargeTime, record.id)
    }
    val odometerWarning = ChargingRecordRules.odometerWarning(previousOdometer, odometer.toDoubleOrNull())
    val energyValue = energy.toDoubleOrNull()
    val estimate = ChargeEnergyCalculator.estimate(
        batteryCapacityKwh = batteryCapacityKwh,
        startSoc = startSoc.toIntOrNull(),
        endSoc = endSoc.toIntOrNull(),
        chargedEnergyKwh = energyValue
    )
    val anomalyWarnings = ChargingAnomalyRules.evaluate(
        startSoc = startSoc.toIntOrNull(),
        endSoc = endSoc.toIntOrNull(),
        energyKwh = energyValue,
        cost = cost.toDoubleOrNull(),
        batteryCapacityKwh = batteryCapacityKwh
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("编辑充电记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("EDIT CHARGE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = ::requestBack) { Icon(Icons.Default.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg)
        ) {
            Spacer(Modifier.height(MaterialTheme.spacing.xs))

            EditSection("充电方式", "CHARGE TYPE") {
                EditChargerTypeSelector(chargerType) { chargerType = it }
            }

            EditSection("SOC 与电量", "ENERGY") {
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    NumberField(startSoc, { startSoc = it.filter(Char::isDigit) }, "起始 SOC", "%", Modifier.weight(1f), KeyboardType.Number)
                    NumberField(endSoc, { endSoc = it.filter(Char::isDigit) }, "结束 SOC", "%", Modifier.weight(1f), KeyboardType.Number)
                }
                NumberField(
                    energy,
                    {
                        energy = it
                        updateCalculatedCost(newEnergy = it)
                    },
                    "桩端 / 电表电量",
                    "kWh",
                    Modifier.fillMaxWidth(),
                    KeyboardType.Decimal
                )
                EditEnergyEstimateRow(estimate.receivedEnergyKwh, estimate.chargedEnergyKwh, estimate.lossEnergyKwh)
                if (energyValue != null && estimate.receivedEnergyKwh != null && energyValue + 0.05 < estimate.receivedEnergyKwh) {
                    Text("桩端电量低于按 SOC 估算的车辆接收电量，请检查数据。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.warningColor)
                }
                anomalyWarnings.forEach { warning ->
                    Text(warning.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.warningColor)
                }
            }

            EditSection("电价与费用", "COST") {
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    NumberField(
                        pricePerKwh,
                        {
                            pricePerKwh = it
                            updateCalculatedCost(newPrice = it)
                        },
                        "电价",
                        "元/kWh",
                        Modifier.weight(1f),
                        KeyboardType.Decimal
                    )
                    NumberField(
                        cost,
                        {
                            cost = it
                            costEditedManually = true
                        },
                        "总费用",
                        "元",
                        Modifier.weight(1f),
                        KeyboardType.Decimal
                    )
                }
                if (costEditedManually) {
                    TextButton(onClick = { costEditedManually = false; updateCalculatedCost() }, contentPadding = PaddingValues(0.dp)) {
                        Text("恢复按电价自动计算")
                    }
                }
            }

            EditSection("时间与地点", "WHEN & WHERE") {
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    OutlinedButton(
                        onClick = { DatePickerDialog(context, { _, y, m, d -> calendar.set(y, m, d); chargeTime = calendar.timeInMillis }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show() },
                        modifier = Modifier.weight(1f)
                    ) { Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(MaterialTheme.spacing.xs)); Text(dateText) }
                    OutlinedButton(
                        onClick = { TimePickerDialog(context, { _, h, minute -> calendar.set(Calendar.HOUR_OF_DAY, h); calendar.set(Calendar.MINUTE, minute); chargeTime = calendar.timeInMillis }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show() },
                        modifier = Modifier.weight(1f)
                    ) { Icon(Icons.Default.Schedule, null); Spacer(Modifier.width(MaterialTheme.spacing.xs)); Text(timeText) }
                }
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("充电地点") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })
                )
            }

            EditSection("车辆与备注", "VEHICLE CONTEXT") {
                NumberField(odometer, { odometer = it }, "当前总里程", "km", Modifier.fillMaxWidth(), KeyboardType.Decimal)
                previousOdometer?.let { Text("上一条有效里程 ${formatEditKm(it)} km", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                odometerWarning?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.warningColor) }
                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    label = { Text("备注") },
                    placeholder = { Text("可选") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                )
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    focusManager.clearFocus()
                    val start = startSoc.toIntOrNull()
                    val end = endSoc.toIntOrNull()
                    val chargedEnergy = energy.toDoubleOrNull()
                    val price = pricePerKwh.toDoubleOrNull()
                    val costValue = cost.toDoubleOrNull()
                    val odometerKm = odometer.toDoubleOrNull()
                    error = when {
                        start == null || start !in 0..100 -> "起始 SOC 需要是 0 到 100"
                        end == null || end !in 0..100 -> "结束 SOC 需要是 0 到 100"
                        end < start -> "结束 SOC 不能低于起始 SOC"
                        chargedEnergy == null || chargedEnergy <= 0 -> "桩端 / 电表电量必须大于 0"
                        price == null || price < 0 -> "电价需要是大于等于 0 的数字"
                        costValue == null || costValue < 0 -> "费用不能小于 0"
                        odometer.isNotBlank() && (odometerKm == null || odometerKm < 0) -> "里程需要是大于等于 0 的数字"
                        else -> null
                    }
                    if (error == null) {
                        onSave(
                            record.copy(
                                location = location.trim().takeIf { it.isNotEmpty() },
                                remark = remark.trim().takeIf { it.isNotEmpty() },
                                startSoc = start!!,
                                endSoc = end!!,
                                energyKwh = chargedEnergy!!,
                                cost = costValue!!,
                                chargerType = chargerType,
                                chargeTimeEpochMillis = chargeTime,
                                odometerKm = odometerKm
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("保存修改") }
            Spacer(Modifier.height(MaterialTheme.spacing.lg))
        }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("放弃未保存修改？") },
            text = { Text("这条充电记录已经被修改，返回后未保存的修改会丢失。") },
            confirmButton = { TextButton(onClick = { showDiscardConfirm = false; onBack() }) { Text("放弃", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDiscardConfirm = false }) { Text("继续编辑") } }
        )
    }
}

@Composable
private fun EditChargerTypeSelector(selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)
    ) {
        EditChargerTypeOptions.forEach { option ->
            FilterChip(
                selected = selected == option.label,
                onClick = { onSelect(option.label) },
                leadingIcon = { Icon(option.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text(option.label) }
            )
        }
    }
}

@Composable
private fun EditEnergyEstimateRow(received: Double?, charged: Double?, loss: Double?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        EditEnergyMetric("车辆接收", received?.let { "${formatEditOne(it)} kWh" } ?: "--", Modifier.weight(1f))
        EditEnergyMetric("桩端计费", charged?.let { "${formatEditOne(it)} kWh" } ?: "--", Modifier.weight(1f))
        EditEnergyMetric("估算损耗", loss?.let { "${formatEditOne(it)} kWh" } ?: "--", Modifier.weight(1f))
    }
}

@Composable
private fun EditEnergyMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EditSection(title: String, eyebrow: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    suffix: String,
    modifier: Modifier,
    keyboardType: KeyboardType
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        suffix = { Text(suffix) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
        modifier = modifier,
        singleLine = true
    )
}

private fun Double.toEditableEditNumber(): String {
    val text = String.format(Locale.US, "%.3f", this)
    return text.trimEnd('0').trimEnd('.')
}

private fun formatEditKm(value: Double) = if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)
private fun formatEditOne(value: Double) = String.format(Locale.US, "%.1f", value)
