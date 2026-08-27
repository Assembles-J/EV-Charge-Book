package com.evchargebook.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evchargebook.domain.MonthlyChargingBucket
import com.evchargebook.ui.theme.EVDesignTokens
import com.evchargebook.ui.theme.spacing
import com.evchargebook.viewmodel.MainUiState
import java.util.Locale

@Composable
fun EnergyCockpitCard(state: MainUiState) {
    val surfaceBrush = Brush.verticalGradient(
        listOf(Color(0xFF0D1512), Color(0xFF0A100E))
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .background(surfaceBrush)
                .padding(MaterialTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(EVDesignTokens.Energy.green.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = null,
                            tint = EVDesignTokens.Energy.green,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(Modifier.size(MaterialTheme.spacing.sm))
                    Column {
                        Text(
                            "ENERGY FLOW",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "本月能源",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Text(
                    "${state.chargingCount} 次",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EVDesignTokens.Energy.green
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        "本月用电",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            one(state.monthEnergy),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.size(MaterialTheme.spacing.xs))
                        Text(
                            "kWh",
                            style = MaterialTheme.typography.titleMedium,
                            color = EVDesignTokens.Energy.green
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "本月费用",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "¥ ${two(state.monthCost)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            MonthlyEnergyBars(state.monthlyTrend)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FlatMetric("平均电价", "¥ ${two(state.averagePrice)}/kWh")
                FlatMetric(
                    "区间电耗",
                    state.intervalEnergyPer100Km?.let { "${one(it)} kWh/100km" } ?: "--"
                )
                FlatMetric("充电次数", "${state.chargingCount} 次")
            }
        }
    }
}

@Composable
private fun MonthlyEnergyBars(months: List<MonthlyChargingBucket>) {
    val values = if (months.isEmpty()) List(6) { 0.0 } else months.takeLast(6).map { it.energyKwh }
    val max = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    val labels = if (months.isEmpty()) List(6) { "--" } else months.takeLast(6).map { "${it.month}月" }

    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
        Text(
            "近 6 个月",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            values.forEachIndexed { index, value ->
                val ratio = (value / max).toFloat().coerceIn(0f, 1f)
                val barHeight = (6 + 50 * ratio).dp
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(barHeight)
                            .background(
                                if (index == values.lastIndex) {
                                    Brush.verticalGradient(
                                        listOf(EVDesignTokens.Energy.green, EVDesignTokens.Energy.green.copy(alpha = 0.28f))
                                    )
                                } else {
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF355347), Color(0xFF18231F))
                                    )
                                },
                                MaterialTheme.shapes.small
                            )
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        labels.getOrElse(index) { "--" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun FlatMetric(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun one(value: Double) = String.format(Locale.US, "%.1f", value)
private fun two(value: Double) = String.format(Locale.US, "%.2f", value)
