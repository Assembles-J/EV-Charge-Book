package com.evchargebook.ui.trip

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.TripPointEntity
import com.evchargebook.domain.TripSpeedTrustRules
import com.evchargebook.ui.theme.EVDesignTokens
import com.evchargebook.ui.theme.spacing
import java.util.Locale
import kotlin.math.max

private const val DETAIL_TREND_LONG_GAP_MS = 120_000L

private data class DetailTrendSampleV06(
    val timestamp: Long,
    val value: Double
)

@Composable
internal fun CompletedTripTrendsV06(points: List<TripPointEntity>) {
    val speedSamples = remember(points) {
        points.mapNotNull { point ->
            trustedDetailSpeedV06(point)?.let { DetailTrendSampleV06(point.capturedAtEpochMillis, it * 3.6) }
        }
    }
    val altitudeSamples = remember(points) {
        points.mapNotNull { point ->
            trustedDetailAltitudeV06(point)?.let { DetailTrendSampleV06(point.capturedAtEpochMillis, it) }
        }
    }

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
                    "仅使用可信定位样本；超过 2 分钟的缺口保持断开。",
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

@Composable
private fun DetailTrendCardV06(
    title: String,
    unit: String,
    samples: List<DetailTrendSampleV06>,
    modifier: Modifier,
    emptyText: String
) {
    val accent = EVDesignTokens.Energy.green
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (samples.size < 2) {
                Text(
                    emptyText,
                    modifier = Modifier.height(72.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    formatDetailTrendValueV06(samples.last().value, unit),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Canvas(Modifier.fillMaxWidth().height(58.dp)) {
                    val minValue = samples.minOf { it.value }
                    val maxValue = samples.maxOf { it.value }
                    val valueRange = max(1.0, maxValue - minValue)
                    val minTime = samples.first().timestamp
                    val maxTime = samples.last().timestamp
                    val timeRange = max(1L, maxTime - minTime).toDouble()
                    val padY = 4.dp.toPx()
                    val usableHeight = (size.height - padY * 2).coerceAtLeast(1f)

                    fun point(sample: DetailTrendSampleV06): Offset {
                        val x = ((sample.timestamp - minTime) / timeRange).toFloat() * size.width
                        val normalized = ((sample.value - minValue) / valueRange).toFloat().coerceIn(0f, 1f)
                        return Offset(x, padY + (1f - normalized) * usableHeight)
                    }

                    samples.zipWithNext().forEach { (from, to) ->
                        if (to.timestamp - from.timestamp <= DETAIL_TREND_LONG_GAP_MS) {
                            drawLine(
                                color = accent.copy(alpha = .78f),
                                start = point(from),
                                end = point(to),
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
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
    if (unit == "m") String.format(Locale.US, "%.0f %s", value, unit)
    else String.format(Locale.US, "%.0f %s", value, unit)
