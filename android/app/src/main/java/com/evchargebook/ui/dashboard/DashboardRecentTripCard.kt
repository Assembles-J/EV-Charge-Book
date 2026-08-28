package com.evchargebook.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RecentTripHeader(onViewAll)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenTrip(trip.id) }
                    .padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        formatTime(trip.startedAtEpochMillis),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = CircleShape,
                        color = EVDesignTokens.Energy.green.copy(alpha = 0.10f)
                    ) {
                        Text(
                            "已完成",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = EVDesignTokens.Energy.green
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    TripRouteRail()
                    Spacer(Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TripEndpointRow(
                            address = endpointText(startAddress, trip.startLatitude, trip.startLongitude, resolving)
                        )
                        TripEndpointRow(
                            address = endpointText(endAddress, trip.endLatitude, trip.endLongitude, resolving)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TripMetric(formatDistanceValue(trip.distanceMeters), "km", Modifier.weight(0.72f))
                    MetricSeparator()
                    TripMetric(formatDuration(trip.elapsedSeconds), "时长", Modifier.weight(0.74f))
                    MetricSeparator()
                    TripMetric(formatConsumptionValue(trip.averageConsumptionKwhPer100Km), "能耗", Modifier.weight(0.74f))
                    MetricSeparator()
                    TripMetric(formatSocRange(trip.startSoc, trip.endSoc), "SOC", Modifier.weight(1.0f))
                    MetricSeparator()
                    TripMetric(formatMileageRange(trip.startMileageKm, trip.endMileageKm), "里程", Modifier.weight(1.8f))
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
                    .size(38.dp)
                    .background(EVDesignTokens.Energy.green.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Route,
                    contentDescription = null,
                    tint = EVDesignTokens.Energy.green,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "最近行程",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clip(CircleShape)
                .clickable(onClick = onViewAll)
                .padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "查看全部",
                style = MaterialTheme.typography.bodyMedium,
                color = EVDesignTokens.Energy.green
            )
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "查看全部行程",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
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
        Box(
            Modifier
                .size(10.dp)
                .border(1.5.dp, markerColor.copy(alpha = 0.90f), CircleShape)
        )
        Box(
            Modifier
                .size(width = 1.dp, height = 42.dp)
                .background(markerColor.copy(alpha = 0.42f))
        )
        Box(
            Modifier
                .size(10.dp)
                .background(markerColor.copy(alpha = 0.68f), CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f), CircleShape)
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
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
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
            .size(width = 1.dp, height = 34.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
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
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RecentTripHeader(onViewAll)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TripRouteRail()
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "完成第一段行程后，这里会显示距离、SOC、里程与可信的能耗估算。",
                        style = MaterialTheme.typography.bodyMedium,
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

private fun formatSocRange(start: Int?, end: Int?): String =
    "${start?.let { "$it%" } ?: "--"}→${end?.let { "$it%" } ?: "--"}"

private fun formatMileageRange(start: Double?, end: Double?): String = when {
    start != null && end != null -> "${formatMileage(start)}→${formatMileage(end)}"
    start != null -> "${formatMileage(start)}→--"
    end != null -> "--→${formatMileage(end)}"
    else -> "--"
}

private fun formatMileage(value: Double): String =
    if (value % 1.0 == 0.0) String.format(Locale.US, "%,.0f", value)
    else String.format(Locale.US, "%,.1f", value)

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
