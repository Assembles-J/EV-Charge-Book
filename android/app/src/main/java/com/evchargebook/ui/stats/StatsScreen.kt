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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.evchargebook.domain.ChargerCategory
import com.evchargebook.domain.ChargingEstimateConfidence
import com.evchargebook.domain.ChargingIntervalSample
import com.evchargebook.domain.ChargingTripCoverageInterval
import com.evchargebook.domain.MonthlyChargingComparison
import com.evchargebook.ui.theme.spacing
import com.evchargebook.viewmodel.MainUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun StatsScreen(state: MainUiState) {
    Scaffold(topBar = { TopAppBar(title = { Column { Text("能耗分析"); Text("所有数据均来自本地充电记录", style = MaterialTheme.typography.labelMedium) } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
            item { MonthSummary(state) }
            MonthlyChargingComparison.compare(state.monthlyTrend)?.let { comparison -> item { MonthComparisonCard(comparison) } }
            if (state.monthlyTrend.isNotEmpty()) item { MonthlyTrendCard(state) }
            if (state.chargingRecords.isNotEmpty()) item { ChargerTypeCard(state) }
            item { Text("累计账本", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) { StatTile("累计费用", "¥ ${two(state.totalCost)}", Icons.Default.Payments, Modifier.weight(1f)); StatTile("累计补能", "${one(state.totalEnergy)} kWh", Icons.Default.Bolt, Modifier.weight(1f)) } }
            item { StatTile("平均充电单价", "¥ ${two(state.averagePrice)} / kWh", Icons.Default.BarChart, Modifier.fillMaxWidth()) }
            item { IntervalAnalyticsCard(state) }
            if (state.intervalSamples.isNotEmpty()) item { IntervalDetailSection(state) }
            item { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(MaterialTheme.spacing.md)) { Text("数据正在积累", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(MaterialTheme.spacing.xs)); Text("记录更多带里程、地点与真实行程的数据后，这里会继续增加地点结构和更长期趋势分析。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        }
    }
}

@Composable private fun MonthSummary(state: MainUiState) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(MaterialTheme.spacing.lg)) {
            Text("本月充电支出", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Text("¥ ${two(state.monthCost)}", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.height(MaterialTheme.spacing.md))
            HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.24f))
            Spacer(Modifier.height(MaterialTheme.spacing.md))
            Row { Text("${one(state.monthEnergy)} kWh", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer); Spacer(Modifier.width(MaterialTheme.spacing.md)); Text("${state.chargingCount} 次记录", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer) }
        }
    }
}

@Composable private fun MonthComparisonCard(comparison: com.evchargebook.domain.MonthlyComparison) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            Text("本月 vs 上月", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${comparison.previous.year}年${comparison.previous.month}月 → ${comparison.current.year}年${comparison.current.month}月", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                ComparisonValue("费用", "¥ ${two(comparison.current.cost)}", comparison.costChangeRate, Modifier.weight(1f))
                ComparisonValue("补能", "${one(comparison.current.energyKwh)} kWh", comparison.energyChangeRate, Modifier.weight(1f))
                ComparisonValue("次数", "${comparison.current.chargingCount} 次", comparison.countChangeRate, Modifier.weight(1f))
            }
            if (comparison.costChangeRate == null || comparison.energyChangeRate == null || comparison.countChangeRate == null) {
                Text("上月对应指标为 0，暂无可靠百分比基线；保留绝对值对比，不显示无意义的无限增长率。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun ComparisonValue(label: String, value: String, changeRate: Double?, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(changeRate?.let(::signedPercent) ?: "暂无可比基线", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun MonthlyTrendCard(state: MainUiState) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            Text("最近 6 个月", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("自然月费用与补能趋势；空月份保留为 0。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.monthlyTrend.forEachIndexed { index, bucket ->
                if (index > 0) HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("${bucket.year}年${bucket.month}月", fontWeight = FontWeight.SemiBold); Text("${bucket.chargingCount} 次充电", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Column { Text("¥ ${two(bucket.cost)}", fontWeight = FontWeight.SemiBold); Text("${one(bucket.energyKwh)} kWh", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text(bucket.averagePricePerKwh?.let { "¥ ${two(it)}/kWh" } ?: "--", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable private fun ChargerTypeCard(state: MainUiState) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            Text("充电方式结构", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("次数占比、补能量和费用均来自当前车辆的本地账本。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.chargerTypeSummary.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(chargerCategoryText(item.category), fontWeight = FontWeight.SemiBold)
                        Text("${item.chargingCount} 次 · ${percent(item.countShare)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column {
                        Text("${one(item.energyKwh)} kWh")
                        Text("电量 ${percent(item.energyShare)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column {
                        Text("¥ ${two(item.cost)}")
                        Text("费用 ${percent(item.costShare)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable private fun IntervalAnalyticsCard(state: MainUiState) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) { Icon(Icons.Default.Route, null, tint = MaterialTheme.colorScheme.primary); Column { Text("里程区间账本估算", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("同一车辆相邻带里程的充电记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            if (state.intervalSampleCount == 0 || state.intervalEnergyPer100Km == null || state.intervalCostPer100Km == null) Text("至少需要两条有效且递增的里程记录，才能形成第一个区间样本。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) { StatValue("补入电量", "${one(state.intervalEnergyPer100Km)} kWh/100km", Modifier.weight(1f)); StatValue("费用", "¥ ${two(state.intervalCostPer100Km)}/100km", Modifier.weight(1f)) }
                Text("${state.intervalSampleCount} 个有效区间 · ${one(state.intervalDistanceKm)} km 样本距离", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.invalidIntervalCount > 0) Text("已排除 ${state.invalidIntervalCount} 个里程倒退、零距离或异常区间。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
            if (state.tripCoverageIntervalCount > 0 && state.tripCoverageRatio != null) {
                HorizontalDivider(); Text("Trip 辅助证据", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) { StatValue("轨迹覆盖", percent(state.tripCoverageRatio), Modifier.weight(1f)); StatValue("Trip 距离", "${one(state.tripCoverageDistanceKm)} km", Modifier.weight(1f)) }
                Text("${state.tripCoverageIntervalCount} 个区间有完整 Trip 证据；对应里程表区间 ${one(state.tripCoverageOdometerKm)} km。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val difference = state.tripCoverageOdometerKm - state.tripCoverageDistanceKm
                if (kotlin.math.abs(difference) >= 1.0) Text("Trip 与里程表相差 ${one(kotlin.math.abs(difference))} km。该差异仅用于发现漏记、GPS 漂移或边界问题，不自动修正任何原始数据。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
            Text("说明：这是按充电账本补入电量/费用与相邻里程计算的区间估算，不等同于车辆 BMS 或表显真实电耗。Trip 只作为辅助证据。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun IntervalDetailSection(state: MainUiState) {
    val recordsById = state.chargingRecords.associateBy { it.id }; val coverageByCurrentRecord = state.tripCoverageIntervals.associateBy { it.currentRecordId }
    val recent = state.intervalSamples.sortedByDescending { recordsById[it.currentRecordId]?.chargeTimeEpochMillis ?: Long.MIN_VALUE }.take(8)
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        Text("最近区间明细", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        recent.forEach { sample -> IntervalDetailCard(sample, coverageByCurrentRecord[sample.currentRecordId], recordsById[sample.previousRecordId]?.chargeTimeEpochMillis, recordsById[sample.currentRecordId]?.chargeTimeEpochMillis) }
        if (state.intervalSamples.size > recent.size) Text("当前仅展示最近 ${recent.size} 个区间，共 ${state.intervalSamples.size} 个有效区间。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun IntervalDetailCard(sample: ChargingIntervalSample, coverage: ChargingTripCoverageInterval?, previousTime: Long?, currentTime: Long?) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
            Text(if (previousTime != null && currentTime != null) "${shortDate(previousTime)} → ${shortDate(currentTime)}" else "充电区间 #${sample.previousRecordId} → #${sample.currentRecordId}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("区间里程 ${one(sample.distanceKm)} km · 本次补入 ${one(sample.replenishedEnergyKwh)} kWh · ¥ ${two(sample.replenishmentCost)}")
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) { StatValue("补入电量", "${one(sample.energyPer100Km)} kWh/100km", Modifier.weight(1f)); StatValue("费用", "¥ ${two(sample.costPer100Km)}/100km", Modifier.weight(1f)) }
            Text("估算可信度：${confidenceText(sample.confidence)} · 两次充电结束 SOC 相差 ${sample.endSocDeltaPoints} 个百分点", style = MaterialTheme.typography.bodySmall, color = if (sample.confidence == ChargingEstimateConfidence.LOW) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
            if (sample.confidence != ChargingEstimateConfidence.HIGH) Text("结束 SOC 差异较大时，本次补入电量不能很好代表上一里程区间的电量消耗；仅降低解读可信度，不修正原始数值。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            coverage?.let { HorizontalDivider(); Text("Trip 证据：${it.completedTripCount} 条 · ${one(it.completedTripDistanceKm)} km · 覆盖 ${percent(it.coverageRatio)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); if (kotlin.math.abs(it.distanceDifferenceKm) >= 1.0) Text("与里程表相差 ${one(kotlin.math.abs(it.distanceDifferenceKm))} km，仅提示，不修正。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
        }
    }
}

@Composable private fun StatTile(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) { ElevatedCard(modifier) { Column(Modifier.padding(MaterialTheme.spacing.md)) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(MaterialTheme.spacing.sm)); Text(label, style = MaterialTheme.typography.labelMedium); Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) } } }
@Composable private fun StatValue(label: String, value: String, modifier: Modifier) { Column(modifier) { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) } }
private fun chargerCategoryText(value: ChargerCategory) = when (value) { ChargerCategory.HOME -> "家充"; ChargerCategory.PUBLIC_SLOW -> "公共慢充"; ChargerCategory.PUBLIC_FAST -> "公共快充"; ChargerCategory.OTHER -> "其他/未分类" }
private fun confidenceText(value: ChargingEstimateConfidence) = when (value) { ChargingEstimateConfidence.HIGH -> "高"; ChargingEstimateConfidence.MEDIUM -> "中"; ChargingEstimateConfidence.LOW -> "低" }
private fun one(value: Double) = String.format(Locale.US, "%.1f", value)
private fun two(value: Double) = String.format(Locale.US, "%.2f", value)
private fun percent(value: Double) = String.format(Locale.US, "%.0f%%", value * 100.0)
private fun signedPercent(value: Double) = String.format(Locale.US, "%+.0f%%", value * 100.0)
private fun shortDate(epochMillis: Long) = SimpleDateFormat("MM-dd HH:mm", Locale.SIMPLIFIED_CHINESE).format(Date(epochMillis))
