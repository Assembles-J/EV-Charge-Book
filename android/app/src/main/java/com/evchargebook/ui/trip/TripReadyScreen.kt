package com.evchargebook.ui.trip

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.TripSessionEntity
import com.evchargebook.data.entity.VehicleEntity
import com.evchargebook.ui.dashboard.HeroVehicleCard
import com.evchargebook.ui.theme.EVDesignTokens
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
    var preparing by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<TripSessionEntity?>(null) }
    val orderedTrips = remember(recentTrips) {
        recentTrips.sortedByDescending { it.endedAtEpochMillis ?: it.startedAtEpochMillis }
    }
    val accent = EVDesignTokens.Energy.green

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            if (preparing) "开始行程" else "行程",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (preparing) "TRIP READY" else "TRIP",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent
                        )
                    }
                },
                navigationIcon = {
                    if (preparing) {
                        IconButton(onClick = { preparing = false }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回行程列表")
                        }
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            if (!preparing) {
                FloatingActionButton(
                    onClick = { if (vehicle != null) preparing = true },
                    containerColor = if (vehicle != null) accent else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (vehicle != null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "开始行程")
                }
            }
        }
    ) { padding ->
        if (preparing) {
            TripPreparationContentV08(
                modifier = Modifier.fillMaxSize().padding(padding),
                vehicle = vehicle,
                currentSoc = currentSoc,
                currentMileageKm = currentMileageKm,
                latestTrip = orderedTrips.firstOrNull(),
                onStart = onStart
            )
        } else {
            TripHomeContentV08(
                modifier = Modifier.fillMaxSize().padding(padding),
                orderedTrips = orderedTrips,
                canStart = vehicle != null,
                onOpenDetail = onOpenDetail,
                onDelete = { deleteTarget = it }
            )
        }
    }

    deleteTarget?.let { trip ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除这条行程？") },
            text = {
                Text("行程及关联轨迹点会一并删除，删除后无法恢复。若这条行程提供了最新 SOC 或里程，车辆当前状态会根据剩余记录重新计算。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(trip)
                        deleteTarget = null
                    }
                ) { Text("确认删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun TripHomeContentV08(
    modifier: Modifier,
    orderedTrips: List<TripSessionEntity>,
    canStart: Boolean,
    onOpenDetail: (Long) -> Unit,
    onDelete: (TripSessionEntity) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(true) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = MaterialTheme.spacing.md,
            end = MaterialTheme.spacing.md,
            top = MaterialTheme.spacing.sm,
            bottom = 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
    ) {
        orderedTrips.firstOrNull()?.let { latest ->
            item {
                LatestTripSummaryV06(
                    trip = latest,
                    onClick = { onOpenDetail(latest.id) }
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = orderedTrips.isNotEmpty()) { expanded = !expanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("最近行程", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            orderedTrips.isEmpty() -> "完成第一段行程后会显示在这里"
                            expanded -> "共 ${orderedTrips.size} 条 · 点击收起"
                            else -> "共 ${orderedTrips.size} 条 · 点击展开"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (orderedTrips.isNotEmpty()) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收起最近行程" else "展开最近行程",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (!canStart) {
                    Text("请先选择车辆", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (orderedTrips.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("还没有行程记录", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "右下角开始行程后，只记录真实 GPS 轨迹，不预设终点或虚构路线。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else if (expanded) {
            items(orderedTrips, key = { it.id }) { trip ->
                TripHistoryCardV06(
                    trip = trip,
                    onClick = { onOpenDetail(trip.id) },
                    onDelete = { onDelete(trip) }
                )
            }
        }
    }
}

@Composable
private fun LatestTripSummaryV06(trip: TripSessionEntity, onClick: () -> Unit) {
    val accent = EVDesignTokens.Energy.green
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Route, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Column(
                    modifier = Modifier.weight(1f).padding(start = MaterialTheme.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text("最近一次", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatHomeTripTime(trip.startedAtEpochMillis), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                Text("已完成", style = MaterialTheme.typography.labelSmall, color = accent)
            }

            val energyText = trip.averageConsumptionKwhPer100Km
                ?.takeIf { it.isFinite() && it >= 0.0 }
                ?.let { String.format(Locale.US, "%.1f", it) }
                ?: "--"
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val compact = maxWidth < 360.dp || LocalConfiguration.current.fontScale >= 1.3f
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
                            HomeMetricV06("距离", formatHomeDistance(trip.distanceMeters), Modifier.weight(1f))
                            HomeMetricV06("耗时", formatHomeDuration(trip.elapsedSeconds), Modifier.weight(1f))
                        }
                        HomeMetricV06("能耗", energyText, Modifier.fillMaxWidth())
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
                        HomeMetricV06("距离", formatHomeDistance(trip.distanceMeters), Modifier.weight(1f))
                        HomeMetricV06("耗时", formatHomeDuration(trip.elapsedSeconds), Modifier.weight(1f))
                        HomeMetricV06("能耗", energyText, Modifier.weight(1f))
                    }
                }
            }

            when {
                trip.startSoc != null && trip.endSoc != null -> "SOC ${trip.startSoc}% → ${trip.endSoc}%"
                trip.startSoc != null -> "SOC ${trip.startSoc}% → --"
                else -> null
            }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HomeMetricV06(label: String, value: String, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun TripPreparationContentV08(
    modifier: Modifier,
    vehicle: VehicleEntity?,
    currentSoc: Int?,
    currentMileageKm: Double?,
    latestTrip: TripSessionEntity?,
    onStart: () -> Unit
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
    ) {
        item {
            HeroVehicleCard(
                vehicle = vehicle,
                currentSoc = currentSoc,
                currentMileageKm = currentMileageKm,
                latestTrip = latestTrip,
                vehicleSwitchEnabled = false
            )
        }
        item { ReadyGpsTruthCardV08() }
        item { TripBackgroundRecordingGuidance() }
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
            Text(
                "向右滑动后才开始持续定位；返回不会创建空行程。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun ReadyGpsTruthCardV08() {
    val accent = EVDesignTokens.Energy.green
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("GPS 实录", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "开始后记录真实定位；不预设终点，也不会虚构路线。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatHomeTripTime(epochMillis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.SIMPLIFIED_CHINESE).format(Date(epochMillis))

private fun formatHomeDistance(meters: Double): String =
    if (meters >= 1000.0) String.format(Locale.US, "%.1f km", meters / 1000.0)
    else String.format(Locale.US, "%.0f m", meters)

private fun formatHomeDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}
