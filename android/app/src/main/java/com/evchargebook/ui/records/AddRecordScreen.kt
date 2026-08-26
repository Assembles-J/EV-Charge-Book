package com.evchargebook.ui.records

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.domain.ChargingRecordRules
import com.evchargebook.location.AndroidGeocoderAddressResolver
import com.evchargebook.location.AndroidLocationProvider
import com.evchargebook.location.LocationFix
import com.evchargebook.ui.theme.spacing
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecordScreen(
    vehicleId: Long,
    records: List<ChargingRecordEntity>,
    onBack: () -> Unit,
    onSave: (
        location: String?, startSoc: Int, endSoc: Int, energyKwh: Double, cost: Double,
        chargerType: String, remark: String?, chargeTime: Long, odometerKm: Double?,
        latitude: Double?, longitude: Double?, locationAccuracyMeters: Double?
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locationProvider = remember(context) { AndroidLocationProvider(context.applicationContext) }
    val addressResolver = remember(context) { AndroidGeocoderAddressResolver(context.applicationContext) }
    val calendar = remember { Calendar.getInstance() }
    var chargeTime by remember { mutableLongStateOf(calendar.timeInMillis) }
    var location by remember { mutableStateOf("") }
    var locationFix by remember { mutableStateOf<LocationFix?>(null) }
    var locating by remember { mutableStateOf(false) }
    var remark by remember { mutableStateOf("") }
    var startSoc by remember { mutableStateOf("") }
    var endSoc by remember { mutableStateOf("") }
    var energy by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var odometer by remember { mutableStateOf("") }
    var chargerType by remember { mutableStateOf("公共慢充") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var addressMessage by remember { mutableStateOf<String?>(null) }

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
                        if (location.isBlank()) location = resolved
                        addressMessage = "已解析地址：$resolved"
                    } else {
                        addressMessage = "已保存经纬度；系统地址服务暂时不可用，可手动填写地点"
                    }
                }
                .onFailure { errorMessage = it.message ?: "获取当前位置失败" }
            locating = false
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) requestCurrentLocation() else errorMessage = "未授予精确定位权限，可继续手动记录充电地点"
    }

    val dateText = remember(chargeTime) { SimpleDateFormat("yyyy年M月d日", Locale.SIMPLIFIED_CHINESE).format(chargeTime) }
    val timeText = remember(chargeTime) { SimpleDateFormat("HH:mm", Locale.SIMPLIFIED_CHINESE).format(chargeTime) }
    val previousOdometer = remember(records, vehicleId, chargeTime) { ChargingRecordRules.previousOdometerKm(records, vehicleId, chargeTime) }
    val odometerValue = odometer.toDoubleOrNull()
    val odometerWarning = ChargingRecordRules.odometerWarning(previousOdometer, odometerValue)

    Scaffold(topBar = { TopAppBar(title = { Text("添加充电记录") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(MaterialTheme.spacing.md).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
            Text("记录本次充电", style = MaterialTheme.typography.titleLarge)
            Text("每次记录都会即时更新你的月度与累计统计。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                OutlinedButton(onClick = { DatePickerDialog(context, { _, year, month, day -> calendar.set(year, month, day); chargeTime = calendar.timeInMillis }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show() }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(MaterialTheme.spacing.xs)); Text(dateText) }
                OutlinedButton(onClick = { TimePickerDialog(context, { _, hour, minute -> calendar.set(Calendar.HOUR_OF_DAY, hour); calendar.set(Calendar.MINUTE, minute); chargeTime = calendar.timeInMillis }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show() }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Schedule, null); Spacer(Modifier.width(MaterialTheme.spacing.xs)); Text(timeText) }
            }
            OutlinedTextField(location, { location = it }, label = { Text("充电地点（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedButton(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) requestCurrentLocation()
                    else locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                },
                enabled = !locating,
                modifier = Modifier.fillMaxWidth()
            ) { Icon(Icons.Default.LocationOn, null); Spacer(Modifier.width(MaterialTheme.spacing.xs)); Text(if (locating) "正在获取位置与地址…" else "使用当前位置") }
            locationFix?.let { fix ->
                Text("已记录坐标：${formatCoordinate(fix.latitude)}, ${formatCoordinate(fix.longitude)} · 精度约 ${fix.accuracyMeters.toInt()} m", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            addressMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("充电方式", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) { listOf("家充", "公共慢充", "公共快充").forEach { type -> FilterChip(selected = chargerType == type, onClick = { chargerType = type }, label = { Text(type) }) } }
            Text("车辆里程（可选）", style = MaterialTheme.typography.titleSmall)
            AddNumberField(odometer, { odometer = it }, "当前总里程", "km", Modifier.fillMaxWidth(), KeyboardType.Decimal)
            previousOdometer?.let { Text("上一条有效里程：${formatKm(it)} km", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            odometerWarning?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
            Text("电池与费用", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) { AddNumberField(startSoc, { startSoc = it.filter(Char::isDigit) }, "起始 SOC", "%", Modifier.weight(1f), KeyboardType.Number); AddNumberField(endSoc, { endSoc = it.filter(Char::isDigit) }, "结束 SOC", "%", Modifier.weight(1f), KeyboardType.Number) }
            AddNumberField(energy, { energy = it }, "充电量", "kWh", Modifier.fillMaxWidth(), KeyboardType.Decimal)
            AddNumberField(cost, { cost = it }, "总费用", "元", Modifier.fillMaxWidth(), KeyboardType.Decimal)
            OutlinedTextField(remark, { remark = it }, label = { Text("备注（可选）") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                val start = startSoc.toIntOrNull(); val end = endSoc.toIntOrNull(); val energyValue = energy.toDoubleOrNull(); val costValue = cost.toDoubleOrNull(); val odometerKm = odometer.toDoubleOrNull()
                errorMessage = when {
                    start == null || start !in 0..100 -> "请输入 0~100 的起始 SOC"
                    end == null || end !in 0..100 -> "请输入 0~100 的结束 SOC"
                    end < start -> "结束 SOC 不能低于起始 SOC"
                    energyValue == null || energyValue <= 0 -> "充电量必须大于 0"
                    costValue == null || costValue < 0 -> "费用不能小于 0"
                    odometer.isNotBlank() && (odometerKm == null || odometerKm < 0) -> "里程需要是大于等于 0 的数字"
                    else -> null
                }
                if (errorMessage == null) {
                    val fix = locationFix
                    onSave(location.trim().takeIf { it.isNotEmpty() }, start!!, end!!, energyValue!!, costValue!!, chargerType, remark.trim().takeIf { it.isNotEmpty() }, chargeTime, odometerKm, fix?.latitude, fix?.longitude, fix?.accuracyMeters?.toDouble())
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("保存记录") }
        }
    }
}

@Composable private fun AddNumberField(value: String, onChange: (String) -> Unit, label: String, suffix: String, modifier: Modifier, keyboardType: KeyboardType) { OutlinedTextField(value, onChange, label = { Text(label) }, suffix = { Text(suffix) }, keyboardOptions = KeyboardOptions(keyboardType = keyboardType), modifier = modifier, singleLine = true) }
private fun formatKm(value: Double) = if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)
private fun formatCoordinate(value: Double) = String.format(Locale.US, "%.6f", value)
