package com.evchargebook.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.viewmodel.MainUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(state: MainUiState, onAddClick: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("EV Charge Book", fontWeight = FontWeight.Bold) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = "增加记录")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                val vehicle = state.vehicle
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = vehicle?.let { "${it.brand} ${it.model}" } ?: "尚未配置车辆",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (vehicle != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("电池 ${vehicle.batteryCapacityKwh} kWh · 标称续航 ${vehicle.rangeKm} km")
                        }
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("本月电费", money(state.monthCost), Icons.Default.EvStation, Modifier.weight(1f))
                    StatCard("本月充电量", "${oneDecimal(state.monthEnergy)} kWh", Icons.Default.ShowChart, Modifier.weight(1f))
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("平均电价", "¥ ${twoDecimals(state.averagePrice)}/kWh", Icons.Default.EvStation, Modifier.weight(1f))
                    StatCard("本月次数", "${state.chargingCount} 次", Icons.Default.ShowChart, Modifier.weight(1f))
                }
            }

            item {
                Text("最近记录", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            }

            if (state.chargingRecords.isEmpty()) {
                item {
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Text("还没有充电记录，点击右下角 + 记录第一次充电。", modifier = Modifier.padding(20.dp))
                    }
                }
            } else {
                items(state.chargingRecords.take(3), key = { it.id }) { record ->
                    RecentRecordItem(record)
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RecentRecordItem(record: ChargingRecordEntity) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(formatTime(record.chargeTimeEpochMillis), style = MaterialTheme.typography.bodySmall)
                Text(record.location ?: "未填写地点", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text("${record.startSoc}% → ${record.endSoc}% · ${oneDecimal(record.energyKwh)} kWh", style = MaterialTheme.typography.bodySmall)
            }
            Text(money(record.cost), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatTime(epochMillis: Long): String =
    DateTimeFormatter.ofPattern("MM-dd HH:mm")
        .withLocale(Locale.SIMPLIFIED_CHINESE)
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMillis))

private fun money(value: Double) = "¥ ${twoDecimals(value)}"
private fun oneDecimal(value: Double) = String.format(Locale.US, "%.1f", value)
private fun twoDecimals(value: Double) = String.format(Locale.US, "%.2f", value)
