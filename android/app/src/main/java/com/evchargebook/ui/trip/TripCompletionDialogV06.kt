package com.evchargebook.ui.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.evchargebook.data.entity.TripSessionEntity
import com.evchargebook.domain.trip.TripEnergyCalculator
import com.evchargebook.ui.theme.EVDesignTokens
import com.evchargebook.ui.theme.spacing
import java.util.Locale

/**
 * Compact final confirmation after the slide-to-end gesture.
 *
 * This intentionally keeps the existing Trip stop/validation contract outside the component.
 * It only presents the already-owned Trip facts and the existing SOC-derived estimate.
 */
@Composable
fun TripCompletionDialogV06(
    activeTrip: TripSessionEntity,
    batteryCapacityKwh: Double?,
    endSocText: String,
    onEndSocChange: (String) -> Unit,
    endMileageText: String,
    onEndMileageChange: (String) -> Unit,
    errorText: String?,
    onContinue: () -> Unit,
    onSaveAndStop: () -> Unit
) {
    val accent = EVDesignTokens.Energy.green
    val configuration = LocalConfiguration.current
    val compactLayout = configuration.screenWidthDp < 380 || configuration.fontScale >= 1.3f
    val parsedEndSoc = endSocText.toIntOrNull()
    val estimate = TripEnergyCalculator.estimate(
        batteryCapacityKwh = batteryCapacityKwh,
        startSoc = activeTrip.startSoc,
        endSoc = parsedEndSoc,
        distanceMeters = activeTrip.distanceMeters
    )

    Dialog(
        onDismissRequest = onContinue,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .widthIn(max = 520.dp)
                .imePadding(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(MaterialTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "完成本次行程",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "确认结束状态后保存真实行程记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                CompletionEvidenceV06(
                    activeTrip = activeTrip,
                    compactLayout = compactLayout
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .22f))

                OutlinedTextField(
                    value = endSocText,
                    onValueChange = { onEndSocChange(it.filter(Char::isDigit).take(3)) },
                    label = { Text("结束 SOC") },
                    suffix = { Text("%") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = endMileageText,
                    onValueChange = onEndMileageChange,
                    label = { Text("结束总里程") },
                    suffix = { Text("km") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "里程已按开始值 + GPS 距离预填，可修改；留空时沿用现有自动估算逻辑。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when {
                    estimate.consumedEnergyKwh != null -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = accent.copy(alpha = .07f)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    "估算能耗",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "SOC ${activeTrip.startSoc}% → ${parsedEndSoc}% · ${String.format(Locale.US, "%.1f", estimate.consumedEnergyKwh)} kWh",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    estimate.averageConsumptionKwhPer100Km?.let {
                                        "约 ${String.format(Locale.US, "%.1f", it)} kWh/100km · 非 BMS 实测"
                                    } ?: "距离不足，暂不能计算平均能耗 · 非 BMS 实测",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    activeTrip.startSoc != null && parsedEndSoc != null && parsedEndSoc in 0..100 -> {
                        Text(
                            if (parsedEndSoc >= activeTrip.startSoc) {
                                "SOC 未下降或出现回升，本次不强行计算行驶能耗。"
                            } else {
                                "当前数据不足以形成可靠的 SOC 能耗估算。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                errorText?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                CompletionActionsV06(
                    compactLayout = compactLayout,
                    accent = accent,
                    onContinue = onContinue,
                    onSaveAndStop = onSaveAndStop
                )
            }
        }
    }
}

@Composable
private fun CompletionEvidenceV06(
    activeTrip: TripSessionEntity,
    compactLayout: Boolean
) {
    if (compactLayout) {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
            ) {
                CompletionMetric(
                    label = "GPS 距离",
                    value = formatCompletionDistance(activeTrip.distanceMeters),
                    modifier = Modifier.weight(1f)
                )
                CompletionMetric(
                    label = "开始 SOC",
                    value = activeTrip.startSoc?.let { "$it%" } ?: "--",
                    modifier = Modifier.weight(1f)
                )
            }
            CompletionMetric(
                label = "开始里程",
                value = activeTrip.startMileageKm?.let(::formatCompletionMileage) ?: "--",
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
        ) {
            CompletionMetric(
                label = "GPS 距离",
                value = formatCompletionDistance(activeTrip.distanceMeters),
                modifier = Modifier.weight(1f)
            )
            CompletionMetric(
                label = "开始 SOC",
                value = activeTrip.startSoc?.let { "$it%" } ?: "--",
                modifier = Modifier.weight(1f)
            )
            CompletionMetric(
                label = "开始里程",
                value = activeTrip.startMileageKm?.let(::formatCompletionMileage) ?: "--",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CompletionActionsV06(
    compactLayout: Boolean,
    accent: Color,
    onContinue: () -> Unit,
    onSaveAndStop: () -> Unit
) {
    if (compactLayout) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)
        ) {
            TextButton(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text("继续行驶")
            }
            Button(
                onClick = onSaveAndStop,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = Color(0xFF03120A)
                )
            ) {
                Text("保存并结束", fontWeight = FontWeight.SemiBold)
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
        ) {
            TextButton(
                onClick = onContinue,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) {
                Text("继续行驶")
            }
            Button(
                onClick = onSaveAndStop,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = Color(0xFF03120A)
                )
            ) {
                Text("保存并结束", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CompletionMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

private fun formatCompletionDistance(meters: Double): String =
    if (meters.isFinite() && meters >= 1000.0) String.format(Locale.US, "%.1f km", meters / 1000.0)
    else if (meters.isFinite() && meters >= 0.0) String.format(Locale.US, "%.0f m", meters)
    else "--"

private fun formatCompletionMileage(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)
