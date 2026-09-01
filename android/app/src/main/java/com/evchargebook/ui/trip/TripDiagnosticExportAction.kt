package com.evchargebook.ui.trip

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.evchargebook.data.database.AppDatabase
import com.evchargebook.data.entity.TripPointEntity
import com.evchargebook.data.entity.TripSessionEntity
import com.evchargebook.data.export.TripDiagnosticExporter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Explicit per-Trip diagnostic export used for physical-device GPS investigations.
 *
 * This is intentionally scoped to a selected Trip instead of the global backup/export surface so
 * a user can attach one small, self-contained evidence file to a concrete continuity regression.
 */
@Composable
internal fun TripDiagnosticExportAction(
    trip: TripSessionEntity,
    points: List<TripPointEntity>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingContent by remember(trip.id) { mutableStateOf<String?>(null) }
    var preparing by remember(trip.id) { mutableStateOf(false) }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
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

    ExtendedFloatingActionButton(
        onClick = {
            if (preparing) return@ExtendedFloatingActionButton
            preparing = true
            scope.launch {
                runCatching {
                    val events = withContext(Dispatchers.IO) {
                        AppDatabase.getInstance(context.applicationContext)
                            .tripDao()
                            .getDiagnosticEvents(trip.id)
                    }
                    TripDiagnosticExporter.toCsv(trip, points, events)
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
        modifier = modifier.padding(20.dp),
        icon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
        text = { Text(if (preparing) "准备中" else "导出诊断") },
    )
}
