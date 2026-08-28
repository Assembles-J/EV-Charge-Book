package com.evchargebook.ui.trip

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
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
import com.evchargebook.ui.theme.EVDesignTokens
import com.evchargebook.ui.theme.spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v0.6 completed/selected Trip detail.
 *
 * The overview intentionally keeps route endpoints in one compact card and moves GPS diagnostics
 * behind progressive disclosure. Missing legacy data remains unavailable rather than inferred.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TripDetailScreenV06(
    trip: TripSessionEntity,
    vehicles: List<VehicleEntity>,
    points: List<TripPointEntity>,
    onBack: () -> Unit
) {
    val vehicle = vehicles.firstOrNull { it.id == trip.vehicleId }
    val firstPoint = points.firstOrNull()
    val lastPoint = points.lastOrNull()
    val context = LocalContext.current
    val addressResolver = remember(context) { AndroidGeocoderAddressResolver(context) }
    var startAddress by remember(trip.id) { mutableStateOf<String?>(null) }
    var endAddress by remember(trip.id) { mutableStateOf<String?>(null) }
    var resolvingAddresses by remember(trip.id) { mutableStateOf(false) }
    var diagnosticsExpanded by remember(trip.id) { mutableStateOf(false) }

    val geometry = remember(points) {
        if (points.size >= 2) TripRouteGeometryBuilder.build(points.map { it.toV06RoutePoint() }) else null
    }
    val elevationSummary = remember(points) { TripElevationAnalytics.summarize(points) }
    val wholeTripAverageMps = if (trip.elapsedSeconds > 0 && trip.distanceMeters >= 0.0) {
        trip.distanceMeters / trip.elapsedSeconds
    } else {
        null
    }
    val displayAverageSpeed = trip.averageSpeedMps ?: wholeTripAverageMps
    val hasVehicleState = listOf(
        trip.startSoc,
        trip.endSoc,
        trip.startMileageKm,
        trip.endMileageKm,
        trip.consumedEnergyKwh,
        trip.averageConsumptionKwhPer100Km
    ).any { it != null }
    val hasAltitude = elevationSummary != null || listOf(
        trip.startAltitudeMeters,
        trip.endAltitudeMeters,
        trip.minAltitudeMeters,
        trip.maxAltitudeMeters
    ).any { it != null }

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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("行程详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
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
                CompletedTripSummaryCardV06(
                    trip = trip,
                    vehicle = vehicle,
                    averageSpeedMps = displayAverageSpeed,
                    hasVehicleState = hasVehicleState
                )
            }

            item {
                CompletedTripEndpointCardV06(
                    trip = trip,
                    startPoint = firstPoint,
                    endPoint = lastPoint,
                    startAddress = startAddress,
                    endAddress = endAddress,
                    resolving = resolvingAddresses
                )
            }

            geometry?.takeIf { it.isDrawable }?.let {
                item {
                    CompletedTripRoutePreviewV06(
                        points = points,
                        finalEndpoint = trip.status == TripStatus.COMPLETED
                    )
                }
            }

            if (hasAltitude) {
                item {
                    CompletedTripAltitudeCardV06(
                        trip = trip,
                        points = points
                    )
                }
            }

            item { CompletedTripTrendsV06(points) }

            item {
                CompletedTripDiagnosticsV06(
                    points = points,
                    gapCount = geometry?.gapCount ?: 0,
                    expanded = diagnosticsExpanded,
                    onToggle = { diagnosticsExpanded = !diagnosticsExpanded }
                )
            }

            item { Spacer(Modifier.height(MaterialTheme.spacing.md)) }
        }
    }
}

