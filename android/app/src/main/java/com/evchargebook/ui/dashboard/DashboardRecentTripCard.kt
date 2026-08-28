package com.evchargebook.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evchargebook.data.entity.TripSessionEntity
import com.evchargebook.location.AndroidGeocoderAddressResolver
import com.evchargebook.ui.theme.EVDesignTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardRecentTripCard(
    trip: TripSessionEntity?,
    onViewAll: () -> Unit = {},
    onOpenTrip: (Long) -> Unit = {}
) {
    if (trip == null) {
        RecentTripEmptyState(onViewAll)
        return
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val resolver = remember(context) { AndroidGeocoderAddressResolver(context) }
    var startAddress by remember(trip.id) { mutableStateOf<String?>(null) }
    var endAddress by remember(trip.id) { mutableStateOf<String?>(null) }
    var resolving by remember(trip.id) { mutableStateOf(false) }

    LaunchedEffect(
        trip.id,
        trip.startLatitude,
        trip.startLongitude,
        trip.endLatitude,
        trip.endLongitude
    ) {
        resolving = true
        startAddress = resolveEndpoint(resolver, trip.startLatitude, trip.startLongitude)
        endAddress = when {
            trip.endLatitude == null || trip.endLongitude == null -> null
            trip.startLatitude == trip.endLatitude && trip.startLongitude == trip.endLongitude -> startAddress
            else -> resolveEndpoint(resolver, trip.endLatitude, trip.endLongitude)
        }
        resolving = false
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = Color(0xFF0A1210)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RecentTripHeader(onViewAll)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenTrip(trip.id) }
                    .padding(top = 1.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        formatTime(trip.startedAtEpochMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = CircleShape,
                        color = EVDesignTokens.Energy.green.copy(alpha = 0.10f)
                    ) {
                        Text(
                            "已完成",
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = EVDesignTokens.Energy.green
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    TripRouteRail()
                    Spacer(Modifier.width(10.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TripEndpointRow(
                            address = endpointText(startAddress, trip.startLatitude, trip.startLongitude, resolving)
                        )
                        TripEndpointRow(
                            address = endpointText(endAddress, trip.endLatitude, trip.endLongitude, resolving)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TripMetric(formatDistanceValue(trip.distanceMeters), "km", Modifier.weight(1f))
                    MetricSeparator()
                    TripMetric(formatDuration(trip.elapsedSeconds), "时长", Modifier.weight(1f))
                    MetricSeparator()
                    TripMetric(formatConsumptionValue(trip.averageConsumptionKwhPer100Km), "kWh/100km", Modifier.weight(1.15f))
                    MetricSeparator()
                    TripMetric(formatSocConsumed(trip.startSoc, trip.endSoc), "SOC", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RecentTripHeader(onViewAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(EVDesignTokens.Energy.green.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Route,
                    contentDescription = null,
                    tint = EVDesignTokens.Energy.green,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "最近行程",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clip(CircleShape)
                .clickable(onClick = onViewAll)
                .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "查看全部",
                style = MaterialTheme.typography.bodySmall,
                color = EVDesignTokens.Energy.green
            )
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "查看全部行程",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun TripEndpointRow(address: String) {
    Text(
        text = address,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun TripRouteRail() {
    val markerColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "起点",
            tint = markerColor.copy(alpha = 0.92f),
            modifier = Modifier.size(16.dp)
        )
        Box(
            Modifier
                .size(width = 1.dp, height = 28.dp)
                .background(markerColor.copy(alpha = 0.38f))
        )
        Icon(
            imageVector = Icons.Default.Flag,
            contentDescription = "终点",
            tint = markerColor.copy(alpha = 0.82f),
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
private fun TripMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(1.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
private fun MetricSeparator() {
    Box(
        Modifier
            .size(width = 1.dp, height = 30.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
    )
}

@Composable
private fun RecentTripEmptyState(onViewAll: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = Color(0xFF0A1210)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RecentTripHeader(onViewAll)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TripRouteRail()
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "完成第一段行程后，这里会显示距离、SOC 与可信的能耗估算。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private suspend fun resolveEndpoint(
    resolver: AndroidGeocoderAddressResolver,
    latitude: Double?,
    longitude: Double?
): String? {
    if (latitude == null || longitude == null) return null
    return resolver.reverse(latitude, longitude)
}

private fun endpointText(address: String?, latitude: Double?, longitude: Double?, resolving: Boolean): String = when {
    resolving && latitude != null && longitude != null -> "地址解析中…"
    !address.isNullOrBlank() -> address
    latitude != null && longitude != null -> "${formatCoordinate(latitude)}, ${formatCoordinate(longitude)}"
    else -> "地点信息不可用"
}

private fun formatSocConsumed(start: Int?, end: Int?): String = when {
    start == null || end == null -> "--"
    end > start -> "--"
    else -> "消耗 ${start - end}%"
}

private fun formatConsumptionValue(value: Double?): String =
    value?.takeIf { it.isFinite() && it >= 0.0 }
        ?.let { String.format(Locale.US, "%.1f", it) }
        ?: "--"

private fun formatDistanceValue(meters: Double): String =
    String.format(Locale.US, "%.1f", meters / 1000.0)

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0 -> "$hours:${minutes.toString().padStart(2, '0')}"
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}

private fun formatTime(epochMillis: Long): String {
    val event = Date(epochMillis)
    val now = Date()
    val day = SimpleDateFormat("yyyyMMdd", Locale.SIMPLIFIED_CHINESE)
    val time = SimpleDateFormat("HH:mm", Locale.SIMPLIFIED_CHINESE)
    return if (day.format(event) == day.format(now)) {
        "今天 ${time.format(event)}"
    } else {
        SimpleDateFormat("M月d日 HH:mm", Locale.SIMPLIFIED_CHINESE).format(event)
    }
}

private fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.5f", value)
