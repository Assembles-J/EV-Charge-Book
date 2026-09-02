package com.evchargebook.ui.trip

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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

private val SpeedLowV07 = Color(0xFFFF4D5A)
private val SpeedLowMidV07 = Color(0xFFFF982E)
private val SpeedMidV07 = Color(0xFFFFD928)
private val SpeedCruiseV07 = Color(0xFF2FE36F)
private val SpeedFastV07 = Color(0xFF2BD9E8)
private val SpeedHighV07 = Color(0xFF4C7DFF)
private val SpeedVeryHighV07 = Color(0xFFB64CFF)
private val SpeedUnknownV07 = Color(0xFF78818D)

/**
 * Shared Trip route viewport.
 *
 * Completed normal view prefers real MapLibre geographic context. Playback keeps the lightweight
 * renderer for this provider-validation slice. If the map style/provider fails, normal view falls
 * back to the same truthful renderer. Persisted TripPoint coordinates remain the only route truth
 * and real long gaps/rebases never become a continuous route.
 */
@Composable
internal fun TripRouteViewportV07(
    points: List<TripPointEntity>,
    modifier: Modifier = Modifier,
    frame: TripPlaybackFrame? = null,
    playbackMode: Boolean = false,
    finalEndpoint: Boolean = true,
    height: Dp = 230.dp,
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
    val viewportKey = routePoints.firstOrNull()?.tripId
    var basemapFailed by remember(viewportKey) { mutableStateOf(false) }

    if (geometry?.isDrawable != true) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!playbackMode && !basemapFailed) {
            TripMapContextV08(
                points = routePoints,
                finalEndpoint = finalEndpoint,
                height = height,
                onProviderFailure = { basemapFailed = true },
            )
        } else {
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
        TripSpeedLegendV07()
    }
}

