package com.evchargebook.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChargingStation
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class ChargingRecord(
    val date: String,
    val location: String,
    val energy: String,
    val cost: String,
    val type: String // 快充 / 慢充
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen() {
    val dummyRecords = listOf(
        ChargingRecord("2023-10-24 22:15", "小桔充电-科技园站", "45.2 kWh", "¥ 67.8", "快充"),
        ChargingRecord("2023-10-20 18:30", "家充桩", "22.5 kWh", "¥ 11.2", "慢充"),
        ChargingRecord("2023-10-15 09:00", "特来电-万达广场", "38.0 kWh", "¥ 52.1", "快充")
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("充电记录历史") },
                actions = {
                    IconButton(onClick = { /* TODO: Filter */ }) {
                        Icon(Icons.Default.FilterList, contentDescription = "筛选")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(dummyRecords) { record ->
                RecordItem(record)
            }
        }
    }
}

@Composable
fun RecordItem(record: ChargingRecord) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标指示器
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = if (record.type == "快充") MaterialTheme.colorScheme.errorContainer 
                                else MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ChargingStation,
                    contentDescription = null,
                    tint = if (record.type == "快充") MaterialTheme.colorScheme.error 
                           else MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 详情
            Column(modifier = Modifier.weight(1f)) {
                Text(record.location, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(record.date, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    SuggestionChip(
                        onClick = { },
                        label = { Text(record.type) },
                        modifier = Modifier.height(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(record.energy, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // 金额
            Text(
                record.cost,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Black
            )
        }
    }
}
