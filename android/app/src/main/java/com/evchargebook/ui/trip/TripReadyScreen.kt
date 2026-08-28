package com.evchargebook.ui.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.TripSessionEntity
import com.evchargebook.data.entity.VehicleEntity
import com.evchargebook.domain.TripValidityRules
import com.evchargebook.domain.TripValidityStatus
import com.evchargebook.ui.components.ResponsiveMetricGrid
import com.evchargebook.ui.theme.spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("行程", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("TRIP READY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg)
        ) {
            item { ReadyGpsCard() }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                    Text(
                        vehicle?.let { "${it.brand} ${it.model}" } ?: "请选择车辆",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "开始时会把当前车辆 SOC / 里程快照为本次行程起点；结束 SOC 会回写成新的车辆当前 SOC。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                ResponsiveMetricGrid(3) { index, modifier ->
                    when (index) {
                        0 -> ReadyStateMetric("当前 SOC", currentSoc?.let { "$it%" } ?: "--", modifier)
                        1 -> ReadyStateMetric("当前里程", currentMileageKm?.let { "${formatReadyMileage(it)} km" } ?: "--", modifier)
                        else -> ReadyStateMetric("轨迹", "GPS 实录", modifier)
                    }
                }
            }
            if (currentSoc == null) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Text(
                            "当前 SOC 尚未维护。本次仍可记录行程，但因为开始 SOC 未知，结束后不会虚构平均能耗；录入的结束 SOC 仍会更新车辆当前 SOC。",
                            modifier = Modifier.padding(MaterialTheme.spacing.md),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                Button(
                    onClick = onStart,
                    enabled = vehicle != null,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(MaterialTheme.spacing.xs))
                    Text("开始行程", style = MaterialTheme.typography.titleMedium)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("全部行程", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("短途只提示检查；明确空行程不会进入汇总统计", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (orderedTrips.isEmpty()) {
                item {
                    Text(
                        "完成第一段行程后，这里会显示真实距离、SOC 与能耗记录。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = MaterialTheme.spacing.md)
                    )
                }
            } else {
                items(orderedTrips, key = { it.id }) { trip ->
                    ReadyRecentTripRow(
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
private fun ReadyGpsCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
        ) {
            Box(
                modifier = Modifier.size(52.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("GPS 轨迹待开始", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "未开始前不绘制虚拟位置或路线。点击开始后才记录并展示真实定位点。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ReadyStateMetric(label: String, value: String, modifier: Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ReadyRecentTripRow(
    trip: TripSessionEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val validity = remember(trip) { TripValidityRules.assess(trip) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = MaterialTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) {
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Route, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(MaterialTheme.spacing.md))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                Text(
                    SimpleDateFormat("MM-dd HH:mm", Locale.SIMPLIFIED_CHINESE).format(Date(trip.startedAtEpochMillis)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                when (validity.status) {
                    TripValidityStatus.INVALID -> TripValidityBadge("无效", true)
                    TripValidityStatus.REVIEW -> TripValidityBadge("建议检查", false)
                    else -> Unit
                }
            }
            Text(
                "${String.format(Locale.US, "%.1f", trip.distanceMeters / 1000.0)} km · ${formatReadyDuration(trip.elapsedSeconds)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (validity.status == TripValidityStatus.INVALID) {
                Text("明确空/异常行程已从 Dashboard 与汇总统计排除，可确认后删除。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            } else if (validity.status == TripValidityStatus.REVIEW) {
                Text("距离和时长都很短，仅提示检查，不自动排除或删除。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            trip.averageConsumptionKwhPer100Km?.let {
                Text("${String.format(Locale.US, "%.1f", it)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("kWh/100km", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } ?: Text("--", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "删除行程",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun TripValidityBadge(text: String, invalid: Boolean) {
    val color = if (invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    Surface(shape = CircleShape, color = color.copy(alpha = .12f)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

private fun formatReadyMileage(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)

private fun formatReadyDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
