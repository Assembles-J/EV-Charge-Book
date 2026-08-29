package com.evchargebook.ui.trip

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.TripPointEntity
import com.evchargebook.data.entity.TripSessionEntity
import com.evchargebook.domain.TripContinuityRules
import com.evchargebook.domain.trip.TripEnergyCalculator
import com.evchargebook.location.AndroidGeocoderAddressResolver
import com.evchargebook.ui.theme.EVDesignTokens
import com.evchargebook.ui.theme.spacing
import java.util.Locale
import kotlin.math.abs

/**
 * Trip v0.7 completion surface.
 *
 * SOC adjustment is intentionally inline: there is no keyboard, secondary edit tap, modal picker,
 * or separate estimated-remaining-SOC card. The caller may seed end SOC from an estimator; once the
 * user saves this screen the wheel values are the explicit completion values.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TripCompletionScreenV07(
    activeTrip: TripSessionEntity,
    points: List<TripPointEntity>,
    batteryCapacityKwh: Double?,
    startSoc: Int,
    endSoc: Int,
    onStartSocChange: (Int) -> Unit,
    onEndSocChange: (Int) -> Unit,
    errorText: String?,
    onBack: () -> Unit,
    onSaveAndFinish: () -> Unit
) {
    val accent = EVDesignTokens.Energy.green
    val estimate = TripEnergyCalculator.estimate(
        batteryCapacityKwh = batteryCapacityKwh,
        startSoc = startSoc,
        endSoc = endSoc,
        distanceMeters = activeTrip.distanceMeters
    )
    val firstPoint = points.firstOrNull()
    val lastPoint = points.lastOrNull()
    val context = LocalContext.current
    val addressResolver = remember(context) { AndroidGeocoderAddressResolver(context) }
    var startAddress by remember(activeTrip.id) { mutableStateOf<String?>(null) }
    var endAddress by remember(activeTrip.id) { mutableStateOf<String?>(null) }
    var resolvingAddresses by remember(activeTrip.id) { mutableStateOf(false) }

    LaunchedEffect(activeTrip.id, firstPoint?.id, lastPoint?.id) {
        if (firstPoint == null && lastPoint == null) {
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
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("结束行程", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回继续记录")
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
                CompletionEndpointsV07(
                    points = points,
                    startAddress = startAddress,
                    endAddress = endAddress,
                    resolving = resolvingAddresses
                )
            }
            item {
                CompletionSummaryV07(
                    activeTrip = activeTrip,
                    averageConsumption = estimate.averageConsumptionKwhPer100Km
                )
            }
            item {
                SocAdjustmentCardV07(
                    startSoc = startSoc,
                    endSoc = endSoc,
                    onStartSocChange = onStartSocChange,
                    onEndSocChange = onEndSocChange
                )
            }
            item { CompletionRoutePreviewV07(points) }
            errorText?.let { message ->
                item {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            item {
                Button(
                    onClick = onSaveAndFinish,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color(0xFF03120A)
                    )
                ) {
                    Text("保存并结束", fontWeight = FontWeight.SemiBold)
                }
            }
            item { Spacer(Modifier.height(MaterialTheme.spacing.md)) }
        }
    }
}

@Composable
private fun CompletionEndpointsV07(
    points: List<TripPointEntity>,
    startAddress: String?,
    endAddress: String?,
    resolving: Boolean
) {
    val accent = EVDesignTokens.Energy.green
    val start = points.firstOrNull()
    val end = points.lastOrNull()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EndpointRowV07(
                icon = {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(17.dp)
                    )
                },
                label = "起点",
                value = endpointTextV07(start, startAddress, resolving, "暂无可信起点")
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .22f))
            EndpointRowV07(
                icon = {
                    Icon(
                        Icons.Default.Flag,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = "终点",
                value = endpointTextV07(end, endAddress, resolving, "暂无可信终点")
            )
        }
    }
}

@Composable
private fun EndpointRowV07(
    icon: @Composable () -> Unit,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        icon()
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

@Composable
private fun CompletionSummaryV07(activeTrip: TripSessionEntity, averageConsumption: Double?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CompletionMetricV07("距离", formatDistanceV07(activeTrip.distanceMeters), Modifier.weight(1f))
            CompletionMetricV07("时长", formatDurationV07(activeTrip.elapsedSeconds), Modifier.weight(1f))
            CompletionMetricV07(
                "平均能耗",
                averageConsumption?.let { String.format(Locale.US, "%.1f", it) } ?: "--",
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CompletionMetricV07(label: String, value: String, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun SocAdjustmentCardV07(
    startSoc: Int,
    endSoc: Int,
    onStartSocChange: (Int) -> Unit,
    onEndSocChange: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("SOC 调整", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactSocWheelV07("开始电量", startSoc, onStartSocChange, Modifier.weight(1f))
                CompactSocWheelV07("结束电量", endSoc, onEndSocChange, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CompactSocWheelV07(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = EVDesignTokens.Energy.green
    val thresholdPx = with(LocalDensity.current) { 24.dp.toPx() }
    var accumulatedDrag by remember(value) { mutableFloatStateOf(0f) }
    val previous = (value - 1).coerceAtLeast(0)
    val next = (value + 1).coerceAtMost(100)

    Column(
        modifier = modifier
            .semantics { contentDescription = "$label $value%，上下滑动调整" }
            .pointerInput(value) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDrag += dragAmount
                        if (abs(accumulatedDrag) >= thresholdPx) {
                            val newValue = if (accumulatedDrag > 0f) value - 1 else value + 1
                            onValueChange(newValue.coerceIn(0, 100))
                            accumulatedDrag = 0f
                        }
                    },
                    onDragEnd = { accumulatedDrag = 0f },
                    onDragCancel = { accumulatedDrag = 0f }
                )
            }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (value > 0) previous.toString() else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .42f)
        )
        Surface(shape = MaterialTheme.shapes.medium, color = accent.copy(alpha = .10f)) {
            Text(
                "$value%",
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent
            )
        }
        Text(
            if (value < 100) next.toString() else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .42f)
        )
    }
}

@Composable
private fun CompletionRoutePreviewV07(points: List<TripPointEntity>) {
    val valid = remember(points) { points.filter { it.latitude.isFinite() && it.longitude.isFinite() } }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("行程轨迹", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            if (valid.size < 2) {
                Text("轨迹点不足，暂不绘制路线", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                MiniRouteCanvasV07(valid)
            }
        }
    }
}

@Composable
private fun MiniRouteCanvasV07(points: List<TripPointEntity>) {
    val accent = EVDesignTokens.Energy.green
    val endColor = MaterialTheme.colorScheme.error
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .semantics { contentDescription = "本次行程轨迹预览" }
    ) {
        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }
        val maxLon = points.maxOf { it.longitude }
        val latSpan = (maxLat - minLat).takeIf { abs(it) > 1e-9 } ?: 1e-9
        val lonSpan = (maxLon - minLon).takeIf { abs(it) > 1e-9 } ?: 1e-9
        val inset = 12.dp.toPx()
        val width = (size.width - inset * 2).coerceAtLeast(1f)
        val height = (size.height - inset * 2).coerceAtLeast(1f)

        fun offset(point: TripPointEntity): Offset {
            val x = inset + (((point.longitude - minLon) / lonSpan).toFloat() * width)
            val y = inset + ((1f - ((point.latitude - minLat) / latSpan).toFloat()) * height)
            return Offset(x, y)
        }

        points.zipWithNext().forEach { (from, to) ->
            val gapSeconds = (to.capturedAtEpochMillis - from.capturedAtEpochMillis) / 1000L
            if (gapSeconds < TripContinuityRules.LONG_GAP_SECONDS) {
                drawLine(
                    color = accent,
                    start = offset(from),
                    end = offset(to),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        val start = offset(points.first())
        val end = offset(points.last())
        drawCircle(color = accent, radius = 5.dp.toPx(), center = start)

        val poleHeight = 13.dp.toPx()
        val poleTop = Offset(end.x, end.y - poleHeight / 2)
        val poleBottom = Offset(end.x, end.y + poleHeight / 2)
        drawLine(endColor, poleTop, poleBottom, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        val flag = Path().apply {
            moveTo(poleTop.x, poleTop.y)
            lineTo(poleTop.x + 10.dp.toPx(), poleTop.y + 3.dp.toPx())
            lineTo(poleTop.x, poleTop.y + 7.dp.toPx())
            close()
        }
        drawPath(flag, endColor)
    }
}

private fun endpointTextV07(
    point: TripPointEntity?,
    address: String?,
    resolving: Boolean,
    missingText: String
): String = when {
    point == null -> missingText
    resolving -> "地址解析中…"
    !address.isNullOrBlank() -> address
    else -> formatPointV07(point)
}

private fun formatPointV07(point: TripPointEntity): String =
    String.format(Locale.US, "%.5f, %.5f", point.latitude, point.longitude)

private fun formatDistanceV07(meters: Double): String = when {
    !meters.isFinite() || meters < 0.0 -> "--"
    meters >= 1000.0 -> String.format(Locale.US, "%.1f km", meters / 1000.0)
    else -> String.format(Locale.US, "%.0f m", meters)
}

private fun formatDurationV07(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    return when {
        hours > 0 -> "${hours}小时${minutes}分"
        else -> "${minutes}分钟"
    }
}
