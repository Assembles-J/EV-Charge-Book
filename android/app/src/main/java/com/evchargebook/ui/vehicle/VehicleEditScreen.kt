package com.evchargebook.ui.vehicle

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun VehicleEditScreen(
    initialBrand: String,
    initialModel: String,
    initialBatteryCapacity: String,
    initialRange: String,
    onSave: (String, String, Double, Int) -> Unit,
    onBack: () -> Unit
) {
    var brand by remember { mutableStateOf(initialBrand) }
    var model by remember { mutableStateOf(initialModel) }
    var battery by remember { mutableStateOf(initialBatteryCapacity) }
    var range by remember { mutableStateOf(initialRange) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑车辆") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(brand, { brand = it }, label = { Text("品牌") })
            OutlinedTextField(model, { model = it }, label = { Text("车型") })
            OutlinedTextField(battery, { battery = it }, label = { Text("电池容量 kWh") })
            OutlinedTextField(range, { range = it }, label = { Text("标称续航 km") })

            Button(
                onClick = {
                    onSave(
                        brand,
                        model,
                        battery.toDoubleOrNull() ?: 0.0,
                        range.toIntOrNull() ?: 0
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存")
            }
        }
    }
}
