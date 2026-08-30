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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
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
import com.evchargebook.domain.TripValidityRules
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
    onSelectVehicle: (Long) -> Unit,
    onOpenTrips: () -> Unit = {},
    onOpenTripDetail: (Long) -> Unit = {},
    onOpenChargingRecords: () -> Unit = {},
    onEditChargingRecord: (ChargingRecordEntity) -> Unit = {}
) {
    val selectedVehicleId = state.vehicle?.id
    val latestCompletedTrip = state.trips
        .asSequence()
        .filter { it.vehicleId == selectedVehicleId && TripValidityRules.isEligibleForAnalytics(it) }
        .maxByOrNull { it.endedAtEpochMillis ?: it.startedAtEpochMillis }
    val dashboardTrip = state.activeTrip
        ?.takeIf { it.vehicleId == selectedVehicleId }
        ?: latestCompletedTrip

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 0.dp,
            end = 8.dp,
            bottom = 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            HeroVehicleCard(
                vehicle = state.vehicle,
                vehicles = state.vehicles,
                currentSoc = state.currentSoc,
                currentMileageKm = state.currentMileageKm,
                latestTrip = latestCompletedTrip,
                edgeToEdgeTop = true,
                vehicleSwitchEnabled = state.activeTrip == null,
                onSelectVehicle = onSelectVehicle
            )
        }
        item {
            DashboardRecentTripCard(
                trip = dashboardTrip,
                onViewAll = onOpenTrips,
                onOpenTrip = onOpenTripDetail
            )
        }
        item { EnergyCockpitCard(state, onOpenChargingRecords) }
        item { RecentChargingHeader(onOpenChargingRecords) }

        if (state.chargingRecords.isEmpty()) {
            item { EmptyChargingTimeline(onAddClick) }
        } else {
            items(state.chargingRecords.take(3), key = { it.id }) { record ->
                ChargingTimelineRow(record, onEdit = { onEditChargingRecord(record) })
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun RecentChargingHeader(onViewAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("最近充电", style = MaterialTheme.typography.titleMedium)
            Text(
                "RECENT CHARGING",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clip(CircleShape)
                .clickable(onClick = onViewAll)
                .padding(horizontal = 7.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "查看全部",
                style = MaterialTheme.typography.bodySmall,
                color = EVDesignTokens.Energy.green
            )
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "查看全部充电记录",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
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
        ChargingMarker()
        Spacer(Modifier.size(MaterialTheme.spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "等待第一笔充电",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(MaterialTheme.spacing.xxs))
            Text(
                "记录后会在这里显示最近的充电记录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(MaterialTheme.spacing.xs))
            Text(
                "记录第一次充电  →",
                style = MaterialTheme.typography.bodySmall,
                color = EVDesignTokens.Energy.green
            )
        }
    }
}

@Composable
private fun ChargingTimelineRow(
    record: ChargingRecordEntity,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = MaterialTheme.spacing.sm),
        verticalAlignment = Alignment.Top
    ) {
        ChargingMarker()
        Spacer(Modifier.size(MaterialTheme.spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    record.location ?: "未命名充电地点",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "+ ${one(record.energyKwh)} kWh",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = EVDesignTokens.Energy.green
                )
            }
            Spacer(Modifier.height(MaterialTheme.spacing.xxs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${formatTime(record.chargeTimeEpochMillis)} · ${record.chargerType ?: "未标记方式"}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "¥ ${two(record.cost)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ChargingMarker() {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(EVDesignTokens.Energy.green.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Bolt,
            contentDescription = null,
            tint = EVDesignTokens.Energy.green,
            modifier = Modifier.size(16.dp)
        )
    }
}

private fun formatTime(epochMillis: Long) = DateTimeFormatter.ofPattern("M月d日 HH:mm")
    .withLocale(Locale.SIMPLIFIED_CHINESE)
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(epochMillis))

private fun one(value: Double) = String.format(Locale.US, "%.1f", value)
private fun two(value: Double) = String.format(Locale.US, "%.2f", value)
