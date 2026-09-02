package com.evchargebook.ui.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.TripPointEntity
import com.evchargebook.data.entity.TripSessionEntity
import com.evchargebook.data.entity.TripStatus
import com.evchargebook.data.entity.VehicleEntity
import com.evchargebook.ui.theme.spacing
import java.util.Locale
import kotlinx.coroutines.delay

/** Trip router + compact active Trip experience. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripScreen(
    vehicle: VehicleEntity?,
    vehicles: List<VehicleEntity>,
    trips: List<TripSessionEntity>,
    activeTrip: TripSessionEntity?,
    selectedTripId: Long?,
    selectedTripPoints: List<TripPointEntity>,
    onStart: () -> Unit,
    onResume: (Long) -> Unit,
    onStop: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onCloseDetail: () -> Unit,
    onDelete: (TripSessionEntity) -> Unit
) {
    val selectedTrip = selectedTripId?.let { id ->
        trips.firstOrNull { it.id == id } ?: activeTrip?.takeIf { it.id == id }
    }
    if (selectedTrip != null) {
        TripDetailScreenV06(selectedTrip, vehicles, selectedTripPoints, onCloseDetail)
        return
    }

    if (activeTrip == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无进行中的行程", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val activeVehicle = vehicles.firstOrNull { it.id == activeTrip.vehicleId } ?: vehicle
    val interrupted = activeTrip.status == TripStatus.INTERRUPTED
    val liveElapsedSeconds = rememberLiveTripElapsedSeconds(activeTrip)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("行程中", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
        ) {
            item { TripActiveVehicleHeroV08(vehicle = activeVehicle) }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
                    ) {
                        Text(
                            if (interrupted) "记录已中断" else "行程进行中",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (interrupted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                        ActiveMetricRowsV08(
                            elapsedSeconds = liveElapsedSeconds,
                            distanceMeters = activeTrip.distanceMeters,
                            averageSpeedMps = activeTrip.averageSpeedMps,
                            maxSpeedMps = activeTrip.maxSpeedMps
                        )
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
                    ) {
                        Text("实时轨迹", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        if (selectedTripPoints.size >= 2) {
                            TripRouteViewportV07(
                                points = selectedTripPoints,
                                finalEndpoint = false,
                                height = 190.dp
                            )
                        } else {
                            Text(
                                "正在等待足够的可信 GPS 点绘制轨迹…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (interrupted) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.error.copy(alpha = .08f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md),
                            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
                        ) {
                            Text("定位记录已中断", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                "修复定位权限或系统定位后，由你明确恢复同一条行程；不会在后台擅自恢复。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(onClick = { onResume(activeTrip.id) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(Modifier.width(MaterialTheme.spacing.xs))
                                Text("恢复记录")
                            }
                        }
                    }
                }
            }

            item {
                TripSlideAction(
                    label = if (interrupted) "滑动结束已中断行程" else "滑动结束行程",
                    enabled = true,
                    onConfirmed = onStop,
                    icon = Icons.Default.Stop,
                    releaseLabel = "松开结束"
                )
                Text(
                    "结束确认后才计算本次能耗等完成态数据；进行中不展示临时估算。",
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item { Spacer(Modifier.size(MaterialTheme.spacing.md)) }
        }
    }
}

@Composable
private fun ActiveMetricRowsV08(
    elapsedSeconds: Long,
    distanceMeters: Double,
    averageSpeedMps: Double?,
    maxSpeedMps: Double?
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
            ActiveMetricV08("行驶时长", formatActiveDuration(elapsedSeconds), Modifier.weight(1f))
            ActiveMetricV08("行驶里程", formatActiveDistance(distanceMeters), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
            ActiveMetricV08("平均速度", averageSpeedMps?.let(::formatActiveSpeed) ?: "--", Modifier.weight(1f))
            ActiveMetricV08("最高速度", maxSpeedMps?.let(::formatActiveSpeed) ?: "--", Modifier.weight(1f))
        }
    }
}

@Composable
private fun ActiveMetricV08(label: String, value: String, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun rememberLiveTripElapsedSeconds(trip: TripSessionEntity): Long {
    if (trip.status != TripStatus.RECORDING) return trip.elapsedSeconds

    var nowEpochMillis by remember(trip.id, trip.startedAtEpochMillis) {
        mutableStateOf(System.currentTimeMillis())
    }
    LaunchedEffect(trip.id, trip.status, trip.startedAtEpochMillis) {
        while (true) {
            nowEpochMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val wallClockElapsed = ((nowEpochMillis - trip.startedAtEpochMillis) / 1_000L).coerceAtLeast(0L)
    return maxOf(trip.elapsedSeconds, wallClockElapsed)
}

private fun formatActiveDistance(meters: Double): String =
    if (meters >= 1000.0) String.format(Locale.US, "%.2f km", meters / 1000.0)
    else if (meters >= 0.0) String.format(Locale.US, "%.0f m", meters)
    else "--"

private fun formatActiveSpeed(mps: Double): String =
    String.format(Locale.US, "%.0f km/h", mps * 3.6)

private fun formatActiveDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hours > 0 -> "%02d:%02d:%02d".format(hours, minutes, secs)
        else -> "%02d:%02d".format(minutes, secs)
    }
}
