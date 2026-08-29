package com.evchargebook.ui.trip

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.TripPointEntity
import com.evchargebook.domain.TripContinuityRules
import com.evchargebook.domain.TripPlaybackFrame
import com.evchargebook.domain.TripPlaybackSample
import com.evchargebook.domain.TripPlaybackTimeline
import com.evchargebook.domain.trip.TripGeoPoint
import com.evchargebook.domain.trip.TripRouteGeometryBuilder
import com.evchargebook.ui.theme.EVDesignTokens
import com.evchargebook.ui.theme.spacing
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Completed-Trip playback surface for the current no-basemap renderer.
 *
 * Playback is presentation-only. It reads persisted TripPoint chronology and delegates seek/gap
 * semantics to TripPlaybackTimeline; no TripPoint or TripSession is ever changed here.
 */
@Composable
internal fun TripPlaybackRouteCardV06(
    points: List<TripPointEntity>,
    modifier: Modifier = Modifier
) {
    val accent = EVDesignTokens.Energy.green
    val playbackPoints = remember(points) {
        points.filter { it.latitude.isFinite() && it.longitude.isFinite() }
    }
    val samples = remember(playbackPoints) {
        playbackPoints.map {
            TripPlaybackSample(
                capturedAtEpochMillis = it.capturedAtEpochMillis,
                latitude = it.latitude,
                longitude = it.longitude,
                bearingDegrees = it.bearingDegrees
            )
        }
    }
    val geometry = remember(playbackPoints) {
        TripRouteGeometryBuilder.build(
            playbackPoints.map {
                TripGeoPoint(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    capturedAtEpochMillis = it.capturedAtEpochMillis,
                    speedMps = it.speedMps
                )
            }
        )
    }
    val totalMillis = remember(samples) { TripPlaybackTimeline.durationMillis(samples) }
    val playable = geometry?.isDrawable == true && totalMillis > 0L && TripPlaybackTimeline.isChronological(samples)

    var playbackMode by remember(playbackPoints.firstOrNull()?.tripId) { mutableStateOf(false) }
    var playing by remember(playbackPoints.firstOrNull()?.tripId) { mutableStateOf(false) }
    var elapsedMillis by remember(playbackPoints.firstOrNull()?.tripId) { mutableLongStateOf(0L) }
    var speed by remember(playbackPoints.firstOrNull()?.tripId) { mutableFloatStateOf(1f) }

    val frame = remember(samples, elapsedMillis) {
        TripPlaybackTimeline.frameAt(samples, elapsedMillis)
    }

    LaunchedEffect(playing, speed, totalMillis) {
        if (!playing || totalMillis <= 0L) return@LaunchedEffect
        var previousRealtime = SystemClock.elapsedRealtime()
        while (playing) {
            delay(50L)
            val now = SystemClock.elapsedRealtime()
            val realDelta = (now - previousRealtime).coerceAtLeast(0L)
            previousRealtime = now
            elapsedMillis = TripPlaybackTimeline.advanceElapsed(
                currentElapsedMillis = elapsedMillis,
                realDeltaMillis = realDelta,
                speedMultiplier = speed,
                totalMillis = totalMillis
            )
            if (elapsedMillis >= totalMillis) playing = false
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("轨迹回放", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            !playable -> "当前轨迹不足以进行可信回放"
                            playbackMode && frame?.isLongGap == true -> "GPS 缺口 · 保持最后真实定位点"
                            playbackMode -> "${formatPlaybackTime(elapsedMillis)} / ${formatPlaybackTime(totalMillis)} · ${formatPlaybackSpeed(speed)}"
                            else -> "${playbackPoints.size} 个 GPS 点 · 按真实时间推进"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (playbackMode && frame?.isLongGap == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = {
                        playbackMode = !playbackMode
                        playing = false
                        if (!playbackMode) elapsedMillis = 0L
                    },
                    enabled = playable,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(if (playbackMode) "退出" else "回放")
                }
            }

            if (playbackMode && playable) {
                geometry?.takeIf { it.isDrawable }?.let { routeGeometry ->
                    PlaybackCanvasV06(
                        points = playbackPoints,
                        frame = frame,
                        elapsedMillis = elapsedMillis,
                        minLatitude = routeGeometry.minLatitude,
                        maxLatitude = routeGeometry.maxLatitude,
                        minLongitude = routeGeometry.minLongitude,
                        maxLongitude = routeGeometry.maxLongitude,
                        accent = accent
                    )
                }

                Slider(
                    value = elapsedMillis.toFloat().coerceIn(0f, totalMillis.toFloat()),
                    onValueChange = {
                        playing = false
                        elapsedMillis = it.toLong().coerceIn(0L, totalMillis)
                    },
                    valueRange = 0f..totalMillis.toFloat().coerceAtLeast(1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "行程回放进度" }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            elapsedMillis = 0L
                            playing = false
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = "重新开始回放")
                    }
                    Spacer(Modifier.width(MaterialTheme.spacing.xs))
                    IconButton(
                        onClick = {
                            if (elapsedMillis >= totalMillis) elapsedMillis = 0L
                            playing = !playing
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playing) "暂停回放" else "开始回放"
                        )
                    }
                    Spacer(Modifier.width(MaterialTheme.spacing.sm))
                    Text(
                        "${formatPlaybackTime(elapsedMillis)} / ${formatPlaybackTime(totalMillis)}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)
                ) {
                    TripPlaybackTimeline.speedPresets.forEach { preset ->
                        val selected = speed == preset
                        Surface(
                            onClick = { speed = preset },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = if (selected) accent.copy(alpha = .12f) else MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    formatPlaybackSpeed(preset),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (frame?.isLongGap == true) {
                    Text(
                        "GPS 缺口 · ${formatPlaybackTime(frame.longGapStartElapsedMillis ?: 0L)} → ${formatPlaybackTime(frame.longGapEndElapsedMillis ?: elapsedMillis)}。缺失区间不会插值成连续驾驶。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackCanvasV06(
    points: List<TripPointEntity>,
    frame: TripPlaybackFrame?,
    elapsedMillis: Long,
    minLatitude: Double,
    maxLatitude: Double,
    minLongitude: Double,
    maxLongitude: Double,
    accent: androidx.compose.ui.graphics.Color
) {
    val endColor = MaterialTheme.colorScheme.error
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(170.dp)
            .semantics {
                contentDescription = if (frame?.isLongGap == true) {
                    "行程轨迹回放；当前处于 GPS 缺口，车辆位置保持在最后真实定位点"
                } else {
                    "行程轨迹回放；移动标记沿真实定位时间推进"
                }
            }
    ) {
        if (points.isEmpty()) return@Canvas
        val pad = 12.dp.toPx()
        val width = (size.width - pad * 2).coerceAtLeast(1f)
        val height = (size.height - pad * 2).coerceAtLeast(1f)
        val latSpan = (maxLatitude - minLatitude).takeIf { it > 0.0 } ?: 1.0
        val lonSpan = (maxLongitude - minLongitude).takeIf { it > 0.0 } ?: 1.0
        val startTime = points.first().capturedAtEpochMillis
        val longGapMillis = TripContinuityRules.LONG_GAP_SECONDS * 1_000L

        fun project(latitude: Double, longitude: Double): Offset {
            val x = ((longitude - minLongitude) / lonSpan).toFloat().coerceIn(0f, 1f)
            val y = (1.0 - (latitude - minLatitude) / latSpan).toFloat().coerceIn(0f, 1f)
            return Offset(pad + x * width, pad + y * height)
        }

        points.zipWithNext().forEach { (from, to) ->
            val delta = to.capturedAtEpochMillis - from.capturedAtEpochMillis
            if (delta <= 0L || delta >= longGapMillis) return@forEach
            val start = project(from.latitude, from.longitude)
            val end = project(to.latitude, to.longitude)
            drawLine(
                color = accent.copy(alpha = .18f),
                start = start,
                end = end,
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )

            val fromElapsed = (from.capturedAtEpochMillis - startTime).coerceAtLeast(0L)
            val toElapsed = (to.capturedAtEpochMillis - startTime).coerceAtLeast(fromElapsed)
            when {
                elapsedMillis >= toElapsed -> drawLine(
                    color = accent.copy(alpha = .82f),
                    start = start,
                    end = end,
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )

                elapsedMillis > fromElapsed && toElapsed > fromElapsed -> {
                    val fraction = ((elapsedMillis - fromElapsed).toDouble() / (toElapsed - fromElapsed).toDouble())
                        .coerceIn(0.0, 1.0)
                    val partial = Offset(
                        x = start.x + (end.x - start.x) * fraction.toFloat(),
                        y = start.y + (end.y - start.y) * fraction.toFloat()
                    )
                    drawLine(
                        color = accent.copy(alpha = .82f),
                        start = start,
                        end = partial,
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
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

        val poleHeight = 13.dp.toPx()
        val flagWidth = 9.dp.toPx()
        val flagHeight = 6.dp.toPx()
        drawLine(endColor, endPoint, Offset(endPoint.x, endPoint.y - poleHeight), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        val flag = Path().apply {
            moveTo(endPoint.x, endPoint.y - poleHeight)
            lineTo(endPoint.x + flagWidth, endPoint.y - poleHeight)
            lineTo(endPoint.x + flagWidth, endPoint.y - poleHeight + flagHeight)
            lineTo(endPoint.x, endPoint.y - poleHeight + flagHeight)
            close()
        }
        drawPath(flag, endColor)

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
            rotate(degrees = direction, pivot = currentOffset) {
                drawPath(vehicle, accent)
            }
        }
    }
}

private fun formatPlaybackSpeed(value: Float): String =
    if (value % 1f == 0f) "${value.toInt()}×" else String.format(Locale.US, "%.1f×", value)

private fun formatPlaybackTime(valueMillis: Long): String {
    val totalSeconds = valueMillis.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
