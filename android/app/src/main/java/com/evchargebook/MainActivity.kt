package com.evchargebook

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import com.evchargebook.bluetooth.BluetoothConnectionStateChecker
import com.evchargebook.data.database.AppDatabase
import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.data.entity.TripStatus
import com.evchargebook.data.export.ChargingCsvExporter
import com.evchargebook.data.repository.ChargingRepository
import com.evchargebook.domain.trip.TripEnergyCalculator
import com.evchargebook.ui.dashboard.DashboardScreen
import com.evchargebook.ui.records.AddRecordScreen
import com.evchargebook.ui.records.RecordEditScreen
import com.evchargebook.ui.records.RecordsScreen
import com.evchargebook.ui.stats.StatsScreen
import com.evchargebook.ui.theme.EvChargeTheme
import com.evchargebook.ui.trip.TripReadyScreen
import com.evchargebook.ui.trip.TripScreen
import com.evchargebook.ui.vehicle.BluetoothPromptScreen
import com.evchargebook.ui.vehicle.VehicleCatalogScreen
import com.evchargebook.ui.vehicle.VehicleEditScreen
import com.evchargebook.ui.vehicle.VehicleScreen
import com.evchargebook.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels {
        val db = AppDatabase.getInstance(applicationContext)
        MainViewModel.Factory(ChargingRepository(db, applicationContext))
    }
    private var openTripConfirmation by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openTripConfirmation = intent.getBooleanExtra(EXTRA_OPEN_TRIP_CONFIRMATION, false)
        enableEdgeToEdge()
        setContent {
            EvChargeTheme {
                MainApp(
                    viewModel = vm,
                    openTripConfirmation = openTripConfirmation,
                    onTripConfirmationConsumed = { openTripConfirmation = false }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_TRIP_CONFIRMATION, false)) openTripConfirmation = true
    }

    companion object {
        const val EXTRA_OPEN_TRIP_CONFIRMATION = "open_trip_confirmation"
    }
}

