package com.evchargebook.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.evchargebook.bluetooth.BluetoothPromptSettings
import com.evchargebook.bluetooth.PairedBluetoothDevice
import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.data.entity.TripPointEntity
import com.evchargebook.data.entity.TripSessionEntity
import com.evchargebook.data.entity.VehicleCatalogEntity
import com.evchargebook.data.entity.VehicleEntity
import com.evchargebook.data.repository.ChargingRepository
import com.evchargebook.domain.ChargingIntervalAnalytics
import com.evchargebook.domain.ChargingStatistics
import com.evchargebook.domain.ChargingTripCoverage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class MainUiState(
    val vehicle: VehicleEntity? = null,
    val vehicles: List<VehicleEntity> = emptyList(),
    val catalogVehicles: List<VehicleCatalogEntity> = emptyList(),
    val bluetoothSettings: BluetoothPromptSettings = BluetoothPromptSettings(),
    val pairedBluetoothDevices: List<PairedBluetoothDevice> = emptyList(),
    val chargingRecords: List<ChargingRecordEntity> = emptyList(),
    val trips: List<TripSessionEntity> = emptyList(),
    val activeTrip: TripSessionEntity? = null,
    val selectedTripId: Long? = null,
    val selectedTripPoints: List<TripPointEntity> = emptyList(),
    val monthCost: Double = 0.0,
    val monthEnergy: Double = 0.0,
    val averagePrice: Double = 0.0,
    val chargingCount: Int = 0,
    val totalCost: Double = 0.0,
    val totalEnergy: Double = 0.0,
    val intervalSampleCount: Int = 0,
    val invalidIntervalCount: Int = 0,
    val intervalDistanceKm: Double = 0.0,
    val intervalEnergyPer100Km: Double? = null,
    val intervalCostPer100Km: Double? = null,
    val tripCoverageIntervalCount: Int = 0,
    val tripCoverageOdometerKm: Double = 0.0,
    val tripCoverageDistanceKm: Double = 0.0,
    val tripCoverageRatio: Double? = null,
    val successMessage: String? = null,
    val errorMessage: String? = null
)

