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
import com.evchargebook.ui.components.ResponsiveMetricGrid
import com.evchargebook.ui.theme.spacing
import com.evchargebook.ui.theme.warningColor
import com.evchargebook.viewmodel.MainUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val StatsHeroBrush = Brush.linearGradient(
    listOf(Color(0xFF06100B), Color(0xFF0B2117), Color(0xFF07120D))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(state: MainUiState) {
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
            MonthlyChargingComparison.compare(state.monthlyTrend)?.let { comparison -> item { MonthComparisonCard(comparison) } }
            if (state.monthlyTrend.isNotEmpty()) item { AnalyticsSection("最近 6 个月", "MONTHLY FLOW") { MonthlyTrendContent(state) } }
            if (state.chargingRecords.isNotEmpty()) item { AnalyticsSection("充电方式", "CHARGING MIX") { ChargerTypeContent(state) } }
            if (state.chargingPlaceSummary.isNotEmpty()) item { AnalyticsSection("常用地点", "CHARGING PLACES") { CommonPlacesContent(state) } }
            item { SectionHeading("累计账本", "LIFETIME TOTAL") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    StatTile("累计费用", "¥ ${two(state.totalCost)}", Icons.Default.Payments, Modifier.weight(1f))
                    StatTile("累计补能", "${one(state.totalEnergy)} kWh", Icons.Default.Bolt, Modifier.weight(1f))
                }
            }
            item { StatTile("平均充电单价", "¥ ${two(state.averagePrice)} / kWh", Icons.Default.BarChart, Modifier.fillMaxWidth()) }
            item { IntervalAnalyticsCard(state) }
            if (state.intervalSamples.isNotEmpty()) item { IntervalDetailSection(state) }
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
        "补能" to "${one(state.monthEnergy)} kWh",
        "次数" to "${state.chargingCount} 次",
        "均价" to "¥ ${two(state.averagePrice)}"
    )

    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = Color.Transparent) {
        Column(
            Modifier.background(StatsHeroBrush).padding(MaterialTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                Spacer(Modifier.width(MaterialTheme.spacing.xs))
                Text("ENERGY / MONTH", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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
private fun MonthComparisonCard(comparison: com.evchargebook.domain.MonthlyComparison) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        SectionHeading("本月 vs 上月", "${comparison.previous.year}年${comparison.previous.month}月 → ${comparison.current.year}年${comparison.current.month}月")
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            ComparisonValue("费用", "¥ ${two(comparison.current.cost)}", comparison.costChangeRate, Modifier.weight(1f))
            ComparisonValue("补能", "${one(comparison.current.energyKwh)} kWh", comparison.energyChangeRate, Modifier.weight(1f))
            ComparisonValue("次数", "${comparison.current.chargingCount} 次", comparison.countChangeRate, Modifier.weight(1f))
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
                Text("${one(bucket.energyKwh)} kWh", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
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
                Text("电量 ${percent(item.energyShare)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
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
                Text("${one(place.energyKwh)} kWh")
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
            SectionHeading("里程区间估算", "INTERVAL ESTIMATE")
        }
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                if (state.intervalSampleCount == 0 || state.intervalEnergyPer100Km == null || state.intervalCostPer100Km == null) {
                    Text("至少需要两条有效且递增的里程记录。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                        StatValue("补入电量", "${one(state.intervalEnergyPer100Km)} kWh/100km", Modifier.weight(1f))
                        StatValue("费用", "¥ ${two(state.intervalCostPer100Km)}/100km", Modifier.weight(1f))
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
            Text("${one(sample.distanceKm)} km · 补入 ${one(sample.replenishedEnergyKwh)} kWh · ¥ ${two(sample.replenishmentCost)}")
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                StatValue("补入电量", "${one(sample.energyPer100Km)} kWh/100km", Modifier.weight(1f))
                StatValue("费用", "¥ ${two(sample.costPer100Km)}/100km", Modifier.weight(1f))
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
