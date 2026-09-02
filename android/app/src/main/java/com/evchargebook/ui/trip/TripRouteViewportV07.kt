package com.evchargebook.ui.trip

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.TripPointEntity
import com.evchargebook.domain.TripCaptureTimeRules
import com.evchargebook.domain.TripContinuityRules
import com.evchargebook.domain.TripPlaybackFrame
import com.evchargebook.domain.TripSpeedTrustRules
import com.evchargebook.domain.trip.TripGeoPoint
import com.evchargebook.domain.trip.TripRouteGeometryBuilder
import com.evchargebook.ui.theme.EVDesignTokens
import kotlinx.coroutines.launch

private val SpeedDeepRedV07 = Color(0xFFD32F2F)
private val SpeedRedV07 = Color(0xFFE53935)
private val SpeedAmberV07 = Color(0xFFFF9800)
private val SpeedYellowV07 = Color(0xFFFDD835)
private val SpeedGreenV07 = Color(0xFF43A047)
private val SpeedBlueV07 = Color(0xFF1E88E5)
private val SpeedDeepBlueV07 = Color(0xFF1565C0)
private val SpeedUnknownV07 = Color(0xFF7A7F86)

/**
 * Shared completed-Trip route renderer used by both the normal route page and playback.
 * Geometry is presentation-only and never mutates persisted TripPoint facts.
 */
@Composable
internal fun TripRouteViewportV07(
    points: List<TripPointEntity>,
    modifier: Modifier = Modifier,
    frame: TripPlaybackFrame? = null,
    playbackMode: Boolean = false,
    finalEndpoint: Boolean = true,
    height: Dp = 190.dp,
) {
    val routePoints = remember(points) {
        points.filter { it.latitude.isFinite() && it.longitude.isFinite() }
    }
    val geometry = remember(routePoints) {
        TripRouteGeometryBuilder.build(
            routePoints.map { point ->
                TripGeoPoint(
                    latitude = point.latitude,
                    longitude = point.longitude,
                    capturedAtEpochMillis = point.capturedAtEpochMillis,
                    speedMps = trustedTripSpeedMpsV07(point),
                    capturedAtElapsedRealtimeNanos = point.capturedAtElapsedRealtimeNanos,
                )
            }
        )
    }

    if (geometry?.isDrawable != true) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TripSpeedLegendV07()
        InteractiveTripRouteCanvasV07(
            points = routePoints,
            frame = frame,
            playbackMode = playbackMode,
            finalEndpoint = finalEndpoint,
            minLatitude = geometry.minLatitude,
            maxLatitude = geometry.maxLatitude,
            minLongitude = geometry.minLongitude,
            maxLongitude = geometry.maxLongitude,
            height = height,
        )
    }
}

