package com.evchargebook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.evchargebook.ui.dashboard.DashboardScreen
import com.evchargebook.ui.records.AddRecordScreen
import com.evchargebook.ui.records.RecordsScreen
import com.evchargebook.ui.stats.StatsScreen
import com.evchargebook.ui.vehicle.VehicleScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainApp()
        }
    }
}

@Composable
fun MainApp() {
    var selectedItem by remember { mutableIntStateOf(0) }
    var isAddingRecord by remember { mutableStateOf(false) }
    
    val items = listOf("总览", "记录", "统计", "车辆")
    val icons = listOf(Icons.Default.Home, Icons.Default.History, Icons.Default.BarChart, Icons.Default.DirectionsCar)

    MaterialTheme {
        if (isAddingRecord) {
            AddRecordScreen(onBack = { isAddingRecord = false })
        } else {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        items.forEachIndexed { index, item ->
                            NavigationBarItem(
                                icon = { Icon(icons[index], contentDescription = item) },
                                label = { Text(item) },
                                selected = selectedItem == index,
                                onClick = { selectedItem = index }
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Surface(modifier = Modifier.padding(innerPadding)) {
                    when (selectedItem) {
                        0 -> DashboardScreen(onAddClick = { isAddingRecord = true })
                        1 -> RecordsScreen()
                        2 -> StatsScreen()
                        3 -> VehicleScreen()
                    }
                }
            }
        }
    }
}
