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
import com.evchargebook.data.repository.DeferChargingCompletionRequest
import com.evchargebook.ui.theme.spacing
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingChargingDetailsScreen(
    session: ChargingSessionEntity,
    onBack: () -> Unit,
    onSave: suspend (DeferChargingCompletionRequest) -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val startSoc = session.startSoc

    var endSoc by remember(session.id, session.updatedAtEpochMillis) {
        mutableStateOf(session.endSoc?.toString().orEmpty())
    }
    var endedAt by remember(session.id, session.updatedAtEpochMillis) {
        mutableLongStateOf(session.endedAtEpochMillis ?: System.currentTimeMillis())
    }
    var location by remember(session.id, session.updatedAtEpochMillis) {
        mutableStateOf(session.location.orEmpty())
    }
    var latitude by remember(session.id, session.updatedAtEpochMillis) { mutableStateOf(session.latitude) }
    var longitude by remember(session.id, session.updatedAtEpochMillis) { mutableStateOf(session.longitude) }
    var locationAccuracyMeters by remember(session.id, session.updatedAtEpochMillis) {
        mutableStateOf(session.locationAccuracyMeters)
    }
    var odometer by remember(session.id, session.updatedAtEpochMillis) {
        mutableStateOf(session.odometerKm?.toPendingDetailsNumber().orEmpty())
    }
    var submitError by remember(session.id) { mutableStateOf<String?>(null) }
    var submitting by remember(session.id) { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    val endSocValue = endSoc.toIntOrNull()
    val odometerValue = odometer.takeIf { it.isNotBlank() }?.toDoubleOrNull()
    val invalidOdometer = odometer.isNotBlank() && (odometerValue == null || odometerValue < 0.0)
    val validEndSoc = startSoc != null && endSocValue != null && endSocValue in startSoc..100
    val validEndTime = endedAt > session.startedAtEpochMillis
    val canSave = !submitting && validEndSoc && validEndTime && !invalidOdometer

    val dateText = remember(endedAt) {
        SimpleDateFormat("M月d日", Locale.SIMPLIFIED_CHINESE).format(Date(endedAt))
    }
    val timeText = remember(endedAt) {
        SimpleDateFormat("HH:mm", Locale.SIMPLIFIED_CHINESE).format(Date(endedAt))
    }

    fun openDatePicker() {
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

    fun openTimePicker() {
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
                    Text(
                        "修改结束信息",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
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
                        ).joinToString(" · ").ifBlank { "待补录充电" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        buildString {
                            append("开始 ${formatPendingDetailsTime(session.startedAtEpochMillis)}")
                            startSoc?.let { append(" · SOC $it%") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                "这里只修正已经结束的充电事实，不会修改待补录的电表电量、费用或电价。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                OutlinedButton(
                    onClick = ::openDatePicker,
                    modifier = Modifier.weight(1f),
                ) { Text(dateText) }
                OutlinedButton(
                    onClick = ::openTimePicker,
                    modifier = Modifier.weight(1f),
                ) { Text(timeText) }
            }
            if (!validEndTime) {
                Text(
                    "结束时间必须晚于开始时间",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            OutlinedTextField(
                value = endSoc,
                onValueChange = {
                    endSoc = it.filter(Char::isDigit)
                    submitError = null
                },
                label = { Text("结束 SOC") },
                suffix = { Text("%") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = endSoc.isNotBlank() && !validEndSoc,
                supportingText = if (startSoc != null && endSoc.isNotBlank() && !validEndSoc) {
                    { Text("结束 SOC 需在 $startSoc%–100% 之间") }
                } else {
                    null
                },
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

            OutlinedTextField(
                value = odometer,
                onValueChange = {
                    odometer = it
                    submitError = null
                },
                label = { Text("结束里程（可选）") },
                suffix = { Text("km") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = invalidOdometer,
                supportingText = if (invalidOdometer) {
                    { Text("请输入不小于 0 的里程") }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (startSoc == null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "这条待补录记录缺少开始 SOC，无法安全修改结束信息。",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(MaterialTheme.spacing.sm),
                    )
                }
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
                enabled = canSave,
                onClick = {
                    val request = DeferChargingCompletionRequest(
                        sessionId = session.id,
                        startSoc = startSoc!!,
                        endSoc = endSocValue!!,
                        endedAtEpochMillis = endedAt,
                        odometerKm = odometerValue,
                        meterEnergyKwh = session.pendingMeterEnergyKwh,
                        totalCost = session.pendingTotalCost,
                        vehicleEnergyKwh = session.pendingVehicleEnergyKwh,
                        location = location.trim().takeIf { it.isNotEmpty() },
                        remark = session.remark,
                        latitude = latitude,
                        longitude = longitude,
                        locationAccuracyMeters = locationAccuracyMeters,
                    )
                    submitting = true
                    submitError = null
                    scope.launch {
                        runCatching { onSave(request) }
                            .onSuccess { onSaved() }
                            .onFailure { submitError = it.message ?: "无法保存结束信息" }
                        submitting = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text(
                    if (submitting) "正在保存…" else "保存结束信息",
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(MaterialTheme.spacing.sm))
        }
    }
}

private fun formatPendingDetailsTime(value: Long): String =
    SimpleDateFormat("M月d日 HH:mm", Locale.SIMPLIFIED_CHINESE).format(Date(value))

private fun Double.toPendingDetailsNumber(): String {
    val text = String.format(Locale.US, "%.1f", this)
    return text.trimEnd('0').trimEnd('.')
}
