package com.evchargebook.ui.trip

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.evchargebook.ui.theme.EVDesignTokens
import java.util.Locale
import kotlin.math.max

internal data class TripTrendSampleV06(
    val timestamp: Long,
    val value: Double
)

/**
 * Lightweight Compose-native trend plot used by active and completed Trip surfaces.
 * Text values remain authoritative; the chart never smooths or bridges long GPS gaps.
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
    val minValue = samples.minOf { it.value }
    val maxValue = samples.maxOf { it.value }
    val valueRange = max(1.0, maxValue - minValue)
    val midValue = minValue + valueRange / 2.0
    val minTime = samples.first().timestamp
    val maxTime = samples.last().timestamp
    val timeRangeMs = max(1L, maxTime - minTime)
    val midElapsedMs = timeRangeMs / 2

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.width(44.dp).height(76.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                AxisValue(formatTripTrendAxisValue(maxValue, unit))
                AxisValue(formatTripTrendAxisValue(midValue, unit))
                AxisValue(formatTripTrendAxisValue(minValue, unit))
            }

            Canvas(Modifier.weight(1f).height(76.dp)) {
                val padY = 5.dp.toPx()
                val usableHeight = (size.height - padY * 2).coerceAtLeast(1f)
                val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)

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
                    val x = ((sample.timestamp - minTime).toDouble() / timeRangeMs.toDouble()).toFloat() * size.width
                    val normalized = ((sample.value - minValue) / valueRange).toFloat().coerceIn(0f, 1f)
                    return Offset(x, padY + (1f - normalized) * usableHeight)
                }

                samples.zipWithNext().forEach { (from, to) ->
                    if (to.timestamp - from.timestamp <= longGapMs) {
                        drawLine(
                            color = accent.copy(alpha = 0.82f),
                            start = point(from),
                            end = point(to),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(44.dp))
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AxisTime("0s", TextAlign.Start)
                AxisTime(formatTripTrendElapsed(midElapsedMs), TextAlign.Center)
                AxisTime(formatTripTrendElapsed(timeRangeMs), TextAlign.End)
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
