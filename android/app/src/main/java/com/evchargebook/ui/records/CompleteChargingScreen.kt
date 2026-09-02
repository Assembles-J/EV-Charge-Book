package com.evchargebook.ui.records

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.ChargingSessionEntity
import com.evchargebook.data.repository.CompleteChargingSessionRequest
import com.evchargebook.domain.charge.ChargeBillingField
import com.evchargebook.domain.charge.ChargeCalculationIssue
import com.evchargebook.ui.theme.spacing
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompleteChargingScreen(
    session: ChargingSessionEntity,
    onBack: () -> Unit,
    onComplete: suspend (CompleteChargingSessionRequest) -> Long,
    onCompleted: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val initialUnitPrice = session.unitPricePerKwh?.toEditableCompletionNumber().orEmpty()
    val initialBilling = remember(session.id, session.updatedAtEpochMillis) {
        ChargeBillingEditor.create(
            totalCostText = "",
            unitPriceText = initialUnitPrice,
            meterEnergyText = "",
            authoritativeBillingFields = if (session.unitPricePerKwh != null) {
                setOf(ChargeBillingField.UNIT_PRICE)
            } else {
                emptySet()
            },
        )
    }

    var startSoc by remember(session.id) { mutableStateOf(session.startSoc?.toString().orEmpty()) }
    var endSoc by remember(session.id) { mutableStateOf("") }
    var billing by remember(session.id, session.updatedAtEpochMillis) { mutableStateOf(initialBilling) }
    var vehicleEnergy by remember(session.id) { mutableStateOf("") }
    var odometer by remember(session.id) { mutableStateOf("") }
    var endedAt by remember(session.id) { mutableLongStateOf(System.currentTimeMillis()) }
    var chargerType by remember(session.id) { mutableStateOf(session.chargerType.orEmpty()) }
    var location by remember(session.id) { mutableStateOf(session.location.orEmpty()) }
    var remark by remember(session.id) { mutableStateOf(session.remark.orEmpty()) }
    var latitude by remember(session.id) { mutableStateOf(session.latitude) }
    var longitude by remember(session.id) { mutableStateOf(session.longitude) }
    var locationAccuracyMeters by remember(session.id) { mutableStateOf(session.locationAccuracyMeters) }
    var submitError by remember(session.id) { mutableStateOf<String?>(null) }
    var submitting by remember(session.id) { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    val startSocValue = startSoc.toIntOrNull()
    val endSocValue = endSoc.toIntOrNull()
    val meterEnergy = billing.meterEnergyKwh
    val totalCost = billing.totalCost
    val vehicleEnergyValue = vehicleEnergy.takeIf { it.isNotBlank() }?.toDoubleOrNull()
    val odometerValue = odometer.takeIf { it.isNotBlank() }?.toDoubleOrNull()
    val invalidVehicleEnergy = vehicleEnergy.isNotBlank() && vehicleEnergyValue == null
    val invalidOdometer = odometer.isNotBlank() && odometerValue == null
    val blockingBillingIssue = billing.issues.any {
        it == ChargeCalculationIssue.NEGATIVE_VALUE || it == ChargeCalculationIssue.BILLING_CONFLICT
    }
    val canSubmit = !submitting &&
        startSocValue != null && startSocValue in 0..100 &&
        endSocValue != null && endSocValue in 0..100 &&
        meterEnergy != null && meterEnergy >= 0.0 &&
        totalCost != null && totalCost >= 0.0 &&
        endedAt > session.startedAtEpochMillis &&
        !invalidVehicleEnergy && !invalidOdometer && !blockingBillingIssue &&
        (vehicleEnergyValue == null || (vehicleEnergyValue >= 0.0 && vehicleEnergyValue <= meterEnergy + 1e-6)) &&
        (odometerValue == null || odometerValue >= 0.0)

    val dateText = remember(endedAt) {
        SimpleDateFormat("yyyy年M月d日", Locale.SIMPLIFIED_CHINESE).format(Date(endedAt))
    }
    val timeText = remember(endedAt) {
        SimpleDateFormat("HH:mm", Locale.SIMPLIFIED_CHINESE).format(Date(endedAt))
    }
    val durationText = remember(endedAt, session.startedAtEpochMillis) {
        formatCompletionDuration((endedAt - session.startedAtEpochMillis).coerceAtLeast(0L))
    }

    fun chooseDate() {
        val calendar = Calendar.getInstance().apply { timeInMillis = endedAt }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val next = Calendar.getInstance().apply {
                    timeInMillis = endedAt
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                }
                endedAt = next.timeInMillis
                submitError = null
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    fun chooseTime() {
        val calendar = Calendar.getInstance().apply { timeInMillis = endedAt }
        TimePickerDialog(
            context,
            { _, hour, minute ->
                val next = Calendar.getInstance().apply {
                    timeInMillis = endedAt
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                endedAt = next.timeInMillis
                submitError = null
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true,
        ).show()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("结束充电", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("COMPLETE CHARGE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        ) {
            Spacer(Modifier.height(MaterialTheme.spacing.xs))

            CompletionSection("本次充电", "SESSION") {
                Text(
                    session.location?.takeIf { it.isNotBlank() } ?: "未记录充电地点",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "开始 ${formatCompletionTime(session.startedAtEpochMillis)} · 已记录 $durationText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val references = listOfNotNull(
                    session.startSoc?.let { "开始 SOC $it%" },
                    session.targetSoc?.let { "目标 SOC $it%（仅参考）" },
                    session.chargerType?.takeIf { it.isNotBlank() },
                )
                if (references.isNotEmpty()) {
                    Text(
                        references.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            CompletionSection("结束时间", "END TIME") {
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    OutlinedButton(onClick = ::chooseDate, modifier = Modifier.weight(1f)) { Text(dateText) }
                    OutlinedButton(onClick = ::chooseTime, modifier = Modifier.weight(1f)) { Text(timeText) }
                }
                if (endedAt <= session.startedAtEpochMillis) {
                    Text("结束时间必须晚于开始时间", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }

            CompletionSection("SOC 与电量", "ENERGY") {
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    CompletionNumberField(
                        value = startSoc,
                        onValueChange = { startSoc = it.filter(Char::isDigit); submitError = null },
                        label = "开始 SOC",
                        suffix = "%",
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Number,
                    )
                    CompletionNumberField(
                        value = endSoc,
                        onValueChange = { endSoc = it.filter(Char::isDigit); submitError = null },
                        label = "结束 SOC",
                        suffix = "%",
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Number,
                    )
                }
                CompletionNumberField(
                    value = billing.meterEnergyText,
                    onValueChange = {
                        billing = ChargeBillingEditor.edit(billing, ChargeBillingField.METER_ENERGY, it)
                        submitError = null
                    },
                    label = "桩端 / 电表电量",
                    suffix = "kWh",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardType = KeyboardType.Decimal,
                )
                CompletionNumberField(
                    value = vehicleEnergy,
                    onValueChange = { vehicleEnergy = it; submitError = null },
                    label = "车辆侧充电量（可选）",
                    suffix = "kWh",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardType = KeyboardType.Decimal,
                )
                if (vehicleEnergyValue != null && meterEnergy != null && vehicleEnergyValue > meterEnergy + 1e-6) {
                    Text("车辆侧充电量不能高于桩端 / 电表电量", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }

            CompletionSection("电价与费用", "COST") {
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    CompletionNumberField(
                        value = billing.unitPriceText,
                        onValueChange = {
                            billing = ChargeBillingEditor.edit(billing, ChargeBillingField.UNIT_PRICE, it)
                            submitError = null
                        },
                        label = "单价",
                        suffix = "元/kWh",
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Decimal,
                    )
                    CompletionNumberField(
                        value = billing.totalCostText,
                        onValueChange = {
                            billing = ChargeBillingEditor.edit(billing, ChargeBillingField.TOTAL_COST, it)
                            submitError = null
                        },
                        label = "总费用",
                        suffix = "元",
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Decimal,
                    )
                }
                if (blockingBillingIssue) {
                    Text("费用、单价和电量存在冲突，请确认其中至少两项事实。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }

            CompletionSection("补充信息", "DETAILS") {
                OutlinedTextField(
                    value = chargerType,
                    onValueChange = { chargerType = it; submitError = null },
                    label = { Text("充电方式") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { value ->
                        if (value != location) {
                            latitude = null
                            longitude = null
                            locationAccuracyMeters = null
                        }
                        location = value
                        submitError = null
                    },
                    label = { Text("充电地点") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (latitude != null && longitude != null) {
                    Text("保留开始充电时的真实定位证据；修改地点文本会清除坐标。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                CompletionNumberField(
                    value = odometer,
                    onValueChange = { odometer = it; submitError = null },
                    label = "结束里程（可选）",
                    suffix = "km",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardType = KeyboardType.Decimal,
                )
                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it; submitError = null },
                    label = { Text("备注（可选）") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            submitError?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(MaterialTheme.spacing.sm),
                    )
                }
            }

            Button(
                enabled = canSubmit,
                onClick = {
                    val request = CompleteChargingSessionRequest(
                        sessionId = session.id,
                        startSoc = startSocValue!!,
                        endSoc = endSocValue!!,
                        meterEnergyKwh = meterEnergy!!,
                        vehicleEnergyKwh = vehicleEnergyValue,
                        totalCost = totalCost!!,
                        endedAtEpochMillis = endedAt,
                        odometerKm = odometerValue,
                        chargerType = chargerType.trim().takeIf { it.isNotEmpty() },
                        location = location.trim().takeIf { it.isNotEmpty() },
                        remark = remark.trim().takeIf { it.isNotEmpty() },
                        latitude = latitude,
                        longitude = longitude,
                        locationAccuracyMeters = locationAccuracyMeters,
                    )
                    submitting = true
                    submitError = null
                    scope.launch {
                        runCatching { onComplete(request) }
                            .onSuccess { onCompleted() }
                            .onFailure { submitError = it.message ?: "无法完成充电记录" }
                        submitting = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(if (submitting) "正在保存…" else "完成并写入充电账本", fontWeight = FontWeight.SemiBold)
            }

            Text(
                "完成后会原子写入一笔历史记录并结束当前会话；重复提交不会生成第二笔记录。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(MaterialTheme.spacing.lg))
        }
    }
}

@Composable
private fun CompletionSection(
    title: String,
    eyebrow: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
        content()
    }
}

@Composable
private fun CompletionNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suffix: String,
    modifier: Modifier,
    keyboardType: KeyboardType,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        suffix = { Text(suffix) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier,
    )
}

private fun formatCompletionTime(value: Long): String =
    SimpleDateFormat("M月d日 HH:mm", Locale.SIMPLIFIED_CHINESE).format(Date(value))

private fun formatCompletionDuration(milliseconds: Long): String {
    val totalMinutes = milliseconds / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "${hours}小时${minutes}分" else "${minutes}分钟"
}

private fun Double.toEditableCompletionNumber(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()