@Composable
fun MainApp(
    viewModel: MainViewModel,
    openTripConfirmation: Boolean = false,
    onTripConfirmationConsumed: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
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
    var pendingCsvContent by remember { mutableStateOf<String?>(null) }
    var pendingRestoreContent by remember { mutableStateOf<String?>(null) }
    var pendingResumeTripId by remember { mutableStateOf<Long?>(null) }
    var promptedConnectedAddress by remember { mutableStateOf<String?>(null) }
    var showTripCompletion by remember { mutableStateOf(false) }
    var tripEndSocText by remember { mutableStateOf("") }
    var tripEndMileageText by remember { mutableStateOf("") }
    var tripCompletionError by remember { mutableStateOf<String?>(null) }

    fun showBluetoothTripPrompt(message: String) {
        viewModel.closeTripDetail()
        tab = 3
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun checkConfiguredBluetoothConnection() {
        val settings = state.bluetoothSettings
        if (!settings.enabled || settings.deviceAddress.isNullOrBlank()) return
        BluetoothConnectionStateChecker.check(context, settings.deviceAddress) { connected ->
            if (connected && promptedConnectedAddress != settings.deviceAddress) {
                promptedConnectedAddress = settings.deviceAddress
                showBluetoothTripPrompt("已连接 ${settings.deviceName ?: "车辆蓝牙"}，请确认是否开始本次行程")
            }
        }
    }

    DisposableEffect(lifecycleOwner, state.bluetoothSettings.enabled, state.bluetoothSettings.deviceAddress) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> checkConfiguredBluetoothConnection()
                Lifecycle.Event.ON_STOP -> promptedConnectedAddress = null
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.bluetoothSettings.enabled, state.bluetoothSettings.deviceAddress) {
        if (state.bluetoothSettings.enabled) checkConfiguredBluetoothConnection()
    }

    LaunchedEffect(openTripConfirmation) {
        if (openTripConfirmation) {
            showBluetoothTripPrompt("已检测到车辆蓝牙连接，请确认是否开始本次行程")
            onTripConfirmationConsumed()
        }
    }

    val createBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val content = pendingExportContent
        if (uri != null && content != null) context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) }
        pendingExportContent = null
    }
    val createCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val content = pendingCsvContent
        if (uri != null && content != null) context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(content) }
        pendingCsvContent = null
    }
    val openBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestoreContent = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (openBluetoothAfterNotificationPermission) {
            openBluetoothAfterNotificationPermission = false
            viewModel.refreshPairedBluetoothDevices()
            bluetoothPrompt = true
        }
    }
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                openBluetoothAfterNotificationPermission = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.refreshPairedBluetoothDevices()
                bluetoothPrompt = true
            }
        }
    }
    val tripLocationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            val resumeId = pendingResumeTripId
            pendingResumeTripId = null
            if (resumeId != null) viewModel.resumeTrip(resumeId) else viewModel.startTrip()
        } else {
            pendingResumeTripId = null
            scope.launch { snackbarHostState.showSnackbar("需要定位权限才能记录真实行程轨迹") }
        }
    }

    val titles = listOf("总览", "记录", "统计", "行程", "车辆")
    val icons = listOf(Icons.Default.Home, Icons.Default.History, Icons.Default.BarChart, Icons.Default.Route, Icons.Default.DirectionsCar)
    LaunchedEffect(state.successMessage) { state.successMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearSuccess() } }
    LaunchedEffect(state.errorMessage) { state.errorMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() } }

    fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    fun startTripWithPermission() {
        pendingResumeTripId = null
        if (hasLocationPermission()) viewModel.startTrip()
        else tripLocationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    fun resumeTripWithPermission(tripId: Long) {
        if (hasLocationPermission()) viewModel.resumeTrip(tripId)
        else {
            pendingResumeTripId = tripId
            tripLocationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    fun requestTripCompletion() {
        val active = state.activeTrip ?: return
        tripEndSocText = ""
        tripEndMileageText = active.startMileageKm
            ?.plus(active.distanceMeters / 1000.0)
            ?.let { String.format(Locale.US, "%.1f", it) }
            .orEmpty()
        tripCompletionError = null
        showTripCompletion = true
    }

    val hasOverlayPage = editVehicle || addVehicle || selectCatalogVehicle || bluetoothPrompt || addRecord || editingRecord != null || state.selectedTripId != null
    BackHandler(enabled = hasOverlayPage) {
        when {
            state.selectedTripId != null -> viewModel.closeTripDetail()
            editingRecord != null -> editingRecord = null
            addRecord -> addRecord = false
            bluetoothPrompt -> bluetoothPrompt = false
            selectCatalogVehicle -> selectCatalogVehicle = false
            addVehicle -> { addVehicle = false; catalogSelection = null }
            editVehicle -> editVehicle = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    actionColor = MaterialTheme.colorScheme.primary
                )
            }
        },
        bottomBar = {
            if (!hasOverlayPage) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp
                ) {
                    titles.forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Icon(icons[index], title) },
                            label = { Text(title) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Surface(Modifier.padding(padding), color = MaterialTheme.colorScheme.background) {
            when {
                addRecord -> AddRecordScreen(
                    vehicleId = state.vehicle?.id ?: 0L,
                    records = state.chargingRecords,
                    batteryCapacityKwh = state.vehicle?.batteryCapacityKwh,
                    currentSoc = state.currentSoc,
                    currentMileageKm = state.currentMileageKm,
                    commonPlaces = state.chargingPlaceSummary.map { it.displayName },
                    onBack = { addRecord = false },
                    onSave = { location, start, end, energy, cost, type, remark, time, odometer, latitude, longitude, accuracy ->
                        viewModel.addChargingRecord(start, end, energy, cost, location, type, remark, time, odometer, latitude, longitude, accuracy)
                        addRecord = false
                    }
                )
                editingRecord != null -> RecordEditScreen(
                    record = editingRecord!!,
                    records = state.chargingRecords,
                    batteryCapacityKwh = state.vehicle?.batteryCapacityKwh,
                    onSave = { viewModel.updateChargingRecord(it); editingRecord = null },
                    onBack = { editingRecord = null }
                )
                editVehicle -> state.vehicle?.let { vehicle ->
                    VehicleEditScreen(
                        initialBrand = vehicle.brand,
                        initialModel = vehicle.model,
                        initialBatteryCapacity = vehicle.batteryCapacityKwh.toString(),
                        initialRange = vehicle.rangeKm.toString(),
                        onSave = { brand, model, capacity, range -> viewModel.saveVehicle(brand, model, capacity, range); editVehicle = false },
                        onBack = { editVehicle = false }
                    )
                }
                addVehicle -> VehicleEditScreen(
                    initialBrand = catalogSelection?.brand.orEmpty(),
                    initialModel = catalogSelection?.modelName.orEmpty(),
                    initialBatteryCapacity = catalogSelection?.batteryCapacityKwh?.toString().orEmpty(),
                    initialRange = catalogSelection?.rangeKm?.toString().orEmpty(),
                    title = "添加车辆",
                    onSave = { brand, model, capacity, range -> viewModel.addVehicle(brand, model, capacity, range, catalogSelection?.catalogId); catalogSelection = null; addVehicle = false },
                    onBack = { addVehicle = false }
                )
                selectCatalogVehicle -> VehicleCatalogScreen(
                    state.catalogVehicles,
                    { selected -> catalogSelection = selected; selectCatalogVehicle = false; addVehicle = true },
                    { catalogSelection = null; selectCatalogVehicle = false; addVehicle = true },
                    { selectCatalogVehicle = false }
                )
                bluetoothPrompt -> BluetoothPromptScreen(state.bluetoothSettings, state.pairedBluetoothDevices, viewModel::saveBluetoothPrompt) { bluetoothPrompt = false }
                else -> when (tab) {
                    0 -> DashboardScreen(state, { addRecord = true }, viewModel::selectVehicle)
                    1 -> RecordsScreen(state.chargingRecords, viewModel::deleteChargingRecord, { addRecord = true }, { editingRecord = it })
                    2 -> StatsScreen(state)
                    3 -> {
                        if (state.activeTrip == null && state.selectedTripId == null) {
                            TripReadyScreen(
                                vehicle = state.vehicle,
                                currentSoc = state.currentSoc,
                                currentMileageKm = state.currentMileageKm,
                                recentTrips = state.trips.filter { it.status == TripStatus.COMPLETED },
                                onStart = ::startTripWithPermission,
                                onOpenDetail = viewModel::openTripDetail
                            )
                        } else {
                            TripScreen(
                                vehicle = state.vehicle,
                                vehicles = state.vehicles,
                                trips = state.trips,
                                activeTrip = state.activeTrip,
                                selectedTripId = state.selectedTripId,
                                selectedTripPoints = state.selectedTripPoints,
                                onStart = ::startTripWithPermission,
                                onResume = ::resumeTripWithPermission,
                                onStop = ::requestTripCompletion,
                                onOpenDetail = viewModel::openTripDetail,
                                onCloseDetail = viewModel::closeTripDetail,
                                onDelete = viewModel::deleteTrip
                            )
                        }
                    }
                    4 -> VehicleScreen(
                        vehicle = state.vehicle,
                        vehicles = state.vehicles,
                        currentSoc = state.currentSoc,
                        currentMileageKm = state.currentMileageKm,
                        onSelect = viewModel::selectVehicle,
                        onAdd = { selectCatalogVehicle = true },
                        onEdit = { editVehicle = true },
                        onArchive = { vehicle -> viewModel.archiveVehicle(vehicle.id) },
                        onBluetoothPrompt = {
                            if (Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                    openBluetoothAfterNotificationPermission = true
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.refreshPairedBluetoothDevices()
                                    bluetoothPrompt = true
                                }
                            } else bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        },
                        onExportBackup = {
                            viewModel.exportBackup(BuildConfig.VERSION_NAME) { content ->
                                pendingExportContent = content
                                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                createBackupLauncher.launch("ev-charge-book-backup-$date.json")
                            }
                        },
                        onExportCsv = {
                            pendingCsvContent = ChargingCsvExporter.encode(state.chargingRecords)
                            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                            createCsvLauncher.launch("ev-charge-book-analysis-$date.csv")
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
            text = { Text("恢复备份会删除当前车辆、充电记录和行程数据，再写入备份内容。此操作不可撤销，建议先导出当前备份。") },
            confirmButton = { TextButton(onClick = { pendingRestoreContent = null; viewModel.restoreBackup(content) }) { Text("确认恢复", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { pendingRestoreContent = null }) { Text("取消") } }
        )
    }

    if (showTripCompletion) {
        val active = state.activeTrip
        if (active == null) {
            showTripCompletion = false
        } else {
            val tripVehicle = state.vehicles.firstOrNull { it.id == active.vehicleId } ?: state.vehicle
            val endSoc = tripEndSocText.toIntOrNull()
            val estimate = TripEnergyCalculator.estimate(
                batteryCapacityKwh = tripVehicle?.batteryCapacityKwh,
                startSoc = active.startSoc,
                endSoc = endSoc,
                distanceMeters = active.distanceMeters
            )
            AlertDialog(
                onDismissRequest = { showTripCompletion = false },
                title = { Text("完成本次行程") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "开始 SOC ${active.startSoc?.let { "$it%" } ?: "未知"} · GPS 距离 ${String.format(Locale.US, "%.1f", active.distanceMeters / 1000.0)} km",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = tripEndSocText,
                            onValueChange = { tripEndSocText = it.filter(Char::isDigit).take(3); tripCompletionError = null },
                            label = { Text("结束 SOC") },
                            suffix = { Text("%") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = tripEndMileageText,
                            onValueChange = { tripEndMileageText = it; tripCompletionError = null },
                            label = { Text("结束总里程") },
                            suffix = { Text("km") },
                            supportingText = { Text("已按开始里程 + GPS 距离预填，可修改；留空则保存时自动估算") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (estimate.consumedEnergyKwh != null) {
                            HorizontalDivider()
                            Text(
                                "SOC ${active.startSoc}% → ${endSoc}% · 估算消耗 ${String.format(Locale.US, "%.1f", estimate.consumedEnergyKwh)} kWh",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                estimate.averageConsumptionKwhPer100Km?.let { "估算平均能耗 ${String.format(Locale.US, "%.1f", it)} kWh/100km" }
                                    ?: "距离不足，暂不能计算平均能耗",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (active.startSoc != null && endSoc != null && endSoc in 0..100) {
                            HorizontalDivider()
                            Text(
                                if (endSoc >= active.startSoc) {
                                    "SOC 未下降或出现回升；可能来自取整、回收制动或补能，本次不强行计算行驶能耗。"
                                } else {
                                    "当前数据不足以形成可靠的 SOC 能耗估算。"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        tripCompletionError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val parsedSoc = tripEndSocText.toIntOrNull()
                        val parsedMileage = tripEndMileageText.toDoubleOrNull()
                        tripCompletionError = when {
                            parsedSoc == null || parsedSoc !in 0..100 -> "请输入 0~100 的结束 SOC"
                            tripEndMileageText.isNotBlank() && (parsedMileage == null || parsedMileage < 0.0) -> "请输入有效的结束总里程"
                            active.startMileageKm != null && parsedMileage != null && parsedMileage < active.startMileageKm -> "结束里程不能低于开始里程"
                            else -> null
                        }
                        if (tripCompletionError == null) {
                            showTripCompletion = false
                            viewModel.stopTrip(parsedSoc!!, parsedMileage)
                        }
                    }) { Text("保存并结束") }
                },
                dismissButton = { TextButton(onClick = { showTripCompletion = false }) { Text("继续行驶") } }
            )
        }
    }
}
