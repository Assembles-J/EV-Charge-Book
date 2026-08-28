package com.evchargebook.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.evchargebook.ui.theme.EVDesignTokens
import com.evchargebook.viewmodel.MainUiState
import java.util.Locale

@Composable
fun EnergyCockpitCard(
    state: MainUiState,
    onOpenRecords: () -> Unit = {}
) {
    val surfaceBrush = Brush.verticalGradient(
        listOf(Color(0xFF0D1512), Color(0xFF09100E))
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .background(surfaceBrush)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(EVDesignTokens.Energy.green.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = null,
                            tint = EVDesignTokens.Energy.green,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.size(10.dp))
                    Column {
                        Text(
                            "ENERGY FLOW",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "本月能源",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onOpenRecords)
                        .padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${state.chargingCount} 次充电",
                        style = MaterialTheme.typography.bodyMedium,
                        color = EVDesignTokens.Energy.green,
                        maxLines = 1
                    )
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "查看充电记录",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EnergyMetric(
                    label = "本月用电",
                    value = one(state.monthEnergy),
                    unit = "kWh",
                    highlightUnit = true,
                    modifier = Modifier.weight(1.15f)
                )
                EnergyDivider()
                EnergyMetric(
                    label = "本月费用",
                    value = "¥ ${two(state.monthCost)}",
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                )
                EnergyDivider()
                EnergyMetric(
                    label = "平均电价",
                    value = "¥ ${two(state.averagePrice)}",
                    unit = "/kWh",
                    compact = true,
                    modifier = Modifier
                        .weight(1.08f)
                        .padding(start = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun EnergyMetric(
    label: String,
    value: String,
    unit: String? = null,
    highlightUnit: Boolean = false,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(Modifier.size(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
            if (unit != null) {
                Spacer(Modifier.size(3.dp))
                Text(
                    unit,
                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium,
                    color = if (highlightUnit) EVDesignTokens.Energy.green else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}

@Composable
private fun EnergyDivider() {
    Box(
        Modifier
            .size(width = 1.dp, height = 52.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    )
}

private fun one(value: Double) = String.format(Locale.US, "%.1f", value)
private fun two(value: Double) = String.format(Locale.US, "%.2f", value)
