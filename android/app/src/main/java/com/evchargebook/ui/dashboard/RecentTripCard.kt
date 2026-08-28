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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Route
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
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.TripSessionEntity
import com.evchargebook.location.AndroidGeocoderAddressResolver
import com.evchargebook.ui.theme.EVDesignTokens
import com.evchargebook.ui.theme.LocalCockpitColors
import com.evchargebook.ui.theme.spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun RecentTripCard(trip: TripSessionEntity?) {
    val cockpit = LocalCockpitColors.current
    val context = LocalContext.current
    val resolver = remember(context) { AndroidGeocoderAddressResolver(context) }
    var startAddress by remember(trip?.id) { mutableStateOf<String?>(null) }
    var endAddress by remember(trip?.id) { mutableStateOf<String?>(null) }
    var resolving by remember(trip?.id) { mutableStateOf(false) }

    LaunchedEffect(
        trip?.id,
        trip?.startLatitude,
        trip?.startLongitude,
        trip?.endLatitude,
        trip?.endLongitude
    ) {
        if (trip == null) {
            startAddress = null
            endAddress = null
            resolving = false
            return@LaunchedEffect
        }
        resolving = true
        startAddress = if (trip.startLatitude != null && trip.startLongitude != null) {
            resolver.reverse(trip.startLatitude, trip.startLongitude)
        } else {
            null
        }
        endAddress = if (trip.endLatitude != null && trip.endLongitude != null) {
            if (trip.startLatitude == trip.endLatitude && trip.startLongitude == trip.endLongitude) {
                startAddress
            } else {
                resolver.reverse(trip.endLatitude, trip.endLongitude)
            }
        } else {
            null
        }
        resolving = false
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(EVDesignTokens.Energy.green.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Route,
                        contentDescription = null,
                        tint = EVDesignTokens.Energy.green,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        "最近行程",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "RECENT TRIP",
                        style = MaterialTheme.typography.labelSmall,
                        color = cockpit.secondaryText
                    )
                }
            }

            if (trip == null) {
                Text(
                    "完成一次行程后，这里会展示路线、SOC、里程和能耗摘要。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cockpit.secondaryText
                )
                return@Column
            }

            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs)) {
                Text(
                    routeText(trip, startAddress, endAddress, resolving),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = cockpit.primaryText
                )
                Text(
                    formatTripTime(trip.startedAtEpochMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = cockpit.secondaryText
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
            ) {
                RecentTripMetric(
                    label = "距离",
                    value = formatDistance(trip.distanceMeters),
                    modifier = Modifier.weight(1f)
                )
                RecentTripMetric(
                    label = "时长",
                    value = formatDuration(trip.elapsedSeconds),
                    modifier = Modifier.weight(1f)
                )
                val consumption = trip.averageConsumptionKwhPer100Km
                    ?.takeIf { it.isFinite() && it >= 0.0 }
                RecentTripMetric(
                    label = "估算能耗",
                    value = consumption?.let { String.format(Locale.US, "%.1f", it) } ?: "--",
                    supporting = consumption?.let { "kWh/100km" },
                    accent = consumption != null,
                    modifier = Modifier.weight(1.15f)
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg)
            ) {
                TripFact(
                    label = "SOC 变化",
                    value = formatSocRange(trip.startSoc, trip.endSoc),
                    modifier = Modifier.weight(1f)
                )
                TripFact(
                    label = "里程",
                    value = formatMileageRange(trip.startMileageKm, trip.endMileageKm),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun RecentTripMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    accent: Boolean = false
) {
    val cockpit = LocalCockpitColors.current
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = cockpit.secondaryText)
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            color = if (accent && value != "--") EVDesignTokens.Energy.green else cockpit.primaryText
        )
        if (supporting != null) {
            Text(
                supporting,
                style = MaterialTheme.typography.labelSmall,
                color = cockpit.secondaryText
            )
        }
    }
}

@Composable
private fun TripFact(label: String, value: String, modifier: Modifier = Modifier) {
    val cockpit = LocalCockpitColors.current
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = cockpit.secondaryText)
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = cockpit.primaryText
        )
    }
}

private fun routeText(
    trip: TripSessionEntity,
    startAddress: String?,
    endAddress: String?,
    resolving: Boolean
): String {
    if (resolving) return "起终点地址解析中…"
    if (!startAddress.isNullOrBlank() || !endAddress.isNullOrBlank()) {
        return "${startAddress ?: "起点地址暂不可用"}  →  ${endAddress ?: "终点地址暂不可用"}"
    }
    val start = coordinatePair(trip.startLatitude, trip.startLongitude)
    val end = coordinatePair(trip.endLatitude, trip.endLongitude)
    return if (start != null || end != null) {
        "${start ?: "起点未知"}  →  ${end ?: "终点未知"}"
    } else {
        "起终点地址暂不可用"
    }
}

private fun coordinatePair(latitude: Double?, longitude: Double?): String? =
    if (latitude != null && longitude != null) {
        String.format(Locale.US, "%.4f, %.4f", latitude, longitude)
    } else {
        null
    }

private fun formatTripTime(epochMillis: Long): String = DateTimeFormatter
    .ofPattern("M月d日 HH:mm", Locale.SIMPLIFIED_CHINESE)
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(epochMillis))

private fun formatDistance(meters: Double): String =
    if (meters >= 1000.0) String.format(Locale.US, "%.1f km", meters / 1000.0)
    else String.format(Locale.US, "%.0f m", meters)

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}" else "$minutes min"
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
