package com.evchargebook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.evchargebook.data.database.AppDatabase
import com.evchargebook.data.repository.ChargingRepository
import com.evchargebook.ui.dashboard.DashboardScreen
import com.evchargebook.ui.records.AddRecordScreen
import com.evchargebook.ui.records.RecordsScreen
import com.evchargebook.ui.stats.StatsScreen
import com.evchargebook.ui.vehicle.VehicleScreen
import com.evchargebook.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels {
        val database = AppDatabase.getInstance(applicationContext)
        MainViewModel.Factory(
            ChargingRepository(
                vehicleDao = database.vehicleDao(),
                chargingRecordDao = database.chargingRecordDao()
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MainApp(mainViewModel) }
    }
}

@Composable
fun MainApp(viewModel: MainViewModel) {
    var selectedItem by remember { mutableIntStateOf(0) }
    var isAddingRecord by remember { mutableStateOf(false) }
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val items = listOf("总览", "记录", "统计", "车辆")
    val icons = listOf(Icons.Default.Home, Icons.Default.History, Icons.Default.BarChart, Icons.Default.DirectionsCar)

    MaterialTheme {
        if (isAddingRecord) {
            AddRecordScreen(
                onBack = { isAddingRecord = false },
                onSave = { location, startSoc, endSoc, energyKwh, cost ->
                    viewModel.addChargingRecord(
                        startSoc = startSoc,
                        endSoc = endSoc,
                        energyKwh = energyKwh,
                        cost = cost,
                        location = location
                    )
                    isAddingRecord = false
                    selectedItem = 1
                }
            )
        } else {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        0 -> DashboardScreen(state = state, onAddClick = { isAddingRecord = true })
                        1 -> RecordsScreen(
                            records = state.chargingRecords,
                            onDelete = viewModel::deleteChargingRecord
                        )
                        2 -> StatsScreen(state)
                        3 -> VehicleScreen()
                    }
                }
            }
        }
    }
}
