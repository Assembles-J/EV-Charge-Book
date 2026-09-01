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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.evchargebook.ui.theme.EVDesignTokens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

internal data class TripTrendSampleV06(
    val timestamp: Long,
    val value: Double
)

/**
 * Interactive Compose-native trend plot used by active and completed Trip surfaces.
 *
 * The chart renders persisted samples only, never smooths across missing data and never bridges
 * long GPS gaps. Two-finger pan / pinch zoom only change the viewport; tap or long-press drag
 * resolves continuously to the nearest real sample.
 */
@Composable
internal fun TripTrendPlotV06(
    samples: List<TripTrendSampleV06>,
    unit: String,
    longGapMs: Long,
    modifier: Modifier = Modifier
) {
    if (samples.size < 2) return

    val accent = EVDesignTokens.Energy.green
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)
    val minTime = samples.first().timestamp
    val maxTime = samples.last().timestamp
    val timeRangeMs = max(1L, maxTime - minTime)
    val viewportKey = samples.first().timestamp

    var zoom by remember(viewportKey) { mutableFloatStateOf(1f) }
    var viewportStartFraction by remember(viewportKey) { mutableFloatStateOf(0f) }
    var selectedTimestamp by remember(viewportKey) { mutableStateOf<Long?>(null) }

    val visibleFraction = (1f / zoom).coerceIn(0.125f, 1f)
    val maxViewportStart = (1f - visibleFraction).coerceAtLeast(0f)
    val clampedStartFraction = viewportStartFraction.coerceIn(0f, maxViewportStart)
    val visibleStartTime = minTime + (timeRangeMs * clampedStartFraction).toLong()
    val visibleEndTime = minTime + (timeRangeMs * (clampedStartFraction + visibleFraction)).toLong()
    val visibleValueSamples = samples.filter { it.timestamp in visibleStartTime..visibleEndTime }.ifEmpty { samples }

    val minValue = visibleValueSamples.minOf { it.value }
    val maxValue = visibleValueSamples.maxOf { it.value }
    val valueRange = max(1.0, maxValue - minValue)
    val midValue = minValue + valueRange / 2.0
    val selectedSample = selectedTimestamp?.let { timestamp -> samples.minByOrNull { abs(it.timestamp - timestamp) } }
    val viewportChanged = zoom > 1.001f || clampedStartFraction > 0.001f

    val currentSamples by rememberUpdatedState(samples)
    val currentVisibleFraction by rememberUpdatedState(visibleFraction)
    val currentStartFraction by rememberUpdatedState(clampedStartFraction)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = selectedSample?.let { sample -> formatSelectedTrendSample(sample, minTime, unit) }
                    ?: "长按左右拖动读点 · 双指缩放/平移",
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

        Row(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.width(44.dp).height(104.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                AxisValue(formatTripTrendAxisValue(maxValue, unit))
                AxisValue(formatTripTrendAxisValue(midValue, unit))
                AxisValue(formatTripTrendAxisValue(minValue, unit))
            }

            Canvas(
                Modifier
                    .weight(1f)
                    .height(104.dp)
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
                            val currentMinTime = sampleList.first().timestamp
                            val currentRangeMs = max(1L, sampleList.last().timestamp - currentMinTime)
                            val targetTimestamp = currentMinTime + (currentRangeMs * globalFraction).toLong()
                            selectedTimestamp = sampleList.minByOrNull { abs(it.timestamp - targetTimestamp) }?.timestamp
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
                            val currentMinTime = sampleList.first().timestamp
                            val currentRangeMs = max(1L, sampleList.last().timestamp - currentMinTime)
                            val targetTimestamp = currentMinTime + (currentRangeMs * globalFraction).toLong()
                            selectedTimestamp = sampleList.minByOrNull { abs(it.timestamp - targetTimestamp) }?.timestamp
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

                fun point(sample: TripTrendSampleV06): Offset {
                    val x = ((sample.timestamp - visibleStartTime).toDouble() / visibleDurationMs.toDouble()).toFloat() * size.width
                    val normalized = ((sample.value - minValue) / valueRange).toFloat().coerceIn(0f, 1f)
                    return Offset(x, padY + (1f - normalized) * usableHeight)
                }

                samples.zipWithNext().forEach { (from, to) ->
                    if (to.timestamp - from.timestamp <= longGapMs &&
                        to.timestamp >= visibleStartTime && from.timestamp <= visibleEndTime
                    ) {
                        drawLine(
                            color = accent.copy(alpha = 0.82f),
                            start = point(from),
                            end = point(to),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                selectedSample?.takeIf { it.timestamp in visibleStartTime..visibleEndTime }?.let { sample ->
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

private fun formatSelectedTrendSample(sample: TripTrendSampleV06, tripStart: Long, unit: String): String {
    val elapsed = formatTripTrendElapsed(sample.timestamp - tripStart)
    val clock = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(sample.timestamp))
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
