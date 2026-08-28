package com.evchargebook.ui.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.TripSessionEntity
import com.evchargebook.data.entity.TripStatus
import com.evchargebook.domain.TripValidityRules
import com.evchargebook.domain.TripValidityStatus
import com.evchargebook.location.AndroidGeocoderAddressResolver
import com.evchargebook.ui.theme.EVDesignTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v0.6 compact history row.
 *
 * Presentation only: addresses are reverse-geocoded from persisted Trip coordinates and always
 * retain a coordinate / unavailable fallback. No route or endpoint is invented by the UI.
 */
@Composable
internal fun TripHistoryCardV06(
    trip: TripSessionEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val endpoints = rememberTripEndpointLabels(trip)
    val validity = remember(trip) { TripValidityRules.assess(trip) }
    val accent = EVDesignTokens.Energy.green
    val statusLabel = when (validity.status) {
        TripValidityStatus.INVALID -> "无效 · 不计汇总"
        TripValidityStatus.REVIEW -> "建议检查"
        else -> compactTripStatus(trip.status)
    }
    val statusColor = when {
        validity.status == TripValidityStatus.INVALID -> MaterialTheme.colorScheme.error
        validity.status == TripValidityStatus.REVIEW -> MaterialTheme.colorScheme.tertiary
        trip.status == TripStatus.INTERRUPTED -> MaterialTheme.colorScheme.error
        else -> accent
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(32.dp).background(accent.copy(alpha = 0.09f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(9.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTripHistoryTime(trip.startedAtEpochMillis),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        maxLines = 1
                    )
                }

                EndpointHistoryLineV06("起", endpoints.start)
                EndpointHistoryLineV06("终", endpoints.end)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTripHistoryDistance(trip.distanceMeters),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatTripHistoryDuration(trip.elapsedSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = trip.averageConsumptionKwhPer100Km
                            ?.takeIf { it.isFinite() && it >= 0.0 }
                            ?.let { String.format(Locale.US, "%.1f kWh/100km", it) }
                            ?: "能耗 --",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (trip.status == TripStatus.COMPLETED) {
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除行程",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

@Composable
private fun EndpointHistoryLineV06(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

private data class TripEndpointLabels(
    val start: String,
    val end: String
)

@Composable
private fun rememberTripEndpointLabels(trip: TripSessionEntity): TripEndpointLabels {
    val context = LocalContext.current
    val resolver = remember(context) { AndroidGeocoderAddressResolver(context) }
    val startFallback = endpointFallback(trip.startLatitude, trip.startLongitude)
    val endFallback = endpointFallback(trip.endLatitude, trip.endLongitude)

    var start by remember(trip.id, trip.startLatitude, trip.startLongitude) {
        mutableStateOf(startFallback)
    }
    var end by remember(trip.id, trip.endLatitude, trip.endLongitude) {
        mutableStateOf(endFallback)
    }

    LaunchedEffect(trip.id, trip.startLatitude, trip.startLongitude) {
        val latitude = trip.startLatitude
        val longitude = trip.startLongitude
        start = if (latitude != null && longitude != null) {
            resolver.reverse(latitude, longitude) ?: startFallback
        } else {
            startFallback
        }
    }

    LaunchedEffect(trip.id, trip.endLatitude, trip.endLongitude) {
        val latitude = trip.endLatitude
        val longitude = trip.endLongitude
        end = if (latitude != null && longitude != null) {
            resolver.reverse(latitude, longitude) ?: endFallback
        } else {
            endFallback
        }
    }

    return TripEndpointLabels(start = start, end = end)
}

private fun endpointFallback(latitude: Double?, longitude: Double?): String {
    if (latitude == null || longitude == null) return "未记录"
    return String.format(Locale.US, "%.4f, %.4f", latitude, longitude)
}

private fun formatTripHistoryTime(epochMillis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.SIMPLIFIED_CHINESE).format(Date(epochMillis))

private fun formatTripHistoryDistance(meters: Double): String =
    if (meters >= 1000.0) {
        String.format(Locale.US, "%.1f km", meters / 1000.0)
    } else {
        String.format(Locale.US, "%.0f m", meters)
    }

private fun formatTripHistoryDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}

private fun compactTripStatus(status: String): String = when (status) {
    TripStatus.COMPLETED -> "已完成"
    TripStatus.INTERRUPTED -> "已中断"
    TripStatus.RECORDING -> "记录中"
    else -> status
}
