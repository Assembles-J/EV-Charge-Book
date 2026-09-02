package com.evchargebook.ui.trip

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evchargebook.BuildConfig
import com.evchargebook.data.database.AppDatabase
import com.evchargebook.data.entity.TripPointEntity
import com.evchargebook.data.entity.TripSessionEntity
import com.evchargebook.data.export.TripDiagnosticExporter
import com.evchargebook.domain.trip.TripElevationAnalytics
import com.evchargebook.ui.theme.EVDesignTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CompletedTripAltitudeCardV07(
    trip: TripSessionEntity,
    points: List<TripPointEntity>,
) {
    val summary = remember(points) { TripElevationAnalytics.summarize(points) }
    val accent = EVDesignTokens.Energy.green
    val metrics = listOf(
        AltitudeMetricV07(Icons.Default.LocationOn, "起点", formatAltitudeV07(summary?.startAltitudeMeters ?: trip.startAltitudeMeters)),
        AltitudeMetricV07(Icons.Default.Flag, "终点", formatAltitudeV07(summary?.endAltitudeMeters ?: trip.endAltitudeMeters)),
        AltitudeMetricV07(Icons.Default.ArrowDownward, "最低", formatAltitudeV07(summary?.minAltitudeMeters ?: trip.minAltitudeMeters)),
        AltitudeMetricV07(Icons.Default.ArrowUpward, "最高", formatAltitudeV07(summary?.maxAltitudeMeters ?: trip.maxAltitudeMeters)),
        AltitudeMetricV07(Icons.Default.TrendingUp, "累计爬升", if (summary?.hasCumulativeEstimate == true) formatAltitudeV07(summary.elevationGainMeters) else "--"),
        AltitudeMetricV07(Icons.Default.TrendingDown, "累计下降", if (summary?.hasCumulativeEstimate == true) formatAltitudeV07(summary.elevationLossMeters) else "--"),
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Default.Terrain,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp),
                )
                Text("海拔", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }

            metrics.chunked(2).forEach { rowMetrics ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    rowMetrics.forEach { metric ->
                        AltitudeMetricCellV07(
                            metric = metric,
                            modifier = Modifier.weight(1f),
                            accent = accent,
                        )
                    }
                }
            }

            if ((summary?.skippedLongGapCount ?: 0) > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp),
                    )
                    Text(
                        "${summary?.skippedLongGapCount} 个 GPS 长缺口已从累计海拔变化中断开。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private data class AltitudeMetricV07(
    val icon: ImageVector,
    val label: String,
    val value: String,
)

@Composable
private fun AltitudeMetricCellV07(
    metric: AltitudeMetricV07,
    modifier: Modifier,
    accent: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            metric.icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(21.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                metric.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(metric.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun CompletedTripDiagnosticsV07(
    trip: TripSessionEntity,
    points: List<TripPointEntity>,
    gapCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val accent = EVDesignTokens.Energy.green
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.MonitorHeart,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("GPS 诊断", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${points.size} 点${if (gapCount > 0) " · $gapCount 个长缺口" else " · 未检测到长缺口"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onToggle) {
                    Text(if (expanded) "收起" else "查看轨迹点", color = accent)
                }
            }

            if (expanded) {
                if (points.isEmpty()) {
                    Text(
                        "本次没有保存有效 GPS 轨迹点。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    points.takeLast(6).reversed().forEachIndexed { index, point ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .16f))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    SimpleDateFormat("HH:mm:ss", Locale.SIMPLIFIED_CHINESE).format(Date(point.capturedAtEpochMillis)),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "${formatCoordinateV07(point.latitude)}, ${formatCoordinateV07(point.longitude)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            point.horizontalAccuracyMeters?.takeIf { it.isFinite() }?.let {
                                Text(
                                    "±${it.toInt()} m",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            TripPlaybackInlineActionV07(points = points)
            TripDiagnosticExportInlineActionV07(trip = trip, points = points)
        }
    }
}

@Composable
private fun TripPlaybackInlineActionV07(points: List<TripPointEntity>) {
    var showPlayback by remember(points.firstOrNull()?.tripId) { mutableStateOf(false) }

    TripDataActionRowV07(
        icon = Icons.Default.PlayCircle,
        title = "轨迹回放",
        subtitle = "全屏按时间回看，拖动/缩放不会触发页面收缩",
        enabled = points.size >= 2,
        onClick = { showPlayback = true },
    )

    if (showPlayback) {
        TripPlaybackFullScreenV07(
            points = points,
            onDismiss = { showPlayback = false },
        )
    }
}

@Composable
private fun TripDiagnosticExportInlineActionV07(
    trip: TripSessionEntity,
    points: List<TripPointEntity>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingContent by remember(trip.id) { mutableStateOf<String?>(null) }
    var preparing by remember(trip.id) { mutableStateOf(false) }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val content = pendingContent
        if (uri != null && content != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                            writer.write(content)
                        } ?: error("无法打开导出文件")
                    }
                }.onSuccess {
                    Toast.makeText(context, "GPS 诊断日志已导出", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, it.message ?: "GPS 诊断日志导出失败", Toast.LENGTH_LONG).show()
                }
                pendingContent = null
            }
        } else {
            pendingContent = null
        }
    }

    TripDataActionRowV07(
        icon = Icons.Default.FileDownload,
        title = if (preparing) "准备中" else "导出诊断",
        subtitle = "导出 GPS 诊断日志 CSV",
        enabled = !preparing,
        onClick = {
            if (preparing) return@TripDataActionRowV07
            preparing = true
            scope.launch {
                runCatching {
                    val events = withContext(Dispatchers.IO) {
                        AppDatabase.getInstance(context.applicationContext)
                            .tripDao()
                            .getDiagnosticEvents(trip.id)
                    }
                    TripDiagnosticExporter.toCsv(
                        trip = trip,
                        points = points,
                        events = events,
                        environment = mapOf(
                            "appVersion" to BuildConfig.VERSION_NAME,
                            "sdkInt" to Build.VERSION.SDK_INT.toString(),
                            "manufacturer" to Build.MANUFACTURER,
                            "brand" to Build.BRAND,
                            "model" to Build.MODEL,
                            "device" to Build.DEVICE,
                        ),
                    )
                }.onSuccess { content ->
                    pendingContent = content
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                    createDocument.launch("ev-charge-book-trip-${trip.id}-gps-$stamp.csv")
                }.onFailure {
                    Toast.makeText(context, it.message ?: "无法准备 GPS 诊断日志", Toast.LENGTH_LONG).show()
                }
                preparing = false
            }
        },
    )
}

@Composable
private fun TripDataActionRowV07(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val accent = EVDesignTokens.Energy.green
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .38f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = accent.copy(alpha = .14f),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.padding(10.dp).size(21.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun formatAltitudeV07(value: Double?): String =
    value?.takeIf { it.isFinite() }?.let { String.format(Locale.US, "%.0f m", it) } ?: "--"

private fun formatCoordinateV07(value: Double): String = String.format(Locale.US, "%.6f", value)
