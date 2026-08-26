package com.evchargebook.ui.trip

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.evchargebook.data.entity.TripSessionEntity
import com.evchargebook.data.entity.TripStatus
import com.evchargebook.data.entity.VehicleEntity
import com.evchargebook.ui.theme.spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripScreen(
    vehicle: VehicleEntity?,
    vehicles: List<VehicleEntity>,
    trips: List<TripSessionEntity>,
    activeTrip: TripSessionEntity?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDelete: (TripSessionEntity) -> Unit
) {
    var deleteTarget by remember { mutableStateOf<TripSessionEntity?>(null) }
    val activeVehicle = activeTrip?.let { trip -> vehicles.firstOrNull { it.id == trip.vehicleId } }

    Scaffold(topBar = { TopAppBar(title = { Text("行程") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
            contentPadding = PaddingValues(vertical = MaterialTheme.spacing.md)
        ) {
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(MaterialTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                        Icon(Icons.Default.DirectionsCar, null)
                        if (activeTrip == null) {
                            Text("准备记录行程", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(vehicle?.let { "当前车辆：${it.brand} ${it.model}" } ?: "请先选择车辆", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("当前阶段仅建立手动行程生命周期；持续 GPS 轨迹将在下一步 foreground service 接入。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = onStart, enabled = vehicle != null, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.PlayArrow, null)
                                Spacer(Modifier.width(MaterialTheme.spacing.xs))
                                Text("开始行程")
                            }
                        } else {
                            Text("行程进行中", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("车辆：${activeVehicle?.let { "${it.brand} ${it.model}" } ?: "车辆 #${activeTrip.vehicleId}"}")
                            Text("开始：${formatTime(activeTrip.startedAtEpochMillis)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (vehicle?.id != activeTrip.vehicleId) {
                                Text("当前选中的车辆与进行中行程不同。请先结束当前行程再开始另一辆车。", color = MaterialTheme.colorScheme.tertiary)
                            }
                            Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Stop, null)
                                Spacer(Modifier.width(MaterialTheme.spacing.xs))
                                Text("结束行程")
                            }
                        }
                    }
                }
            }

            item { Text("历史行程", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            if (trips.isEmpty()) {
                item { Text("还没有已记录行程。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(trips, key = { it.id }) { trip ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(formatTime(trip.startedAtEpochMillis), fontWeight = FontWeight.SemiBold)
                                    Text(statusText(trip.status), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (trip.status == TripStatus.COMPLETED) {
                                    IconButton(onClick = { deleteTarget = trip }) { Icon(Icons.Default.Delete, "删除行程") }
                                }
                            }
                            if (trip.endedAtEpochMillis != null) Text("耗时：${formatDuration(trip.elapsedSeconds)}")
                            if (trip.distanceMeters > 0.0) Text("距离：${String.format(Locale.US, "%.1f", trip.distanceMeters / 1000.0)} km")
                            else Text("轨迹采样尚未接入，本条暂不计算距离", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { trip ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除这条行程？") },
            text = { Text("行程及后续关联的轨迹点会一并删除。") },
            confirmButton = { TextButton(onClick = { onDelete(trip); deleteTarget = null }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }
        )
    }
}

private fun formatTime(epochMillis: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.SIMPLIFIED_CHINESE).format(Date(epochMillis))
private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) "${hours}小时${minutes}分" else if (minutes > 0) "${minutes}分${secs}秒" else "${secs}秒"
}
private fun statusText(status: String) = when (status) {
    TripStatus.RECORDING -> "进行中"
    TripStatus.INTERRUPTED -> "已中断"
    TripStatus.COMPLETED -> "已完成"
    else -> status
}
