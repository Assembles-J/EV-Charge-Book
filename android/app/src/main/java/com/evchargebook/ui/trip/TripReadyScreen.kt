package com.evchargebook.ui.trip

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.TripSessionEntity
import com.evchargebook.data.entity.VehicleEntity
import com.evchargebook.ui.theme.EVDesignTokens
import com.evchargebook.ui.theme.spacing
import java.util.Locale

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun TripReadyScreen(
    vehicle: VehicleEntity?,
    currentSoc: Int?,
    currentMileageKm: Double?,
    recentTrips: List<TripSessionEntity>,
    onStart: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onDelete: (TripSessionEntity) -> Unit = {}
) {
    var deleteTarget by remember { mutableStateOf<TripSessionEntity?>(null) }
    val orderedTrips = remember(recentTrips) {
        recentTrips.sortedByDescending { it.endedAtEpochMillis ?: it.startedAtEpochMillis }
    }
    val accent = EVDesignTokens.Energy.green

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("行程", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("TRIP READY", style = MaterialTheme.typography.labelSmall, color = accent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
        ) {
            item {
                ReadyVehicleSnapshotCard(
                    vehicle = vehicle,
                    currentSoc = currentSoc,
                    currentMileageKm = currentMileageKm
                )
            }
            item { ReadyGpsCard() }

            if (currentSoc == null && vehicle != null) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Text(
                            "当前 SOC 未维护：仍可记录真实行程，但不会据此虚构行驶能耗。",
                            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                TripSlideAction(
                    label = "滑动开始行程",
                    enabled = vehicle != null,
                    onConfirmed = onStart
                )
                if (vehicle == null) {
                    Text(
                        "选择车辆后才能开始记录。",
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("全部行程", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (orderedTrips.isEmpty()) "完成第一段行程后会显示在这里" else "共 ${orderedTrips.size} 条 · 点击查看轨迹与明细",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (orderedTrips.isEmpty()) {
                item {
                    Text(
                        "暂无已完成行程。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = MaterialTheme.spacing.md)
                    )
                }
            } else {
                items(orderedTrips, key = { it.id }) { trip ->
                    TripHistoryCardV06(
                        trip = trip,
                        onClick = { onOpenDetail(trip.id) },
                        onDelete = { deleteTarget = trip }
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    deleteTarget?.let { trip ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除这条行程？") },
            text = {
                Text(
                    "行程及关联轨迹点会一并删除，删除后无法恢复。若这条行程提供了最新 SOC 或里程，车辆当前状态会根据剩余记录重新计算。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(trip)
                        deleteTarget = null
                    }
                ) {
                    Text("确认删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ReadyVehicleSnapshotCard(
    vehicle: VehicleEntity?,
    currentSoc: Int?,
    currentMileageKm: Double?
) {
    val accent = EVDesignTokens.Energy.green
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        vehicle?.let { "${it.brand} ${it.model}" } ?: "请选择车辆",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "行程起点快照",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (vehicle != null) {
                    Text("READY", style = MaterialTheme.typography.labelMedium, color = accent)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReadySnapshotMetric(
                    label = "当前 SOC",
                    value = currentSoc?.let { "$it%" } ?: "--",
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier
                        .width(1.dp)
                        .height(38.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                )
                ReadySnapshotMetric(
                    label = "当前里程",
                    value = currentMileageKm?.let { "${formatReadyMileage(it)} km" } ?: "--",
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                "开始时记录当前 SOC 与里程；不预设终点。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReadySnapshotMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = MaterialTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ReadyGpsCard() {
    val accent = EVDesignTokens.Energy.green
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(accent.copy(alpha = .10f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(21.dp)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("GPS 真实轨迹", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "开始后记录真实定位；不会预设终点或虚构路线。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatReadyMileage(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)
