package com.evchargebook.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.evchargebook.domain.ChargerCategory
import com.evchargebook.domain.ChargingEstimateConfidence
import com.evchargebook.domain.ChargingIntervalSample
import com.evchargebook.domain.ChargingTripCoverageInterval
import com.evchargebook.domain.MonthlyChargingComparison
import com.evchargebook.domain.trip.TripEnergyAnalytics
import com.evchargebook.domain.trip.TripEnergySummary
import com.evchargebook.ui.components.ResponsiveMetricGrid
import com.evchargebook.ui.theme.spacing
import com.evchargebook.ui.theme.warningColor
import com.evchargebook.viewmodel.MainUiState
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(state: MainUiState) {
    val zoneId = ZoneId.systemDefault()
    val month = YearMonth.from(Instant.now().atZone(zoneId))
    val monthStart = month.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    val nextMonthStart = month.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    val monthTripEnergy = TripEnergyAnalytics.summarize(state.trips, monthStart, nextMonthStart)
    val lifetimeTripEnergy = TripEnergyAnalytics.summarize(state.trips)
    val monthComparison = MonthlyChargingComparison.compare(state.monthlyTrend)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text("统计", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
        ) {
            item { MonthSummaryV06(state) }
            monthComparison?.let { comparison -> item { MonthComparisonCardV06(comparison) } }
            item { TripEnergyEstimateCardV06(monthTripEnergy, lifetimeTripEnergy) }

            if (state.monthlyTrend.isNotEmpty()) {
                item { AnalyticsSectionV06("最近 6 个月充电") { MonthlyTrendContentV06(state) } }
            }
            if (state.chargingRecords.isNotEmpty()) {
                item { AnalyticsSectionV06("充电方式") { ChargerTypeContentV06(state) } }
            }
            if (state.chargingPlaceSummary.isNotEmpty()) {
                item { AnalyticsSectionV06("常用地点") { CommonPlacesContentV06(state) } }
            }

            item { LifetimeSummaryV06(state) }
            item { IntervalAnalyticsCardV06(state) }
            if (state.intervalSamples.isNotEmpty()) item { IntervalDetailSectionV06(state) }
            item { DataScopeNoteV06() }
            item { DataAccumulationNoteV06() }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun MonthSummaryV06(state: MainUiState) {
    val metrics = listOf(
        "电网补能" to "${one(state.monthEnergy)} kWh",
        "充电次数" to "${state.chargingCount} 次",
        "桩端均价" to "¥ ${two(state.averagePrice)}"
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
                Text("本月", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("充电支出", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("¥ ${two(state.monthCost)}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .24f))
            ResponsiveMetricGrid(metrics.size) { index, modifier ->
                val (label, value) = metrics[index]
                StatValueV06(label, value, modifier)
            }
        }
    }
}

private data class ComparisonMetricV06(
    val label: String,
    val value: String,
    val changeRate: Double?
)

@Composable
private fun MonthComparisonCardV06(comparison: com.evchargebook.domain.MonthlyComparison) {
    val metrics = listOf(
        ComparisonMetricV06("费用", "¥ ${two(comparison.current.cost)}", comparison.costChangeRate),
        ComparisonMetricV06("电网补能", "${one(comparison.current.energyKwh)} kWh", comparison.energyChangeRate),
        ComparisonMetricV06("充电次数", "${comparison.current.chargingCount} 次", comparison.countChangeRate)
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
        ) {
            SectionHeadingV06(
                title = "本月 vs 上月",
                subtitle = "${comparison.previous.year}年${comparison.previous.month}月 → ${comparison.current.year}年${comparison.current.month}月"
            )
            ResponsiveMetricGrid(metrics.size) { index, modifier ->
                val metric = metrics[index]
                Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(metric.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(metric.value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        metric.changeRate?.let(::signedPercent) ?: "--",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (metric.changeRate == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (metrics.any { it.changeRate == null }) {
                Text(
                    "上月为 0 的指标不显示无意义的增长率。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TripEnergyEstimateCardV06(month: TripEnergySummary, lifetime: TripEnergySummary) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
        ) {
            Text("行驶能耗估算", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "依据已完成 Trip 的开始/结束 SOC 与配置电池容量推导，不是 BMS 实测。平均值按总估算能量 ÷ 总有效距离加权计算。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (month.eligibleTripCount == 0) {
                Text(
                    if (month.completedTripCount == 0) "本月暂无已完成 Trip。" else "本月 Trip 暂无足够的 SOC 下降数据用于能耗估算。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ResponsiveMetricGrid(3) { index, modifier ->
                    when (index) {
                        0 -> StatValueV06("本月估算消耗", "${one(month.estimatedEnergyKwh)} kWh", modifier)
                        1 -> StatValueV06("有效 Trip 距离", "${one(month.distanceKm)} km", modifier)
                        else -> StatValueV06(
                            "加权平均能耗",
                            month.weightedAverageKwhPer100Km?.let { "${one(it)} kWh/100km" } ?: "--",
                            modifier
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
            Text(
                "累计可估算 ${lifetime.eligibleTripCount} 段 · ${one(lifetime.distanceKm)} km · 估算 ${one(lifetime.estimatedEnergyKwh)} kWh",
                style = MaterialTheme.typography.bodyMedium
            )
            if (lifetime.excludedTripCount > 0) {
                Text(
                    "另有 ${lifetime.excludedTripCount} 段已完成行程因 SOC 未下降、距离不足或数据缺失未纳入能耗平均。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AnalyticsSectionV06(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
        Text(
            title,
            modifier = Modifier.padding(start = 2.dp, top = MaterialTheme.spacing.xs),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
                content = content
            )
        }
    }
}

@Composable
private fun MonthlyTrendContentV06(state: MainUiState) {
    val maxEnergy = state.monthlyTrend.maxOfOrNull { it.energyKwh }?.takeIf { it > 0.0 } ?: 1.0
    state.monthlyTrend.forEachIndexed { index, bucket ->
        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .42f))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("${bucket.year}年${bucket.month}月", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${bucket.chargingCount} 次 · 电网补能 ${one(bucket.energyKwh)} kWh",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("¥ ${two(bucket.cost)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        bucket.averagePricePerKwh?.let { "¥ ${two(it)}/kWh" } ?: "--",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            EvidenceBarV06(bucket.energyKwh / maxEnergy)
        }
    }
}

@Composable
private fun ChargerTypeContentV06(state: MainUiState) {
    state.chargerTypeSummary.forEachIndexed { index, item ->
        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .42f))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(chargerCategoryText(item.category), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${item.chargingCount} 次 · ${percent(item.countShare)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${one(item.energyKwh)} kWh", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "电量 ${percent(item.energyShare)} · 费用 ${percent(item.costShare)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("¥ ${two(item.cost)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            EvidenceBarV06(item.energyShare)
        }
    }
}

@Composable
private fun CommonPlacesContentV06(state: MainUiState) {
    state.chargingPlaceSummary.take(5).forEachIndexed { index, place ->
        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .42f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1.4f)) {
                Text(
                    place.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${place.chargingCount} 次 · ${shortDate(place.latestChargeTimeEpochMillis)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(MaterialTheme.spacing.sm))
            Column(horizontalAlignment = Alignment.End) {
                Text("${one(place.energyKwh)} kWh", style = MaterialTheme.typography.titleSmall)
                Text("¥ ${two(place.cost)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    place.averagePricePerKwh?.let { "¥ ${two(it)}/kWh" } ?: "--",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EvidenceBarV06(progress: Double) {
    val fraction = progress.takeIf { it.isFinite() }?.toFloat()?.coerceIn(0f, 1f) ?: 0f
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = .28f), CircleShape)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(3.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = .72f), CircleShape)
        )
    }
}

@Composable
private fun LifetimeSummaryV06(state: MainUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
        ) {
            Text("充电账本累计", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            ResponsiveMetricGrid(3) { index, modifier ->
                when (index) {
                    0 -> StatValueV06("累计费用", "¥ ${two(state.totalCost)}", modifier)
                    1 -> StatValueV06("累计电网补能", "${one(state.totalEnergy)} kWh", modifier)
                    else -> StatValueV06("平均桩端单价", "¥ ${two(state.averagePrice)} / kWh", modifier)
                }
            }
        }
    }
}

@Composable
private fun IntervalAnalyticsCardV06(state: MainUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
        ) {
            Text("里程区间估算", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "这里衡量两次充电记录之间每 100 km 对应的补入电量/费用，不等同于车辆行驶能耗。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.intervalSampleCount == 0 || state.intervalEnergyPer100Km == null || state.intervalCostPer100Km == null) {
                Text("至少需要两条有效且递增的里程记录。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                ResponsiveMetricGrid(2) { index, modifier ->
                    if (index == 0) {
                        StatValueV06("每100km补入电量", "${one(state.intervalEnergyPer100Km)} kWh", modifier)
                    } else {
                        StatValueV06("每100km费用", "¥ ${two(state.intervalCostPer100Km)}", modifier)
                    }
                }
                Text(
                    "${state.intervalSampleCount} 个有效区间 · ${one(state.intervalDistanceKm)} km 样本",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.invalidIntervalCount > 0) {
                    Text(
                        "已排除 ${state.invalidIntervalCount} 个异常区间。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.warningColor
                    )
                }
            }
            if (state.tripCoverageIntervalCount > 0 && state.tripCoverageRatio != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                ResponsiveMetricGrid(2) { index, modifier ->
                    if (index == 0) {
                        StatValueV06("轨迹覆盖", percent(state.tripCoverageRatio), modifier)
                    } else {
                        StatValueV06("Trip 距离", "${one(state.tripCoverageDistanceKm)} km", modifier)
                    }
                }
                Text(
                    "${state.tripCoverageIntervalCount} 个区间有完整 Trip 证据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun IntervalDetailSectionV06(state: MainUiState) {
    val recordsById = state.chargingRecords.associateBy { it.id }
    val coverageByCurrentRecord = state.tripCoverageIntervals.associateBy { it.currentRecordId }
    val recent = state.intervalSamples
        .sortedByDescending { recordsById[it.currentRecordId]?.chargeTimeEpochMillis ?: Long.MIN_VALUE }
        .take(8)

    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
        SectionHeadingV06("最近区间明细", "${state.intervalSamples.size} 个有效区间")
        recent.forEach { sample ->
            IntervalDetailCardV06(
                sample = sample,
                coverage = coverageByCurrentRecord[sample.currentRecordId],
                previousTime = recordsById[sample.previousRecordId]?.chargeTimeEpochMillis,
                currentTime = recordsById[sample.currentRecordId]?.chargeTimeEpochMillis
            )
        }
    }
}

@Composable
private fun IntervalDetailCardV06(
    sample: ChargingIntervalSample,
    coverage: ChargingTripCoverageInterval?,
    previousTime: Long?,
    currentTime: Long?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)
        ) {
            Text(
                if (previousTime != null && currentTime != null) {
                    "${shortDate(previousTime)} → ${shortDate(currentTime)}"
                } else {
                    "充电区间 #${sample.previousRecordId} → #${sample.currentRecordId}"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "${one(sample.distanceKm)} km · 桩端补入 ${one(sample.replenishedEnergyKwh)} kWh · ¥ ${two(sample.replenishmentCost)}",
                style = MaterialTheme.typography.bodySmall
            )
            ResponsiveMetricGrid(2) { index, modifier ->
                if (index == 0) {
                    StatValueV06("每100km补入电量", "${one(sample.energyPer100Km)} kWh", modifier)
                } else {
                    StatValueV06("每100km费用", "¥ ${two(sample.costPer100Km)}", modifier)
                }
            }
            Text(
                "可信度 ${confidenceText(sample.confidence)} · 结束 SOC 差 ${sample.endSocDeltaPoints} 个百分点",
                style = MaterialTheme.typography.bodySmall,
                color = if (sample.confidence == ChargingEstimateConfidence.LOW) MaterialTheme.warningColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
            coverage?.let {
                Text(
                    "Trip ${it.completedTripCount} 条 · ${one(it.completedTripDistanceKm)} km · 覆盖 ${percent(it.coverageRatio)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DataScopeNoteV06() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("统计口径", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "充电记录中的 kWh 是充电桩/电表侧补能事实；Trip 中的 kWh/100km 是依据电池容量与整数 SOC 变化推导的估算。两类数据分开展示，不直接相减或混算充电效率。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DataAccumulationNoteV06() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = MaterialTheme.spacing.xs),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.padding(top = 5.dp).size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        Spacer(Modifier.width(MaterialTheme.spacing.sm))
        Column {
            Text("数据仍在积累", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "更多带里程、地点和真实行程的记录，会让长期趋势和区间估算更可靠。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatValueV06(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionHeadingV06(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun chargerCategoryText(value: ChargerCategory) = when (value) {
    ChargerCategory.HOME -> "家充"
    ChargerCategory.PUBLIC_SLOW -> "公共慢充"
    ChargerCategory.PUBLIC_FAST -> "公共快充"
    ChargerCategory.OTHER -> "其他/未分类"
}

private fun confidenceText(value: ChargingEstimateConfidence) = when (value) {
    ChargingEstimateConfidence.HIGH -> "高"
    ChargingEstimateConfidence.MEDIUM -> "中"
    ChargingEstimateConfidence.LOW -> "低"
}

private fun one(value: Double) = String.format(Locale.US, "%.1f", value)
private fun two(value: Double) = String.format(Locale.US, "%.2f", value)
private fun percent(value: Double) = String.format(Locale.US, "%.0f%%", value * 100.0)
private fun signedPercent(value: Double) = String.format(Locale.US, "%+.0f%%", value * 100.0)
private fun shortDate(epochMillis: Long) = SimpleDateFormat("MM-dd HH:mm", Locale.SIMPLIFIED_CHINESE).format(Date(epochMillis))
