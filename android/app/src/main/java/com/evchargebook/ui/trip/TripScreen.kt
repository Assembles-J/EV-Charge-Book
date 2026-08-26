package com.evchargebook.ui.trip

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
            contentPadding = PaddingValues(vertical = MaterialTheme.spacing.md)
        ) {
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(MaterialTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                        Icon(Icons.Default.DirectionsCar, null)
                        if (activeTrip == null) {
                            Text("准备记录行程", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(vehicle?.let { "当前车辆：${it.brand} ${it.model}" } ?: "请先选择车辆", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("开始后会使用前台定位服务持续记录真实轨迹。锁屏时通知栏会保留行程状态。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = onStart, enabled = vehicle != null, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.PlayArrow, null)
                                Spacer(Modifier.width(MaterialTheme.spacing.xs))
                                Text("开始行程")
                            }
                        } else {
                            val interrupted = activeTrip.status == TripStatus.INTERRUPTED
                            Text(if (interrupted) "行程已中断" else "行程进行中", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("车辆：${activeVehicle?.let { "${it.brand} ${it.model}" } ?: "车辆 #${activeTrip.vehicleId}"}")
                            Text("开始：${formatTime(activeTrip.startedAtEpochMillis)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (activeTrip.distanceMeters > 0.0) Text("已记录：${formatDistance(activeTrip.distanceMeters)}")
                            if (interrupted) {
                                Text("定位服务曾被系统、权限或定位开关中断。恢复会继续同一条行程，不会新建重复记录。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                                Button(onClick = { onResume(activeTrip.id) }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Refresh, null)
                                    Spacer(Modifier.width(MaterialTheme.spacing.xs))
                                    Text("恢复行程记录")
                                }
                            }
                            if (vehicle?.id != activeTrip.vehicleId) {
                                Text("当前选中的车辆与未完成行程不同。请先结束当前行程再开始另一辆车。", color = MaterialTheme.colorScheme.tertiary)
                            }
                            OutlinedButton(onClick = { onOpenDetail(activeTrip.id) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Route, null)
                                Spacer(Modifier.width(MaterialTheme.spacing.xs))
                                Text("查看实时详情")
                            }
                            Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Stop, null)
                                Spacer(Modifier.width(MaterialTheme.spacing.xs))
                                Text(if (interrupted) "结束这条行程" else "结束行程")
                            }
                        }
                    }
                }
            }

            item { Text("历史行程", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            if (trips.isEmpty()) {
                item { Text("还没有已记录行程。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(trips.filter { it.id != activeTrip?.id }, key = { it.id }) { trip ->
                    OutlinedCard(onClick = { onOpenDetail(trip.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(formatTime(trip.startedAtEpochMillis), fontWeight = FontWeight.SemiBold)
                                    Text(statusText(trip.status), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (trip.status == TripStatus.COMPLETED) {
                                    IconButton(onClick = { deleteTarget = trip }) { Icon(Icons.Default.Delete, "删除行程") }
                                }
                            }
                            Text("耗时：${formatDuration(trip.elapsedSeconds)}")
                            Text(if (trip.distanceMeters > 0.0) "距离：${formatDistance(trip.distanceMeters)}" else "没有有效轨迹点，距离未计算", style = MaterialTheme.typography.bodyMedium)
                            trip.averageSpeedMps?.let { Text("平均速度：${formatSpeed(it)}") }
                        }
                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripDetailScreen(
    trip: TripSessionEntity,
    vehicles: List<VehicleEntity>,
    points: List<TripPointEntity>,
    onBack: () -> Unit
) {
    val vehicle = vehicles.firstOrNull { it.id == trip.vehicleId }
    val firstPoint = points.firstOrNull()
    val lastPoint = points.lastOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("行程详情") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
            contentPadding = PaddingValues(vertical = MaterialTheme.spacing.md)
        ) {
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(MaterialTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                        Text(vehicle?.let { "${it.brand} ${it.model}" } ?: "车辆 #${trip.vehicleId}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(statusText(trip.status), color = MaterialTheme.colorScheme.primary)
                        Text("开始：${formatTime(trip.startedAtEpochMillis)}")
                        trip.endedAtEpochMillis?.let { Text("结束：${formatTime(it)}") }
                    }
                }
            }
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                        Text("行程汇总", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("距离：${formatDistance(trip.distanceMeters)}")
                        Text("总耗时：${formatDuration(trip.elapsedSeconds)}")
                        trip.movingSeconds?.let { Text("移动：${formatDuration(it)}") }
                        trip.stoppedSeconds?.let { Text("停车：${formatDuration(it)}") }
                        trip.averageSpeedMps?.let { Text("平均速度：${formatSpeed(it)}") }
                        trip.maxSpeedMps?.let { Text("最高速度：${formatSpeed(it)}") }
                        if (trip.minAltitudeMeters != null || trip.maxAltitudeMeters != null) {
                            Text("海拔范围：${trip.minAltitudeMeters?.let(::formatAltitude) ?: "--"} ~ ${trip.maxAltitudeMeters?.let(::formatAltitude) ?: "--"}")
                        }
                    }
                }
            }
            if (points.size >= 2) {
                item { TripRoutePreview(points) }
            }
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                        Text("轨迹数据", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("有效轨迹点：${points.size}")
                        firstPoint?.let { Text("起点：${formatCoordinate(it.latitude)}, ${formatCoordinate(it.longitude)}") }
                        lastPoint?.let { Text("终点：${formatCoordinate(it.latitude)}, ${formatCoordinate(it.longitude)}") }
                        if (points.isEmpty()) Text("本次没有保存有效 GPS 轨迹点。不会根据时间或直线距离伪造路线。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else Text("当前已可预览真实轨迹形状；后续地图底图只替换渲染层，不改变这些 WGS84 轨迹事实。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (points.isNotEmpty()) {
                item { Text("最近轨迹点", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(points.takeLast(8).reversed(), key = { it.id }) { point ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(MaterialTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                            Text(SimpleDateFormat("HH:mm:ss", Locale.SIMPLIFIED_CHINESE).format(Date(point.capturedAtEpochMillis)), fontWeight = FontWeight.SemiBold)
                            Text("${formatCoordinate(point.latitude)}, ${formatCoordinate(point.longitude)}")
                            val pieces = buildList {
                                point.speedMps?.let { add(formatSpeed(it)) }
                                point.horizontalAccuracyMeters?.let { add("精度 ${it.toInt()} m") }
                                point.altitudeMeters?.let { add(formatAltitude(it)) }
                            }
                            if (pieces.isNotEmpty()) Text(pieces.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TripRoutePreview(points: List<TripPointEntity>) {
    val geometry = remember(points) {
        TripRouteGeometryBuilder.build(points.map { TripGeoPoint(it.latitude, it.longitude) })
    }
    if (geometry == null || !geometry.isDrawable) return

    val routeColor = MaterialTheme.colorScheme.primary
    val startColor = MaterialTheme.colorScheme.tertiary
    val endColor = MaterialTheme.colorScheme.error
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("轨迹预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${geometry.points.size} 点", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                val paddingPx = 20.dp.toPx()
                val width = (size.width - paddingPx * 2).coerceAtLeast(1f)
                val height = (size.height - paddingPx * 2).coerceAtLeast(1f)
                val offsets = geometry.points.map { point ->
                    Offset(
                        x = paddingPx + point.x * width,
                        y = paddingPx + point.y * height
                    )
                }

                drawRect(
                    color = borderColor,
                    topLeft = Offset(paddingPx / 2, paddingPx / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - paddingPx, size.height - paddingPx),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                )
                offsets.zipWithNext().forEach { (from, to) ->
                    drawLine(routeColor, from, to, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                }
                drawCircle(startColor, radius = 6.dp.toPx(), center = offsets.first())
                drawCircle(endColor, radius = 6.dp.toPx(), center = offsets.last())
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("起点", style = MaterialTheme.typography.labelMedium, color = startColor)
                Text("终点", style = MaterialTheme.typography.labelMedium, color = endColor)
            }
            Text(
                "仅按本次真实 WGS84 轨迹点归一化绘制，不含道路吸附、地图匹配或虚构路线。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatTime(epochMillis: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.SIMPLIFIED_CHINESE).format(Date(epochMillis))
private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) "${hours}小时${minutes}分" else if (minutes > 0) "${minutes}分${secs}秒" else "${secs}秒"
}
private fun formatDistance(meters: Double) = if (meters >= 1000.0) String.format(Locale.US, "%.2f km", meters / 1000.0) else String.format(Locale.US, "%.0f m", meters)
private fun formatSpeed(mps: Double) = String.format(Locale.US, "%.1f km/h", mps * 3.6)
private fun formatAltitude(meters: Double) = String.format(Locale.US, "%.0f m", meters)
private fun formatCoordinate(value: Double) = String.format(Locale.US, "%.6f", value)
private fun statusText(status: String) = when (status) {
    TripStatus.RECORDING -> "进行中"
    TripStatus.INTERRUPTED -> "已中断，可恢复"
    TripStatus.COMPLETED -> "已完成"
    else -> status
}
