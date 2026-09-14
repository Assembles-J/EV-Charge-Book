package com.evchargebook.ui.trip

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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

private enum class TripDetailSectionV06(val label: String) {
    OVERVIEW("概览"),
    ROUTE("轨迹"),
    DATA("数据")
}

/**
 * Completed/selected Trip detail.
 *
 * Route and data surfaces intentionally stay isolated. This revision only compacts Overview.
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
    var selectedSection by remember(trip.id) { mutableStateOf(TripDetailSectionV06.OVERVIEW) }

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
            Column {
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
                TripDetailSectionBarV06(
                    selected = selectedSection,
                    onSelected = { selectedSection = it }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
        ) {
            when (selectedSection) {
                TripDetailSectionV06.OVERVIEW -> {
                    item {
                        CompletedTripOverviewV08(
                            trip = trip,
                            vehicle = vehicle,
                            averageSpeedMps = displayAverageSpeed
                        )
                    }
                    item {
                        CompletedTripEndpointCardV08(
                            trip = trip,
                            startPoint = firstPoint,
                            endPoint = lastPoint,
                            startAddress = startAddress,
                            endAddress = endAddress,
                            resolving = resolvingAddresses
                        )
                    }
                }

                TripDetailSectionV06.ROUTE -> {
                    if (geometry?.isDrawable == true) {
                        item {
                            CompletedTripRoutePreviewV06(
                                points = points,
                                finalEndpoint = trip.status == TripStatus.COMPLETED
                            )
                        }
                    } else {
                        item {
                            DetailUnavailableCardV06(
                                title = "暂无可绘制轨迹",
                                detail = if (points.isEmpty()) {
                                    "本次没有保存有效 GPS 轨迹点。"
                                } else {
                                    "当前仅有 ${points.size} 个 GPS 点，尚不足以绘制可信路线。"
                                }
                            )
                        }
                    }
                    item { CompletedTripTrendsV06(trip = trip, points = points) }
                }

                TripDetailSectionV06.DATA -> {
                    if (hasAltitude) {
                        item {
                            CompletedTripAltitudeCardV07(
                                trip = trip,
                                points = points
                            )
                        }
                    } else {
                        item {
                            DetailUnavailableCardV06(
                                title = "暂无可信海拔",
                                detail = "没有通过当前定位精度规则的海拔样本，不展示合成值。"
                            )
                        }
                    }
                    item {
                        CompletedTripDiagnosticsV07(
                            trip = trip,
                            points = points,
                            gapCount = geometry?.gapCount ?: 0,
                            expanded = diagnosticsExpanded,
                            onToggle = { diagnosticsExpanded = !diagnosticsExpanded }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(MaterialTheme.spacing.md)) }
        }
    }
}

@Composable
private fun TripDetailSectionBarV06(
    selected: TripDetailSectionV06,
    onSelected: (TripDetailSectionV06) -> Unit
) {
    val accent = EVDesignTokens.Energy.green
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)
        ) {
            TripDetailSectionV06.entries.forEach { section ->
                Column(modifier = Modifier.weight(1f)) {
                    TextButton(
                        onClick = { onSelected(section) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            section.label,
                            color = if (section == selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (section == selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                    HorizontalDivider(
                        thickness = 2.dp,
                        color = if (section == selected) accent else MaterialTheme.colorScheme.background
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailUnavailableCardV06(title: String, detail: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class OverviewMetricV08(val label: String, val value: String)

@Composable
private fun CompletedTripOverviewV08(
    trip: TripSessionEntity,
    vehicle: VehicleEntity?,
    averageSpeedMps: Double?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        vehicle?.displayName ?: vehicle?.let { "${it.brand} ${it.model}" } ?: "车辆 #${trip.vehicleId}",
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

            OverviewMetricGroupV08(
                title = "行程摘要",
                icon = Icons.Default.Route,
                metrics = buildList {
                    add(OverviewMetricV08("距离", formatV06Distance(trip.distanceMeters)))
                    add(OverviewMetricV08("耗时", formatV06Duration(trip.elapsedSeconds)))
                    add(OverviewMetricV08("平均速度", averageSpeedMps?.let(::formatV06Speed) ?: "--"))
                    if (trip.status == TripStatus.COMPLETED) {
                        add(OverviewMetricV08("估算能耗", formatV06Consumption(trip.averageConsumptionKwhPer100Km)))
                    }
                }
            )

            OverviewMetricGroupV08(
                title = "车辆变化",
                icon = Icons.Default.DirectionsCar,
                metrics = listOf(
                    OverviewMetricV08("SOC 变化", formatV06SocRange(trip.startSoc, trip.endSoc)),
                    OverviewMetricV08("里程变化", formatV06MileageRange(trip.startMileageKm, trip.endMileageKm))
                )
            )

            OverviewMetricGroupV08(
                title = "行驶状态",
                icon = Icons.Default.Speed,
                metrics = listOf(
                    OverviewMetricV08("最高速度", trip.maxSpeedMps?.let(::formatV06Speed) ?: "--"),
                    OverviewMetricV08("移动时间", trip.movingSeconds?.let(::formatV06Duration) ?: "--")
                )
            )
        }
    }
}

@Composable
private fun OverviewMetricGroupV08(
    title: String,
    icon: ImageVector,
    metrics: List<OverviewMetricV08>
) {
    val accent = EVDesignTokens.Energy.green
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .14f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = CircleShape, color = accent.copy(alpha = .10f)) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.padding(6.dp).size(15.dp)
                    )
                }
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
            metrics.chunked(2).forEach { rowMetrics ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    rowMetrics.forEach { metric ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(metric.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(metric.value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (rowMetrics.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CompletedTripEndpointCardV08(
    trip: TripSessionEntity,
    startPoint: TripPointEntity?,
    endPoint: TripPointEntity?,
    startAddress: String?,
    endAddress: String?,
    resolving: Boolean
) {
    val accent = EVDesignTokens.Energy.green
    val finalEndpoint = trip.status == TripStatus.COMPLETED
    val displayPair = remember(startAddress, endAddress) {
        if (!startAddress.isNullOrBlank() && !endAddress.isNullOrBlank()) {
            compactTripEndpointDisplayV08(startAddress, endAddress)
        } else {
            TripEndpointDisplayV08(startAddress.orEmpty(), endAddress.orEmpty())
        }
    }

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
                address = displayPair.start.takeIf { it.isNotBlank() },
                resolving = resolving,
                recording = trip.status == TripStatus.RECORDING,
                recordingText = "行程进行中，结束后解析地址",
                icon = {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .22f))
            if (finalEndpoint) {
                EndpointLineV06(
                    label = "终点",
                    point = endPoint,
                    address = displayPair.end.takeIf { it.isNotBlank() },
                    resolving = resolving,
                    recording = false,
                    icon = {
                        Icon(Icons.Default.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(17.dp))
                    }
                )
            } else {
                EndpointLineV06(
                    label = if (trip.status == TripStatus.RECORDING) "当前点" else "最后记录点",
                    point = endPoint,
                    address = displayPair.end.takeIf { it.isNotBlank() },
                    resolving = resolving,
                    recording = trip.status == TripStatus.RECORDING,
                    recordingText = "当前最新定位点",
                    icon = {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
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

@Composable
private fun CompletedTripRoutePreviewV06(points: List<TripPointEntity>, finalEndpoint: Boolean) {
    val accent = EVDesignTokens.Energy.green
    val geometry = remember(points) {
        TripRouteGeometryBuilder.build(points.map { it.toV06RoutePoint() })
    } ?: return
    if (!geometry.isDrawable) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .16f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    "轨迹",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (geometry.gapCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = .10f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = .28f)),
                    ) {
                        Text(
                            "${geometry.gapCount} 个长缺口",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    Text(
                        "${geometry.points.size} 个 GPS 点",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            TripRouteViewportV07(
                points = points,
                finalEndpoint = finalEndpoint,
                height = 250.dp,
            )

            Text(
                if (geometry.gapCount > 0) {
                    "GPS 长缺口仅用灰色虚线提示未知区间，不会当作真实道路轨迹。"
                } else {
                    "轨迹颜色来自可信 GPS 时速；拖动或双指缩放不会修改行程数据。"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        speedMps = trustedSpeed,
        capturedAtElapsedRealtimeNanos = capturedAtElapsedRealtimeNanos
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

private fun formatV06Coordinate(value: Double): String = String.format(Locale.US, "%.6f", value)

private fun statusV06Text(status: String): String = when (status) {
    TripStatus.RECORDING -> "进行中"
    TripStatus.INTERRUPTED -> "已中断"
    TripStatus.COMPLETED -> "已完成"
    else -> status
}
