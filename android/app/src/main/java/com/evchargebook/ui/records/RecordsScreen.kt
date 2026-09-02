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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.evchargebook.data.database.AppDatabase
import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.data.entity.ChargingSessionEntity
import com.evchargebook.data.repository.ChargingRepository
import com.evchargebook.ui.components.EmptyState
import com.evchargebook.ui.components.ResponsiveMetricGrid
import com.evchargebook.ui.theme.spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(
    records: List<ChargingRecordEntity>,
    activeSession: ChargingSessionEntity?,
    onDelete: (ChargingRecordEntity) -> Unit,
    onStartCharging: () -> Unit,
    onMaintainRecord: () -> Unit,
    onEdit: (ChargingRecordEntity) -> Unit,
    onEditActive: (ChargingSessionEntity) -> Unit,
    onCancelActive: (ChargingSessionEntity) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val database = remember(context) { AppDatabase.getInstance(context) }
    val chargingRepository = remember(database, context) { ChargingRepository(database, context) }
    val pendingSessions by chargingRepository.pendingChargingSessions.collectAsState(initial = emptyList())

    var pendingDelete by remember { mutableStateOf<ChargingRecordEntity?>(null) }
    var pendingCancel by remember { mutableStateOf<ChargingSessionEntity?>(null) }
    var pendingDiscard by remember { mutableStateOf<ChargingSessionEntity?>(null) }
    var completingSession by remember { mutableStateOf<ChargingSessionEntity?>(null) }
    var backfillingSession by remember { mutableStateOf<ChargingSessionEntity?>(null) }
    var editingPendingSession by remember { mutableStateOf<ChargingSessionEntity?>(null) }

    completingSession?.let { session ->
        CompleteChargingScreen(
            session = session,
            onBack = { completingSession = null },
            onComplete = chargingRepository::completeChargingSession,
            onDefer = chargingRepository::deferChargingCompletion,
            onCompleted = { completingSession = null },
        )
        return
    }

    backfillingSession?.let { session ->
        ChargingMeterBackfillScreen(
            session = session,
            onBack = { backfillingSession = null },
            onBackfill = chargingRepository::backfillChargingSession,
            onCompleted = { backfillingSession = null },
        )
        return
    }

    editingPendingSession?.let { session ->
        PendingChargingDetailsScreen(
            session = session,
            onBack = { editingPendingSession = null },
            onSave = chargingRepository::deferChargingCompletion,
            onSaved = { editingPendingSession = null },
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("充电记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
        ) {
            item {
                ChargingEntryActions(
                    hasActiveSession = activeSession != null,
                    onStartCharging = onStartCharging,
                    onMaintainRecord = onMaintainRecord,
                )
            }

            activeSession?.let { session ->
                item {
                    ActiveChargingCard(
                        session = session,
                        onComplete = { completingSession = session },
                        onEdit = { onEditActive(session) },
                        onCancel = { pendingCancel = session },
                    )
                }
            }

            if (pendingSessions.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("待补录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${pendingSessions.size} 笔",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(pendingSessions, key = { "pending-${it.id}" }) { session ->
                    PendingChargingCard(
                        session = session,
                        onEditDetails = { editingPendingSession = session },
                        onBackfill = { backfillingSession = session },
                        onDiscard = { pendingDiscard = session },
                    )
                }
            }

            if (records.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(if (pendingSessions.isEmpty()) 260.dp else 180.dp)) {
                        EmptyState(
                            "还没有已完成的充电记录",
                            when {
                                activeSession != null -> "当前充电仍在进行；结束后会进入账本或待补录。"
                                pendingSessions.isNotEmpty() -> "上方充电等待补充电表数据，补齐后才进入正式统计。"
                                else -> "可以开始记录充电，也可以补录已有账单。"
                            },
                            "补录历史充电",
                            onMaintainRecord,
                        )
                    }
                }
            } else {
                item { LedgerSummaryV06(records) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("最近记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${records.size} 笔",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(records, key = { it.id }) { record ->
                    RecordTimelineItemV06(
                        record = record,
                        onEdit = { onEdit(record) },
                        onDelete = { pendingDelete = record },
                    )
                }
            }
            item { Spacer(Modifier.height(MaterialTheme.spacing.lg)) }
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
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }

    pendingCancel?.let { session ->
        AlertDialog(
            onDismissRequest = { pendingCancel = null },
            title = { Text("取消这次充电？") },
            text = { Text("取消只结束本地进行中会话，不会生成一笔已完成充电记录。") },
            confirmButton = {
                TextButton(onClick = { onCancelActive(session); pendingCancel = null }) {
                    Text("取消本次充电", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingCancel = null }) { Text("继续记录") } },
        )
    }

    pendingDiscard?.let { session ->
        AlertDialog(
            onDismissRequest = { pendingDiscard = null },
            title = { Text("删除这次待补录充电？") },
            text = { Text("这次物理充电已经结束。删除后不会生成历史账本记录，也无法恢复这次待补录信息。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { chargingRepository.discardPendingChargingSession(session.id) }
                        pendingDiscard = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDiscard = null }) { Text("保留") } },
        )
    }
}

@Composable
private fun ChargingEntryActions(
    hasActiveSession: Boolean,
    onStartCharging: () -> Unit,
    onMaintainRecord: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
    ) {
        Button(
            onClick = onStartCharging,
            enabled = !hasActiveSession,
            modifier = Modifier.weight(1f),
        ) {
            Text(if (hasActiveSession) "充电进行中" else "开始充电")
        }
        OutlinedButton(onClick = onMaintainRecord, modifier = Modifier.weight(1f)) {
            Text("充电记录维护")
        }
    }
}

@Composable
private fun ActiveChargingCard(
    session: ChargingSessionEntity,
    onComplete: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
) {
    var now by remember(session.id) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session.id) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    val elapsed = (now - session.startedAtEpochMillis).coerceAtLeast(0L)
    val facts = listOfNotNull(
        session.chargerType?.takeIf { it.isNotBlank() },
        session.startSoc?.let { "开始 $it%" },
        session.targetSoc?.let { "目标 $it%" },
        session.unitPricePerKwh?.let { "¥${two(it)}/kWh" },
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                Spacer(Modifier.width(MaterialTheme.spacing.xs))
                Text("充电中", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(formatElapsed(elapsed), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                session.location?.takeIf { it.isNotBlank() } ?: "未记录充电地点",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "开始于 ${format(session.startedAtEpochMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (facts.isNotEmpty()) {
                Text(
                    facts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel) { Text("取消") }
                TextButton(onClick = onEdit) { Text("编辑") }
                Button(onClick = onComplete) { Text("结束充电") }
            }
        }
    }
}