@Composable
internal fun TripSpeedLegendV07(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            "可信 GPS 时速区间 · km/h",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpeedLegendCellV07("0–5", SpeedDeepRedV07, Modifier.weight(1f))
            SpeedLegendCellV07("5–15", SpeedRedV07, Modifier.weight(1f))
            SpeedLegendCellV07("15–30", SpeedAmberV07, Modifier.weight(1f))
            SpeedLegendCellV07("30–50", SpeedYellowV07, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpeedLegendCellV07("50–70", SpeedGreenV07, Modifier.weight(1f))
            SpeedLegendCellV07("70–90", SpeedBlueV07, Modifier.weight(1f))
            SpeedLegendCellV07("90+", SpeedDeepBlueV07, Modifier.weight(1f))
            SpeedLegendCellV07("未知", SpeedUnknownV07, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SpeedLegendCellV07(label: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun InteractiveTripRouteCanvasV07(
    points: List<TripPointEntity>,
    frame: TripPlaybackFrame?,
    playbackMode: Boolean,
    finalEndpoint: Boolean,
    minLatitude: Double,
    maxLatitude: Double,
    minLongitude: Double,
    maxLongitude: Double,
    height: Dp,
) {
    val accent = EVDesignTokens.Energy.green
    val endColor = MaterialTheme.colorScheme.error
    val viewportKey = points.firstOrNull()?.tripId
    var zoom by remember(viewportKey) { mutableFloatStateOf(1f) }
    var pan by remember(viewportKey) { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    val viewportChanged = zoom > 1.001f || pan != Offset.Zero

    fun animateBackToFullRoute() {
        val startZoom = zoom
        val startPan = pan
        scope.launch {
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            ) { fraction, _ ->
                zoom = startZoom + (1f - startZoom) * fraction
                pan = Offset(
                    x = startPan.x * (1f - fraction),
                    y = startPan.y * (1f - fraction),
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "双指缩放 · 单指拖动查看",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (viewportChanged) {
                TextButton(onClick = ::animateBackToFullRoute) {
                    Text("回到全程", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(viewportKey) {
                    detectTransformGestures(panZoomLock = true) { centroid, gesturePan, gestureZoom, _ ->
                        val oldZoom = zoom.coerceAtLeast(1f)
                        val newZoom = (oldZoom * gestureZoom).coerceIn(1f, 6f)
                        val ratio = newZoom / oldZoom
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val anchoredPan = Offset(
                            x = centroid.x - center.x - (centroid.x - center.x - pan.x) * ratio,
                            y = centroid.y - center.y - (centroid.y - center.y - pan.y) * ratio,
                        )
                        val maxPanX = size.width.toFloat() * (newZoom - 1f) * 0.5f
                        val maxPanY = size.height.toFloat() * (newZoom - 1f) * 0.5f
                        zoom = newZoom
                        pan = Offset(
                            x = (anchoredPan.x + gesturePan.x).coerceIn(-maxPanX, maxPanX),
                            y = (anchoredPan.y + gesturePan.y).coerceIn(-maxPanY, maxPanY),
                        )
                    }
                }
                .semantics {
                    contentDescription = when {
                        playbackMode && frame?.isLongGap == true ->
                            "可拖动缩放的行程轨迹回放；当前处于 GPS 缺口，车辆位置保持在最后真实定位点"
                        playbackMode ->
                            "可拖动缩放的行程轨迹回放；轨迹颜色表示可信 GPS 时速区间"
                        else ->
                            "可拖动缩放的真实行程轨迹；轨迹颜色表示可信 GPS 时速区间，断开表示 GPS 长缺口"
                    }
                },
        ) {
            if (points.isEmpty()) return@Canvas
            val pad = 12.dp.toPx()
            val width = (size.width - pad * 2).coerceAtLeast(1f)
            val canvasHeight = (size.height - pad * 2).coerceAtLeast(1f)
            val latSpan = (maxLatitude - minLatitude).takeIf { it > 0.0 } ?: 1.0
            val lonSpan = (maxLongitude - minLongitude).takeIf { it > 0.0 } ?: 1.0
            val longGapMillis = TripContinuityRules.LONG_GAP_SECONDS * 1_000L
            val center = Offset(size.width / 2f, size.height / 2f)

            fun project(latitude: Double, longitude: Double): Offset {
                val x = ((longitude - minLongitude) / lonSpan).toFloat().coerceIn(0f, 1f)
                val y = (1.0 - (latitude - minLatitude) / latSpan).toFloat().coerceIn(0f, 1f)
                val base = Offset(pad + x * width, pad + y * canvasHeight)
                return Offset(
                    x = center.x + (base.x - center.x) * zoom + pan.x,
                    y = center.y + (base.y - center.y) * zoom + pan.y,
                )
            }

            fun segmentStyle(from: TripPointEntity, to: TripPointEntity): Pair<Color, Float> {
                val speedKph = (trustedTripSpeedMpsV07(to) ?: trustedTripSpeedMpsV07(from))?.times(3.6)
                val normalized = speedKph?.let { (it / 120.0).toFloat().coerceIn(0f, 1f) } ?: 0f
                return trustedSpeedColorV07(speedKph) to (2.7f + normalized * 1.7f).dp.toPx()
            }

            points.zipWithNext().forEachIndexed { index, (from, to) ->
                val timing = TripCaptureTimeRules.between(
                    previousEpochMillis = from.capturedAtEpochMillis,
                    previousElapsedRealtimeNanos = from.capturedAtElapsedRealtimeNanos,
                    currentEpochMillis = to.capturedAtEpochMillis,
                    currentElapsedRealtimeNanos = to.capturedAtElapsedRealtimeNanos,
                )
                if (!timing.accepted || timing.breaksContinuity(longGapMillis)) return@forEachIndexed

                val start = project(from.latitude, from.longitude)
                val end = project(to.latitude, to.longitude)
                val (segmentColor, segmentStroke) = segmentStyle(from, to)

                if (!playbackMode) {
                    drawLine(segmentColor, start, end, segmentStroke, StrokeCap.Round)
                    return@forEachIndexed
                }

                drawLine(
                    color = segmentColor.copy(alpha = .25f),
                    start = start,
                    end = end,
                    strokeWidth = segmentStroke,
                    cap = StrokeCap.Round,
                )

                val currentFrame = frame
                when {
                    currentFrame == null -> Unit
                    index < currentFrame.currentSampleIndex ->
                        drawLine(segmentColor, start, end, segmentStroke, StrokeCap.Round)
                    index == currentFrame.currentSampleIndex &&
                        currentFrame.nextSampleIndex == index + 1 &&
                        !currentFrame.isLongGap -> {
                        val fraction = currentFrame.segmentFraction.toFloat().coerceIn(0f, 1f)
                        val partial = Offset(
                            x = start.x + (end.x - start.x) * fraction,
                            y = start.y + (end.y - start.y) * fraction,
                        )
                        drawLine(segmentColor, start, partial, segmentStroke, StrokeCap.Round)
                    }
                }
            }

            val startPoint = project(points.first().latitude, points.first().longitude)
            val endPoint = project(points.last().latitude, points.last().longitude)
            val markerSize = 6.dp.toPx()
            val startTriangle = Path().apply {
                moveTo(startPoint.x - markerSize * .45f, startPoint.y - markerSize)
                lineTo(startPoint.x + markerSize, startPoint.y)
                lineTo(startPoint.x - markerSize * .45f, startPoint.y + markerSize)
                close()
            }
            drawPath(startTriangle, accent)

            if (finalEndpoint) {
                val poleHeight = 13.dp.toPx()
                val flagWidth = 9.dp.toPx()
                val flagHeight = 6.dp.toPx()
                drawLine(
                    endColor,
                    endPoint,
                    Offset(endPoint.x, endPoint.y - poleHeight),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                val flag = Path().apply {
                    moveTo(endPoint.x, endPoint.y - poleHeight)
                    lineTo(endPoint.x + flagWidth, endPoint.y - poleHeight)
                    lineTo(endPoint.x + flagWidth, endPoint.y - poleHeight + flagHeight)
                    lineTo(endPoint.x, endPoint.y - poleHeight + flagHeight)
                    close()
                }
                drawPath(flag, endColor)
            } else {
                drawCircle(accent.copy(alpha = .22f), 6.dp.toPx(), endPoint)
                drawCircle(accent, 3.dp.toPx(), endPoint)
            }

            if (playbackMode) {
                frame?.let { current ->
                    val currentOffset = project(current.latitude, current.longitude)
                    val direction = current.bearingDegrees?.takeIf { it.isFinite() }?.toFloat() ?: 0f
                    val vehicleSize = 7.dp.toPx()
                    val vehicle = Path().apply {
                        moveTo(currentOffset.x, currentOffset.y - vehicleSize)
                        lineTo(currentOffset.x + vehicleSize * .68f, currentOffset.y + vehicleSize * .65f)
                        lineTo(currentOffset.x, currentOffset.y + vehicleSize * .30f)
                        lineTo(currentOffset.x - vehicleSize * .68f, currentOffset.y + vehicleSize * .65f)
                        close()
                    }
                    rotate(degrees = direction, pivot = currentOffset) { drawPath(vehicle, accent) }
                }
            }
        }
    }
}

internal fun trustedTripSpeedMpsV07(point: TripPointEntity): Double? {
    val value = point.speedMps ?: return null
    if (!value.isFinite() || value < 0.0) return null
    return value.takeIf {
        TripSpeedTrustRules.eligibleForMeasuredSpeed(
            reportedSpeedMps = value,
            provider = point.provider,
            horizontalAccuracyMeters = point.horizontalAccuracyMeters,
            speedAccuracyMps = point.speedAccuracyMps,
        )
    }
}

private fun trustedSpeedColorV07(speedKph: Double?): Color = when {
    speedKph == null || !speedKph.isFinite() -> SpeedUnknownV07
    speedKph < 5.0 -> SpeedDeepRedV07
    speedKph < 15.0 -> SpeedRedV07
    speedKph < 30.0 -> SpeedAmberV07
    speedKph < 50.0 -> SpeedYellowV07
    speedKph < 70.0 -> SpeedGreenV07
    speedKph < 90.0 -> SpeedBlueV07
    else -> SpeedDeepBlueV07
}
