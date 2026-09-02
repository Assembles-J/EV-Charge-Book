package com.evchargebook.ui.trip

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.evchargebook.domain.TripCaptureTimeRules
import com.evchargebook.ui.theme.EVDesignTokens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

internal data class TripTrendSampleV06(
    val timestamp: Long,
    val value: Double,
    val capturedAtElapsedRealtimeNanos: Long? = null,
)

internal data class TripTrendTimelineSampleV06(
    val epochMillis: Long,
    val timelineMillis: Long,
    val value: Double,
    val breakBefore: Boolean,
)

internal fun buildTripTrendTimelineV06(
    samples: List<TripTrendSampleV06>,
    longGapMs: Long,
): List<TripTrendTimelineSampleV06> {
    if (samples.isEmpty()) return emptyList()
    val result = mutableListOf(
        TripTrendTimelineSampleV06(
            epochMillis = samples.first().timestamp,
            timelineMillis = 0L,
            value = samples.first().value,
            breakBefore = false,
        )
    )
    var timelineMillis = 0L
    samples.zipWithNext().forEach { (previous, current) ->
        val timing = TripCaptureTimeRules.between(
            previousEpochMillis = previous.timestamp,
            previousElapsedRealtimeNanos = previous.capturedAtElapsedRealtimeNanos,
            currentEpochMillis = current.timestamp,
            currentElapsedRealtimeNanos = current.capturedAtElapsedRealtimeNanos,
        )
        val breakBefore = !timing.accepted || timing.breaksContinuity(longGapMs)
        val intervalMillis = when {
            !timing.accepted -> longGapMs
            timing.requiresRebase -> max(
                longGapMs,
                (current.timestamp - previous.timestamp).coerceAtLeast(0L),
            )
            else -> timing.deltaMillis ?: 0L
        }
        timelineMillis += intervalMillis
        result += TripTrendTimelineSampleV06(
            epochMillis = current.timestamp,
            timelineMillis = timelineMillis,
            value = current.value,
            breakBefore = breakBefore,
        )
    }
    return result
}

/**
 * Interactive Compose-native trend plot used by active and completed Trip surfaces.
 *
 * The chart renders persisted samples only, never smooths across missing data and never bridges
 * long GPS gaps. Optional area fill is built independently for each real continuous segment.
 */
