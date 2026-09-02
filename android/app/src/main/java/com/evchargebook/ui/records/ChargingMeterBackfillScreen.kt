package com.evchargebook.ui.records

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.ChargingSessionEntity
import com.evchargebook.data.repository.BackfillChargingSessionRequest
import com.evchargebook.domain.charge.ChargeBillingField
import com.evchargebook.domain.charge.ChargeCalculationIssue
import com.evchargebook.ui.theme.spacing
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargingMeterBackfillScreen(
    session: ChargingSessionEntity,
    onBack: () -> Unit,
    onBackfill: suspend (BackfillChargingSessionRequest) -> Long,
    onCompleted: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val initialPrice = session.unitPricePerKwh?.toBackfillNumber().orEmpty()
    val initialEnergy = session.pendingMeterEnergyKwh?.toBackfillNumber().orEmpty()
    val initialCost = session.pendingTotalCost?.toBackfillNumber().orEmpty()
    var billing by remember(session.id, session.updatedAtEpochMillis) {
        mutableStateOf(
            ChargeBillingEditor.create(
                totalCostText = initialCost,
                unitPriceText = initialPrice,
                meterEnergyText = initialEnergy,
                authoritativeBillingFields = buildSet {
                    if (session.pendingTotalCost != null) add(ChargeBillingField.TOTAL_COST)
                    if (session.unitPricePerKwh != null) add(ChargeBillingField.UNIT_PRICE)
                    if (session.pendingMeterEnergyKwh != null) add(ChargeBillingField.METER_ENERGY)
                },
            )
        )
    }
    var submitError by remember(session.id) { mutableStateOf<String?>(null) }
    var submitting by remember(session.id) { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    val meterEnergy = billing.meterEnergyKwh
    val totalCost = billing.totalCost
    val meterIsConfirmed = ChargeBillingField.METER_ENERGY in billing.calculationInput.authoritativeBillingFields
    val invalidMeterText = billing.meterEnergyText.isNotBlank() && (meterEnergy == null || meterEnergy <= 0.0)
    val invalidCostText = billing.totalCostText.isNotBlank() && (totalCost == null || totalCost < 0.0)
    val invalidPriceText = billing.unitPriceText.isNotBlank() && (billing.unitPrice == null || billing.unitPrice < 0.0)
    val blockingBillingIssue = billing.issues.any {
        it == ChargeCalculationIssue.NEGATIVE_VALUE || it == ChargeCalculationIssue.BILLING_CONFLICT
    }
    val canSubmit = !submitting && meterIsConfirmed &&
        meterEnergy != null && meterEnergy > 0.0 &&
        totalCost != null && totalCost >= 0.0 &&
        !invalidMeterText && !invalidCostText && !invalidPriceText && !blockingBillingIssue

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("补充电表数据", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
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
                        listOfNotNull(session.chargerType, session.location).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "待补录充电" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${formatBackfillTime(session.startedAtEpochMillis)} → ${session.endedAtEpochMillis?.let(::formatBackfillTime) ?: "已结束"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (session.startSoc != null && session.endSoc != null) {
                        Text(
                            "SOC ${session.startSoc}% → ${session.endSoc}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(
                "家充电表可以第二天再补。只有确认真实电表电量后，这笔充电才会进入费用、电量和次数统计。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = billing.meterEnergyText,
                onValueChange = {
                    billing = ChargeBillingEditor.edit(billing, ChargeBillingField.METER_ENERGY, it)
                    submitError = null
                },
                label = { Text("电表 / 桩端实际电量") },
                suffix = { Text("kWh") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                OutlinedTextField(
                    value = billing.unitPriceText,
                    onValueChange = {
                        billing = ChargeBillingEditor.edit(billing, ChargeBillingField.UNIT_PRICE, it)
                        submitError = null
                    },
                    label = { Text("电价") },
                    suffix = { Text("元/kWh") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = billing.totalCostText,
                    onValueChange = {
                        billing = ChargeBillingEditor.edit(billing, ChargeBillingField.TOTAL_COST, it)
                        submitError = null
                    },
                    label = { Text("总费用") },
                    suffix = { Text("元") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }

            when {
                blockingBillingIssue -> Text(
                    "费用、单价和电量不一致，请确认输入。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                invalidMeterText || invalidCostText || invalidPriceText -> Text(
                    "请输入有效数字。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                meterEnergy != null && !meterIsConfirmed -> Text(
                    "当前电量是联动计算结果。请以电表/充电桩显示值为准，最后确认一次实际电量。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    val request = BackfillChargingSessionRequest(
                        sessionId = session.id,
                        meterEnergyKwh = meterEnergy!!,
                        totalCost = totalCost!!,
                        vehicleEnergyKwh = session.pendingVehicleEnergyKwh,
                    )
                    submitting = true
                    submitError = null
                    scope.launch {
                        runCatching { onBackfill(request) }
                            .onSuccess { onCompleted() }
                            .onFailure { submitError = it.message ?: "无法补充电表数据" }
                        submitting = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text(if (submitting) "正在入账…" else "补齐并写入充电账本", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(MaterialTheme.spacing.sm))
        }
    }
}

private fun formatBackfillTime(value: Long): String =
    SimpleDateFormat("M月d日 HH:mm", Locale.SIMPLIFIED_CHINESE).format(Date(value))

private fun Double.toBackfillNumber(): String {
    val text = String.format(Locale.US, "%.4f", this)
    return text.trimEnd('0').trimEnd('.')
}
