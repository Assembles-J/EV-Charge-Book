package com.evchargebook.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

private val StatsHeroBrush = Brush.linearGradient(
    listOf(Color(0xFF06100B), Color(0xFF0B2117), Color(0xFF07120D))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(state: MainUiState) {
    val zoneId = ZoneId.systemDefault()
    val month = YearMonth.from(Instant.now().atZone(zoneId))
    val monthStart = month.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    val nextMonthStart = month.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    val monthTripEnergy = TripEnergyAnalytics.summarize(state.trips, monthStart, nextMonthStart)
    val lifetimeTripEnergy = TripEnergyAnalytics.summarize(state.trips)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("统计", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("ENERGY ANALYTICS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg)
        ) {
            item { MonthSummary(state) }
            item { TripEnergyEstimateCard(monthTripEnergy, lifetimeTripEnergy) }
            MonthlyChargingComparison.compare(state.monthlyTrend)?.let { comparison -> item { MonthComparisonCard(comparison) } }
            if (state.monthlyTrend.isNotEmpty()) item { AnalyticsSection("最近 6 个月充电", "CHARGING FLOW") { MonthlyTrendContent(state) } }
            if (state.chargingRecords.isNotEmpty()) item { AnalyticsSection("充电方式", "CHARGING MIX") { ChargerTypeContent(state) } }
            if (state.chargingPlaceSummary.isNotEmpty()) item { AnalyticsSection("常用地点", "CHARGING PLACES") { CommonPlacesContent(state) } }
            item { SectionHeading("充电账本累计", "CHARGING / LIFETIME") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    StatTile("累计费用", "¥ ${two(state.totalCost)}", Icons.Default.Payments, Modifier.weight(1f))
                    StatTile("累计电网补能", "${one(state.totalEnergy)} kWh", Icons.Default.Bolt, Modifier.weight(1f))
                }
            }
            item { StatTile("平均桩端单价", "¥ ${two(state.averagePrice)} / kWh", Icons.Default.BarChart, Modifier.fillMaxWidth()) }
            item { IntervalAnalyticsCard(state) }
            if (state.intervalSamples.isNotEmpty()) item { IntervalDetailSection(state) }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(Modifier.padding(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                        Text("统计口径", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "充电记录中的 kWh 是充电桩/电表侧补能事实；Trip 中的 kWh/100km 是依据电池容量与整数 SOC 变化推导的估算。两类数据分开展示，不直接相减或混算充电效率。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.sm), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                    Spacer(Modifier.width(MaterialTheme.spacing.sm))
                    Column {
                        Text("数据仍在积累", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("更多带里程、地点和真实行程的记录，会让长期趋势和区间估算更可靠。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun MonthSummary(state: MainUiState) {
    val metrics = listOf(
        "电网补能" to "${one(state.monthEnergy)} kWh",
        "充电次数" to "${state.chargingCount} 次",
        "桩端均价" to "¥ ${two(state.averagePrice)}"
    )

    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = Color.Transparent) {
        Column(
            Modifier.background(StatsHeroBrush).padding(MaterialTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                Spacer(Modifier.width(MaterialTheme.spacing.xs))
                Text("CHARGING / MONTH", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs)) {
                Text("本月充电支出", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("¥ ${two(state.monthCost)}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .28f))
            ResponsiveMetricGrid(metrics.size) { index, modifier ->
                val (label, value) = metrics[index]
                StatValue(label, value, modifier)
            }
        }
    }
}

@Composable
private fun TripEnergyEstimateCard(month: TripEnergySummary, lifetime: TripEnergySummary) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        SectionHeading("行驶能耗估算", "TRIP / SOC ESTIMATE")
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                Text(
                    "依据已完成 Trip 的开始/结束 SOC 与配置电池容量推导，不是 BMS 实测。平均值按总估算能量 ÷ 总有效距离加权计算。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (month.eligibleTripCount == 0) {
                    Text(
                        if (month.completedTripCount == 0) "本月暂无已完成 Trip。" else "本月 Trip 暂无足够的 SOC 下降数据用于能耗估算。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    ResponsiveMetricGrid(3) { index, modifier ->
                        when (index) {
                            0 -> StatValue("本月估算消耗", "${one(month.estimatedEnergyKwh)} kWh", modifier)
                            1 -> StatValue("有效 Trip 距离", "${one(month.distanceKm)} km", modifier)
                            else -> StatValue("加权平均能耗", month.weightedAverageKwhPer100Km?.let { "${one(it)} kWh/100km" } ?: "--", modifier)
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
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
}

@Composable
private fun MonthComparisonCard(comparison: com.evchargebook.domain.MonthlyComparison) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        SectionHeading("本月 vs 上月", "${comparison.previous.year}年${comparison.previous.month}月 → ${comparison.current.year}年${comparison.current.month}月")
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            ComparisonValue("费用", "¥ ${two(comparison.current.cost)}", comparison.costChangeRate, Modifier.weight(1f))
            ComparisonValue("电网补能", "${one(comparison.current.energyKwh)} kWh", comparison.energyChangeRate, Modifier.weight(1f))
            ComparisonValue("充电次数", "${comparison.current.chargingCount} 次", comparison.countChangeRate, Modifier.weight(1f))
        }
        if (comparison.costChangeRate == null || comparison.energyChangeRate == null || comparison.countChangeRate == null) {
            Text("上月为 0 的指标不显示无意义的增长率。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ComparisonValue(label: String, value: String, changeRate: Double?, modifier: Modifier) {
    Column(modifier.padding(vertical = MaterialTheme.spacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(changeRate?.let(::signedPercent) ?: "--", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun AnalyticsSection(title: String, eyebrow: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        SectionHeading(title, eyebrow)
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm), content = content)
        }
    }
}

@Composable
private fun MonthlyTrendContent(state: MainUiState) {
    state.monthlyTrend.forEachIndexed { index, bucket ->
        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("${bucket.year}年${bucket.month}月", fontWeight = FontWeight.SemiBold)
                Text("${bucket.chargingCount} 次", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("¥ ${two(bucket.cost)}", fontWeight = FontWeight.SemiBold)
                Text("电网补能 ${one(bucket.energyKwh)} kWh", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(bucket.averagePricePerKwh?.let { "¥ ${two(it)}/kWh" } ?: "--", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChargerTypeContent(state: MainUiState) {
    state.chargerTypeSummary.forEachIndexed { index, item ->
        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(chargerCategoryText(item.category), fontWeight = FontWeight.SemiBold)
                Text("${item.chargingCount} 次 · ${percent(item.countShare)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column {
                Text("${one(item.energyKwh)} kWh")
                Text("桩端电量 ${percent(item.energyShare)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("¥ ${two(item.cost)}")
                Text("费用 ${percent(item.costShare)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CommonPlacesContent(state: MainUiState) {
    state.chargingPlaceSummary.take(5).forEachIndexed { index, place ->
        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1.4f)) {
                Text(place.displayName, fontWeight = FontWeight.SemiBold)
                Text("${place.chargingCount} 次 · ${shortDate(place.latestChargeTimeEpochMillis)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
                Text("桩端 ${one(place.energyKwh)} kWh")
                Text("¥ ${two(place.cost)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(place.averagePricePerKwh?.let { "¥ ${two(it)}/kWh" } ?: "--", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IntervalAnalyticsCard(state: MainUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .10f)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Route, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
            }
            SectionHeading("里程区间估算", "CHARGE-TO-CHARGE ESTIMATE")
        }
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                Text(
                    "这里衡量两次充电记录之间每 100 km 对应的补入电量/费用，不等同于车辆行驶能耗。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.intervalSampleCount == 0 || state.intervalEnergyPer100Km == null || state.intervalCostPer100Km == null) {
                    Text("至少需要两条有效且递增的里程记录。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                        StatValue("每100km补入电量", "${one(state.intervalEnergyPer100Km)} kWh", Modifier.weight(1f))
                        StatValue("每100km费用", "¥ ${two(state.intervalCostPer100Km)}", Modifier.weight(1f))
                    }
                    Text("${state.intervalSampleCount} 个有效区间 · ${one(state.intervalDistanceKm)} km 样本", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.invalidIntervalCount > 0) Text("已排除 ${state.invalidIntervalCount} 个异常区间。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.warningColor)
                }
                if (state.tripCoverageIntervalCount > 0 && state.tripCoverageRatio != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
                    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                        StatValue("轨迹覆盖", percent(state.tripCoverageRatio), Modifier.weight(1f))
                        StatValue("Trip 距离", "${one(state.tripCoverageDistanceKm)} km", Modifier.weight(1f))
                    }
                    Text("${state.tripCoverageIntervalCount} 个区间有完整 Trip 证据。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun IntervalDetailSection(state: MainUiState) {
    val recordsById = state.chargingRecords.associateBy { it.id }
    val coverageByCurrentRecord = state.tripCoverageIntervals.associateBy { it.currentRecordId }
    val recent = state.intervalSamples.sortedByDescending { recordsById[it.currentRecordId]?.chargeTimeEpochMillis ?: Long.MIN_VALUE }.take(8)
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        SectionHeading("最近区间明细", "${state.intervalSamples.size} 个有效区间")
        recent.forEach { sample ->
            IntervalDetailCard(
                sample,
                coverageByCurrentRecord[sample.currentRecordId],
                recordsById[sample.previousRecordId]?.chargeTimeEpochMillis,
                recordsById[sample.currentRecordId]?.chargeTimeEpochMillis
            )
        }
    }
}

@Composable
private fun IntervalDetailCard(sample: ChargingIntervalSample, coverage: ChargingTripCoverageInterval?, previousTime: Long?, currentTime: Long?) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
            Text(
                if (previousTime != null && currentTime != null) "${shortDate(previousTime)} → ${shortDate(currentTime)}" else "充电区间 #${sample.previousRecordId} → #${sample.currentRecordId}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text("${one(sample.distanceKm)} km · 桩端补入 ${one(sample.replenishedEnergyKwh)} kWh · ¥ ${two(sample.replenishmentCost)}")
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                StatValue("每100km补入电量", "${one(sample.energyPer100Km)} kWh", Modifier.weight(1f))
                StatValue("每100km费用", "¥ ${two(sample.costPer100Km)}", Modifier.weight(1f))
            }
            Text(
                "可信度 ${confidenceText(sample.confidence)} · 结束 SOC 差 ${sample.endSocDeltaPoints} 个百分点",
                style = MaterialTheme.typography.bodySmall,
                color = if (sample.confidence == ChargingEstimateConfidence.LOW) MaterialTheme.warningColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
            coverage?.let {
                Text("Trip ${it.completedTripCount} 条 · ${one(it.completedTripDistanceKm)} km · 覆盖 ${percent(it.coverageRatio)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(MaterialTheme.spacing.md)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(MaterialTheme.spacing.sm))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StatValue(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
