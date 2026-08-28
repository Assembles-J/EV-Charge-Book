package com.evchargebook.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.ui.components.EmptyState
import com.evchargebook.ui.components.ResponsiveMetricGrid
import com.evchargebook.ui.theme.spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val LedgerHeroBrush = Brush.linearGradient(
    listOf(Color(0xFF06100B), Color(0xFF0B2117), Color(0xFF07120D))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(
    records: List<ChargingRecordEntity>,
    onDelete: (ChargingRecordEntity) -> Unit,
    onAdd: () -> Unit,
    onEdit: (ChargingRecordEntity) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<ChargingRecordEntity?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("充电记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("CHARGE JOURNAL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("记录充电") }
            )
        }
    ) { padding ->
        if (records.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                EmptyState("还没有充电记录", "从第一笔充电开始建立你的能耗账本。", "记录第一次充电", onAdd)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg)
            ) {
                item { LedgerCockpit(records) }
                item {
                    Column {
                        Text("最近记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("RECENT CHARGING", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(records, key = { it.id }) { record ->
                    RecordTimelineItem(record, onEdit = { onEdit(record) }) { pendingDelete = record }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    pendingDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这笔记录？") },
            text = { Text("删除后无法恢复，统计数据也会同步更新。") },
            confirmButton = {
                TextButton(onClick = { onDelete(record); pendingDelete = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun LedgerCockpit(records: List<ChargingRecordEntity>) {
    val totalCost = records.sumOf { it.cost }
    val totalEnergy = records.sumOf { it.energyKwh }
    val averagePrice = if (totalEnergy > 0.0) totalCost / totalEnergy else 0.0
    val metrics = listOf(
        "补能" to "${one(totalEnergy)} kWh",
        "记录" to "${records.size} 笔",
        "均价" to "¥ ${two(averagePrice)}"
    )

    Surface(modifier = Modifier.fillMaxWidth(), color = Color.Transparent, shape = MaterialTheme.shapes.extraLarge) {
        Column(
            Modifier.background(LedgerHeroBrush).padding(MaterialTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                Spacer(Modifier.width(MaterialTheme.spacing.xs))
                Text("CHARGE / LEDGER", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs)) {
                Text("累计支出", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("¥ ${two(totalCost)}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .28f))
            ResponsiveMetricGrid(metrics.size) { index, modifier ->
                val (label, value) = metrics[index]
                CockpitValue(label, value, modifier)
            }
        }
    }
}

@Composable
private fun CockpitValue(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun RecordTimelineItem(record: ChargingRecordEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(vertical = MaterialTheme.spacing.sm),
        verticalAlignment = Alignment.Top
    ) {
        ChargingRail()
        Spacer(Modifier.width(MaterialTheme.spacing.md))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(record.location ?: "未命名充电地点", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(format(record.chargeTimeEpochMillis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("¥ ${two(record.cost)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("+ ${one(record.energyKwh)} kWh", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("SOC ${record.startSoc}% → ${record.endSoc}%", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("¥ ${two(record.pricePerKwh)}/kWh", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.DeleteOutline, "删除此记录", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
            val extras = listOfNotNull(record.chargerType, record.odometerKm?.let { "里程 ${formatKm(it)} km" })
            if (extras.isNotEmpty()) {
                Text(extras.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ChargingRail() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(Modifier.size(36.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Bolt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
        Box(Modifier.width(1.dp).height(68.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = .42f)))
    }
}

private fun format(value: Long) = DateTimeFormatter.ofPattern("M月d日 HH:mm")
    .withLocale(Locale.SIMPLIFIED_CHINESE)
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(value))

private fun formatKm(value: Double) = if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)
private fun one(value: Double) = String.format(Locale.US, "%.1f", value)
private fun two(value: Double) = String.format(Locale.US, "%.2f", value)
