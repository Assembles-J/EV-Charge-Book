package com.evchargebook.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.ui.components.EmptyState
import com.evchargebook.ui.components.ResponsiveMetricGrid
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
                title = {
                    Text("充电记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            SmallFloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "记录充电")
            }
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
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
            ) {
                item { LedgerSummaryV06(records) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("最近记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${records.size} 笔",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(records, key = { it.id }) { record ->
                    RecordTimelineItemV06(
                        record = record,
                        onEdit = { onEdit(record) },
                        onDelete = { pendingDelete = record }
                    )
                }
                item { Spacer(Modifier.height(64.dp)) }
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
private fun LedgerSummaryV06(records: List<ChargingRecordEntity>) {
    val totalCost = records.sumOf { it.cost }
    val totalEnergy = records.sumOf { it.energyKwh }
    val averagePrice = if (totalEnergy > 0.0) totalCost / totalEnergy else 0.0
    val metrics = listOf(
        "电网补能" to "${one(totalEnergy)} kWh",
        "记录" to "${records.size} 笔",
        "桩端均价" to "¥ ${two(averagePrice)}"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                Spacer(Modifier.width(MaterialTheme.spacing.xs))
                Text(
                    "累计账本",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("累计支出", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "¥ ${two(totalCost)}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .24f))
            ResponsiveMetricGrid(metrics.size) { index, modifier ->
                val (label, value) = metrics[index]
                LedgerMetricV06(label, value, modifier)
            }
        }
    }
}

@Composable
private fun LedgerMetricV06(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RecordTimelineItemV06(
    record: ChargingRecordEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = MaterialTheme.spacing.xs),
        verticalAlignment = Alignment.Top
    ) {
        ChargingRailV06()
        Spacer(Modifier.width(MaterialTheme.spacing.sm))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        record.location?.takeIf { it.isNotBlank() } ?: "未记录充电地点",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        format(record.chargeTimeEpochMillis),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(MaterialTheme.spacing.sm))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "¥ ${two(record.cost)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "+${one(record.energyKwh)} kWh",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "SOC ${record.startSoc}% → ${record.endSoc}% · ¥ ${two(record.pricePerKwh)}/kWh",
                        style = MaterialTheme.typography.bodySmall
                    )
                    val extras = listOfNotNull(
                        record.chargerType?.takeIf { it.isNotBlank() },
                        record.odometerKm?.let { "里程 ${formatKm(it)} km" }
                    )
                    if (extras.isNotEmpty()) {
                        Text(
                            extras.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "删除此记录",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
        }
    }
}

@Composable
private fun ChargingRailV06() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = .10f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
        Box(
            Modifier
                .width(1.dp)
                .height(66.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = .32f))
        )
    }
}

private fun format(value: Long) = DateTimeFormatter.ofPattern("M月d日 HH:mm")
    .withLocale(Locale.SIMPLIFIED_CHINESE)
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(value))

private fun formatKm(value: Double) =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)

private fun one(value: Double) = String.format(Locale.US, "%.1f", value)
private fun two(value: Double) = String.format(Locale.US, "%.2f", value)