@Composable
private fun PendingChargingCard(
    session: ChargingSessionEntity,
    onEditDetails: () -> Unit,
    onBackfill: () -> Unit,
    onDiscard: () -> Unit,
) {
    val facts = listOfNotNull(
        session.startSoc?.let { start -> session.endSoc?.let { end -> "SOC $start% → $end%" } },
        session.pendingMeterEnergyKwh?.let { "${one(it)} kWh" },
        session.pendingTotalCost?.let { "¥${two(it)}" },
        session.chargerType?.takeIf { it.isNotBlank() },
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.secondary, CircleShape))
                Spacer(Modifier.width(MaterialTheme.spacing.xs))
                Text("待补电表数据", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    session.endedAtEpochMillis?.let(::format) ?: format(session.startedAtEpochMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                session.location?.takeIf { it.isNotBlank() } ?: "未记录充电地点",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (facts.isNotEmpty()) {
                Text(
                    facts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "补齐前不计入累计费用、电量和充电次数。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDiscard) { Text("删除") }
                TextButton(onClick = onEditDetails) { Text("修改结束信息") }
                Button(onClick = onBackfill) { Text("补充电表数据") }
            }
        }
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
        "桩端均价" to "¥ ${two(averagePrice)}",
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                Spacer(Modifier.width(MaterialTheme.spacing.xs))
                Text("累计账本", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column {
                Text("累计支出", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("¥ ${two(totalCost)}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
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
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(vertical = MaterialTheme.spacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        ChargingRailV06()
        Spacer(Modifier.width(MaterialTheme.spacing.sm))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        record.location?.takeIf { it.isNotBlank() } ?: "未记录充电地点",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(format(record.chargeTimeEpochMillis), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(MaterialTheme.spacing.sm))
                Column(horizontalAlignment = Alignment.End) {
                    Text("¥ ${two(record.cost)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("+${one(record.energyKwh)} kWh", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("SOC ${record.startSoc}% → ${record.endSoc}% · ¥ ${two(record.pricePerKwh)}/kWh", style = MaterialTheme.typography.bodySmall)
                    val extras = listOfNotNull(
                        record.chargerType?.takeIf { it.isNotBlank() },
                        record.odometerKm?.let { "里程 ${formatKm(it)} km" },
                    )
                    if (extras.isNotEmpty()) {
                        Text(
                            extras.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "删除此记录",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
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
            color = MaterialTheme.colorScheme.primary.copy(alpha = .10f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Bolt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
            }
        }
        Box(Modifier.width(1.dp).height(66.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = .32f)))
    }
}

private fun format(value: Long) = DateTimeFormatter.ofPattern("M月d日 HH:mm")
    .withLocale(Locale.SIMPLIFIED_CHINESE)
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(value))

private fun formatElapsed(milliseconds: Long): String {
    val totalMinutes = milliseconds / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "${hours}小时${minutes}分" else "${minutes}分钟"
}

private fun formatKm(value: Double) =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)

private fun one(value: Double) = String.format(Locale.US, "%.1f", value)
private fun two(value: Double) = String.format(Locale.US, "%.2f", value)
