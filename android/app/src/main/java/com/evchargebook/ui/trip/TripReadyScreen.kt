package com.evchargebook.ui.trip

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.TripSessionEntity
import com.evchargebook.data.entity.VehicleEntity
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
    onOpenDetail: (Long) -> Unit
) {
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
            item { ReadyMapCard() }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                    Text(
                        vehicle?.let { "${it.brand} ${it.model}" } ?: "请选择车辆",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "开始后只记录真实 GPS 轨迹，当前状态会作为本次行程起点。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    ReadyStateMetric("当前 SOC", currentSoc?.let { "$it%" } ?: "--", Modifier.weight(1f))
                    ReadyStateMetric("当前里程", currentMileageKm?.let { "${formatReadyMileage(it)} km" } ?: "--", Modifier.weight(1f))
                    ReadyStateMetric("轨迹", "GPS 实录", Modifier.weight(1f))
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
                    Text("最近行程", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("RECENT TRIPS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (recentTrips.isEmpty()) {
                item {
                    Text(
                        "完成第一段行程后，这里会显示真实距离、SOC 与能耗记录。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = MaterialTheme.spacing.md)
                    )
                }
            } else {
                items(minOf(recentTrips.size, 3)) { index ->
                    val trip = recentTrips[index]
                    ReadyRecentTripRow(trip = trip, onClick = { onOpenDetail(trip.id) })
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ReadyMapCard() {
    Surface(
        modifier = Modifier.fillMaxWidth().height(210.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
            val primary = MaterialTheme.colorScheme.primary
            Canvas(Modifier.fillMaxSize()) {
                val spacing = 34.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    x += spacing
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    y += spacing
                }
                val center = Offset(size.width * 0.54f, size.height * 0.52f)
                drawCircle(primary.copy(alpha = 0.12f), radius = 46.dp.toPx(), center = center)
                drawCircle(primary.copy(alpha = 0.24f), radius = 23.dp.toPx(), center = center)
                drawCircle(primary, radius = 7.dp.toPx(), center = center)
                drawLine(
                    primary.copy(alpha = 0.55f),
                    Offset(size.width * 0.12f, size.height * 0.78f),
                    Offset(center.x - 8.dp.toPx(), center.y + 7.dp.toPx()),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(MaterialTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                Spacer(Modifier.width(MaterialTheme.spacing.xs))
                Text("GPS READY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Row(
                modifier = Modifier.align(Alignment.BottomStart).padding(MaterialTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(MaterialTheme.spacing.xs))
                Column {
                    Text("当前位置", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("开始后锁定真实起点", style = MaterialTheme.typography.bodyMedium)
                }
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
private fun ReadyRecentTripRow(trip: TripSessionEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = MaterialTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) {
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Route, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(MaterialTheme.spacing.md))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                SimpleDateFormat("MM-dd HH:mm", Locale.SIMPLIFIED_CHINESE).format(Date(trip.startedAtEpochMillis)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "${String.format(Locale.US, "%.1f", trip.distanceMeters / 1000.0)} km · ${formatReadyDuration(trip.elapsedSeconds)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            trip.averageConsumptionKwhPer100Km?.let {
                Text("${String.format(Locale.US, "%.1f", it)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("kWh/100km", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } ?: Text("--", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatReadyMileage(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)

private fun formatReadyDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