@Composable
private fun CompletedTripSummaryCardV06(
    trip: TripSessionEntity,
    vehicle: VehicleEntity?,
    averageSpeedMps: Double?,
    hasVehicleState: Boolean
) {
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
                        vehicle?.let { "${it.brand} ${it.model}" } ?: "车辆 #${trip.vehicleId}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${formatV06DateTime(trip.startedAtEpochMillis)} · ${statusV06Text(trip.status)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (trip.status == TripStatus.INTERRUPTED) {
                    Icon(
                        Icons.Default.WarningAmber,
                        contentDescription = "行程记录中断",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
                verticalAlignment = Alignment.Bottom
            ) {
                SummaryPrimaryMetricV06("距离", formatV06Distance(trip.distanceMeters), Modifier.weight(1f))
                SummaryPrimaryMetricV06("耗时", formatV06Duration(trip.elapsedSeconds), Modifier.weight(1f))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .28f))

            SummaryMetricGridV06(
                listOf(
                    SummaryMetricV06("平均速度", averageSpeedMps?.let(::formatV06Speed) ?: "--"),
                    SummaryMetricV06("估算能耗", formatV06Consumption(trip.averageConsumptionKwhPer100Km)),
                    SummaryMetricV06("SOC", formatV06SocRange(trip.startSoc, trip.endSoc)),
                    SummaryMetricV06("总里程", formatV06MileageRange(trip.startMileageKm, trip.endMileageKm)),
                    SummaryMetricV06("最高速度", trip.maxSpeedMps?.let(::formatV06Speed) ?: "--"),
                    SummaryMetricV06("移动时间", trip.movingSeconds?.let(::formatV06Duration) ?: "--")
                )
            )

            if (hasVehicleState) {
                Text(
                    "SOC / 能耗沿用现有估算口径，非 BMS 实测；缺失、SOC 回升或无法可信计算时保持 --。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CompletedTripEndpointCardV06(
    trip: TripSessionEntity,
    startPoint: TripPointEntity?,
    endPoint: TripPointEntity?,
    startAddress: String?,
    endAddress: String?,
    resolving: Boolean
) {
    val accent = EVDesignTokens.Energy.green
    val finalEndpoint = trip.status == TripStatus.COMPLETED
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
        ) {
            EndpointLineV06(
                label = "起点",
                point = startPoint,
                address = startAddress,
                resolving = resolving,
                recording = trip.status == TripStatus.RECORDING,
                recordingText = "行程进行中，结束后解析地址",
                icon = {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .22f))
            if (finalEndpoint) {
                EndpointLineV06(
                    label = "终点",
                    point = endPoint,
                    address = endAddress,
                    resolving = resolving,
                    recording = false,
                    icon = {
                        Icon(
                            Icons.Default.Flag,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                )
            } else {
                EndpointLineV06(
                    label = if (trip.status == TripStatus.RECORDING) "当前点" else "最后记录点",
                    point = endPoint,
                    address = endAddress,
                    resolving = resolving,
                    recording = trip.status == TripStatus.RECORDING,
                    recordingText = "当前最新定位点",
                    icon = {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun EndpointLineV06(
    label: String,
    point: TripPointEntity?,
    address: String?,
    resolving: Boolean,
    recording: Boolean,
    recordingText: String? = null,
    icon: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
    ) {
        Column(
            modifier = Modifier.width(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            icon()
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                when {
                    point == null -> "暂无保存的${label}"
                    recording -> recordingText ?: "行程进行中"
                    resolving -> "地址解析中…"
                    !address.isNullOrBlank() -> address
                    else -> "地址暂不可用"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (point != null) {
                Text(
                    "${formatV06Coordinate(point.latitude)}, ${formatV06Coordinate(point.longitude)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class SummaryMetricV06(val label: String, val value: String)

@Composable
private fun SummaryPrimaryMetricV06(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SummaryMetricGridV06(metrics: List<SummaryMetricV06>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 360.dp || LocalConfiguration.current.fontScale >= 1.3f
        val columns = if (compact) 2 else 3
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            metrics.chunked(columns).forEach { rowMetrics ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
                ) {
                    rowMetrics.forEach { metric ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(metric.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(metric.value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    repeat(columns - rowMetrics.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun CompletedTripRoutePreviewV06(points: List<TripPointEntity>, finalEndpoint: Boolean) {
    val accent = EVDesignTokens.Energy.green
    val geometry = remember(points) {
        TripRouteGeometryBuilder.build(points.map { it.toV06RoutePoint() })
    } ?: return
    if (!geometry.isDrawable) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("轨迹", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    if (geometry.gapCount > 0) "${geometry.gapCount} 个长缺口" else "${geometry.points.size} 个 GPS 点",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (geometry.gapCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val endColor = MaterialTheme.colorScheme.error
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .semantics {
                        contentDescription = if (finalEndpoint) {
                            "真实行程轨迹；绿色播放标记为起点，红旗标记为终点，长 GPS 缺口保持断开"
                        } else {
                            "真实行程轨迹；绿色播放标记为起点，绿色圆点为当前或最后记录点，长 GPS 缺口保持断开"
                        }
                    }
            ) {
                val pad = 12.dp.toPx()
                val width = (size.width - pad * 2).coerceAtLeast(1f)
                val height = (size.height - pad * 2).coerceAtLeast(1f)
                fun offset(x: Float, y: Float) = Offset(pad + x * width, pad + y * height)

                geometry.segments.forEach { segment ->
                    segment.zipWithNext().forEach { (from, to) ->
                        drawLine(
                            color = accent.copy(alpha = .78f),
                            start = offset(from.x, from.y),
                            end = offset(to.x, to.y),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                val start = geometry.points.first().let { offset(it.x, it.y) }
                val end = geometry.points.last().let { offset(it.x, it.y) }
                val marker = 6.dp.toPx()
                val startTriangle = Path().apply {
                    moveTo(start.x - marker * .45f, start.y - marker)
                    lineTo(start.x + marker, start.y)
                    lineTo(start.x - marker * .45f, start.y + marker)
                    close()
                }
                drawPath(startTriangle, accent)

                if (finalEndpoint) {
                    val poleHeight = 13.dp.toPx()
                    drawLine(endColor, end, Offset(end.x, end.y - poleHeight), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    val flag = Path().apply {
                        moveTo(end.x, end.y - poleHeight)
                        lineTo(end.x + 9.dp.toPx(), end.y - poleHeight + 3.dp.toPx())
                        lineTo(end.x, end.y - poleHeight + 6.dp.toPx())
                        close()
                    }
                    drawPath(flag, endColor)
                } else {
                    drawCircle(accent.copy(alpha = .22f), 6.dp.toPx(), end)
                    drawCircle(accent, 3.dp.toPx(), end)
                }
            }

            if (geometry.gapCount > 0) {
                Text(
                    "GPS 长缺口不会用实线补齐。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CompletedTripAltitudeCardV06(trip: TripSessionEntity, points: List<TripPointEntity>) {
    val summary = remember(points) { TripElevationAnalytics.summarize(points) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
        ) {
            Text("海拔", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            SummaryMetricGridV06(
                listOf(
                    SummaryMetricV06("起点", formatV06Altitude(summary?.startAltitudeMeters ?: trip.startAltitudeMeters)),
                    SummaryMetricV06("终点", formatV06Altitude(summary?.endAltitudeMeters ?: trip.endAltitudeMeters)),
                    SummaryMetricV06("最低", formatV06Altitude(summary?.minAltitudeMeters ?: trip.minAltitudeMeters)),
                    SummaryMetricV06("最高", formatV06Altitude(summary?.maxAltitudeMeters ?: trip.maxAltitudeMeters)),
                    SummaryMetricV06("累计爬升", if (summary?.hasCumulativeEstimate == true) formatV06Altitude(summary.elevationGainMeters) else "--"),
                    SummaryMetricV06("累计下降", if (summary?.hasCumulativeEstimate == true) formatV06Altitude(summary.elevationLossMeters) else "--")
                )
            )
            if ((summary?.skippedLongGapCount ?: 0) > 0) {
                Text(
                    "${summary?.skippedLongGapCount} 个 GPS 长缺口已从累计海拔变化中断开。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CompletedTripDiagnosticsV06(
    points: List<TripPointEntity>,
    gapCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("GPS 诊断", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${points.size} 点${if (gapCount > 0) " · $gapCount 个长缺口" else " · 未检测到长缺口"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onToggle) { Text(if (expanded) "收起" else "查看轨迹点") }
            }

            if (expanded) {
                Spacer(Modifier.height(MaterialTheme.spacing.xs))
                if (points.isEmpty()) {
                    Text("本次没有保存有效 GPS 轨迹点。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    points.takeLast(6).reversed().forEachIndexed { index, point ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .16f))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.xs),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    SimpleDateFormat("HH:mm:ss", Locale.SIMPLIFIED_CHINESE).format(Date(point.capturedAtEpochMillis)),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${formatV06Coordinate(point.latitude)}, ${formatV06Coordinate(point.longitude)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            point.horizontalAccuracyMeters?.takeIf { it.isFinite() }?.let {
                                Text("±${it.toInt()} m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun TripPointEntity.toV06RoutePoint(): TripGeoPoint {
    val trustedSpeed = speedMps?.takeIf { speed ->
        TripSpeedTrustRules.eligibleForMeasuredSpeed(
            reportedSpeedMps = speed,
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

private fun formatV06DateTime(epochMillis: Long): String =
    SimpleDateFormat("M月d日 HH:mm", Locale.SIMPLIFIED_CHINESE).format(Date(epochMillis))

private fun formatV06Duration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${secs}s"
        else -> "${secs}s"
    }
}

private fun formatV06Distance(meters: Double): String =
    if (meters.isFinite() && meters >= 1000.0) String.format(Locale.US, "%.2f km", meters / 1000.0)
    else if (meters.isFinite() && meters >= 0.0) String.format(Locale.US, "%.0f m", meters)
    else "--"

private fun formatV06Speed(mps: Double): String =
    mps.takeIf { it.isFinite() && it >= 0.0 }?.let { String.format(Locale.US, "%.1f km/h", it * 3.6) } ?: "--"

private fun formatV06Consumption(value: Double?): String =
    value?.takeIf { it.isFinite() && it >= 0.0 }?.let { String.format(Locale.US, "%.1f kWh/100km", it) } ?: "--"

private fun formatV06SocRange(start: Int?, end: Int?): String =
    "${start?.let { "$it%" } ?: "--"} → ${end?.let { "$it%" } ?: "--"}"

private fun formatV06MileageRange(start: Double?, end: Double?): String = when {
    start?.isFinite() == true && end?.isFinite() == true -> "${formatV06Mileage(start)} → ${formatV06Mileage(end)} km"
    start?.isFinite() == true -> "${formatV06Mileage(start)} → -- km"
    end?.isFinite() == true -> "-- → ${formatV06Mileage(end)} km"
    else -> "--"
}

private fun formatV06Mileage(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)

private fun formatV06Altitude(value: Double?): String =
    value?.takeIf { it.isFinite() }?.let { String.format(Locale.US, "%.0f m", it) } ?: "--"

private fun formatV06Coordinate(value: Double): String = String.format(Locale.US, "%.6f", value)

private fun statusV06Text(status: String): String = when (status) {
    TripStatus.RECORDING -> "进行中"
    TripStatus.INTERRUPTED -> "已中断"
    TripStatus.COMPLETED -> "已完成"
    else -> status
}
