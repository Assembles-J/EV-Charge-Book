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
import com.evchargebook.ui.theme.spacing
import com.evchargebook.viewmodel.MainUiState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun StatsScreen(state: MainUiState) {
    Scaffold(topBar = { TopAppBar(title = { Column { Text("能耗分析"); Text("所有数据均来自本地充电记录", style = MaterialTheme.typography.labelMedium) } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
            item { MonthSummary(state) }
            item { Text("累计账本", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) { StatTile("累计费用", "¥ ${two(state.totalCost)}", Icons.Default.Payments, Modifier.weight(1f)); StatTile("累计补能", "${one(state.totalEnergy)} kWh", Icons.Default.Bolt, Modifier.weight(1f)) } }
            item { StatTile("平均充电单价", "¥ ${two(state.averagePrice)} / kWh", Icons.Default.BarChart, Modifier.fillMaxWidth()) }
            item { IntervalAnalyticsCard(state) }
            item { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(MaterialTheme.spacing.md)) { Text("数据正在积累", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(MaterialTheme.spacing.xs)); Text("记录更多带里程的充电数据与真实行程后，这里会继续增加月度趋势、地点与行程交叉分析。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
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

@Composable private fun IntervalAnalyticsCard(state: MainUiState) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                Icon(Icons.Default.Route, null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text("里程区间账本估算", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("同一车辆相邻带里程的充电记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (state.intervalSampleCount == 0 || state.intervalEnergyPer100Km == null || state.intervalCostPer100Km == null) {
                Text("至少需要两条有效且递增的里程记录，才能形成第一个区间样本。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    StatValue("补入电量", "${one(state.intervalEnergyPer100Km)} kWh/100km", Modifier.weight(1f))
                    StatValue("费用", "¥ ${two(state.intervalCostPer100Km)}/100km", Modifier.weight(1f))
                }
                Text("${state.intervalSampleCount} 个有效区间 · ${one(state.intervalDistanceKm)} km 样本距离", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.invalidIntervalCount > 0) {
                    Text("已排除 ${state.invalidIntervalCount} 个里程倒退、零距离或异常区间。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }

            Text("说明：这是按充电账本补入电量/费用与相邻里程计算的区间估算，不等同于车辆 BMS 或表显真实电耗。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun StatTile(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    ElevatedCard(modifier) { Column(Modifier.padding(MaterialTheme.spacing.md)) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(MaterialTheme.spacing.sm)); Text(label, style = MaterialTheme.typography.labelMedium); Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) } }
}

@Composable private fun StatValue(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

private fun one(value: Double) = String.format(Locale.US, "%.1f", value)
private fun two(value: Double) = String.format(Locale.US, "%.2f", value)
