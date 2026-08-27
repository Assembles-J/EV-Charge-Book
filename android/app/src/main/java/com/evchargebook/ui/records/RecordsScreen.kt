package com.evchargebook.ui.records

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.ui.components.EmptyState
import com.evchargebook.ui.theme.spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
                title = { Text("充电记录", style = MaterialTheme.typography.headlineSmall) },
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
                contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.xs),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
            ) {
                item { LedgerCockpit(records) }
                item {
                    Text(
                        "最近记录",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                items(records, key = { it.id }) { record ->
                    RecordItem(record, onEdit = { onEdit(record) }) { pendingDelete = record }
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

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            Modifier.padding(MaterialTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(8.dp),
                    color = MaterialTheme.colorScheme.inversePrimary,
                    shape = MaterialTheme.shapes.extraSmall
                ) {}
                Spacer(Modifier.width(MaterialTheme.spacing.xs))
                Text(
                    "CHARGE / LEDGER",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = .62f)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs)) {
                Text(
                    "累计支出",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = .66f)
                )
                Text(
                    "¥ ${two(totalCost)}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.inverseOnSurface
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = .12f))

            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
                CockpitValue("补能", "${one(totalEnergy)} kWh", Modifier.weight(1f))
                CockpitValue("记录", "${records.size} 笔", Modifier.weight(1f))
                CockpitValue("均价", "¥ ${two(averagePrice)}", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CockpitValue(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = .52f)
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.inverseOnSurface
        )
    }
}

@Composable
private fun RecordItem(record: ChargingRecordEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(record.location ?: "未命名充电地点", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(MaterialTheme.spacing.xxs))
                    Text(
                        format(record.chargeTimeEpochMillis),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("¥ ${two(record.cost)}", style = MaterialTheme.typography.titleLarge)
                    Text("${one(record.energyKwh)} kWh", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(MaterialTheme.spacing.sm))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(MaterialTheme.spacing.sm))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "SOC ${record.startSoc}% → ${record.endSoc}%",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "¥ ${two(record.pricePerKwh)}/kWh",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "删除此记录") }
            }

            val extras = listOfNotNull(
                record.chargerType,
                record.odometerKm?.let { "里程 ${formatKm(it)} km" }
            )
            if (extras.isNotEmpty()) {
                Text(
                    extras.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun format(value: Long) = DateTimeFormatter.ofPattern("M月d日 HH:mm")
    .withLocale(Locale.SIMPLIFIED_CHINESE)
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(value))

private fun formatKm(value: Double) = if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)
private fun one(value: Double) = String.format(Locale.US, "%.1f", value)
private fun two(value: Double) = String.format(Locale.US, "%.2f", value)