@Composable
internal fun TripSpeedLegendV07(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .56f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .20f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "速度（km/h）",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < 390.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        LegendRowV07(
                            listOf(
                                "0–5" to SpeedLowV07,
                                "5–15" to SpeedLowMidV07,
                                "15–30" to SpeedMidV07,
                                "30–50" to SpeedCruiseV07,
                            )
                        )
                        LegendRowV07(
                            listOf(
                                "50–70" to SpeedFastV07,
                                "70–90" to SpeedHighV07,
                                "90+" to SpeedVeryHighV07,
                                "未知" to SpeedUnknownV07,
                            )
                        )
                    }
                } else {
                    LegendRowV07(
                        listOf(
                            "0–5" to SpeedLowV07,
                            "5–15" to SpeedLowMidV07,
                            "15–30" to SpeedMidV07,
                            "30–50" to SpeedCruiseV07,
                            "50–70" to SpeedFastV07,
                            "70–90" to SpeedHighV07,
                            "90+" to SpeedVeryHighV07,
                            "未知" to SpeedUnknownV07,
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendRowV07(items: List<Pair<String, Color>>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { (label, color) ->
            SpeedLegendCellV07(label, color, Modifier.weight(1f))
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
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = .075f)
    val gapColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .66f)
    val markerOutline = MaterialTheme.colorScheme.onSurface.copy(alpha = .92f)
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

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .17f)),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .pointerInput(viewportKey) {
                        detectTransformGestures(panZoomLock = true) { centroid, gesturePan, gestureZoom, _ ->
                            val oldZoom = zoom.coerceAtLeast(1f)
                            val newZoom = (oldZoom * gestureZoom).coerceIn(1f, 24f)
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
                                "可拖动缩放的真实行程轨迹；轨迹颜色表示可信 GPS 时速区间；灰色虚线仅标识 GPS 长缺口，并非真实道路轨迹"
                        }
                    },
            ) {
                if (points.isEmpty()) return@Canvas

                val gridStep = 42.dp.toPx()
                var gridX = 0f
                while (gridX <= size.width) {
                    drawLine(gridColor, Offset(gridX, 0f), Offset(gridX, size.height), 1.dp.toPx())
                    gridX += gridStep
                }
                var gridY = 0f
                while (gridY <= size.height) {
                    drawLine(gridColor, Offset(0f, gridY), Offset(size.width, gridY), 1.dp.toPx())
                    gridY += gridStep
                }

                val padX = 18.dp.toPx()
                val padTop = 18.dp.toPx()
                val padBottom = 58.dp.toPx()
                val width = (size.width - padX * 2).coerceAtLeast(1f)
                val canvasHeight = (size.height - padTop - padBottom).coerceAtLeast(1f)
                val latSpan = (maxLatitude - minLatitude).takeIf { it > 0.0 } ?: 1.0
                val lonSpan = (maxLongitude - minLongitude).takeIf { it > 0.0 } ?: 1.0
                val longGapMillis = TripContinuityRules.LONG_GAP_SECONDS * 1_000L
                val center = Offset(size.width / 2f, (padTop + canvasHeight / 2f))
                val gapDash = PathEffect.dashPathEffect(
                    floatArrayOf(7.dp.toPx(), 7.dp.toPx()),
                )

                fun project(latitude: Double, longitude: Double): Offset {
                    val x = ((longitude - minLongitude) / lonSpan).toFloat().coerceIn(0f, 1f)
                    val y = (1.0 - (latitude - minLatitude) / latSpan).toFloat().coerceIn(0f, 1f)
                    val base = Offset(padX + x * width, padTop + y * canvasHeight)
                    return Offset(
                        x = center.x + (base.x - center.x) * zoom + pan.x,
                        y = center.y + (base.y - center.y) * zoom + pan.y,
                    )
                }

                fun segmentStyle(from: TripPointEntity, to: TripPointEntity): Pair<Color, Float> {
                    val speedKph = (trustedTripSpeedMpsV07(to) ?: trustedTripSpeedMpsV07(from))?.times(3.6)
                    val normalized = speedKph?.let { (it / 120.0).toFloat().coerceIn(0f, 1f) } ?: 0f
                    return trustedSpeedColorV07(speedKph) to (3.2f + normalized * 1.7f).dp.toPx()
                }

                points.zipWithNext().forEachIndexed { index, (from, to) ->
                    val timing = TripCaptureTimeRules.between(
                        previousEpochMillis = from.capturedAtEpochMillis,
                        previousElapsedRealtimeNanos = from.capturedAtElapsedRealtimeNanos,
                        currentEpochMillis = to.capturedAtEpochMillis,
                        currentElapsedRealtimeNanos = to.capturedAtElapsedRealtimeNanos,
                    )
                    if (!timing.accepted) return@forEachIndexed

                    val start = project(from.latitude, from.longitude)
                    val end = project(to.latitude, to.longitude)
                    if (timing.breaksContinuity(longGapMillis)) {
                        drawLine(
                            color = gapColor,
                            start = start,
                            end = end,
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                            pathEffect = gapDash,
                        )
                        return@forEachIndexed
                    }

                    val (segmentColor, segmentStroke) = segmentStyle(from, to)
                    if (!playbackMode) {
                        drawLine(segmentColor, start, end, segmentStroke, StrokeCap.Round)
                        return@forEachIndexed
                    }

                    drawLine(
                        color = segmentColor.copy(alpha = .22f),
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

                drawCircle(accent.copy(alpha = .18f), 11.dp.toPx(), startPoint)
                drawCircle(markerOutline, 7.dp.toPx(), startPoint)
                drawCircle(accent, 5.dp.toPx(), startPoint)

                if (finalEndpoint) {
                    drawCircle(endColor.copy(alpha = .18f), 11.dp.toPx(), endPoint)
                    drawCircle(markerOutline, 7.dp.toPx(), endPoint)
                    drawCircle(endColor, 4.5.dp.toPx(), endPoint)
                    val poleTop = Offset(endPoint.x, endPoint.y - 21.dp.toPx())
                    drawLine(
                        endColor,
                        endPoint,
                        poleTop,
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    val flag = Path().apply {
                        moveTo(poleTop.x, poleTop.y)
                        lineTo(poleTop.x + 11.dp.toPx(), poleTop.y + 3.dp.toPx())
                        lineTo(poleTop.x, poleTop.y + 7.dp.toPx())
                        close()
                    }
                    drawPath(flag, endColor)
                } else {
                    drawCircle(accent.copy(alpha = .20f), 9.dp.toPx(), endPoint)
                    drawCircle(accent, 4.dp.toPx(), endPoint)
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

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GestureChipV07("拖动")
                    GestureChipV07("双指缩放")
                }
                Surface(
                    onClick = ::animateBackToFullRoute,
                    enabled = viewportChanged,
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .90f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .24f)),
                ) {
                    Text(
                        "回到全程",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (viewportChanged) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .58f)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GestureChipV07(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .84f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .18f)),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    speedKph < 5.0 -> SpeedLowV07
    speedKph < 15.0 -> SpeedLowMidV07
    speedKph < 30.0 -> SpeedMidV07
    speedKph < 50.0 -> SpeedCruiseV07
    speedKph < 70.0 -> SpeedFastV07
    speedKph < 90.0 -> SpeedHighV07
    else -> SpeedVeryHighV07
}
