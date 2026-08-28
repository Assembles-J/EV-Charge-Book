package com.evchargebook.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ArrowForward
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.TripSessionEntity
import com.evchargebook.location.AndroidGeocoderAddressResolver
import com.evchargebook.ui.components.ResponsiveMetricGrid
import com.evchargebook.ui.theme.spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardRecentTripCard(trip: TripSessionEntity?) {
    if (trip == null) {
        RecentTripEmptyState()
        return
    }

    val context = LocalContext.current
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
        startAddress = resolveEndpoint(
            resolver = resolver,
            latitude = trip.startLatitude,
            longitude = trip.startLongitude
        )
        endAddress = when {
            trip.endLatitude == null || trip.endLongitude == null -> null
            trip.startLatitude == trip.endLatitude && trip.startLongitude == trip.endLongitude -> startAddress
            else -> resolveEndpoint(
                resolver = resolver,
                latitude = trip.endLatitude,
                longitude = trip.endLongitude
            )
        }
        resolving = false
    }

    val metrics = listOf(
        "距离" to formatDistance(trip.distanceMeters),
        "耗时" to formatDuration(trip.elapsedSeconds),
        "估算能耗" to formatConsumption(trip.averageConsumptionKwhPer100Km)
    )

    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("最近行程", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("RECENT TRIP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                formatTime(trip.startedAtEpochMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier.padding(MaterialTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
            ) {
                RouteSummary(
                    start = endpointText(startAddress, trip.startLatitude, trip.startLongitude, resolving),
                    end = endpointText(endAddress, trip.endLatitude, trip.endLongitude, resolving)
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))

                ResponsiveMetricGrid(metrics.size) { index, modifier ->
                    val (label, value) = metrics[index]
                    RecentTripMetric(label, value, modifier)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg)
                ) {
                    RecentTripStateValue(
                        label = "SOC",
                        value = formatSocRange(trip.startSoc, trip.endSoc),
                        modifier = Modifier.weight(1f)
                    )
                    RecentTripStateValue(
                        label = "总里程",
                        value = formatMileageRange(trip.startMileageKm, trip.endMileageKm),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteSummary(start: String, end: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = .10f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Route,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                start,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    end,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RecentTripMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RecentTripStateValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun RecentTripEmptyState() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
        ) {
            Box(
                Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .10f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Route, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f)) {
                Text("最近行程", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "完成第一段行程后，这里会显示距离、SOC、里程与可信的能耗估算。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
    "${start?.let { "$it%" } ?: "--"} → ${end?.let { "$it%" } ?: "--"}"

private fun formatMileageRange(start: Double?, end: Double?): String = when {
    start != null && end != null -> "${formatMileage(start)} → ${formatMileage(end)} km"
    start != null -> "${formatMileage(start)} → -- km"
    end != null -> "-- → ${formatMileage(end)} km"
    else -> "--"
}

private fun formatMileage(value: Double): String =
    if (value % 1.0 == 0.0) String.format(Locale.US, "%,.0f", value)
    else String.format(Locale.US, "%,.1f", value)

private fun formatConsumption(value: Double?): String =
    value?.takeIf { it.isFinite() && it >= 0.0 }
        ?.let { String.format(Locale.US, "%.1f kWh/100km", it) }
        ?: "--"

private fun formatDistance(meters: Double): String =
    if (meters >= 1000.0) String.format(Locale.US, "%.1f km", meters / 1000.0)
    else String.format(Locale.US, "%.0f m", meters)

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}

private fun formatTime(epochMillis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.SIMPLIFIED_CHINESE).format(Date(epochMillis))

private fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.5f", value)
