package com.evchargebook.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.ui.components.EmptyState
import com.evchargebook.ui.theme.spacing
import com.evchargebook.viewmodel.MainUiState

@Composable
fun DashboardScreen(state: MainUiState, onAddClick: () -> Unit, onSelectVehicle: (Long) -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddClick,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("记录充电") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                horizontal = MaterialTheme.spacing.md,
                vertical = MaterialTheme.spacing.sm
            ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
        ) {
            item {
                HeroVehicleCard(state.vehicle)
            }
            item {
                EnergyDashboardSummary(state)
            }
            item {
                Text("最近充电", style = MaterialTheme.typography.titleLarge)
            }
            if (state.chargingRecords.isEmpty()) {
                item {
                    EmptyState(
                        "开始记录第一次充电",
                        "记录电量、费用和地点，之后会自动汇总。",
                        "新增充电",
                        onAddClick
                    )
                }
            } else {
                items(state.chargingRecords.take(3), key = { it.id }) {
                    DashboardChargeItem(it)
                }
            }
            item { Spacer(Modifier.height(64.dp)) }
        }
    }
}

@Composable
private fun EnergyDashboardSummary(state: MainUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
        Text("本月能源", style = MaterialTheme.typography.titleLarge)
        Text(
            "${String.format("%.1f", state.monthEnergy)} kWh",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            "¥ ${String.format("%.2f", state.monthCost)} · 平均电价 ¥ ${String.format("%.2f", state.averagePrice)}/kWh",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DashboardChargeItem(record: ChargingRecordEntity) {
    Column {
        Text(record.location ?: "未命名充电地点", style = MaterialTheme.typography.titleMedium)
        Text(
            "${String.format("%.1f", record.energyKwh)} kWh · ¥ ${String.format("%.2f", record.cost)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
