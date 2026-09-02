package com.evchargebook.ui.trip

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.TripPointEntity
import com.evchargebook.domain.TripPlaybackSample
import com.evchargebook.domain.TripPlaybackTimeline
import com.evchargebook.domain.trip.TripGeoPoint
import com.evchargebook.domain.trip.TripRouteGeometryBuilder
import com.evchargebook.ui.theme.EVDesignTokens
import com.evchargebook.ui.theme.spacing
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
internal fun TripPlaybackRouteCardV06(points: List<TripPointEntity>, modifier: Modifier = Modifier) {
    val accent = EVDesignTokens.Energy.green
    val playbackPoints = remember(points) { points.filter { it.latitude.isFinite() && it.longitude.isFinite() } }
    val samples = remember(playbackPoints) {
        playbackPoints.map {
            TripPlaybackSample(
                capturedAtEpochMillis = it.capturedAtEpochMillis,
                latitude = it.latitude,
                longitude = it.longitude,
                bearingDegrees = it.bearingDegrees,
                capturedAtElapsedRealtimeNanos = it.capturedAtElapsedRealtimeNanos,
            )
        }
    }
    val geometry = remember(playbackPoints) {
        TripRouteGeometryBuilder.build(playbackPoints.map {
            TripGeoPoint(
                latitude = it.latitude,
                longitude = it.longitude,
                capturedAtEpochMillis = it.capturedAtEpochMillis,
                speedMps = trustedTripSpeedMpsV07(it),
                capturedAtElapsedRealtimeNanos = it.capturedAtElapsedRealtimeNanos,
            )
        })
    }
    val totalMillis = remember(samples) { TripPlaybackTimeline.durationMillis(samples) }
    val playable = geometry?.isDrawable == true && totalMillis > 0L && TripPlaybackTimeline.isChronological(samples)
    var playing by remember(playbackPoints.firstOrNull()?.tripId) { mutableStateOf(false) }
    var elapsedMillis by remember(playbackPoints.firstOrNull()?.tripId) { mutableLongStateOf(0L) }
    var speed by remember(playbackPoints.firstOrNull()?.tripId) { mutableFloatStateOf(1f) }
    val frame = remember(samples, elapsedMillis) { TripPlaybackTimeline.frameAt(samples, elapsedMillis) }
    val currentTrustedSpeedKph = frame?.let { playbackPoints.getOrNull(it.currentSampleIndex) }
        ?.let(::trustedTripSpeedMpsV07)?.times(3.6)

    LaunchedEffect(playing, speed, totalMillis) {
        if (!playing || totalMillis <= 0L) return@LaunchedEffect
        var previousRealtime = SystemClock.elapsedRealtime()
        while (playing) {
            delay(50L)
            val now = SystemClock.elapsedRealtime()
            elapsedMillis = TripPlaybackTimeline.advanceElapsed(
                currentElapsedMillis = elapsedMillis,
                realDeltaMillis = (now - previousRealtime).coerceAtLeast(0L),
                speedMultiplier = speed,
                totalMillis = totalMillis,
            )
            previousRealtime = now
            if (elapsedMillis >= totalMillis) playing = false
        }
    }

    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            Text("轨迹回放", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    !playable -> "当前轨迹不足以进行可信回放"
                    frame?.isLongGap == true -> "GPS 缺口 · 保持最后真实定位点"
                    else -> listOfNotNull(
                        "${formatPlaybackTime(elapsedMillis)} / ${formatPlaybackTime(totalMillis)}",
                        formatPlaybackSpeed(speed),
                        currentTrustedSpeedKph?.let { String.format(Locale.US, "%.1f km/h", it) },
                    ).joinToString(" · ")
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (frame?.isLongGap == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (playable) {
                TripRouteViewportV07(points = playbackPoints, frame = frame, playbackMode = true, finalEndpoint = true)
                Slider(
                    value = elapsedMillis.toFloat().coerceIn(0f, totalMillis.toFloat()),
                    onValueChange = { playing = false; elapsedMillis = it.toLong().coerceIn(0L, totalMillis) },
                    valueRange = 0f..totalMillis.toFloat().coerceAtLeast(1f),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "行程回放进度" },
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { elapsedMillis = 0L; playing = false }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Replay, contentDescription = "重新开始回放")
                    }
                    Spacer(Modifier.width(MaterialTheme.spacing.xs))
                    IconButton(onClick = { if (elapsedMillis >= totalMillis) elapsedMillis = 0L; playing = !playing }, modifier = Modifier.size(48.dp)) {
                        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (playing) "暂停回放" else "开始回放")
                    }
                    Spacer(Modifier.width(MaterialTheme.spacing.sm))
                    Text("${formatPlaybackTime(elapsedMillis)} / ${formatPlaybackTime(totalMillis)}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                    TripPlaybackTimeline.speedPresets.forEach { preset ->
                        val selected = speed == preset
                        Surface(
                            onClick = { speed = preset },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = if (selected) accent.copy(alpha = .12f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
                                Text(formatPlaybackSpeed(preset), color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                }
                if (frame?.isLongGap == true) {
                    Text(
                        "GPS 缺口 · ${formatPlaybackTime(frame.longGapStartElapsedMillis ?: 0L)} → ${formatPlaybackTime(frame.longGapEndElapsedMillis ?: elapsedMillis)}。缺失区间不会插值成连续驾驶。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

private fun formatPlaybackSpeed(value: Float): String = if (value % 1f == 0f) "${value.toInt()}×" else String.format(Locale.US, "%.1f×", value)

private fun formatPlaybackTime(valueMillis: Long): String {
    val totalSeconds = valueMillis.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
