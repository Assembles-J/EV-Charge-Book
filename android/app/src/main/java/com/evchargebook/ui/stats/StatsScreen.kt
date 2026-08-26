package com.evchargebook.ui.stats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Payments
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
            item { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(MaterialTheme.spacing.md)) { Text("数据正在积累", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(MaterialTheme.spacing.xs)); Text("记录更多充电后，这里会逐步呈现清晰的月度趋势与地点洞察。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
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

@Composable private fun StatTile(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    ElevatedCard(modifier) { Column(Modifier.padding(MaterialTheme.spacing.md)) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(MaterialTheme.spacing.sm)); Text(label, style = MaterialTheme.typography.labelMedium); Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) } }
}
private fun one(value: Double) = String.format(Locale.US, "%.1f", value)
private fun two(value: Double) = String.format(Locale.US, "%.2f", value)