class MainViewModel(private val repository: ChargingRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    private val selectedTripId = MutableStateFlow<Long?>(null)

    init {
        viewModelScope.launch { repository.ensureDefaultVehicle() }
        viewModelScope.launch { repository.bluetoothSettings.collect { settings -> _uiState.value = _uiState.value.copy(bluetoothSettings = settings) } }
        viewModelScope.launch { repository.trips.collect { trips -> _uiState.value = _uiState.value.copy(trips = trips) } }
        viewModelScope.launch { repository.activeTrip.collect { trip -> _uiState.value = _uiState.value.copy(activeTrip = trip) } }
        viewModelScope.launch {
            combine(repository.chargingRecords, repository.trips) { records, trips ->
                ChargingTripCoverage.summarize(records, trips)
            }.collect { coverage ->
                _uiState.value = _uiState.value.copy(
                    tripCoverageIntervalCount = coverage.intervals.size,
                    tripCoverageOdometerKm = coverage.odometerDistanceKm,
                    tripCoverageDistanceKm = coverage.completedTripDistanceKm,
                    tripCoverageRatio = coverage.coverageRatio
                )
            }
        }
        viewModelScope.launch {
            selectedTripId.flatMapLatest { tripId ->
                tripId?.let { repository.observeTripPoints(it) } ?: flowOf(emptyList())
            }.collect { points ->
                _uiState.value = _uiState.value.copy(selectedTripId = selectedTripId.value, selectedTripPoints = points)
            }
        }
        viewModelScope.launch {
            combine(repository.vehicle, repository.vehicles, repository.catalogVehicles, repository.chargingRecords) { vehicle, vehicles, catalogVehicles, records ->
                val now = Instant.now().atZone(ZoneId.systemDefault())
                val month = YearMonth.from(now)
                val start = month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val end = month.plusMonths(1).atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val summary = ChargingStatistics.summarize(records, start, end)
                val intervals = ChargingIntervalAnalytics.summarize(records)
                _uiState.value.copy(
                    vehicle = vehicle,
                    vehicles = vehicles,
                    catalogVehicles = catalogVehicles,
                    chargingRecords = records,
                    monthCost = summary.monthCost,
                    monthEnergy = summary.monthEnergy,
                    averagePrice = summary.averagePrice,
                    chargingCount = summary.chargingCount,
                    totalCost = summary.totalCost,
                    totalEnergy = summary.totalEnergy,
                    intervalSampleCount = intervals.samples.size,
                    invalidIntervalCount = intervals.invalidIntervalCount,
                    intervalDistanceKm = intervals.totalDistanceKm,
                    intervalEnergyPer100Km = intervals.energyPer100Km,
                    intervalCostPer100Km = intervals.costPer100Km
                )
            }.collect { _uiState.value = it }
        }
    }

    fun exportBackup(appVersion: String, onReady: (String) -> Unit) { viewModelScope.launch { runCatching { repository.exportBackup(appVersion) }.onSuccess(onReady).onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message ?: "备份导出失败") } } }
    fun restoreBackup(content: String) { viewModelScope.launch { runCatching { repository.restoreBackup(content) }.onSuccess { _uiState.value = _uiState.value.copy(successMessage = "本地备份已恢复") }.onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message ?: "备份恢复失败") } } }

    fun startTrip() {
        val vehicleId = _uiState.value.vehicle?.id ?: return
        viewModelScope.launch {
            runCatching { repository.startTrip(vehicleId) }
                .onSuccess { _uiState.value = _uiState.value.copy(successMessage = "行程已开始") }
                .onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message ?: "无法开始行程") }
        }
    }

    fun resumeTrip(tripId: Long) {
        viewModelScope.launch {
            runCatching { repository.resumeTrip(tripId) }
                .onSuccess { _uiState.value = _uiState.value.copy(successMessage = "行程记录已恢复") }
                .onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message ?: "无法恢复行程") }
        }
    }

    fun stopTrip() {
        viewModelScope.launch {
            runCatching { repository.stopActiveTrip() }
                .onSuccess { _uiState.value = _uiState.value.copy(successMessage = "行程已结束") }
                .onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message ?: "无法结束行程") }
        }
    }

    fun openTripDetail(tripId: Long) { selectedTripId.value = tripId }
    fun closeTripDetail() { selectedTripId.value = null; _uiState.value = _uiState.value.copy(selectedTripId = null, selectedTripPoints = emptyList()) }

    fun deleteTrip(trip: TripSessionEntity) {
        viewModelScope.launch {
            runCatching { repository.deleteTrip(trip) }
                .onSuccess { if (selectedTripId.value == trip.id) closeTripDetail() }
                .onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message ?: "无法删除行程") }
        }
    }

    fun saveVehicle(brand: String, model: String, battery: Double, range: Int) {
        val current = _uiState.value.vehicle ?: return
        viewModelScope.launch { runCatching { repository.saveVehicle(current.copy(brand = brand.trim(), model = model.trim(), batteryCapacityKwh = battery, rangeKm = range)) }.onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message) } }
    }

    fun addVehicle(brand: String, model: String, battery: Double, range: Int, catalogVehicleId: String? = null) { viewModelScope.launch { runCatching { repository.addVehicle(brand, model, battery, range, catalogVehicleId) }.onSuccess { _uiState.value = _uiState.value.copy(successMessage = "车辆已添加并切换") }.onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message) } } }
    fun selectVehicle(vehicleId: Long) { viewModelScope.launch { runCatching { repository.selectVehicle(vehicleId) }.onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message) } } }
    fun archiveVehicle(vehicleId: Long) { viewModelScope.launch { runCatching { repository.archiveVehicle(vehicleId) }.onSuccess { _uiState.value = _uiState.value.copy(successMessage = "车辆已归档，历史记录仍保留") }.onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message) } } }
    fun refreshPairedBluetoothDevices() { _uiState.value = _uiState.value.copy(pairedBluetoothDevices = repository.pairedBluetoothDevices()) }
    fun saveBluetoothPrompt(enabled: Boolean, address: String?, name: String?) { viewModelScope.launch { repository.saveBluetoothPrompt(enabled, address, name) } }

    fun addChargingRecord(
        startSoc: Int,
        endSoc: Int,
        energyKwh: Double,
        cost: Double,
        location: String?,
        chargerType: String?,
        remark: String?,
        chargeTime: Long,
        odometerKm: Double?,
        latitude: Double?,
        longitude: Double?,
        locationAccuracyMeters: Double?
    ) {
        val id = _uiState.value.vehicle?.id ?: return
        viewModelScope.launch {
            runCatching {
                repository.addChargingRecord(
                    vehicleId = id,
                    startSoc = startSoc,
                    endSoc = endSoc,
                    energyKwh = energyKwh,
                    cost = cost,
                    location = location,
                    chargerType = chargerType,
                    remark = remark,
                    chargeTimeEpochMillis = chargeTime,
                    odometerKm = odometerKm,
                    latitude = latitude,
                    longitude = longitude,
                    locationAccuracyMeters = locationAccuracyMeters
                )
            }.onSuccess { _uiState.value = _uiState.value.copy(successMessage = "充电记录已保存") }
                .onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message) }
        }
    }

    fun deleteChargingRecord(record: ChargingRecordEntity) { viewModelScope.launch { repository.deleteChargingRecord(record) } }
    fun updateChargingRecord(record: ChargingRecordEntity) { viewModelScope.launch { runCatching { repository.updateChargingRecord(record) }.onSuccess { _uiState.value = _uiState.value.copy(successMessage = "充电记录已更新") }.onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message) } } }
    fun clearError() { _uiState.value = _uiState.value.copy(errorMessage = null) }
    fun clearSuccess() { _uiState.value = _uiState.value.copy(successMessage = null) }

    class Factory(private val repository: ChargingRepository): ViewModelProvider.Factory {
        override fun <T:ViewModel> create(modelClass:Class<T>):T = MainViewModel(repository) as T
    }
}
