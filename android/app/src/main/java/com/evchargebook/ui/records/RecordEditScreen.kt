package com.evchargebook.ui.records

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.ChargingRecordEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordEditScreen(
    record: ChargingRecordEntity,
    onSave: (ChargingRecordEntity) -> Unit,
    onBack: () -> Unit
) {
    var location by remember { mutableStateOf(record.location.orEmpty()) }
    var energy by remember { mutableStateOf(record.energyKwh.toString()) }
    var cost by remember { mutableStateOf(record.cost.toString()) }
    var startSoc by remember { mutableStateOf(record.startSoc.toString()) }
    var endSoc by remember { mutableStateOf(record.endSoc.toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑充电记录") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(location, { location = it }, label = { Text("地点") })
            OutlinedTextField(startSoc, { startSoc = it }, label = { Text("开始 SOC") })
            OutlinedTextField(endSoc, { endSoc = it }, label = { Text("结束 SOC") })
            OutlinedTextField(energy, { energy = it }, label = { Text("电量 kWh") })
            OutlinedTextField(cost, { cost = it }, label = { Text("费用") })

            Button(
                onClick = {
                    onSave(
                        record.copy(
                            location = location,
                            startSoc = startSoc.toIntOrNull() ?: record.startSoc,
                            endSoc = endSoc.toIntOrNull() ?: record.endSoc,
                            energyKwh = energy.toDoubleOrNull() ?: record.energyKwh,
                            cost = cost.toDoubleOrNull() ?: record.cost
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存修改")
            }
        }
    }
}
