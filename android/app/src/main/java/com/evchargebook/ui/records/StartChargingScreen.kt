package com.evchargebook.ui.records

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.evchargebook.data.database.AppDatabase
import com.evchargebook.data.entity.ChargingSessionEntity
import com.evchargebook.data.entity.VehicleEntity
import com.evchargebook.data.repository.StartChargingSessionRequest
import com.evchargebook.location.AndroidGeocoderAddressResolver
import com.evchargebook.location.AndroidLocationProvider
import com.evchargebook.ui.theme.spacing
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val ActiveChargingTypes = listOf("家充", "公共慢充", "公共快充", "超充", "闪充")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartChargingScreen(
    vehicle: VehicleEntity,
    currentSoc: Int?,
    commonPlaces: List<String>,
    existingSession: ChargingSessionEntity? = null,
    onBack: () -> Unit,
    onStart: (StartChargingSessionRequest) -> Unit,
    onUpdate: (ChargingSessionEntity) -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val database = remember(context) { AppDatabase.getInstance(context.applicationContext) }
    val recentRecords by database.chargingRecordDao().observeForVehicle(vehicle.id).collectAsState(initial = emptyList())
    val locationProvider = remember(context) { AndroidLocationProvider(context.applicationContext) }
    val addressResolver = remember(context) { AndroidGeocoderAddressResolver(context.applicationContext) }

    val defaultStartedAt = remember { System.currentTimeMillis() }
    val initialStartedAt = existingSession?.startedAtEpochMillis ?: defaultStartedAt
    val initialLocation = existingSession?.location.orEmpty()
    val initialStartSoc = existingSession?.startSoc?.toString() ?: currentSoc?.toString().orEmpty()
    val initialTargetSoc = existingSession?.targetSoc?.toString().orEmpty()
    val initialType = existingSession?.chargerType.orEmpty()
    val initialPrice = existingSession?.unitPricePerKwh?.toEditableChargingNumber().orEmpty()

    var startedAt by remember(existingSession?.id) { mutableLongStateOf(initialStartedAt) }
    var startSoc by remember(existingSession?.id, currentSoc) { mutableStateOf(initialStartSoc) }
    var targetSoc by remember(existingSession?.id) { mutableStateOf(initialTargetSoc) }
    var chargerType by remember(existingSession?.id) { mutableStateOf(initialType) }
    var unitPrice by remember(existingSession?.id) { mutableStateOf(initialPrice) }
    var priceTouched by remember(existingSession?.id) { mutableStateOf(existingSession != null) }
    var location by remember(existingSession?.id) { mutableStateOf(initialLocation) }
    var latitude by remember(existingSession?.id) { mutableStateOf(existingSession?.latitude) }
    var longitude by remember(existingSession?.id) { mutableStateOf(existingSession?.longitude) }
    var accuracyMeters by remember(existingSession?.id) { mutableStateOf(existingSession?.locationAccuracyMeters) }
    var locating by remember { mutableStateOf(false) }
    var locationMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    val isEditing = existingSession != null
    val recentPriceChoices = remember(recentRecords, chargerType) {
        recentRecords
            .asSequence()
            .filter { it.energyKwh > 0.0 && it.cost >= 0.0 }
            .filter { chargerType.isBlank() || it.chargerType == chargerType }
            .map { it.pricePerKwh }
            .filter { it >= 0.0 }
            .distinctBy { (it * 1000).toLong() }
            .take(4)
            .toList()
    }

    LaunchedEffect(chargerType, recentRecords, priceTouched, isEditing) {
        if (!isEditing && !priceTouched && chargerType.isNotBlank()) {
            val remembered = recentRecords.firstOrNull {
                it.chargerType == chargerType && it.energyKwh > 0.0 && it.cost >= 0.0
            }?.pricePerKwh
            if (remembered != null) unitPrice = remembered.toEditableChargingNumber()
        }
    }

    val isDirty = startedAt != initialStartedAt ||
        startSoc != initialStartSoc || targetSoc != initialTargetSoc || chargerType != initialType ||
        unitPrice != initialPrice || location != initialLocation ||
        latitude != existingSession?.latitude || longitude != existingSession?.longitude ||
        accuracyMeters != existingSession?.locationAccuracyMeters

    fun clearCoordinateEvidence() {
        latitude = null
        longitude = null
        accuracyMeters = null
        locationMessage = null
    }

    fun requestBack() {
        if (isDirty) showDiscardConfirm = true else onBack()
    }

    BackHandler(enabled = isDirty) { showDiscardConfirm = true }

    fun requestCurrentLocation() {
        locating = true
        locationMessage = null
        scope.launch {
            runCatching { locationProvider.currentLocation() }
                .onSuccess { fix ->
                    latitude = fix.latitude
                    longitude = fix.longitude
                    accuracyMeters = fix.accuracyMeters.toDouble()
                    val resolved = addressResolver.reverse(fix.latitude, fix.longitude)
                    if (!resolved.isNullOrBlank()) {
                        location = resolved
                        locationMessage = "已使用当前位置"
                    } else {
                        locationMessage = "已保存当前位置坐标"
                    }
                }
                .onFailure { locationMessage = it.message ?: "暂时无法获取当前位置" }
            locating = false
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) requestCurrentLocation()
        else locationMessage = "未授予精确定位权限，可手动填写地点"
    }

    LaunchedEffect(existingSession?.id) {
        if (existingSession == null) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                requestCurrentLocation()
            } else {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    val dateText = remember(startedAt) {
        SimpleDateFormat("M月d日", Locale.SIMPLIFIED_CHINESE).format(startedAt)
    }
    val timeText = remember(startedAt) {
        SimpleDateFormat("HH:mm", Locale.SIMPLIFIED_CHINESE).format(startedAt)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEditing) "编辑充电中" else "开始充电",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = ::requestBack) { Icon(Icons.Default.ArrowBack, "返回") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
        ) {
            Spacer(Modifier.height(MaterialTheme.spacing.xs))

            CompactFieldLabel("开始时间")
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                OutlinedButton(
                    onClick = {
                        val calendar = Calendar.getInstance().apply { timeInMillis = startedAt }
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                calendar.set(Calendar.YEAR, year)
                                calendar.set(Calendar.MONTH, month)
                                calendar.set(Calendar.DAY_OF_MONTH, day)
                                startedAt = calendar.timeInMillis
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH),
                        ).show()
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.sm),
                ) {
                    Icon(Icons.Default.CalendarMonth, null)
                    Spacer(Modifier.width(6.dp))
                    Text(dateText)
                }
                OutlinedButton(
                    onClick = {
                        val calendar = Calendar.getInstance().apply { timeInMillis = startedAt }
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                calendar.set(Calendar.HOUR_OF_DAY, hour)
                                calendar.set(Calendar.MINUTE, minute)
                                calendar.set(Calendar.SECOND, 0)
                                startedAt = calendar.timeInMillis
                            },
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE),
                            true,
                        ).show()
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.sm),
                ) {
                    Icon(Icons.Default.Schedule, null)
                    Spacer(Modifier.width(6.dp))
                    Text(timeText)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                ChargingOptionalNumberField(
                    value = startSoc,
                    onChange = { startSoc = it.filter(Char::isDigit) },
                    label = "开始 SOC",
                    suffix = "%",
                    modifier = Modifier.weight(1f),
                    keyboardType = KeyboardType.Number,
                )
                ChargingOptionalNumberField(
                    value = targetSoc,
                    onChange = { targetSoc = it.filter(Char::isDigit) },
                    label = "目标 SOC",
                    suffix = "%",
                    modifier = Modifier.weight(1f),
                    keyboardType = KeyboardType.Number,
                )
            }

            CompactFieldLabel("充电方式")
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
            ) {
                ActiveChargingTypes.forEach { type ->
                    FilterChip(
                        selected = chargerType == type,
                        onClick = {
                            chargerType = type
                            priceTouched = false
                        },
                        label = { Text(type) },
                    )
                }
            }

            ChargingOptionalNumberField(
                value = unitPrice,
                onChange = {
                    unitPrice = it
                    priceTouched = true
                },
                label = "电价",
                suffix = "元/kWh",
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Decimal,
            )
            if (recentPriceChoices.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
                ) {
                    recentPriceChoices.forEach { price ->
                        val text = price.toEditableChargingNumber()
                        FilterChip(
                            selected = unitPrice == text,
                            onClick = {
                                unitPrice = text
                                priceTouched = true
                            },
                            label = { Text("¥$text") },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = location,
                onValueChange = { newValue ->
                    if (newValue != location) clearCoordinateEvidence()
                    location = newValue
                },
                label = { Text("充电地点") },
                placeholder = { Text("家 / 公司地库 / 充电站") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            if (commonPlaces.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
                ) {
                    commonPlaces.take(4).forEach { place ->
                        FilterChip(
                            selected = location == place && latitude == null,
                            onClick = {
                                clearCoordinateEvidence()
                                location = place
                            },
                            label = { Text(place) },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                TextButton(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            requestCurrentLocation()
                        } else {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    },
                    enabled = !locating,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Icon(Icons.Default.LocationOn, null)
                    Text(if (locating) " 正在定位…" else " 使用当前位置")
                }
                locationMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    focusManager.clearFocus()
                    val startValue = startSoc.takeIf { it.isNotBlank() }?.toIntOrNull()
                    val targetValue = targetSoc.takeIf { it.isNotBlank() }?.toIntOrNull()
                    val priceValue = unitPrice.takeIf { it.isNotBlank() }?.toDoubleOrNull()
                    errorMessage = when {
                        startSoc.isNotBlank() && (startValue == null || startValue !in 0..100) -> "开始 SOC 需要在 0~100 之间"
                        targetSoc.isNotBlank() && (targetValue == null || targetValue !in 0..100) -> "目标 SOC 需要在 0~100 之间"
                        startValue != null && targetValue != null && targetValue < startValue -> "目标 SOC 不能低于开始 SOC"
                        unitPrice.isNotBlank() && (priceValue == null || priceValue < 0.0) -> "请输入有效电价"
                        else -> null
                    }
                    if (errorMessage == null) {
                        val request = StartChargingSessionRequest(
                            vehicleId = vehicle.id,
                            startedAtEpochMillis = startedAt,
                            startSoc = startValue,
                            targetSoc = targetValue,
                            chargerType = chargerType.trim().takeIf { it.isNotEmpty() },
                            unitPricePerKwh = priceValue,
                            location = location.trim().takeIf { it.isNotEmpty() },
                            remark = existingSession?.remark,
                            latitude = latitude,
                            longitude = longitude,
                            locationAccuracyMeters = accuracyMeters,
                        )
                        if (existingSession == null) {
                            onStart(request)
                        } else {
                            onUpdate(
                                existingSession.copy(
                                    startedAtEpochMillis = request.startedAtEpochMillis,
                                    startSoc = request.startSoc,
                                    targetSoc = request.targetSoc,
                                    chargerType = request.chargerType,
                                    unitPricePerKwh = request.unitPricePerKwh,
                                    location = request.location,
                                    latitude = request.latitude,
                                    longitude = request.longitude,
                                    locationAccuracyMeters = request.locationAccuracyMeters,
                                )
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text(if (isEditing) "保存" else "开始充电", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(MaterialTheme.spacing.sm))
        }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("放弃未保存修改？") },
            text = { Text(if (isEditing) "当前修改还没有保存。" else "当前填写的开始充电信息还没有保存。") },
            confirmButton = {
                TextButton(onClick = { showDiscardConfirm = false; onBack() }) {
                    Text("放弃", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDiscardConfirm = false }) { Text("继续填写") } },
        )
    }
}

@Composable
private fun CompactFieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun ChargingOptionalNumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    suffix: String,
    modifier: Modifier,
    keyboardType: KeyboardType,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        suffix = { Text(suffix) },
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
    )
}

private fun Double.toEditableChargingNumber(): String {
    val text = String.format(Locale.US, "%.3f", this)
    return text.trimEnd('0').trimEnd('.')
}
