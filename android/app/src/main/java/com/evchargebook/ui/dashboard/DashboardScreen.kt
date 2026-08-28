package com.evchargebook.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.ui.theme.EVDesignTokens
import com.evchargebook.ui.theme.spacing
import com.evchargebook.viewmodel.MainUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DashboardScreen(
    state: MainUiState,
    onAddClick: () -> Unit,
    onSelectVehicle: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            horizontal = MaterialTheme.spacing.md,
            vertical = MaterialTheme.spacing.md
        ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg)
    ) {
//        item { DashboardBrandHeader() }
        item { HeroVehicleCard(state.vehicle, state.currentSoc, state.currentMileageKm) }
        item { EnergyCockpitCard(state) }
        item { RecentChargingHeader(onAddClick) }

        if (state.chargingRecords.isEmpty()) {
            item { EmptyChargingTimeline(onAddClick) }
        } else {
            items(state.chargingRecords.take(3), key = { it.id }) { record ->
                ChargingTimelineRow(record)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun DashboardBrandHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "EV Charge Book",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.size(MaterialTheme.spacing.xs))
                Text(
                    "v0.5",
                    style = MaterialTheme.typography.labelLarge,
                    color = EVDesignTokens.Energy.green
                )
            }
            Text(
                "DRIVE · CHARGE · REVIEW",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(EVDesignTokens.Energy.green.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Bolt,
                contentDescription = null,
                tint = EVDesignTokens.Energy.green,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun RecentChargingHeader(onAddClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("最近充电", style = MaterialTheme.typography.titleLarge)
            Text(
                "RECENT CHARGING",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(EVDesignTokens.Energy.green)
                .clickable(onClick = onAddClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "记录充电",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun EmptyChargingTimeline(onAddClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAddClick)
            .padding(vertical = MaterialTheme.spacing.sm),
        verticalAlignment = Alignment.Top
    ) {
        TimelineRail(isLast = true)
        Spacer(Modifier.size(MaterialTheme.spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "等待第一笔充电",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(MaterialTheme.spacing.xxs))
            Text(
                "记录后会在这里形成连续的充电时间轴。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(MaterialTheme.spacing.xs))
            Text(
                "记录第一次充电  →",
                style = MaterialTheme.typography.bodyMedium,
                color = EVDesignTokens.Energy.green
            )
        }
    }
}

@Composable
private fun ChargingTimelineRow(record: ChargingRecordEntity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.spacing.sm),
        verticalAlignment = Alignment.Top
    ) {
        TimelineRail(isLast = false)
        Spacer(Modifier.size(MaterialTheme.spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    record.location ?: "未命名充电地点",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "¥ ${two(record.cost)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(MaterialTheme.spacing.xxs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${formatTime(record.chargeTimeEpochMillis)} · ${record.chargerType ?: "未标记方式"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "+ ${one(record.energyKwh)} kWh",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EVDesignTokens.Energy.green
                )
            }
        }
    }
}

@Composable
private fun TimelineRail(isLast: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                modifier = Modifier.size(18.dp)
            )
        }
        if (!isLast) {
            Box(
                modifier = Modifier
                    .size(width = 1.dp, height = 36.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
            )
        }
    }
}

private fun formatTime(epochMillis: Long) = DateTimeFormatter.ofPattern("M月d日 HH:mm")
    .withLocale(Locale.SIMPLIFIED_CHINESE)
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(epochMillis))

private fun one(value: Double) = String.format(Locale.US, "%.1f", value)
private fun two(value: Double) = String.format(Locale.US, "%.2f", value)
