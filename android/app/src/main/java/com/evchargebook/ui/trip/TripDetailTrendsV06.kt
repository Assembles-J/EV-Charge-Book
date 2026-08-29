package com.evchargebook.ui.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.TripPointEntity
import com.evchargebook.domain.TripSpeedTrustRules
import com.evchargebook.ui.theme.spacing
import java.util.Locale

private const val DETAIL_TREND_LONG_GAP_MS = 120_000L

@Composable
internal fun CompletedTripTrendsV06(points: List<TripPointEntity>) {
    val speedSamples = remember(points) {
        points.mapNotNull { point ->
            trustedDetailSpeedV06(point)?.let { TripTrendSampleV06(point.capturedAtEpochMillis, it * 3.6) }
        }
    }
    val altitudeSamples = remember(points) {
        points.mapNotNull { point ->
            trustedDetailAltitudeV06(point)?.let { TripTrendSampleV06(point.capturedAtEpochMillis, it) }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
    ) {
        TripPlaybackRouteCardV06(points = points)

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("趋势", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "横轴为行程时间，纵轴为可信样本值；超过 2 分钟的缺口保持断开。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val compact = maxWidth < 360.dp
                    if (compact) {
                        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                            DetailTrendCardV06("速度", "km/h", speedSamples, Modifier.fillMaxWidth(), "暂无可信速度样本")
                            DetailTrendCardV06("海拔", "m", altitudeSamples, Modifier.fillMaxWidth(), "暂无可信海拔样本")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
                        ) {
                            DetailTrendCardV06("速度", "km/h", speedSamples, Modifier.weight(1f), "暂无可信速度样本")
                            DetailTrendCardV06("海拔", "m", altitudeSamples, Modifier.weight(1f), "暂无可信海拔样本")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailTrendCardV06(
    title: String,
    unit: String,
    samples: List<TripTrendSampleV06>,
    modifier: Modifier,
    emptyText: String
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (samples.size < 2) {
                Text(
                    emptyText,
                    modifier = Modifier.height(96.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                TrendSummaryV06(unit = unit, samples = samples)
                TripTrendPlotV06(
                    samples = samples,
                    unit = unit,
                    longGapMs = DETAIL_TREND_LONG_GAP_MS
                )
            }
        }
    }
}

@Composable
private fun TrendSummaryV06(unit: String, samples: List<TripTrendSampleV06>) {
    val min = samples.minOf { it.value }
    val max = samples.maxOf { it.value }
    val average = samples.map { it.value }.average()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (unit == "km/h") {
            TrendSummaryValueV06("平均", average, unit)
            TrendSummaryValueV06("最高", max, unit)
        } else {
            TrendSummaryValueV06("最低", min, unit)
            TrendSummaryValueV06("最高", max, unit)
        }
    }
}

@Composable
private fun TrendSummaryValueV06(label: String, value: Double, unit: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            formatDetailTrendValueV06(value, unit),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
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
            speedAccuracyMps = point.speedAccuracyMps
        )
    }
}

private fun trustedDetailAltitudeV06(point: TripPointEntity): Double? {
    val altitude = point.altitudeMeters?.takeIf { it.isFinite() } ?: return null
    val verticalAccuracy = point.verticalAccuracyMeters
    if (verticalAccuracy != null && (!verticalAccuracy.isFinite() || verticalAccuracy > 50.0)) return null
    val horizontalAccuracy = point.horizontalAccuracyMeters
    if (horizontalAccuracy != null && (!horizontalAccuracy.isFinite() || horizontalAccuracy > 80.0)) return null
    return altitude
}

private fun formatDetailTrendValueV06(value: Double, unit: String): String =
    String.format(Locale.US, "%.0f %s", value, unit)