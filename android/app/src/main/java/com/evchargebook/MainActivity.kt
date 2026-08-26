package com.evchargebook

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.evchargebook.data.database.AppDatabase
import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.data.repository.ChargingRepository
import com.evchargebook.ui.dashboard.DashboardScreen
import com.evchargebook.ui.records.AddRecordScreen
import com.evchargebook.ui.records.RecordEditScreen
import com.evchargebook.ui.records.RecordsScreen
import com.evchargebook.ui.stats.StatsScreen
import com.evchargebook.ui.theme.EvChargeTheme
import com.evchargebook.ui.vehicle.VehicleEditScreen
import com.evchargebook.ui.vehicle.VehicleScreen
import com.evchargebook.ui.vehicle.VehicleCatalogScreen
import com.evchargebook.ui.vehicle.BluetoothPromptScreen
import com.evchargebook.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels {
        val db = AppDatabase.getInstance(applicationContext)
        MainViewModel.Factory(ChargingRepository(db, applicationContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { EvChargeTheme { MainApp(vm) } }
    }
}

@Composable
fun MainApp(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(0) }
    var editVehicle by remember { mutableStateOf(false) }
    var addVehicle by remember { mutableStateOf(false) }
    var selectCatalogVehicle by remember { mutableStateOf(false) }
    var bluetoothPrompt by remember { mutableStateOf(false) }
    var openBluetoothAfterNotificationPermission by remember { mutableStateOf(false) }
    var catalogSelection by remember { mutableStateOf<com.evchargebook.data.entity.VehicleCatalogEntity?>(null) }
    var addRecord by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<ChargingRecordEntity?>(null) }
    var pendingExportContent by remember { mutableStateOf<String?>(null) }
    var pendingRestoreContent by remember { mutableStateOf<String?>(null) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val content = pendingExportContent
        if (uri != null && content != null) {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) }
        }
        pendingExportContent = null
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingRestoreContent = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (openBluetoothAfterNotificationPermission) { openBluetoothAfterNotificationPermission = false; viewModel.refreshPairedBluetoothDevices(); bluetoothPrompt = true } }
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) { if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) { openBluetoothAfterNotificationPermission = true; notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) } else { viewModel.refreshPairedBluetoothDevices(); bluetoothPrompt = true } } }

    val titles = listOf("总览", "记录", "统计", "车辆")

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccess()
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!editVehicle && !addVehicle && !selectCatalogVehicle && !bluetoothPrompt && !addRecord && editingRecord == null) {
                NavigationBar {
                    titles.forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Icon(listOf(Icons.Default.Home, Icons.Default.History, Icons.Default.BarChart, Icons.Default.DirectionsCar)[index], title) },
                            label = { Text(title) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Surface(Modifier.padding(padding)) {
            when {
                addRecord -> {
                    AddRecordScreen(
                        vehicleId = state.vehicle?.id ?: 0L,
                        records = state.chargingRecords,
                        onBack = { addRecord = false },
                        onSave = { location, start, end, energy, cost, type, remark, time, odometer ->
                            viewModel.addChargingRecord(start, end, energy, cost, location, type, remark, time, odometer)
                            addRecord = false
                        }
                    )
                }
                editingRecord != null -> {
                    RecordEditScreen(
                        record = editingRecord!!,
                        records = state.chargingRecords,
                        onSave = { viewModel.updateChargingRecord(it); editingRecord = null },
                        onBack = { editingRecord = null }
                    )
                }
                editVehicle -> {
                    state.vehicle?.let { vehicle ->
                        VehicleEditScreen(
                            initialBrand = vehicle.brand,
                            initialModel = vehicle.model,
                            initialBatteryCapacity = vehicle.batteryCapacityKwh.toString(),
                            initialRange = vehicle.rangeKm.toString(),
                            onSave = { brand, model, capacity, range ->
                                viewModel.saveVehicle(brand, model, capacity, range)
                                editVehicle = false
                            },
                            onBack = { editVehicle = false }
                        )
                    }
                }
                addVehicle -> {
                    VehicleEditScreen(
                        initialBrand = catalogSelection?.brand.orEmpty(),
                        initialModel = catalogSelection?.modelName.orEmpty(),
                        initialBatteryCapacity = catalogSelection?.batteryCapacityKwh?.toString().orEmpty(),
                        initialRange = catalogSelection?.rangeKm?.toString().orEmpty(),
                        title = "添加车辆",
                        onSave = { brand, model, capacity, range ->
                            viewModel.addVehicle(brand, model, capacity, range, catalogSelection?.catalogId)
                            catalogSelection = null
                            addVehicle = false
                        },
                        onBack = { addVehicle = false }
                    )
                }
                selectCatalogVehicle -> VehicleCatalogScreen(state.catalogVehicles, { selected -> catalogSelection = selected; selectCatalogVehicle = false; addVehicle = true }, { catalogSelection = null; selectCatalogVehicle = false; addVehicle = true }, { selectCatalogVehicle = false })
                bluetoothPrompt -> BluetoothPromptScreen(state.bluetoothSettings, state.pairedBluetoothDevices, viewModel::saveBluetoothPrompt) { bluetoothPrompt = false }
                else -> when (tab) {
                    0 -> DashboardScreen(state, { addRecord = true }, viewModel::selectVehicle)
                    1 -> RecordsScreen(state.chargingRecords, viewModel::deleteChargingRecord, { addRecord = true }, { editingRecord = it })
                    2 -> StatsScreen(state)
                    3 -> VehicleScreen(
                        vehicle = state.vehicle,
                        vehicles = state.vehicles,
                        onSelect = viewModel::selectVehicle,
                        onAdd = { selectCatalogVehicle = true },
                        onEdit = { editVehicle = true },
                        onArchive = { vehicle -> viewModel.archiveVehicle(vehicle.id) },
                        onBluetoothPrompt = {
                            if (Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) { if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) { openBluetoothAfterNotificationPermission = true; notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) } else { viewModel.refreshPairedBluetoothDevices(); bluetoothPrompt = true } }
                            else bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        },
                        onExportBackup = {
                            viewModel.exportBackup(BuildConfig.VERSION_NAME) { content ->
                                pendingExportContent = content
                                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                createBackupLauncher.launch("ev-charge-book-backup-$date.json")
                            }
                        },
                        onImportBackup = { openBackupLauncher.launch(arrayOf("application/json", "text/plain")) }
                    )
                }
            }
        }
    }

    pendingRestoreContent?.let { content ->
        AlertDialog(
            onDismissRequest = { pendingRestoreContent = null },
            title = { Text("覆盖当前本地数据？") },
            text = { Text("恢复备份会删除当前车辆和充电记录，再写入备份内容。此操作不可撤销，建议先导出当前备份。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestoreContent = null
                    viewModel.restoreBackup(content)
                }) { Text("确认恢复", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingRestoreContent = null }) { Text("取消") } }
        )
    }
}
