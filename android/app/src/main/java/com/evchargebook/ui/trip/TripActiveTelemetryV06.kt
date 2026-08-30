package com.evchargebook.ui.trip

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.TripPointEntity
import com.evchargebook.domain.TripSpeedTrustRules
import com.evchargebook.domain.trip.TripGeoPoint
import com.evchargebook.domain.trip.TripRouteGeometry
import com.evchargebook.domain.trip.TripRouteGeometryBuilder
import com.evchargebook.ui.theme.EVDesignTokens
import com.evchargebook.ui.theme.spacing
import java.util.Locale

private const val ACTIVE_TREND_LONG_GAP_MS = 120_000L

internal data class ActiveTripTelemetrySummary(
    val trustedLatestSpeedMps: Double?,
    val trustedSpeedPointCount: Int,
    val altitudePointCount: Int
)

internal fun summarizeActiveTripTelemetry(points: List<TripPointEntity>): ActiveTripTelemetrySummary {
    val trustedSpeeds = points.mapNotNull { point ->
        point.speedMps?.takeIf {
            TripSpeedTrustRules.eligibleForMeasuredSpeed(
                reportedSpeedMps = point.speedMps,
                provider = point.provider,
                horizontalAccuracyMeters = point.horizontalAccuracyMeters,
                speedAccuracyMps = point.speedAccuracyMps
            )
        }
    }
    val altitudeCount = points.count { point -> trustedAltitude(point) != null }
    return ActiveTripTelemetrySummary(
        trustedLatestSpeedMps = trustedSpeeds.lastOrNull(),
        trustedSpeedPointCount = trustedSpeeds.size,
        altitudePointCount = altitudeCount
    )
}

