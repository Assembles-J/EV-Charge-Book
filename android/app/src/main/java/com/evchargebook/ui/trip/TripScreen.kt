package com.evchargebook.ui.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.TripPointEntity
import com.evchargebook.data.entity.TripSessionEntity
import com.evchargebook.data.entity.TripStatus
import com.evchargebook.data.entity.VehicleEntity
import com.evchargebook.ui.theme.EVDesignTokens
import com.evchargebook.ui.theme.spacing
import java.util.Locale

/**
 * Trip router + active v0.6 cockpit.
 *
 * The no-active state is owned by TripReadyScreen. Selected/completed detail is intentionally
 * isolated in TripDetailScreen.kt so #149-#151 can evolve it without destabilizing live tracking.
 */
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
        TripDetailScreenV05(selectedTrip, vehicles, selectedTripPoints, onCloseDetail)
        return
    }

    if (activeTrip == null) {
        // MainActivity routes this state to TripReadyScreen. Keep a truthful fallback for state races.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无进行中的行程", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    var confirmStop by remember(activeTrip.id) { mutableStateOf(false) }
    val activeVehicle = vehicles.firstOrNull { it.id == activeTrip.vehicleId } ?: vehicle
    val interrupted = activeTrip.status == TripStatus.INTERRUPTED
    val telemetry = remember(selectedTripPoints) { summarizeActiveTripTelemetry(selectedTripPoints) }
    val accent = EVDesignTokens.Energy.green

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("行程进行中", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .size(7.dp)
                                .background(if (interrupted) MaterialTheme.colorScheme.error else accent, CircleShape)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    activeVehicle?.let { "${it.brand} ${it.model}" } ?: "车辆 #${activeTrip.vehicleId}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    if (interrupted) "定位已中断，等待用户恢复" else "真实 GPS 持续记录",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            ActiveStatusPill(
                                text = if (interrupted) "需恢复" else "记录中",
                                warning = interrupted
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            ActivePrimaryMetric(
                                label = "行程距离",
                                value = if (activeTrip.distanceMeters > 0.0) formatActiveDistance(activeTrip.distanceMeters) else "--",
                                modifier = Modifier.weight(1f)
                            )
                            ActivePrimaryMetric(
                                label = "当前速度",
                                value = telemetry.trustedLatestSpeedMps?.let(::formatActiveSpeed) ?: "--",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                        ActiveMetricGrid(
                            listOf(
                                ActiveMetric("已记录", formatActiveDuration(activeTrip.elapsedSeconds)),
                                ActiveMetric("行驶均速", activeTrip.averageSpeedMps?.let(::formatActiveSpeed) ?: "--"),
                                ActiveMetric("最高速度", activeTrip.maxSpeedMps?.let(::formatActiveSpeed) ?: "--"),
                                ActiveMetric("起始 SOC", activeTrip.startSoc?.let { "$it%" } ?: "--"),
                                ActiveMetric("GPS 点", selectedTripPoints.size.toString()),
                                ActiveMetric("海拔样本", telemetry.altitudePointCount.toString())
                            )
                        )
                    }
                }
            }

            item {
                TripActiveTelemetryV06(points = selectedTripPoints)
            }

            if (interrupted) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
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
                OutlinedButton(
                    onClick = { onOpenDetail(activeTrip.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Route, contentDescription = null)
                    Spacer(Modifier.width(MaterialTheme.spacing.xs))
                    Text("查看完整轨迹与诊断")
                }
            }

            item {
                TripSlideAction(
                    label = if (interrupted) "滑动结束已中断行程" else "滑动结束行程",
                    enabled = true,
                    onConfirmed = { confirmStop = true },
                    icon = Icons.Default.Stop,
                    releaseLabel = "松开结束"
                )
                Text(
                    "结束前仍会保留一次确认，避免驾驶中误操作。",
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item { Spacer(Modifier.size(MaterialTheme.spacing.md)) }
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("结束当前行程？") },
            text = { Text("结束后会停止持续定位并保存当前已经记录的真实行程数据。") },
            confirmButton = {
                TextButton(onClick = { confirmStop = false; onStop() }) {
                    Text("结束行程", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmStop = false }) { Text("继续记录") }
            }
        )
    }
}

private data class ActiveMetric(val label: String, val value: String)

@Composable
private fun ActivePrimaryMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ActiveMetricGrid(metrics: List<ActiveMetric>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 360.dp || LocalConfiguration.current.fontScale >= 1.3f
        val columns = if (compact) 2 else 3
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            metrics.chunked(columns).forEach { rowMetrics ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
                    rowMetrics.forEach { metric ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(metric.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(metric.value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    repeat(columns - rowMetrics.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ActiveStatusPill(text: String, warning: Boolean) {
    val accent = EVDesignTokens.Energy.green
    val color = if (warning) MaterialTheme.colorScheme.error else accent
    Surface(shape = CircleShape, color = color.copy(alpha = 0.10f)) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (warning) Icons.Default.WarningAmber else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Text(text, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

private fun formatActiveDistance(meters: Double): String =
    if (meters >= 1000.0) String.format(Locale.US, "%.2f km", meters / 1000.0)
    else String.format(Locale.US, "%.0f m", meters)

private fun formatActiveSpeed(mps: Double): String =
    String.format(Locale.US, "%.0f km/h", mps * 3.6)

private fun formatActiveDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${secs}s"
        else -> "${secs}s"
    }
}
