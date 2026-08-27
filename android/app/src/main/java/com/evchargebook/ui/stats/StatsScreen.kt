package com.evchargebook.ui.stats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(state: MainUiState) {
    Scaffold(topBar = { TopAppBar(title = { Text("统计") }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg)
        ) {
            item { MonthSummary(state) }
            MonthlyChargingComparison.compare(state.monthlyTrend)?.let { comparison -> item { MonthComparisonCard(comparison) } }
            if (state.monthlyTrend.isNotEmpty()) item { AnalyticsSection("最近 6 个月", "费用、补能与平均电价") { MonthlyTrendContent(state) } }
            if (state.chargingRecords.isNotEmpty()) item { AnalyticsSection("充电方式", "当前车辆的充电结构") { ChargerTypeContent(state) } }
            if (state.chargingPlaceSummary.isNotEmpty()) item { AnalyticsSection("常用地点", "按已保存地点文本统计") { CommonPlacesContent(state) } }
            item { SectionHeading("累计账本", "从第一条记录至今") }
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
                Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(MaterialTheme.spacing.md)) {
                        Text("数据仍在积累", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(MaterialTheme.spacing.xs))
                        Text("更多带里程、地点和真实行程的记录，会让长期趋势和区间估算更可靠。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
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

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface
    ) {
        Column(Modifier.padding(MaterialTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(8.dp), color = MaterialTheme.colorScheme.inversePrimary, shape = MaterialTheme.shapes.extraSmall) {}
                Spacer(Modifier.width(MaterialTheme.spacing.xs))
                Text("ENERGY / MONTH", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = .60f))
            }
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs)) {
                Text("本月充电支出", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = .62f))
                Text("¥ ${two(state.monthCost)}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.inverseOnSurface)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = .12f))
            ResponsiveMetricGrid(metrics.size) { index, modifier ->
                val (label, value) = metrics[index]
                StatValue(label, value, modifier, MaterialTheme.colorScheme.inverseOnSurface)
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
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(MaterialTheme.spacing.md)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(changeRate?.let(::signedPercent) ?: "--", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AnalyticsSection(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        SectionHeading(title, subtitle)
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm), content = content)
        }
    }
}

@Composable
private fun MonthlyTrendContent(state: MainUiState) {
    state.monthlyTrend.forEachIndexed { index, bucket ->
        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("${bucket.year}年${bucket.month}月", fontWeight = FontWeight.SemiBold); Text("${bucket.chargingCount} 次", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Column { Text("¥ ${two(bucket.cost)}", fontWeight = FontWeight.SemiBold); Text("${one(bucket.energyKwh)} kWh", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(bucket.averagePricePerKwh?.let { "¥ ${two(it)}/kWh" } ?: "--", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChargerTypeContent(state: MainUiState) {
    state.chargerTypeSummary.forEachIndexed { index, item ->
        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text(chargerCategoryText(item.category), fontWeight = FontWeight.SemiBold); Text("${item.chargingCount} 次 · ${percent(item.countShare)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Column { Text("${one(item.energyKwh)} kWh"); Text("电量 ${percent(item.energyShare)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Column { Text("¥ ${two(item.cost)}"); Text("费用 ${percent(item.costShare)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun CommonPlacesContent(state: MainUiState) {
    val topPlaces = state.chargingPlaceSummary.take(5)
    topPlaces.forEachIndexed { index, place ->
        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1.4f)) { Text(place.displayName, fontWeight = FontWeight.SemiBold); Text("${place.chargingCount} 次 · ${shortDate(place.latestChargeTimeEpochMillis)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Column(Modifier.weight(1f)) { Text("${one(place.energyKwh)} kWh"); Text("¥ ${two(place.cost)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(place.averagePricePerKwh?.let { "¥ ${two(it)}/kWh" } ?: "--", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IntervalAnalyticsCard(state: MainUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            Icon(Icons.Default.Route, null, tint = MaterialTheme.colorScheme.primary)
            SectionHeading("里程区间估算", "基于相邻充电记录，不等同 BMS 表显电耗")
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
                    HorizontalDivider()
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
        recent.forEach { sample -> IntervalDetailCard(sample, coverageByCurrentRecord[sample.currentRecordId], recordsById[sample.previousRecordId]?.chargeTimeEpochMillis, recordsById[sample.currentRecordId]?.chargeTimeEpochMillis) }
    }
}

@Composable
private fun IntervalDetailCard(sample: ChargingIntervalSample, coverage: ChargingTripCoverageInterval?, previousTime: Long?, currentTime: Long?) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
            Text(if (previousTime != null && currentTime != null) "${shortDate(previousTime)} → ${shortDate(currentTime)}" else "充电区间 #${sample.previousRecordId} → #${sample.currentRecordId}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("${one(sample.distanceKm)} km · 补入 ${one(sample.replenishedEnergyKwh)} kWh · ¥ ${two(sample.replenishmentCost)}")
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                StatValue("补入电量", "${one(sample.energyPer100Km)} kWh/100km", Modifier.weight(1f))
                StatValue("费用", "¥ ${two(sample.costPer100Km)}/100km", Modifier.weight(1f))
            }
            Text("可信度 ${confidenceText(sample.confidence)} · 结束 SOC 差 ${sample.endSocDeltaPoints} 个百分点", style = MaterialTheme.typography.bodySmall, color = if (sample.confidence == ChargingEstimateConfidence.LOW) MaterialTheme.warningColor else MaterialTheme.colorScheme.onSurfaceVariant)
            coverage?.let { Text("Trip ${it.completedTripCount} 条 · ${one(it.completedTripDistanceKm)} km · 覆盖 ${percent(it.coverageRatio)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
private fun StatValue(label: String, value: String, modifier: Modifier, contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = contentColor.copy(alpha = .68f))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = contentColor)
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

private fun chargerCategoryText(value: ChargerCategory) = when (value) { ChargerCategory.HOME -> "家充"; ChargerCategory.PUBLIC_SLOW -> "公共慢充"; ChargerCategory.PUBLIC_FAST -> "公共快充"; ChargerCategory.OTHER -> "其他/未分类" }
private fun confidenceText(value: ChargingEstimateConfidence) = when (value) { ChargingEstimateConfidence.HIGH -> "高"; ChargingEstimateConfidence.MEDIUM -> "中"; ChargingEstimateConfidence.LOW -> "低" }
private fun one(value: Double) = String.format(Locale.US, "%.1f", value)
private fun two(value: Double) = String.format(Locale.US, "%.2f", value)
private fun percent(value: Double) = String.format(Locale.US, "%.0f%%", value * 100.0)
private fun signedPercent(value: Double) = String.format(Locale.US, "%+.0f%%", value * 100.0)
private fun shortDate(epochMillis: Long) = SimpleDateFormat("MM-dd HH:mm", Locale.SIMPLIFIED_CHINESE).format(Date(epochMillis))
