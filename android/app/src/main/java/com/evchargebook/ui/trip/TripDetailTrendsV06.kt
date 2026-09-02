package com.evchargebook.ui.trip

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.TripPointEntity
import com.evchargebook.data.entity.TripSessionEntity
import com.evchargebook.domain.TripSpeedTrustRules
import com.evchargebook.ui.theme.EVDesignTokens
import com.evchargebook.ui.theme.spacing
import java.util.Locale

private const val DETAIL_TREND_LONG_GAP_MS = 120_000L

@Composable
internal fun CompletedTripTrendsV06(
    trip: TripSessionEntity,
    points: List<TripPointEntity>,
) {
    val speedSamples = remember(points) {
        points.mapNotNull { point ->
            trustedDetailSpeedV06(point)?.let {
                TripTrendSampleV06(
                    timestamp = point.capturedAtEpochMillis,
                    value = it * 3.6,
                    capturedAtElapsedRealtimeNanos = point.capturedAtElapsedRealtimeNanos,
                )
            }
        }
    }
    val fallbackAverageMps = if (trip.elapsedSeconds > 0L && trip.distanceMeters.isFinite() && trip.distanceMeters >= 0.0) {
        trip.distanceMeters / trip.elapsedSeconds
    } else {
        null
    }
    val averageSpeedKph = (trip.averageSpeedMps ?: fallbackAverageMps)
        ?.takeIf { it.isFinite() && it >= 0.0 }
        ?.times(3.6)
    val maxSpeedKph = trip.maxSpeedMps
        ?.takeIf { it.isFinite() && it >= 0.0 }
        ?.times(3.6)
        ?: speedSamples.maxOfOrNull { it.value }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
    ) {
        RouteSnapshotBarV07(
            distanceMeters = trip.distanceMeters,
            elapsedSeconds = trip.elapsedSeconds,
            averageSpeedKph = averageSpeedKph,
            maxSpeedKph = maxSpeedKph,
        )
        SpeedTrendCardV07(
            samples = speedSamples,
            averageSpeedKph = averageSpeedKph,
            maxSpeedKph = maxSpeedKph,
        )
    }
}

private data class RouteSnapshotMetricV07(
    val icon: ImageVector,
    val label: String,
    val value: String,
)

@Composable
private fun RouteSnapshotBarV07(
    distanceMeters: Double,
    elapsedSeconds: Long,
    averageSpeedKph: Double?,
    maxSpeedKph: Double?,
) {
    val metrics = listOf(
        RouteSnapshotMetricV07(Icons.Default.LocationOn, "轨迹里程", formatRouteDistanceV07(distanceMeters)),
        RouteSnapshotMetricV07(Icons.Default.Schedule, "行驶时长", formatTrendDurationV07(elapsedSeconds)),
        RouteSnapshotMetricV07(Icons.Default.Speed, "平均速度", formatSpeedMetricV07(averageSpeedKph)),
        RouteSnapshotMetricV07(Icons.Default.ShowChart, "最高速度", formatSpeedMetricV07(maxSpeedKph)),
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .16f)),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (maxWidth < 400.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    metrics.chunked(2).forEach { rowMetrics ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            rowMetrics.forEach { metric ->
                                RouteSnapshotMetricCellV07(metric, Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    metrics.forEachIndexed { index, metric ->
                        RouteSnapshotMetricCellV07(metric, Modifier.weight(1f))
                        if (index < metrics.lastIndex) {
                            VerticalDivider(
                                modifier = Modifier.height(42.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = .18f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteSnapshotMetricCellV07(
    metric: RouteSnapshotMetricV07,
    modifier: Modifier,
) {
    val accent = EVDesignTokens.Energy.green
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = accent.copy(alpha = .12f),
        ) {
            Icon(
                metric.icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.padding(7.dp).size(18.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                metric.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                metric.value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SpeedTrendCardV07(
    samples: List<TripTrendSampleV06>,
    averageSpeedKph: Double?,
    maxSpeedKph: Double?,
) {
    val accent = EVDesignTokens.Energy.green

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .16f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.ShowChart,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(21.dp),
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    "速度趋势",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "可信 GPS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (samples.size < 2) {
                Text(
                    "暂无足够可信速度样本",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .13f)),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(Modifier.fillMaxWidth()) {
                            TrendHeroMetricV07("平均", averageSpeedKph, Modifier.weight(1f))
                            VerticalDivider(
                                modifier = Modifier.height(48.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = .16f),
                            )
                            TrendHeroMetricV07("最高", maxSpeedKph, Modifier.weight(1f))
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .13f))
                        TripTrendPlotV06(
                            samples = samples,
                            unit = "km/h",
                            longGapMs = DETAIL_TREND_LONG_GAP_MS,
                            plotHeight = 168.dp,
                            fillArea = true,
                            showInteractionHeader = false,
                        )
                    }
                }

                Text(
                    "长按查看读点 · 拖动时间轴 · 双指缩放区间",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TrendHeroMetricV07(
    label: String,
    value: Double?,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value?.let { String.format(Locale.US, "%.0f", it) } ?: "--",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "km/h",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun trustedDetailSpeedV06(point: TripPointEntity): Double? {
    val speed = point.speedMps ?: return null
    if (!speed.isFinite() || speed < 0.0) return null
    return speed.takeIf {
        TripSpeedTrustRules.eligibleForMeasuredSpeed(
            reportedSpeedMps = speed,
            provider = point.provider,
            horizontalAccuracyMeters = point.horizontalAccuracyMeters,
            speedAccuracyMps = point.speedAccuracyMps,
        )
    }
}

private fun formatRouteDistanceV07(meters: Double): String = when {
    !meters.isFinite() || meters < 0.0 -> "--"
    meters >= 1000.0 -> String.format(Locale.US, "%.1f km", meters / 1000.0)
    else -> String.format(Locale.US, "%.0f m", meters)
}

private fun formatSpeedMetricV07(valueKph: Double?): String =
    valueKph?.takeIf { it.isFinite() && it >= 0.0 }
        ?.let { String.format(Locale.US, "%.0f km/h", it) }
        ?: "--"

private fun formatTrendDurationV07(totalSeconds: Long): String {
    val secondsSafe = totalSeconds.coerceAtLeast(0L)
    val hours = secondsSafe / 3_600L
    val minutes = (secondsSafe % 3_600L) / 60L
    val seconds = secondsSafe % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
