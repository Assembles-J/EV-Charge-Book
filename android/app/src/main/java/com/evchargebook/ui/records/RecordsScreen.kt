package com.evchargebook.ui.records

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.ChargingRecordEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(
    records: List<ChargingRecordEntity>,
    onDelete: (ChargingRecordEntity) -> Unit
) {
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("充电记录") }) }
    ) { padding ->
        if (records.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("暂无充电记录。回到总览点击 + 添加第一条记录。")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(records, key = { it.id }) { record ->
                    RecordItem(record = record, onDelete = { onDelete(record) })
                }
            }
        }
    }
}

@Composable
private fun RecordItem(record: ChargingRecordEntity, onDelete: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(record.location ?: "未填写地点", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(formatTime(record.chargeTimeEpochMillis), style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text("SOC ${record.startSoc}% → ${record.endSoc}%")
                Text("${oneDecimal(record.energyKwh)} kWh · ¥ ${twoDecimals(record.pricePerKwh)}/kWh")
            }
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                Text("¥ ${twoDecimals(record.cost)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除记录")
                }
            }
        }
    }
}

private fun formatTime(epochMillis: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withLocale(Locale.SIMPLIFIED_CHINESE)
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMillis))

private fun oneDecimal(value: Double) = String.format(Locale.US, "%.1f", value)
private fun twoDecimals(value: Double) = String.format(Locale.US, "%.2f", value)
