package com.evchargebook.ui.trip

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.TripPointEntity
import com.evchargebook.data.entity.TripSessionEntity
import com.evchargebook.data.entity.TripStatus
import com.evchargebook.data.entity.VehicleEntity
import com.evchargebook.domain.TripSpeedTrustRules
import com.evchargebook.domain.trip.TripElevationAnalytics
import com.evchargebook.domain.trip.TripGeoPoint
import com.evchargebook.domain.trip.TripRouteGeometryBuilder
import com.evchargebook.location.AndroidGeocoderAddressResolver
import com.evchargebook.ui.theme.spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DetailHeroBrush = Brush.linearGradient(
    listOf(Color(0xFF06100B), Color(0xFF0A2116), Color(0xFF07120D))
)
private val DetailSpeedDeepRed = Color(0xFF8E1919)
private val DetailSpeedRed = Color(0xFFE44545)
private val DetailSpeedYellow = Color(0xFFF2C94C)
private val DetailSpeedGreen = Color(0xFF35C46A)
private val DetailSpeedBlue = Color(0xFF3B82F6)
private val DetailSpeedDeepBlue = Color(0xFF2457C5)

/**
 * Existing Trip detail surface, isolated from the active cockpit so v0.6 slices can evolve
 * independently. #149-#151 own the subsequent detail/map/diagnostic redesign.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TripDetailScreenV05(
    trip: TripSessionEntity,
    vehicles: List<VehicleEntity>,
    points: List<TripPointEntity>,
    onBack: () -> Unit
) {
    val vehicle = vehicles.firstOrNull { it.id == trip.vehicleId }
    val firstPoint = points.firstOrNull()
    val lastPoint = points.lastOrNull()
    val wholeTripAverageMps = if (trip.elapsedSeconds > 0) trip.distanceMeters / trip.elapsedSeconds else null
    val context = LocalContext.current
    val addressResolver = remember(context) { AndroidGeocoderAddressResolver(context) }
    var startAddress by remember(trip.id) { mutableStateOf<String?>(null) }
    var endAddress by remember(trip.id) { mutableStateOf<String?>(null) }
    var resolvingAddresses by remember(trip.id) { mutableStateOf(false) }
    val elevationSummary = remember(points) { TripElevationAnalytics.summarize(points) }
    val hasAltitude = elevationSummary != null || listOf(
        trip.startAltitudeMeters,
        trip.endAltitudeMeters,
        trip.minAltitudeMeters,
        trip.maxAltitudeMeters
    ).any { it != null }
    val hasVehicleStateData = listOf(
        trip.startSoc,
        trip.endSoc,
        trip.startMileageKm,
        trip.endMileageKm,
        trip.consumedEnergyKwh,
        trip.averageConsumptionKwhPer100Km
    ).any { it != null }
    val geometry = remember(points) {
        if (points.size >= 2) TripRouteGeometryBuilder.build(points.map { it.toDetailRouteGeoPoint() }) else null
    }

    LaunchedEffect(trip.id, trip.status, firstPoint?.id, lastPoint?.id) {
        if (trip.status == TripStatus.RECORDING || (firstPoint == null && lastPoint == null)) {
            startAddress = null
            endAddress = null
            resolvingAddresses = false
            return@LaunchedEffect
        }
        resolvingAddresses = true
        startAddress = firstPoint?.let { addressResolver.reverse(it.latitude, it.longitude) }
        endAddress = when {
            lastPoint == null -> null
            firstPoint != null && firstPoint.latitude == lastPoint.latitude && firstPoint.longitude == lastPoint.longitude -> startAddress
            else -> addressResolver.reverse(lastPoint.latitude, lastPoint.longitude)
        }
        resolvingAddresses = false
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
                    Column(Modifier.background(DetailHeroBrush).padding(MaterialTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("TRIP / SUMMARY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Text(vehicle?.let { "${it.brand} ${it.model}" } ?: "车辆 #${trip.vehicleId}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                Text("${formatDetailTime(trip.startedAtEpochMillis)} · ${detailStatusText(trip.status)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            geometry?.let { DetailStatusPill(if (it.gapCount > 0) "GPS ${it.gapCount} 缺口" else "GPS 连续", it.gapCount > 0) }
                        }
                        Text("行程距离", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatDetailDistance(trip.distanceMeters), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .28f))
                        DetailResponsiveMetrics(
                            listOf(
                                DetailMetricData("总耗时", formatDetailDuration(trip.elapsedSeconds)),
                                DetailMetricData("全程均速", wholeTripAverageMps?.let(::formatDetailSpeed) ?: "--"),
                                DetailMetricData("行驶均速", trip.averageSpeedMps?.let(::formatDetailSpeed) ?: "--"),
                                DetailMetricData("最高速度", trip.maxSpeedMps?.let(::formatDetailSpeed) ?: "--"),
                                DetailMetricData("移动时间", trip.movingSeconds?.let(::formatDetailDuration) ?: "--"),
                                DetailMetricData("GPS 点", points.size.toString())
                            )
                        )
                    }
                }
            }

            if (hasVehicleStateData) {
                item {
                    DetailSectionHeading("能耗与车辆状态", "SOC 能耗基于配置电池容量和整数 SOC 变化估算，不冒充 BMS 实测值")
                    Spacer(Modifier.height(MaterialTheme.spacing.sm))
                    DetailResponsiveMetrics(
                        listOf(
                            DetailMetricData("SOC", formatDetailSocRange(trip.startSoc, trip.endSoc)),
                            DetailMetricData("估算消耗", formatDetailEnergyKwh(trip.consumedEnergyKwh)),
                            DetailMetricData("估算能耗", formatDetailConsumption(trip.averageConsumptionKwhPer100Km)),
                            DetailMetricData("总里程", formatDetailMileageRange(trip.startMileageKm, trip.endMileageKm))
                        )
                    )
                    if (trip.startSoc != null && trip.endSoc != null && trip.endSoc >= trip.startSoc && trip.consumedEnergyKwh == null) {
                        Spacer(Modifier.height(MaterialTheme.spacing.sm))
                        Text(
                            "SOC 未下降或出现回升，本次不强行推算行驶能耗；可能来自整数取整、能量回收或中途补能。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            geometry?.let { if (it.isDrawable) item { DetailRoutePreview(points) } }

            if (hasAltitude) {
                item {
                    DetailSectionHeading(
                        "海拔",
                        if ((elevationSummary?.skippedLongGapCount ?: 0) > 0) {
                            "来自可信定位海拔；GPS 长缺口两侧不会被拼成累计爬升/下降"
                        } else {
                            "来自可信定位海拔；累计爬升/下降会抑制小幅 GPS 垂直抖动"
                        }
                    )
                    Spacer(Modifier.height(MaterialTheme.spacing.sm))
                    DetailResponsiveMetrics(
                        listOf(
                            DetailMetricData("起点海拔", formatDetailAltitude(elevationSummary?.startAltitudeMeters ?: trip.startAltitudeMeters)),
                            DetailMetricData("终点海拔", formatDetailAltitude(elevationSummary?.endAltitudeMeters ?: trip.endAltitudeMeters)),
                            DetailMetricData("最低海拔", formatDetailAltitude(elevationSummary?.minAltitudeMeters ?: trip.minAltitudeMeters)),
                            DetailMetricData("最高海拔", formatDetailAltitude(elevationSummary?.maxAltitudeMeters ?: trip.maxAltitudeMeters)),
                            DetailMetricData(
                                "累计爬升",
                                if (elevationSummary?.hasCumulativeEstimate == true) formatDetailAltitude(elevationSummary.elevationGainMeters) else "--"
                            ),
                            DetailMetricData(
                                "累计下降",
                                if (elevationSummary?.hasCumulativeEstimate == true) formatDetailAltitude(elevationSummary.elevationLossMeters) else "--"
                            )
                        )
                    )
                    elevationSummary?.takeIf { it.skippedLongGapCount > 0 }?.let {
                        Spacer(Modifier.height(MaterialTheme.spacing.sm))
                        Text(
                            "${it.skippedLongGapCount} 个 GPS 长缺口已从累计海拔变化中断开；不会用缺失区间的高度差制造地形变化。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                DetailSectionHeading("轨迹可信度", if ((geometry?.gapCount ?: 0) > 0) "存在长时间 GPS 缺口，缺失区间保持断开" else "当前轨迹未检测到超过 2 分钟的长缺口")
                Spacer(Modifier.height(MaterialTheme.spacing.sm))
                DetailEndpointRow("起点", firstPoint, startAddress, resolvingAddresses, trip.status == TripStatus.RECORDING)
                Spacer(Modifier.height(MaterialTheme.spacing.md))
                DetailEndpointRow("终点", lastPoint, endAddress, resolvingAddresses, trip.status == TripStatus.RECORDING)
                if (points.isEmpty()) Text("本次没有保存有效 GPS 轨迹点。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (points.isNotEmpty()) {
                item { DetailSectionHeading("最近轨迹点", "用于排查 GPS 质量，不作为主视觉") }
                items(points.takeLast(6).reversed(), key = { it.id }) { point ->
                    Row(Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.xs), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(SimpleDateFormat("HH:mm:ss", Locale.SIMPLIFIED_CHINESE).format(Date(point.capturedAtEpochMillis)), fontWeight = FontWeight.SemiBold)
                            Text("${formatDetailCoordinate(point.latitude)}, ${formatDetailCoordinate(point.longitude)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        point.horizontalAccuracyMeters?.let { Text("±${it.toInt()} m", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailEndpointRow(
    label: String,
    point: TripPointEntity?,
    address: String?,
    resolving: Boolean,
    tripRecording: Boolean
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            when {
                point == null -> "--"
                tripRecording -> "行程进行中，结束后解析地址"
                resolving -> "地址解析中…"
                !address.isNullOrBlank() -> address
                else -> "地址暂不可用"
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        if (point != null) {
            Text(
                "坐标 · ${formatDetailCoordinate(point.latitude)}, ${formatDetailCoordinate(point.longitude)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class DetailMetricData(val label: String, val value: String)

@Composable
private fun DetailResponsiveMetrics(metrics: List<DetailMetricData>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 360.dp || LocalConfiguration.current.fontScale >= 1.3f
        val columns = if (compact) 2 else 3
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            metrics.chunked(columns).forEach { rowMetrics ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
                    rowMetrics.forEach { metric -> DetailMetric(metric.label, metric.value, Modifier.weight(1f)) }
                    repeat(columns - rowMetrics.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun DetailMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun DetailSectionHeading(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DetailStatusPill(text: String, warning: Boolean) {
    val color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Surface(shape = CircleShape, color = color.copy(alpha = .12f)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (warning) Icons.Default.WarningAmber else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Text(text, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

@Composable
private fun DetailRoutePreview(points: List<TripPointEntity>) {
    val geometry = remember(points) {
        TripRouteGeometryBuilder.build(points.map { it.toDetailRouteGeoPoint() })
    }
    if (geometry == null || !geometry.isDrawable) return
    val fallbackRouteColor = MaterialTheme.colorScheme.outline.copy(alpha = .72f)
    val startColor = MaterialTheme.colorScheme.tertiary
    val endColor = MaterialTheme.colorScheme.error

    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("真实轨迹", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${geometry.points.size} 点 · ${geometry.segments.size} 段", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DetailStatusPill(if (geometry.gapCount > 0) "${geometry.gapCount} 个长缺口" else "轨迹连续", geometry.gapCount > 0)
            }

            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .semantics { contentDescription = "真实行程轨迹；轨迹颜色表示本车速度，圆形标记为起点，方形标记为终点" }
            ) {
                val p = 16.dp.toPx()
                val w = (size.width - p * 2).coerceAtLeast(1f)
                val h = (size.height - p * 2).coerceAtLeast(1f)
                fun offset(point: com.evchargebook.domain.trip.TripRoutePoint) = Offset(p + point.x * w, p + point.y * h)
                geometry.segments.forEach { segment ->
                    segment.zipWithNext().forEach { (fromPoint, toPoint) ->
                        val from = offset(fromPoint)
                        val to = offset(toPoint)
                        val segmentSpeed = detailAverageSpeed(fromPoint.speedMps, toPoint.speedMps)
                        drawLine(detailSpeedRouteColor(segmentSpeed, fallbackRouteColor), from, to, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
                val start = offset(geometry.points.first())
                val end = offset(geometry.points.last())
                drawCircle(startColor, 8.dp.toPx(), start)
                val endSize = 14.dp.toPx()
                drawRect(
                    color = endColor,
                    topLeft = Offset(end.x - endSize / 2, end.y - endSize / 2),
                    size = Size(endSize, endSize)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs)) {
                Text("车辆速度分布", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(
                            Brush.horizontalGradient(listOf(DetailSpeedDeepRed, DetailSpeedRed, DetailSpeedYellow, DetailSpeedGreen, DetailSpeedBlue, DetailSpeedDeepBlue)),
                            CircleShape
                        )
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("15", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("30", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("70", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("90+ km/h", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("颜色仅表示本车可信 GPS 速度，不代表道路拥堵或交通状态。灰色线段表示速度数据不足。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(startColor, CircleShape))
                    Spacer(Modifier.width(MaterialTheme.spacing.xs))
                    Text("起点 · 圆形", style = MaterialTheme.typography.labelMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(endColor))
                    Spacer(Modifier.width(MaterialTheme.spacing.xs))
                    Text("终点 · 方形", style = MaterialTheme.typography.labelMedium)
                }
            }
            if (geometry.gapCount > 0) {
                Text("检测到 ${geometry.gapCount} 处超过 2 分钟的 GPS 缺口。断点保持断开，不使用实线伪装连续。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Text("基于真实 WGS84 轨迹点绘制，不做道路吸附或虚构路线。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun TripPointEntity.toDetailRouteGeoPoint(): TripGeoPoint {
    val trustedSpeed = speedMps.takeIf {
        TripSpeedTrustRules.eligibleForMeasuredSpeed(
            reportedSpeedMps = speedMps,
            provider = provider,
            horizontalAccuracyMeters = horizontalAccuracyMeters,
            speedAccuracyMps = speedAccuracyMps
        )
    }
    return TripGeoPoint(
        latitude = latitude,
        longitude = longitude,
        capturedAtEpochMillis = capturedAtEpochMillis,
        speedMps = trustedSpeed
    )
}

private fun detailAverageSpeed(first: Double?, second: Double?): Double? =
    if (first != null && second != null) (first + second) / 2.0 else null

private fun detailSpeedRouteColor(speedMps: Double?, fallback: Color): Color {
    val kmh = speedMps?.times(3.6)?.takeIf { it.isFinite() && it >= 0.0 } ?: return fallback
    return when {
        kmh <= 5.0 -> DetailSpeedDeepRed
        kmh <= 15.0 -> DetailSpeedRed
        kmh <= 30.0 -> detailBlendColor(DetailSpeedRed, DetailSpeedYellow, ((kmh - 15.0) / 15.0).toFloat())
        kmh <= 50.0 -> detailBlendColor(DetailSpeedYellow, DetailSpeedGreen, ((kmh - 30.0) / 20.0).toFloat())
        kmh <= 70.0 -> DetailSpeedGreen
        kmh <= 90.0 -> detailBlendColor(DetailSpeedGreen, DetailSpeedBlue, ((kmh - 70.0) / 20.0).toFloat())
        else -> detailBlendColor(DetailSpeedBlue, DetailSpeedDeepBlue, ((kmh - 90.0) / 40.0).toFloat().coerceIn(0f, 1f))
    }
}

private fun detailBlendColor(from: Color, to: Color, fraction: Float): Color {
    val value = fraction.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * value,
        green = from.green + (to.green - from.green) * value,
        blue = from.blue + (to.blue - from.blue) * value,
        alpha = from.alpha + (to.alpha - from.alpha) * value
    )
}

private fun formatDetailSocRange(start: Int?, end: Int?): String =
    "${start?.let { "$it%" } ?: "--"} → ${end?.let { "$it%" } ?: "--"}"

private fun formatDetailEnergyKwh(value: Double?): String =
    value?.takeIf { it.isFinite() && it >= 0.0 }?.let { String.format(Locale.US, "%.1f kWh", it) } ?: "--"

private fun formatDetailConsumption(value: Double?): String =
    value?.takeIf { it.isFinite() && it >= 0.0 }?.let { String.format(Locale.US, "%.1f kWh/100km", it) } ?: "--"

private fun formatDetailMileageRange(start: Double?, end: Double?): String =
    when {
        start != null && end != null -> "${formatDetailMileage(start)} → ${formatDetailMileage(end)} km"
        start != null -> "${formatDetailMileage(start)} → -- km"
        end != null -> "-- → ${formatDetailMileage(end)} km"
        else -> "--"
    }

private fun formatDetailMileage(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)

private fun formatDetailTime(epochMillis: Long) = SimpleDateFormat("M月d日 HH:mm", Locale.SIMPLIFIED_CHINESE).format(Date(epochMillis))
private fun formatDetailDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "${h}小时${m}分" else if (m > 0) "${m}分${s}秒" else "${s}秒"
}
private fun formatDetailDistance(meters: Double) = if (meters >= 1000.0) String.format(Locale.US, "%.2f km", meters / 1000.0) else String.format(Locale.US, "%.0f m", meters)
private fun formatDetailSpeed(mps: Double) = String.format(Locale.US, "%.1f km/h", mps * 3.6)
private fun formatDetailAltitude(meters: Double?) = meters?.takeIf { it.isFinite() }?.let { String.format(Locale.US, "%.0f m", it) } ?: "--"
private fun formatDetailCoordinate(value: Double) = String.format(Locale.US, "%.6f", value)
private fun detailStatusText(status: String) = when (status) {
    TripStatus.RECORDING -> "进行中"
    TripStatus.INTERRUPTED -> "已中断，可恢复"
    TripStatus.COMPLETED -> "已完成"
    else -> status
}
