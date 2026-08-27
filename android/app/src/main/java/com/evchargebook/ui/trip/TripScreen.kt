package com.evchargebook.ui.trip

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
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

private val TripHeroBrush = Brush.linearGradient(
    listOf(Color(0xFF06100B), Color(0xFF0A2116), Color(0xFF07120D))
)

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
    val selectedTrip = selectedTripId?.let { id ->
        trips.firstOrNull { it.id == id } ?: activeTrip?.takeIf { it.id == id }
    }
    if (selectedTrip != null) {
        TripDetailScreen(selectedTrip, vehicles, selectedTripPoints, onCloseDetail)
        return
    }

    var deleteTarget by remember { mutableStateOf<TripSessionEntity?>(null) }
    var confirmStop by remember { mutableStateOf(false) }
    val activeVehicle = activeTrip?.let { trip -> vehicles.firstOrNull { it.id == trip.vehicleId } }
    val savedTrips = trips.filter { it.id != activeTrip?.id }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("行程", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("TRIP JOURNAL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            item {
                ActiveTripPanel(
                    selectedVehicle = vehicle,
                    activeVehicle = activeVehicle,
                    activeTrip = activeTrip,
                    onStart = onStart,
                    onResume = onResume,
                    onStop = { confirmStop = true },
                    onOpenDetail = onOpenDetail
                )
            }
            item {
                SectionHeading(
                    "历史行程",
                    if (savedTrips.isEmpty()) "真实行程会按时间顺序沉淀在这里" else "${savedTrips.size} 条已保存行程"
                )
            }
            if (savedTrips.isEmpty()) {
                item { EmptyTripTimeline() }
            } else {
                items(savedTrips, key = { it.id }) { trip ->
                    TripHistoryRow(
                        trip = trip,
                        onClick = { onOpenDetail(trip.id) },
                        onDelete = { deleteTarget = trip }
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (confirmStop && activeTrip != null) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("结束当前行程？") },
            text = { Text("结束后会停止持续定位并保存当前已经记录的真实行程数据。") },
            confirmButton = {
                TextButton(onClick = { confirmStop = false; onStop() }) {
                    Text("结束行程", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("继续记录") } }
        )
    }

    deleteTarget?.let { trip ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除这条行程？") },
            text = { Text("行程及关联轨迹点会一并删除。") },
            confirmButton = {
                TextButton(onClick = { onDelete(trip); deleteTarget = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
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

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.background(TripHeroBrush).padding(MaterialTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(if (interrupted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, CircleShape))
                    Spacer(Modifier.width(MaterialTheme.spacing.xs))
                    Text(
                        when {
                            activeTrip == null -> "TRIP / READY"
                            interrupted -> "TRIP / INTERRUPTED"
                            else -> "TRIP / LIVE"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (interrupted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
                StatusPill(
                    text = when {
                        activeTrip == null -> "待命"
                        interrupted -> "需恢复"
                        else -> "记录中"
                    },
                    warning = interrupted
                )
            }

            if (activeTrip == null) {
                Text("准备出发", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    selectedVehicle?.let { "${it.brand} ${it.model}" } ?: "请先选择车辆",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .28f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
                    ReadyMetric("轨迹", "GPS 实录", Modifier.weight(1f))
                    ReadyMetric("距离", "真实计算", Modifier.weight(1f))
                    ReadyMetric("中断", "可恢复", Modifier.weight(1f))
                }
                Text(
                    "开始后持续记录真实 GPS 轨迹；没有有效定位点时不会虚构距离。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onStart, enabled = selectedVehicle != null, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(MaterialTheme.spacing.xs))
                    Text("开始行程")
                }
                return@Column
            }

            Text(
                activeVehicle?.let { "${it.brand} ${it.model}" } ?: "车辆 #${activeTrip.vehicleId}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs)) {
                Text("当前距离", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (activeTrip.distanceMeters > 0) formatDistance(activeTrip.distanceMeters) else "--",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .28f))
            ResponsiveCockpitMetrics(
                listOf(
                    CockpitMetricData("耗时", formatDuration(activeTrip.elapsedSeconds)),
                    CockpitMetricData("行驶均速", activeTrip.averageSpeedMps?.let(::formatSpeed) ?: "--"),
                    CockpitMetricData("最高速度", activeTrip.maxSpeedMps?.let(::formatSpeed) ?: "--")
                )
            )

            if (interrupted) {
                Surface(color = MaterialTheme.colorScheme.error.copy(alpha = .10f), shape = MaterialTheme.shapes.medium) {
                    Text(
                        "定位服务曾被中断。恢复后会继续同一条行程，并继续更新 GPS 健康状态。",
                        modifier = Modifier.padding(MaterialTheme.spacing.sm),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Button(onClick = { onResume(activeTrip.id) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(MaterialTheme.spacing.xs))
                    Text("恢复记录")
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                OutlinedButton(onClick = { onOpenDetail(activeTrip.id) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Route, null)
                    Spacer(Modifier.width(MaterialTheme.spacing.xs))
                    Text("实时轨迹")
                }
                TextButton(onClick = onStop, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Stop, null)
                    Spacer(Modifier.width(MaterialTheme.spacing.xs))
                    Text(if (interrupted) "结束行程" else "结束")
                }
            }
        }
    }
}

@Composable
private fun ReadyMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusPill(text: String, warning: Boolean) {
    val color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Surface(shape = CircleShape, color = color.copy(alpha = .12f)) {
        Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun EmptyTripTimeline() {
    Row(Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.sm), verticalAlignment = Alignment.Top) {
        TripTimelineRail(isLast = true, active = false)
        Spacer(Modifier.width(MaterialTheme.spacing.md))
        Column {
            Text("等待第一段真实行程", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(MaterialTheme.spacing.xxs))
            Text("完成行程后会形成连续的驾驶日志。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TripHistoryRow(trip: TripSessionEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.sm), verticalAlignment = Alignment.Top) {
            TripTimelineRail(isLast = false, active = trip.status != TripStatus.COMPLETED)
            Spacer(Modifier.width(MaterialTheme.spacing.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(trip.startedAtEpochMillis), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (trip.status == TripStatus.COMPLETED) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, "删除行程", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Text(formatDistance(trip.distanceMeters), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${formatDuration(trip.elapsedSeconds)} · ${trip.averageSpeedMps?.let(::formatSpeed) ?: "均速 --"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(statusText(trip.status), style = MaterialTheme.typography.labelMedium, color = if (trip.status == TripStatus.INTERRUPTED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun TripTimelineRail(isLast: Boolean, active: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(34.dp).background((if (active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary).copy(alpha = .12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Route, null, tint = if (active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        if (!isLast) {
            Box(Modifier.width(1.dp).height(58.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = .42f)))
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("行程详情") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg)
        ) {
            item {
                Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = Color.Transparent) {
                    Column(Modifier.background(TripHeroBrush).padding(MaterialTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("TRIP / SUMMARY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Text(vehicle?.let { "${it.brand} ${it.model}" } ?: "车辆 #${trip.vehicleId}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                Text("${formatTime(trip.startedAtEpochMillis)} · ${statusText(trip.status)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            geometry?.let { StatusPill(if (it.gapCount > 0) "GPS ${it.gapCount} 缺口" else "GPS 连续", it.gapCount > 0) }
                        }
                        Text("行程距离", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatDistance(trip.distanceMeters), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .28f))
                        ResponsiveCockpitMetrics(
                            listOf(
                                CockpitMetricData("总耗时", formatDuration(trip.elapsedSeconds)),
                                CockpitMetricData("全程均速", wholeTripAverageMps?.let(::formatSpeed) ?: "--"),
                                CockpitMetricData("行驶均速", trip.averageSpeedMps?.let(::formatSpeed) ?: "--"),
                                CockpitMetricData("最高速度", trip.maxSpeedMps?.let(::formatSpeed) ?: "--"),
                                CockpitMetricData("移动时间", trip.movingSeconds?.let(::formatDuration) ?: "--"),
                                CockpitMetricData("GPS 点", points.size.toString())
                            )
                        )
                    }
                }
            }

            geometry?.let { if (it.isDrawable) item { TripRoutePreview(points) } }

            item {
                SectionHeading("轨迹可信度", if ((geometry?.gapCount ?: 0) > 0) "存在长时间 GPS 缺口，缺失区间保持断开" else "当前轨迹未检测到超过 2 分钟的长缺口")
                Spacer(Modifier.height(MaterialTheme.spacing.sm))
                CoordinateRow("起点", firstPoint)
                Spacer(Modifier.height(MaterialTheme.spacing.xs))
                CoordinateRow("终点", lastPoint)
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
private fun CoordinateRow(label: String, point: TripPointEntity?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(point?.let { "${formatCoordinate(it.latitude)}, ${formatCoordinate(it.longitude)}" } ?: "--", style = MaterialTheme.typography.bodyMedium)
    }
}

private data class CockpitMetricData(val label: String, val value: String)

@Composable
private fun ResponsiveCockpitMetrics(metrics: List<CockpitMetricData>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 360.dp || LocalConfiguration.current.fontScale >= 1.3f
        val columns = if (compact) 2 else 3
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            metrics.chunked(columns).forEach { rowMetrics ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
                    rowMetrics.forEach { metric -> CockpitMetric(metric.label, metric.value, Modifier.weight(1f)) }
                    repeat(columns - rowMetrics.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun CockpitMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
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
                StatusPill(if (geometry.gapCount > 0) "${geometry.gapCount} 个长缺口" else "轨迹连续", geometry.gapCount > 0)
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
                Text("检测到 ${geometry.gapCount} 处超过 2 分钟的 GPS 缺口。断点保持断开，不使用实线伪装连续。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Text("基于真实 WGS84 轨迹点绘制，不做道路吸附或虚构路线。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatTime(epochMillis: Long) = SimpleDateFormat("M月d日 HH:mm", Locale.SIMPLIFIED_CHINESE).format(Date(epochMillis))
private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "${h}小时${m}分" else if (m > 0) "${m}分${s}秒" else "${s}秒"
}
private fun formatDistance(meters: Double) = if (meters >= 1000.0) String.format(Locale.US, "%.2f km", meters / 1000.0) else String.format(Locale.US, "%.0f m", meters)
private fun formatSpeed(mps: Double) = String.format(Locale.US, "%.1f km/h", mps * 3.6)
private fun formatCoordinate(value: Double) = String.format(Locale.US, "%.6f", value)
private fun statusText(status: String) = when (status) {
    TripStatus.RECORDING -> "进行中"
    TripStatus.INTERRUPTED -> "已中断，可恢复"
    TripStatus.COMPLETED -> "已完成"
    else -> status
}
