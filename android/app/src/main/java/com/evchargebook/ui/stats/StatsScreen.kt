package com.evchargebook.ui.stats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evchargebook.viewmodel.MainUiState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(state: MainUiState) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("数据统计", fontWeight = FontWeight.Bold) }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Text("本月", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp)) }
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("本月充电费用", style = MaterialTheme.typography.labelMedium)
                        Text("¥ ${twoDecimals(state.monthCost)}", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("${oneDecimal(state.monthEnergy)} kWh · ${state.chargingCount} 次")
                    }
                }
            }

            item { Text("累计", style = MaterialTheme.typography.titleLarge) }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiniStatCard("累计费用", "¥ ${twoDecimals(state.totalCost)}", Modifier.weight(1f))
                    MiniStatCard("累计电量", "${oneDecimal(state.totalEnergy)} kWh", Modifier.weight(1f))
                }
            }
            item {
                MiniStatCard("平均电价", "¥ ${twoDecimals(state.averagePrice)}/kWh", Modifier.fillMaxWidth())
            }

            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Default.Info, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("趋势图、快慢充占比和百公里成本属于 v0.2。当前版本只展示真实可计算数据。")
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

private fun oneDecimal(value: Double) = String.format(Locale.US, "%.1f", value)
private fun twoDecimals(value: Double) = String.format(Locale.US, "%.2f", value)
