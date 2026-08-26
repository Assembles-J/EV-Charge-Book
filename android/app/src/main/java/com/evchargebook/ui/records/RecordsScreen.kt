package com.evchargebook.ui.records

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.ui.components.EmptyState
import com.evchargebook.ui.theme.spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(records: List<ChargingRecordEntity>, onDelete: (ChargingRecordEntity) -> Unit, onAdd: () -> Unit, onEdit: (ChargingRecordEntity) -> Unit) {
    var pendingDelete by remember { mutableStateOf<ChargingRecordEntity?>(null) }
    Scaffold(
        topBar = { TopAppBar(title = { Column { Text("充电记录"); Text("每一度电，都有迹可循", style = MaterialTheme.typography.labelMedium) } }) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Default.Add, null) }, text = { Text("记录充电") }) }
    ) { padding ->
        if (records.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) { EmptyState("还没有充电记录", "从第一笔充电开始，建立你的能耗账本。", "记录第一次充电", onAdd) }
        } else LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
        ) {
            item { Text("共 ${records.size} 笔 · 累计 ¥ ${two(records.sumOf { it.cost })}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(records, key = { it.id }) { record -> RecordItem(record, onEdit = { onEdit(record) }) { pendingDelete = record } }
        }
    }
    pendingDelete?.let { record ->
        AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text("删除这笔记录？") }, text = { Text("删除后将无法恢复，统计数据会同步更新。") },
            confirmButton = { TextButton(onClick = { onDelete(record); pendingDelete = null }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } })
    }
}

@Composable private fun RecordItem(record: ChargingRecordEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    ElevatedCard(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) { Icon(Icons.Default.ElectricBolt, null, Modifier.padding(MaterialTheme.spacing.sm), MaterialTheme.colorScheme.onPrimaryContainer) }
            Spacer(Modifier.width(MaterialTheme.spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(record.location ?: "未命名充电地点", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                record.chargerType?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                Text(format(record.chargeTimeEpochMillis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(MaterialTheme.spacing.xs))
                Text("SOC ${record.startSoc}% → ${record.endSoc}%  ·  ${one(record.energyKwh)} kWh", style = MaterialTheme.typography.bodyMedium)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("¥ ${two(record.cost)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("¥ ${two(record.pricePerKwh)}/kWh", style = MaterialTheme.typography.labelSmall)
                IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "删除此记录") }
            }
        }
    }
}
private fun format(value: Long) = DateTimeFormatter.ofPattern("M月d日 HH:mm").withLocale(Locale.SIMPLIFIED_CHINESE).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(value))
private fun one(value: Double) = String.format(Locale.US, "%.1f", value)
private fun two(value: Double) = String.format(Locale.US, "%.2f", value)