@Composable
internal fun TripTrendPlotV06(
    samples: List<TripTrendSampleV06>,
    unit: String,
    longGapMs: Long,
    modifier: Modifier = Modifier,
    plotHeight: Dp = 104.dp,
    fillArea: Boolean = false,
    showInteractionHeader: Boolean = true,
) {
    if (samples.size < 2) return

    val timelineSamples = remember(samples, longGapMs) { buildTripTrendTimelineV06(samples, longGapMs) }
    if (timelineSamples.size < 2) return

    val accent = EVDesignTokens.Energy.green
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)
    val minTime = timelineSamples.first().timelineMillis
    val maxTime = timelineSamples.last().timelineMillis
    val timeRangeMs = max(1L, maxTime - minTime)
    val viewportKey = samples.first().timestamp

    var zoom by remember(viewportKey) { mutableFloatStateOf(1f) }
    var viewportStartFraction by remember(viewportKey) { mutableFloatStateOf(0f) }
    var selectedTimelineMillis by remember(viewportKey) { mutableStateOf<Long?>(null) }

    val visibleFraction = (1f / zoom).coerceIn(0.125f, 1f)
    val maxViewportStart = (1f - visibleFraction).coerceAtLeast(0f)
    val clampedStartFraction = viewportStartFraction.coerceIn(0f, maxViewportStart)
    val visibleStartTime = minTime + (timeRangeMs * clampedStartFraction).toLong()
    val visibleEndTime = minTime + (timeRangeMs * (clampedStartFraction + visibleFraction)).toLong()
    val visibleValueSamples = timelineSamples
        .filter { it.timelineMillis in visibleStartTime..visibleEndTime }
        .ifEmpty { timelineSamples }

    val minValue = visibleValueSamples.minOf { it.value }
    val maxValue = visibleValueSamples.maxOf { it.value }
    val valueRange = max(1.0, maxValue - minValue)
    val midValue = minValue + valueRange / 2.0
    val averageValue = visibleValueSamples.map { it.value }.average()
    val selectedSample = selectedTimelineMillis?.let { selected ->
        timelineSamples.minByOrNull { abs(it.timelineMillis - selected) }
    }
    val viewportChanged = zoom > 1.001f || clampedStartFraction > 0.001f

    val currentSamples by rememberUpdatedState(timelineSamples)
    val currentVisibleFraction by rememberUpdatedState(visibleFraction)
    val currentStartFraction by rememberUpdatedState(clampedStartFraction)

    Column(modifier = modifier.fillMaxWidth()) {
        if (showInteractionHeader || selectedSample != null || viewportChanged) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = selectedSample?.let { sample -> formatSelectedTrendSample(sample, unit) }
                        ?: if (showInteractionHeader) {
                            "长按左右拖动读点 · 双指缩放/平移"
                        } else {
                            "已调整查看区间"
                        },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                if (viewportChanged) {
                    TextButton(
                        onClick = {
                            zoom = 1f
                            viewportStartFraction = 0f
                        }
                    ) {
                        Text("全程", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.width(44.dp).height(plotHeight),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                AxisValue(formatTripTrendAxisValue(maxValue, unit))
                AxisValue(formatTripTrendAxisValue(midValue, unit))
                AxisValue(formatTripTrendAxisValue(minValue, unit))
            }

            Canvas(
                Modifier
                    .weight(1f)
                    .height(plotHeight)
                    .pointerInput(viewportKey) {
                        detectTransformGestures(panZoomLock = true) { centroid, pan, gestureZoom, _ ->
                            val canvasWidth = size.width.toFloat().coerceAtLeast(1f)
                            val oldZoom = zoom
                            val oldVisible = (1f / oldZoom).coerceIn(0.125f, 1f)
                            val newZoom = (oldZoom * gestureZoom).coerceIn(1f, 8f)
                            val newVisible = (1f / newZoom).coerceIn(0.125f, 1f)
                            val focus = (centroid.x / canvasWidth).coerceIn(0f, 1f)
                            val focusGlobal = viewportStartFraction + focus * oldVisible
                            val panShift = (pan.x / canvasWidth) * newVisible
                            val newMaxStart = (1f - newVisible).coerceAtLeast(0f)

                            zoom = newZoom
                            viewportStartFraction = (focusGlobal - focus * newVisible - panShift)
                                .coerceIn(0f, newMaxStart)
                        }
                    }
                    .pointerInput(viewportKey) {
                        fun selectNearestAt(x: Float) {
                            val sampleList = currentSamples
                            if (sampleList.isEmpty()) return
                            val canvasWidth = size.width.toFloat().coerceAtLeast(1f)
                            val localFraction = (x / canvasWidth).coerceIn(0f, 1f)
                            val globalFraction = currentStartFraction + localFraction * currentVisibleFraction
                            val currentMinTime = sampleList.first().timelineMillis
                            val currentRangeMs = max(1L, sampleList.last().timelineMillis - currentMinTime)
                            val targetTimeline = currentMinTime + (currentRangeMs * globalFraction).toLong()
                            selectedTimelineMillis = sampleList
                                .minByOrNull { abs(it.timelineMillis - targetTimeline) }
                                ?.timelineMillis
                        }

                        detectTapGestures { tap -> selectNearestAt(tap.x) }
                    }
                    .pointerInput(viewportKey) {
                        fun selectNearestAt(x: Float) {
                            val sampleList = currentSamples
                            if (sampleList.isEmpty()) return
                            val canvasWidth = size.width.toFloat().coerceAtLeast(1f)
                            val localFraction = (x / canvasWidth).coerceIn(0f, 1f)
                            val globalFraction = currentStartFraction + localFraction * currentVisibleFraction
                            val currentMinTime = sampleList.first().timelineMillis
                            val currentRangeMs = max(1L, sampleList.last().timelineMillis - currentMinTime)
                            val targetTimeline = currentMinTime + (currentRangeMs * globalFraction).toLong()
                            selectedTimelineMillis = sampleList
                                .minByOrNull { abs(it.timelineMillis - targetTimeline) }
                                ?.timelineMillis
                        }

                        detectDragGesturesAfterLongPress(
                            onDragStart = { start -> selectNearestAt(start.x) },
                            onDrag = { change, _ ->
                                change.consume()
                                selectNearestAt(change.position.x)
                            }
                        )
                    }
            ) {
                val padY = 5.dp.toPx()
                val usableHeight = (size.height - padY * 2).coerceAtLeast(1f)
                val visibleDurationMs = max(1L, visibleEndTime - visibleStartTime)

                listOf(0f, 0.5f, 1f).forEach { fraction ->
                    val y = padY + fraction * usableHeight
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                fun point(sample: TripTrendTimelineSampleV06): Offset {
                    val x = ((sample.timelineMillis - visibleStartTime).toDouble() / visibleDurationMs.toDouble()).toFloat() * size.width
                    val normalized = ((sample.value - minValue) / valueRange).toFloat().coerceIn(0f, 1f)
                    return Offset(x, padY + (1f - normalized) * usableHeight)
                }

                if (fillArea) {
                    val segments = mutableListOf<MutableList<TripTrendTimelineSampleV06>>()
                    var currentSegment = mutableListOf<TripTrendTimelineSampleV06>()
                    timelineSamples.forEach { sample ->
                        if (sample.breakBefore && currentSegment.isNotEmpty()) {
                            segments += currentSegment
                            currentSegment = mutableListOf()
                        }
                        currentSegment += sample
                    }
                    if (currentSegment.isNotEmpty()) segments += currentSegment

                    val baselineY = padY + usableHeight
                    segments.forEach { segment ->
                        if (segment.size < 2 ||
                            segment.last().timelineMillis < visibleStartTime ||
                            segment.first().timelineMillis > visibleEndTime
                        ) return@forEach

                        val first = point(segment.first())
                        val fillPath = Path().apply {
                            moveTo(first.x, baselineY)
                            lineTo(first.x, first.y)
                            segment.drop(1).forEach { sample ->
                                val offset = point(sample)
                                lineTo(offset.x, offset.y)
                            }
                            val last = point(segment.last())
                            lineTo(last.x, baselineY)
                            close()
                        }
                        drawPath(fillPath, color = accent.copy(alpha = .10f))
                    }

                    val normalizedAverage = ((averageValue - minValue) / valueRange).toFloat().coerceIn(0f, 1f)
                    val averageY = padY + (1f - normalizedAverage) * usableHeight
                    drawLine(
                        color = accent.copy(alpha = .24f),
                        start = Offset(0f, averageY),
                        end = Offset(size.width, averageY),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx())),
                    )
                }

                timelineSamples.zipWithNext().forEach { (from, to) ->
                    if (!to.breakBefore &&
                        to.timelineMillis >= visibleStartTime && from.timelineMillis <= visibleEndTime
                    ) {
                        drawLine(
                            color = accent.copy(alpha = 0.88f),
                            start = point(from),
                            end = point(to),
                            strokeWidth = if (fillArea) 2.3.dp.toPx() else 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                selectedSample?.takeIf { it.timelineMillis in visibleStartTime..visibleEndTime }?.let { sample ->
                    val selected = point(sample)
                    val crosshairColor = gridColor.copy(alpha = 0.85f)
                    drawLine(
                        color = crosshairColor,
                        start = Offset(selected.x, 0f),
                        end = Offset(selected.x, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = crosshairColor,
                        start = Offset(0f, selected.y),
                        end = Offset(size.width, selected.y),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawCircle(accent.copy(alpha = 0.22f), radius = 7.dp.toPx(), center = selected)
                    drawCircle(accent, radius = 3.5.dp.toPx(), center = selected)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(44.dp))
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AxisTime(formatTripTrendElapsed(visibleStartTime - minTime), TextAlign.Start)
                AxisTime(formatTripTrendElapsed((visibleStartTime + visibleEndTime) / 2 - minTime), TextAlign.Center)
                AxisTime(formatTripTrendElapsed(visibleEndTime - minTime), TextAlign.End)
            }
        }
    }
}

@Composable
private fun AxisValue(value: String) {
    Text(
        text = value,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1
    )
}

@Composable
private fun AxisTime(value: String, align: TextAlign) {
    Text(
        text = value,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align
    )
}

private fun formatSelectedTrendSample(sample: TripTrendTimelineSampleV06, unit: String): String {
    val elapsed = formatTripTrendElapsed(sample.timelineMillis)
    val clock = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(sample.epochMillis))
    val value = when (unit) {
        "km/h" -> String.format(Locale.US, "%.1f km/h", sample.value)
        "m" -> String.format(Locale.US, "%.1f m", sample.value)
        else -> String.format(Locale.US, "%.2f %s", sample.value, unit)
    }
    return "T+$elapsed · $clock · $value"
}

private fun formatTripTrendAxisValue(value: Double, unit: String): String =
    when (unit) {
        "km/h" -> String.format(Locale.US, "%.0f", value)
        "m" -> String.format(Locale.US, "%.0f", value)
        else -> String.format(Locale.US, "%.1f", value)
    }

private fun formatTripTrendElapsed(elapsedMs: Long): String {
    val totalSeconds = (elapsedMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0 -> String.format(Locale.US, "%d:%02d", hours, minutes)
        minutes > 0 -> String.format(Locale.US, "%d:%02d", minutes, seconds)
        else -> "${seconds}s"
    }
}
