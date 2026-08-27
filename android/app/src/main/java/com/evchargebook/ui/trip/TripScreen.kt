package com.evchargebook.ui.trip

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.TripPointEntity
import com.evchargebook.data.entity.TripSessionEntity
import com.evchargebook.data.entity.TripStatus
import com.evchargebook.data.entity.VehicleEntity
import com.evchargebook.domain.trip.TripGeoPoint
import com.evchargebook.domain.trip.TripRouteGeometryBuilder
import com.evchargebook.ui.theme.spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripScreen(
    vehicle: VehicleEntity?,
    vehicles: List<VehicleEntity>,
    trips: List<TripSessionEntity>,
    activeTrip: TripSessionEntity?,
    selectedTripId: Long?,
    selectedTripPoints: List<TripPointEntity>,
    onStart: () -> Unit,
    onResume: (Long) -> Unit,
    onStop: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onCloseDetail: () -> Unit,
    onDelete: (TripSessionEntity) -> Unit
) {
    val selectedTrip = selectedTripId?.let { id -> trips.firstOrNull { it.id == id } ?: activeTrip?.takeIf { it.id == id } }
    if (selectedTrip != null) {
        TripDetailScreen(selectedTrip, vehicles, selectedTripPoints, onCloseDetail)
        return
    }

    var deleteTarget by remember { mutableStateOf<TripSessionEntity?>(null) }
    val activeVehicle = activeTrip?.let { trip -> vehicles.firstOrNull { it.id == trip.vehicleId } }

    Scaffold(topBar = { TopAppBar(title = { Text("行程") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg)
        ) {
            item { ActiveTripPanel(vehicle, activeVehicle, activeTrip, onStart, onResume, onStop, onOpenDetail) }
            item { SectionHeading("历史行程", if (trips.isEmpty()) "开始一次真实行程后会显示在这里" else "${trips.count { it.id != activeTrip?.id }} 条已保存行程") }
            if (trips.isEmpty()) {
                item { Text("暂无行程记录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(trips.filter { it.id != activeTrip?.id }, key = { it.id }) { trip ->
                    TripHistoryRow(trip, onClick = { onOpenDetail(trip.id) }, onDelete = { deleteTarget = trip })
                }
            }
        }
    }

    deleteTarget?.let { trip ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除这条行程？") },
            text = { Text("行程及关联轨迹点会一并删除。") },
            confirmButton = { TextButton(onClick = { onDelete(trip); deleteTarget = null }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun ActiveTripPanel(
    selectedVehicle: VehicleEntity?,
    activeVehicle: VehicleEntity?,
    activeTrip: TripSessionEntity?,
    onStart: () -> Unit,
    onResume: (Long) -> Unit,
    onStop: () -> Unit,
    onOpenDetail: (Long) -> Unit
) {
    val interrupted = activeTrip?.status == TripStatus.INTERRUPTED
    if (activeTrip == null) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(Modifier.padding(MaterialTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
                Text("TRIP / READY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("准备出发", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(selectedVehicle?.let { "${it.brand} ${it.model}" } ?: "请先选择车辆", style = MaterialTheme.typography.bodyLarge)
                Text("开始后会持续记录真实 GPS 轨迹；没有有效定位点时不会虚构距离。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onStart, enabled = selectedVehicle != null, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(MaterialTheme.spacing.xs))
                    Text("开始行程")
                }
            }
        }
        return
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface
    ) {
        Column(Modifier.padding(MaterialTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (interrupted) "TRIP / INTERRUPTED" else "TRIP / LIVE",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (interrupted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.inversePrimary,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (interrupted) MaterialTheme.colorScheme.error.copy(alpha = .16f) else MaterialTheme.colorScheme.inversePrimary.copy(alpha = .14f)
                ) {
                    Text(
                        if (interrupted) "需恢复" else "记录中",
                        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.xs, vertical = MaterialTheme.spacing.xxs),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (interrupted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.inversePrimary
                    )
                }
            }

            Text(
                activeVehicle?.let { "${it.brand} ${it.model}" } ?: "车辆 #${activeTrip.vehicleId}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = .7f)
            )

            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs)) {
                Text("当前距离", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = .58f))
                Text(
                    if (activeTrip.distanceMeters > 0) formatDistance(activeTrip.distanceMeters) else "--",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.inverseOnSurface
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = .12f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
                CockpitMetric("耗时", formatDuration(activeTrip.elapsedSeconds), Modifier.weight(1f))
                CockpitMetric("行驶均速", activeTrip.averageSpeedMps?.let(::formatSpeed) ?: "--", Modifier.weight(1f))
                CockpitMetric("最高速度", activeTrip.maxSpeedMps?.let(::formatSpeed) ?: "--", Modifier.weight(1f))
            }

            if (interrupted) {
                Text(
                    "定位服务曾被中断。恢复后会继续同一条行程；GPS 健康状态也会继续通过前台通知更新。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = .7f)
                )
                Button(onClick = { onResume(activeTrip.id) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(MaterialTheme.spacing.xs))
                    Text("恢复记录")
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                OutlinedButton(
                    onClick = { onOpenDetail(activeTrip.id) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.inverseOnSurface),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = .3f)))
                ) {
                    Icon(Icons.Default.Route, null)
                    Spacer(Modifier.width(MaterialTheme.spacing.xs))
                    Text("实时轨迹")
                }
                TextButton(
                    onClick = onStop,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.inverseOnSurface)
                ) {
                    Icon(Icons.Default.Stop, null)
                    Spacer(Modifier.width(MaterialTheme.spacing.xs))
                    Text(if (interrupted) "结束行程" else "结束")
                }
            }
        }
    }
}

@Composable
private fun TripHistoryRow(trip: TripSessionEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs)) {
                Text(formatTime(trip.startedAtEpochMillis), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${formatDistance(trip.distanceMeters)} · ${formatDuration(trip.elapsedSeconds)}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    listOfNotNull(
                        trip.averageSpeedMps?.let { "行驶均速 ${formatSpeed(it)}" },
                        statusText(trip.status)
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (trip.status == TripStatus.COMPLETED) IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "删除行程") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripDetailScreen(trip: TripSessionEntity, vehicles: List<VehicleEntity>, points: List<TripPointEntity>, onBack: () -> Unit) {
    val vehicle = vehicles.firstOrNull { it.id == trip.vehicleId }
    val firstPoint = points.firstOrNull()
    val lastPoint = points.lastOrNull()
    val wholeTripAverageMps = if (trip.elapsedSeconds > 0) trip.distanceMeters / trip.elapsedSeconds else null
    val geometry = remember(points) {
        if (points.size >= 2) TripRouteGeometryBuilder.build(points.map { TripGeoPoint(it.latitude, it.longitude, it.capturedAtEpochMillis) }) else null
    }

    Scaffold(topBar = { TopAppBar(title = { Text("行程详情") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface
                ) {
                    Column(Modifier.padding(MaterialTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("TRIP / SUMMARY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.inversePrimary)
                                Text(vehicle?.let { "${it.brand} ${it.model}" } ?: "车辆 #${trip.vehicleId}", style = MaterialTheme.typography.titleLarge)
                                Text("${formatTime(trip.startedAtEpochMillis)} · ${statusText(trip.status)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = .65f))
                            }
                            geometry?.let {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = if (it.gapCount > 0) MaterialTheme.colorScheme.error.copy(alpha = .16f) else MaterialTheme.colorScheme.inversePrimary.copy(alpha = .14f)
                                ) {
                                    Text(
                                        if (it.gapCount > 0) "GPS ${it.gapCount} 缺口" else "GPS 连续",
                                        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.xs, vertical = MaterialTheme.spacing.xxs),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (it.gapCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.inversePrimary
                                    )
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs)) {
                            Text("行程距离", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = .58f))
                            Text(formatDistance(trip.distanceMeters), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = .12f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
                            CockpitMetric("总耗时", formatDuration(trip.elapsedSeconds), Modifier.weight(1f))
                            CockpitMetric("全程均速", wholeTripAverageMps?.let(::formatSpeed) ?: "--", Modifier.weight(1f))
                            CockpitMetric("行驶均速", trip.averageSpeedMps?.let(::formatSpeed) ?: "--", Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
                            CockpitMetric("最高速度", trip.maxSpeedMps?.let(::formatSpeed) ?: "--", Modifier.weight(1f))
                            CockpitMetric("移动时间", trip.movingSeconds?.let(::formatDuration) ?: "--", Modifier.weight(1f))
                            CockpitMetric("GPS 点", points.size.toString(), Modifier.weight(1f))
                        }
                    }
                }
            }

            geometry?.let { if (it.isDrawable) item { TripRoutePreview(points) } }

            item {
                SectionHeading("轨迹可信度", if ((geometry?.gapCount ?: 0) > 0) "存在长时间 GPS 缺口，缺失区间不会被伪造连接" else "当前已保存轨迹中未检测到超过 2 分钟的长缺口")
                Spacer(Modifier.height(MaterialTheme.spacing.sm))
                firstPoint?.let { Text("起点  ${formatCoordinate(it.latitude)}, ${formatCoordinate(it.longitude)}") }
                lastPoint?.let { Text("终点  ${formatCoordinate(it.latitude)}, ${formatCoordinate(it.longitude)}") }
                if (points.isEmpty()) Text("本次没有保存有效 GPS 轨迹点。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (points.isNotEmpty()) {
                item { SectionHeading("最近轨迹点", "用于排查 GPS 质量，不作为主视觉") }
                items(points.takeLast(6).reversed(), key = { it.id }) { point ->
                    Row(Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.xs), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(SimpleDateFormat("HH:mm:ss", Locale.SIMPLIFIED_CHINESE).format(Date(point.capturedAtEpochMillis)), fontWeight = FontWeight.SemiBold)
                            Text("${formatCoordinate(point.latitude)}, ${formatCoordinate(point.longitude)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        point.horizontalAccuracyMeters?.let { Text("±${it.toInt()} m", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CockpitMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = .52f))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.inverseOnSurface)
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TripRoutePreview(points: List<TripPointEntity>) {
    val geometry = remember(points) {
        TripRouteGeometryBuilder.build(points.map { TripGeoPoint(it.latitude, it.longitude, it.capturedAtEpochMillis) })
    }
    if (geometry == null || !geometry.isDrawable) return
    val routeColor = MaterialTheme.colorScheme.primary
    val startColor = MaterialTheme.colorScheme.tertiary
    val endColor = MaterialTheme.colorScheme.error

    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("真实轨迹", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${geometry.points.size} 点 · ${geometry.segments.size} 段", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (geometry.gapCount > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        if (geometry.gapCount > 0) "${geometry.gapCount} 个长缺口" else "轨迹连续",
                        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.xs, vertical = MaterialTheme.spacing.xxs),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (geometry.gapCount > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Canvas(Modifier.fillMaxWidth().height(240.dp)) {
                val p = 16.dp.toPx()
                val w = (size.width - p * 2).coerceAtLeast(1f)
                val h = (size.height - p * 2).coerceAtLeast(1f)
                fun offset(point: com.evchargebook.domain.trip.TripRoutePoint) = Offset(p + point.x * w, p + point.y * h)
                geometry.segments.forEach { segment ->
                    segment.map(::offset).zipWithNext().forEach { (from, to) ->
                        drawLine(routeColor, from, to, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
                drawCircle(startColor, 6.dp.toPx(), offset(geometry.points.first()))
                drawCircle(endColor, 6.dp.toPx(), offset(geometry.points.last()))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("起点", style = MaterialTheme.typography.labelMedium, color = startColor)
                Text("终点", style = MaterialTheme.typography.labelMedium, color = endColor)
            }
            if (geometry.gapCount > 0) {
                Text("检测到 ${geometry.gapCount} 处超过 2 分钟的 GPS 缺口。断点保持断开，不用实线假装连续。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Text("基于真实 WGS84 轨迹点绘制，不做道路吸附或虚构路线。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatTime(epochMillis: Long) = SimpleDateFormat("M月d日 HH:mm", Locale.SIMPLIFIED_CHINESE).format(Date(epochMillis))
private fun formatDuration(seconds: Long): String { val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60; return if (h > 0) "${h}小时${m}分" else if (m > 0) "${m}分${s}秒" else "${s}秒" }
private fun formatDistance(meters: Double) = if (meters >= 1000.0) String.format(Locale.US, "%.2f km", meters / 1000.0) else String.format(Locale.US, "%.0f m", meters)
private fun formatSpeed(mps: Double) = String.format(Locale.US, "%.1f km/h", mps * 3.6)
private fun formatCoordinate(value: Double) = String.format(Locale.US, "%.6f", value)
private fun statusText(status: String) = when (status) { TripStatus.RECORDING -> "进行中"; TripStatus.INTERRUPTED -> "已中断，可恢复"; TripStatus.COMPLETED -> "已完成"; else -> status }
