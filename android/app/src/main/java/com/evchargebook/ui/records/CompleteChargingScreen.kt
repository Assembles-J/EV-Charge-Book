package com.evchargebook.ui.records

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
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
import com.evchargebook.data.repository.DeferChargingCompletionRequest
import com.evchargebook.domain.charge.ChargeBillingField
import com.evchargebook.domain.charge.ChargeCalculationEngine
import com.evchargebook.domain.charge.ChargeCalculationInput
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
    currentSoc: Int?,
    currentSocUpdatedAtEpochMillis: Long?,
    batteryCapacityKwh: Double?,
    onBack: () -> Unit,
    onComplete: suspend (CompleteChargingSessionRequest) -> Long,
    onDefer: suspend (DeferChargingCompletionRequest) -> Unit,
    onCompleted: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val initialBilling = remember(session.id, session.updatedAtEpochMillis) {
        ChargeBillingEditor.create(
            totalCostText = session.pendingTotalCost?.toEditableCompletionNumber().orEmpty(),
            unitPriceText = session.unitPricePerKwh?.toEditableCompletionNumber().orEmpty(),
            meterEnergyText = session.pendingMeterEnergyKwh?.toEditableCompletionNumber().orEmpty(),
            authoritativeBillingFields = buildSet {
                if (session.pendingTotalCost != null) add(ChargeBillingField.TOTAL_COST)
                if (session.unitPricePerKwh != null) add(ChargeBillingField.UNIT_PRICE)
                if (session.pendingMeterEnergyKwh != null) add(ChargeBillingField.METER_ENERGY)
            },
        )
    }

    var startSoc by remember(session.id) { mutableStateOf(session.startSoc?.toString().orEmpty()) }
    var endSoc by remember(session.id) { mutableStateOf(session.endSoc?.toString().orEmpty()) }
    var endSocTouched by remember(session.id) { mutableStateOf(session.endSoc != null) }
    var billing by remember(session.id, session.updatedAtEpochMillis) { mutableStateOf(initialBilling) }
    var odometer by remember(session.id) { mutableStateOf(session.odometerKm?.toEditableCompletionNumber().orEmpty()) }
    var endedAt by remember(session.id) {
        mutableLongStateOf(session.endedAtEpochMillis ?: System.currentTimeMillis())
    }
    var location by remember(session.id) { mutableStateOf(session.location.orEmpty()) }
    var latitude by remember(session.id) { mutableStateOf(session.latitude) }
    var longitude by remember(session.id) { mutableStateOf(session.longitude) }
    var locationAccuracyMeters by remember(session.id) { mutableStateOf(session.locationAccuracyMeters) }
    var submitError by remember(session.id) { mutableStateOf<String?>(null) }
    var submitting by remember(session.id) { mutableStateOf(false) }

    LaunchedEffect(currentSoc, currentSocUpdatedAtEpochMillis, session.id) {
        val knownSoc = currentSoc
        val isNewerFact = (currentSocUpdatedAtEpochMillis ?: 0L) > session.startedAtEpochMillis
        val differsFromStart = knownSoc != null && knownSoc != session.startSoc
        val notBelowStart = knownSoc != null && (session.startSoc == null || knownSoc >= session.startSoc)
        if (!endSocTouched && endSoc.isBlank() && isNewerFact && differsFromStart && notBelowStart) {
            endSoc = knownSoc.toString()
        }
    }

    BackHandler(onBack = onBack)

    val startSocValue = startSoc.toIntOrNull()
    val endSocValue = endSoc.toIntOrNull()
    val meterEnergy = billing.meterEnergyKwh
    val totalCost = billing.totalCost
    val unitPrice = billing.unitPrice
    val meterIsConfirmed =
        ChargeBillingField.METER_ENERGY in billing.calculationInput.authoritativeBillingFields
    val odometerValue = odometer.takeIf { it.isNotBlank() }?.toDoubleOrNull()

    val invalidMeterText = billing.meterEnergyText.isNotBlank() && (meterEnergy == null || meterEnergy <= 0.0)
    val invalidCostText = billing.totalCostText.isNotBlank() && (totalCost == null || totalCost < 0.0)
    val invalidPriceText = billing.unitPriceText.isNotBlank() && (unitPrice == null || unitPrice < 0.0)
    val invalidOdometer = odometer.isNotBlank() && (odometerValue == null || odometerValue < 0.0)
    val blockingBillingIssue = billing.issues.any {
        it == ChargeCalculationIssue.NEGATIVE_VALUE || it == ChargeCalculationIssue.BILLING_CONFLICT
    }
    val calculation = remember(billing.calculationInput, endedAt, session.startedAtEpochMillis) {
        ChargeCalculationEngine.calculate(
            billing.calculationInput.copy(
                startTimeEpochMillis = session.startedAtEpochMillis,
                endTimeEpochMillis = endedAt,
            )
        )
    }
    val invalidTiming = ChargeCalculationIssue.END_NOT_AFTER_START in calculation.issues

    val estimatedVehicleEnergy = if (
        batteryCapacityKwh != null && batteryCapacityKwh > 0.0 &&
        startSocValue != null && endSocValue != null && endSocValue >= startSocValue
    ) {
        batteryCapacityKwh * (endSocValue - startSocValue) / 100.0
    } else {
        null
    }

    val hasFinalBilling =
        meterIsConfirmed &&
        meterEnergy != null && meterEnergy > 0.0 &&
        totalCost != null && totalCost >= 0.0 &&
        !blockingBillingIssue

    val canEnd = !submitting &&
        startSocValue != null && startSocValue in 0..100 &&
        endSocValue != null && endSocValue in 0..100 && endSocValue >= startSocValue &&
        !invalidTiming &&
        !invalidMeterText && !invalidCostText && !invalidPriceText && !invalidOdometer &&
        !blockingBillingIssue

    val dateText = remember(endedAt) {
        SimpleDateFormat("M月d日", Locale.SIMPLIFIED_CHINESE).format(Date(endedAt))
    }
    val timeText = remember(endedAt) {
        SimpleDateFormat("HH:mm", Locale.SIMPLIFIED_CHINESE).format(Date(endedAt))
    }
    val durationText = calculation.durationMillis?.let(::formatCompletionDuration)

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
                title = { Text("结束充电", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } },
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
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
        ) {
            Spacer(Modifier.height(MaterialTheme.spacing.xs))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier.padding(MaterialTheme.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        listOfNotNull(
                            session.chargerType?.takeIf { it.isNotBlank() },
                            session.location?.takeIf { it.isNotBlank() },
                        ).joinToString(" · ").ifBlank { "本次充电" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "开始 ${formatCompletionTime(session.startedAtEpochMillis)} · ${durationText ?: "时长待确认"}" +
                            (session.targetSoc?.let { " · 目标 $it%" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                OutlinedButton(onClick = ::chooseDate, modifier = Modifier.weight(1f)) { Text(dateText) }
                OutlinedButton(onClick = ::chooseTime, modifier = Modifier.weight(1f)) { Text(timeText) }
            }
            if (invalidTiming) {
                Text("结束时间必须晚于开始时间", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
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
                    onValueChange = {
                        endSoc = it.filter(Char::isDigit)
                        endSocTouched = true
                        submitError = null
                    },
                    label = "结束 SOC",
                    suffix = "%",
                    modifier = Modifier.weight(1f),
                    keyboardType = KeyboardType.Number,
                )
            }

            estimatedVehicleEnergy?.let {
                Text(
                    "按 SOC 与电池容量估算，车辆约充入 ${"%.1f".format(Locale.US, it)} kWh",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            CompletionNumberField(
                value = billing.meterEnergyText,
                onValueChange = {
                    billing = ChargeBillingEditor.edit(billing, ChargeBillingField.METER_ENERGY, it)
                    submitError = null
                },
                label = "电表 / 桩端电量（可稍后补）",
                suffix = "kWh",
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Decimal,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                CompletionNumberField(
                    value = billing.unitPriceText,
                    onValueChange = {
                        billing = ChargeBillingEditor.edit(billing, ChargeBillingField.UNIT_PRICE, it)
                        submitError = null
                    },
                    label = "电价",
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

            when {
                blockingBillingIssue -> Text(
                    "费用、单价和电量不一致，请确认输入。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                invalidMeterText || invalidCostText || invalidPriceText -> Text(
                    "已填写的电表/费用信息需要是有效数字；暂时不知道可以直接留空。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                meterEnergy != null && !meterIsConfirmed -> Text(
                    "当前电量是联动计算结果，不会当作实测电表值入账；本次会先保存为待补录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                !hasFinalBilling -> Text(
                    "电表数据还没出来也可以结束充电；本次会保存为待补录，不进入费用和电量统计。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

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

            CompletionNumberField(
                value = odometer,
                onValueChange = { odometer = it; submitError = null },
                label = "结束里程（可选）",
                suffix = "km",
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Decimal,
            )

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
                enabled = canEnd,
                onClick = {
                    submitting = true
                    submitError = null
                    scope.launch {
                        val result = if (hasFinalBilling) {
                            runCatching {
                                onComplete(
                                    CompleteChargingSessionRequest(
                                        sessionId = session.id,
                                        startSoc = startSocValue!!,
                                        endSoc = endSocValue!!,
                                        meterEnergyKwh = meterEnergy!!,
                                        vehicleEnergyKwh = null,
                                        totalCost = totalCost!!,
                                        endedAtEpochMillis = endedAt,
                                        odometerKm = odometerValue,
                                        chargerType = session.chargerType,
                                        location = location.trim().takeIf { it.isNotEmpty() },
                                        remark = session.remark,
                                        latitude = latitude,
                                        longitude = longitude,
                                        locationAccuracyMeters = locationAccuracyMeters,
                                    )
                                )
                            }
                        } else {
                            runCatching {
                                onDefer(
                                    DeferChargingCompletionRequest(
                                        sessionId = session.id,
                                        startSoc = startSocValue!!,
                                        endSoc = endSocValue!!,
                                        endedAtEpochMillis = endedAt,
                                        odometerKm = odometerValue,
                                        meterEnergyKwh = meterEnergy?.takeIf { meterIsConfirmed && it > 0.0 },
                                        totalCost = totalCost?.takeIf { it >= 0.0 },
                                        vehicleEnergyKwh = null,
                                        location = location.trim().takeIf { it.isNotEmpty() },
                                        remark = session.remark,
                                        latitude = latitude,
                                        longitude = longitude,
                                        locationAccuracyMeters = locationAccuracyMeters,
                                    )
                                )
                            }
                        }
                        result
                            .onSuccess { onCompleted() }
                            .onFailure { submitError = it.message ?: "无法结束充电" }
                        submitting = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text(
                    when {
                        submitting -> "正在保存…"
                        hasFinalBilling -> "完成并写入充电账本"
                        else -> "结束充电 · 稍后补电表"
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(MaterialTheme.spacing.sm))
        }
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

private fun Double.toEditableCompletionNumber(): String {
    val text = String.format(Locale.US, "%.4f", this)
    return text.trimEnd('0').trimEnd('.')
}