@Composable
internal fun TripActiveTelemetryV06(
    points: List<TripPointEntity>,
    modifier: Modifier = Modifier
) {
    val accent = EVDesignTokens.Energy.green
    val geometry = remember(points) {
        if (points.size < 2) null
        else TripRouteGeometryBuilder.build(points.map { point ->
            TripGeoPoint(
                latitude = point.latitude,
                longitude = point.longitude,
                capturedAtEpochMillis = point.capturedAtEpochMillis,
                speedMps = trustedSpeed(point)
            )
        })
    }
    val speedSamples = remember(points) {
        points.mapNotNull { point ->
            trustedSpeed(point)?.let { TripTrendSampleV06(point.capturedAtEpochMillis, it * 3.6) }
        }
    }
    val altitudeSamples = remember(points) {
        points.mapNotNull { point ->
            trustedAltitude(point)?.let { TripTrendSampleV06(point.capturedAtEpochMillis, it) }
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Route, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(MaterialTheme.spacing.xs))
                    Text("实时轨迹", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        when {
                            geometry == null -> "等待轨迹"
                            speedSamples.isNotEmpty() -> String.format(
                                Locale.US,
                                "%.1f km/h · %d 点",
                                speedSamples.last().value,
                                geometry.points.size
                            )
                            else -> "${geometry.points.size} 点 · ${geometry.segments.size} 段"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (geometry == null || !geometry.isDrawable) {
                    Column(
                        modifier = Modifier.fillMaxWidth().height(112.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("等待足够的可信 GPS 点", style = MaterialTheme.typography.bodyMedium)
                        Text("不会用虚拟路线填充空白", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    ActiveRouteCanvasV06(
                        geometry = geometry,
                        accent = accent,
                        viewportKey = points.firstOrNull()?.tripId
                    )
                }

                if ((geometry?.gapCount ?: 0) > 0) {
                    Text(
                        "${geometry?.gapCount} 个 GPS 长缺口保持断开，不伪装连续路线。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            TripTrendMiniCard(
                title = "速度趋势",
                unit = "km/h",
                samples = speedSamples,
                modifier = Modifier.fillMaxWidth(),
                emptyText = "暂无可信速度"
            )
            TripTrendMiniCard(
                title = "海拔趋势",
                unit = "m",
                samples = altitudeSamples,
                modifier = Modifier.fillMaxWidth(),
                emptyText = "暂无可信海拔"
            )
        }
    }
}

@Composable
private fun ActiveRouteCanvasV06(
    geometry: TripRouteGeometry,
    accent: androidx.compose.ui.graphics.Color,
    viewportKey: Long?
) {
    var zoom by remember(viewportKey) { mutableFloatStateOf(1f) }
    var pan by remember(viewportKey) { mutableStateOf(Offset.Zero) }
    val viewportChanged = zoom > 1.001f || pan != Offset.Zero

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "双指缩放 · 拖动查看 · 轨迹越亮/越粗表示可信速度越高",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (viewportChanged) {
                TextButton(onClick = { zoom = 1f; pan = Offset.Zero }) {
                    Text("回到全程", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .pointerInput(geometry, zoom, pan) {
                    detectTransformGestures(panZoomLock = true) { _, gesturePan, gestureZoom, _ ->
                        val newZoom = (zoom * gestureZoom).coerceIn(1f, 6f)
                        val maxPanX = size.width.toFloat() * (newZoom - 1f) * 0.5f
                        val maxPanY = size.height.toFloat() * (newZoom - 1f) * 0.5f
                        zoom = newZoom
                        pan = Offset(
                            x = (pan.x + gesturePan.x).coerceIn(-maxPanX, maxPanX),
                            y = (pan.y + gesturePan.y).coerceIn(-maxPanY, maxPanY)
                        )
                    }
                }
                .semantics {
                    contentDescription = "可拖动缩放的当前真实行程轨迹；轨迹亮度和粗细表示可信速度，绿色标记为起点和当前点，断开表示 GPS 长缺口"
                }
        ) {
            val pad = 12.dp.toPx()
            val width = (size.width - pad * 2).coerceAtLeast(1f)
            val height = (size.height - pad * 2).coerceAtLeast(1f)
            val center = Offset(size.width / 2f, size.height / 2f)

            fun offset(x: Float, y: Float): Offset {
                val base = Offset(pad + x * width, pad + y * height)
                return Offset(
                    x = center.x + (base.x - center.x) * zoom + pan.x,
                    y = center.y + (base.y - center.y) * zoom + pan.y
                )
            }

            geometry.segments.forEach { segment ->
                segment.zipWithNext().forEach { (from, to) ->
                    val speedMps = to.speedMps ?: from.speedMps
                    val normalized = speedMps?.let { (it * 3.6 / 120.0).toFloat().coerceIn(0f, 1f) } ?: 0f
                    val alpha = if (speedMps == null) 0.32f else 0.45f + normalized * 0.5f
                    val stroke = (2.5f + normalized * 2.0f).dp.toPx()
                    drawLine(
                        color = accent.copy(alpha = alpha),
                        start = offset(from.x, from.y),
                        end = offset(to.x, to.y),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
            }

            val start = geometry.points.first().let { offset(it.x, it.y) }
            val current = geometry.points.last().let { offset(it.x, it.y) }
            val marker = 7.dp.toPx()
            val triangle = Path().apply {
                moveTo(start.x - marker * .45f, start.y - marker)
                lineTo(start.x + marker, start.y)
                lineTo(start.x - marker * .45f, start.y + marker)
                close()
            }
            drawPath(triangle, accent)
            drawCircle(accent.copy(alpha = .22f), 6.dp.toPx(), current)
            drawCircle(accent, 3.dp.toPx(), current)
        }
    }
}

@Composable
private fun TripTrendMiniCard(
    title: String,
    unit: String,
    samples: List<TripTrendSampleV06>,
    modifier: Modifier,
    emptyText: String
) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (samples.isNotEmpty()) {
                        Text(
                            formatActiveTrendValue(samples.last().value, unit),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (samples.size >= 2) {
                    Text(
                        "${formatActiveTrendAxis(samples.minOf { it.value })}–${formatActiveTrendAxis(samples.maxOf { it.value })} $unit",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (samples.size < 2) {
                Column(
                    modifier = Modifier.fillMaxWidth().height(68.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(emptyText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                TripTrendPlotV06(
                    samples = samples,
                    unit = unit,
                    longGapMs = ACTIVE_TREND_LONG_GAP_MS
                )
            }
        }
    }
}

private fun trustedSpeed(point: TripPointEntity): Double? {
    val value = point.speedMps ?: return null
    if (!value.isFinite() || value < 0.0) return null
    return value.takeIf {
        TripSpeedTrustRules.eligibleForMeasuredSpeed(
            reportedSpeedMps = value,
            provider = point.provider,
            horizontalAccuracyMeters = point.horizontalAccuracyMeters,
            speedAccuracyMps = point.speedAccuracyMps
        )
    }
}

private fun trustedAltitude(point: TripPointEntity): Double? {
    val altitude = point.altitudeMeters?.takeIf { it.isFinite() } ?: return null
    val verticalAccuracy = point.verticalAccuracyMeters
    if (verticalAccuracy != null && (!verticalAccuracy.isFinite() || verticalAccuracy > 50.0)) return null
    val horizontalAccuracy = point.horizontalAccuracyMeters
    if (horizontalAccuracy != null && (!horizontalAccuracy.isFinite() || horizontalAccuracy > 80.0)) return null
    return altitude
}

private fun formatActiveTrendValue(value: Double, unit: String): String =
    String.format(Locale.US, "%.0f %s", value, unit)

private fun formatActiveTrendAxis(value: Double): String = String.format(Locale.US, "%.0f", value)
