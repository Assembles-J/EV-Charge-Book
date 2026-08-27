package com.evchargebook.ui.records

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.domain.ChargingAnomalyRules
import com.evchargebook.domain.ChargingRecordRules
import com.evchargebook.domain.charge.ChargeDefaultResolver
import com.evchargebook.domain.charge.ChargeEnergyCalculator
import com.evchargebook.location.AndroidGeocoderAddressResolver
import com.evchargebook.location.AndroidLocationProvider
import com.evchargebook.location.LocationFix
import com.evchargebook.ui.theme.spacing
import com.evchargebook.ui.theme.warningColor
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private data class ChargerTypeOption(val label: String, val icon: ImageVector)

private val ChargerTypeOptions = listOf(
    ChargerTypeOption("家充", Icons.Default.Home),
    ChargerTypeOption("公共慢充", Icons.Default.EvStation),
    ChargerTypeOption("公共快充", Icons.Default.Bolt),
    ChargerTypeOption("超充", Icons.Default.Speed),
    ChargerTypeOption("闪充", Icons.Default.ElectricBolt)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecordScreen(
    vehicleId: Long,
    records: List<ChargingRecordEntity>,
    batteryCapacityKwh: Double? = null,
    currentSoc: Int? = null,
    currentMileageKm: Double? = null,
    commonPlaces: List<String> = emptyList(),
    onBack: () -> Unit,
    onSave: (
        location: String?, startSoc: Int, endSoc: Int, energyKwh: Double, cost: Double,
        chargerType: String, remark: String?, chargeTime: Long, odometerKm: Double?,
        latitude: Double?, longitude: Double?, locationAccuracyMeters: Double?
    ) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val locationProvider = remember(context) { AndroidLocationProvider(context.applicationContext) }
    val addressResolver = remember(context) { AndroidGeocoderAddressResolver(context.applicationContext) }
    val vehicleRecords = remember(records, vehicleId) { records.filter { it.vehicleId == vehicleId && !it.isDeleted } }
    val defaults = remember(vehicleRecords, currentSoc) {
        ChargeDefaultResolver.resolve(currentSoc = currentSoc, records = vehicleRecords)
    }
    val calendar = remember { Calendar.getInstance() }
    val initialChargeTime = remember { calendar.timeInMillis }
    val initialOdometer = remember(currentMileageKm) { currentMileageKm?.toEditableNumber().orEmpty() }

    var chargeTime by remember { mutableLongStateOf(initialChargeTime) }
    var location by remember(defaults) { mutableStateOf(defaults.location.orEmpty()) }
    var locationFix by remember { mutableStateOf<LocationFix?>(null) }
    var locating by remember { mutableStateOf(false) }
    var remark by remember { mutableStateOf("") }
    var startSoc by remember(defaults) { mutableStateOf(defaults.startSoc?.toString().orEmpty()) }
    var endSoc by remember(defaults) { mutableStateOf(defaults.endSoc.toString()) }
    var energy by remember { mutableStateOf("") }
    var chargerType by remember(defaults) { mutableStateOf(defaults.chargerType) }
    var pricePerKwh by remember(defaults) {
        mutableStateOf((defaults.pricePerKwh ?: defaultPriceForType(defaults.chargerType)).toEditableNumber())
    }
    var cost by remember { mutableStateOf("") }
    var costEditedManually by remember { mutableStateOf(false) }
    var odometer by remember(initialOdometer) { mutableStateOf(initialOdometer) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var addressMessage by remember { mutableStateOf<String?>(null) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    fun updateCalculatedCost(newEnergy: String = energy, newPrice: String = pricePerKwh) {
        if (!costEditedManually) {
            val amount = newEnergy.toDoubleOrNull()
            val price = newPrice.toDoubleOrNull()
            cost = if (amount != null && amount > 0.0 && price != null && price >= 0.0) {
                (amount * price).toEditableNumber()
            } else {
                ""
            }
        }
    }

    val isDirty = chargeTime != initialChargeTime || remark.isNotBlank() || energy.isNotBlank() ||
        odometer != initialOdometer || startSoc != defaults.startSoc?.toString().orEmpty() ||
        endSoc != defaults.endSoc.toString() || chargerType != defaults.chargerType ||
        location != defaults.location.orEmpty() || locationFix != null || costEditedManually

    fun requestBack() {
        if (isDirty) showDiscardConfirm = true else onBack()
    }

    BackHandler(enabled = isDirty) { showDiscardConfirm = true }

    fun requestCurrentLocation() {
        locating = true
        addressMessage = null
        scope.launch {
            runCatching { locationProvider.currentLocation() }
                .onSuccess { fix ->
                    locationFix = fix
                    errorMessage = null
                    val resolved = addressResolver.reverse(fix.latitude, fix.longitude)
                    if (!resolved.isNullOrBlank()) {
                        location = resolved
                        addressMessage = "已自动使用当前位置"
                    } else {
                        addressMessage = "已保存当前位置坐标；地址解析暂不可用"
                    }
                }
                .onFailure { errorMessage = it.message ?: "获取当前位置失败" }
            locating = false
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) requestCurrentLocation() else addressMessage = "未授予精确定位权限，可继续手动填写地点"
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            requestCurrentLocation()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val dateText = remember(chargeTime) { SimpleDateFormat("M月d日", Locale.SIMPLIFIED_CHINESE).format(chargeTime) }
    val timeText = remember(chargeTime) { SimpleDateFormat("HH:mm", Locale.SIMPLIFIED_CHINESE).format(chargeTime) }
    val previousOdometer = remember(records, vehicleId, chargeTime) { ChargingRecordRules.previousOdometerKm(records, vehicleId, chargeTime) }
    val odometerValue = odometer.toDoubleOrNull()
    val odometerWarning = ChargingRecordRules.odometerWarning(previousOdometer, odometerValue)
    val energyValue = energy.toDoubleOrNull()
    val startSocValue = startSoc.toIntOrNull()
    val endSocValue = endSoc.toIntOrNull()
    val estimate = ChargeEnergyCalculator.estimate(batteryCapacityKwh, startSocValue, endSocValue, energyValue)
    val anomalyWarnings = ChargingAnomalyRules.evaluate(
        startSoc = startSocValue,
        endSoc = endSocValue,
        energyKwh = energyValue,
        cost = cost.toDoubleOrNull(),
        batteryCapacityKwh = batteryCapacityKwh
    )
    val recentPresets = remember(vehicleRecords) {
        vehicleRecords.distinctBy { "${it.chargerType}|${it.location}|${it.pricePerKwh.toEditableNumber()}" }.take(4)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("记录充电", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("NEW CHARGE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

            FormSection("充电方式", "CHARGE TYPE", "按最近使用习惯自动带入，随时可以切换。") {
                ChargerTypeSelector(chargerType) { selected ->
                    chargerType = selected
                    val remembered = ChargeDefaultResolver.priceForType(vehicleRecords, selected) ?: defaultPriceForType(selected)
                    pricePerKwh = remembered.toEditableNumber()
                    updateCalculatedCost(newPrice = pricePerKwh)
                }
                if (recentPresets.isNotEmpty()) {
                    Text("最近使用", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                        recentPresets.forEach { record ->
                            AssistChip(
                                onClick = {
                                    chargerType = record.chargerType ?: ChargeDefaultResolver.FALLBACK_CHARGER_TYPE
                                    endSoc = record.endSoc.toString()
                                    pricePerKwh = record.pricePerKwh.toEditableNumber()
                                    record.location?.let { location = it }
                                    updateCalculatedCost(newPrice = pricePerKwh)
                                },
                                label = { Text("${record.chargerType ?: "充电"} · ¥${formatTwo(record.pricePerKwh)}") }
                            )
                        }
                    }
                }
            }

            FormSection("SOC 与电量", "ENERGY", "起始 SOC 和里程优先继承车辆当前状态；车辆接收电量按电池容量和 SOC 变化自动估算。") {
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    AddNumberField(startSoc, { startSoc = it.filter(Char::isDigit) }, "起始 SOC", "%", Modifier.weight(1f), KeyboardType.Number)
                    AddNumberField(endSoc, { endSoc = it.filter(Char::isDigit) }, "结束 SOC", "%", Modifier.weight(1f), KeyboardType.Number)
                }
                AddNumberField(
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
                EnergyEstimateRow(
                    receivedEnergyKwh = estimate.receivedEnergyKwh,
                    chargedEnergyKwh = estimate.chargedEnergyKwh,
                    lossEnergyKwh = estimate.lossEnergyKwh
                )
                if (energyValue != null && estimate.receivedEnergyKwh != null && energyValue + 0.05 < estimate.receivedEnergyKwh) {
                    Text("桩端电量低于按 SOC 估算的车辆接收电量，请检查 SOC 或充电量。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.warningColor)
                }
                anomalyWarnings.forEach { warning ->
                    Text(warning.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.warningColor)
                }
            }

            FormSection("电价与费用", "COST", "电价优先复用同类型最近记录；总费用默认自动计算，也允许修改。") {
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    AddNumberField(
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
                    AddNumberField(
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

            FormSection("时间与地点", "WHEN & WHERE", "默认获取当前位置，也可以选择常用地点或手动修改。") {
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
                    onValueChange = { newValue ->
                        if (newValue != location) locationFix = null
                        location = newValue
                    },
                    label = { Text("充电地点") },
                    placeholder = { Text("例如：家 / 公司地库 / 充电站") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (commonPlaces.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                        commonPlaces.take(5).forEach { place ->
                            AssistChip(onClick = { location = place; locationFix = null }, label = { Text(place) })
                        }
                    }
                }
                TextButton(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) requestCurrentLocation()
                        else locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    },
                    enabled = !locating,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.LocationOn, null)
                    Spacer(Modifier.width(MaterialTheme.spacing.xs))
                    Text(if (locating) "正在获取位置…" else "重新获取当前位置")
                }
                addressMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            FormSection("车辆与备注", "VEHICLE CONTEXT", "当前里程优先继承车辆状态；里程与备注都可以修改。") {
                AddNumberField(odometer, { odometer = it }, "当前总里程", "km", Modifier.fillMaxWidth(), KeyboardType.Decimal)
                previousOdometer?.let { Text("上一条有效里程 ${formatKm(it)} km", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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

            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Button(
                onClick = {
                    focusManager.clearFocus()
                    val start = startSoc.toIntOrNull()
                    val end = endSoc.toIntOrNull()
                    val chargedEnergy = energy.toDoubleOrNull()
                    val costValue = cost.toDoubleOrNull()
                    val odometerKm = odometer.toDoubleOrNull()
                    errorMessage = when {
                        start == null || start !in 0..100 -> "请输入 0~100 的起始 SOC"
                        end == null || end !in 0..100 -> "请输入 0~100 的结束 SOC"
                        end < start -> "结束 SOC 不能低于起始 SOC"
                        chargedEnergy == null || chargedEnergy <= 0 -> "桩端 / 电表电量必须大于 0"
                        pricePerKwh.toDoubleOrNull() == null || pricePerKwh.toDouble() < 0 -> "请输入有效电价"
                        costValue == null || costValue < 0 -> "费用不能小于 0"
                        odometer.isNotBlank() && (odometerKm == null || odometerKm < 0) -> "里程需要是大于等于 0 的数字"
                        else -> null
                    }
                    if (errorMessage == null) {
                        val fix = locationFix
                        onSave(
                            location.trim().takeIf { it.isNotEmpty() }, start!!, end!!, chargedEnergy!!, costValue!!,
                            chargerType, remark.trim().takeIf { it.isNotEmpty() }, chargeTime, odometerKm,
                            fix?.latitude, fix?.longitude, fix?.accuracyMeters?.toDouble()
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("保存充电记录") }
            Spacer(Modifier.height(MaterialTheme.spacing.lg))
        }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("放弃未保存修改？") },
            text = { Text("当前填写的充电记录还没有保存，返回后这些内容会丢失。") },
            confirmButton = { TextButton(onClick = { showDiscardConfirm = false; onBack() }) { Text("放弃", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDiscardConfirm = false }) { Text("继续填写") } }
        )
    }
}

@Composable
private fun ChargerTypeSelector(selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)
    ) {
        ChargerTypeOptions.forEach { option ->
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
private fun EnergyEstimateRow(
    receivedEnergyKwh: Double?,
    chargedEnergyKwh: Double?,
    lossEnergyKwh: Double?
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        EnergyMetric("车辆接收", receivedEnergyKwh?.let { "${formatOne(it)} kWh" } ?: "--", Modifier.weight(1f))
        EnergyMetric("桩端计费", chargedEnergyKwh?.let { "${formatOne(it)} kWh" } ?: "--", Modifier.weight(1f))
        EnergyMetric("估算损耗", lossEnergyKwh?.let { "${formatOne(it)} kWh" } ?: "--", Modifier.weight(1f))
    }
}

@Composable
private fun EnergyMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FormSection(title: String, eyebrow: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun AddNumberField(
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

private fun defaultPriceForType(type: String): Double = when (type) {
    "家充" -> 0.65
    "公共慢充" -> 1.00
    "公共快充" -> 1.40
    "超充" -> 1.80
    "闪充" -> 2.00
    else -> 1.00
}

private fun Double.toEditableNumber(): String {
    val text = String.format(Locale.US, "%.3f", this)
    return text.trimEnd('0').trimEnd('.')
}

private fun formatKm(value: Double) = if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)
private fun formatOne(value: Double) = String.format(Locale.US, "%.1f", value)
private fun formatTwo(value: Double) = String.format(Locale.US, "%.2f", value)
