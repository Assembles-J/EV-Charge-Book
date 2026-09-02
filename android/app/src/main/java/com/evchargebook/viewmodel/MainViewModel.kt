package com.evchargebook.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.evchargebook.bluetooth.BluetoothPromptSettings
import com.evchargebook.bluetooth.PairedBluetoothDevice
import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.data.entity.ChargingSessionEntity
import com.evchargebook.data.entity.TripPointEntity
import com.evchargebook.data.entity.TripSessionEntity
import com.evchargebook.data.entity.VehicleCatalogEntity
import com.evchargebook.data.entity.VehicleEntity
import com.evchargebook.data.repository.BackfillChargingSessionRequest
import com.evchargebook.data.repository.ChargingRepository
import com.evchargebook.data.repository.CompleteChargingSessionRequest
import com.evchargebook.data.repository.DeferChargingCompletionRequest
import com.evchargebook.data.repository.StartChargingSessionRequest
import com.evchargebook.domain.ChargerCategorySummary
import com.evchargebook.domain.ChargerTypeAnalytics
import com.evchargebook.domain.ChargingIntervalAnalytics
import com.evchargebook.domain.ChargingIntervalSample
import com.evchargebook.domain.ChargingPlaceAnalytics
import com.evchargebook.domain.ChargingPlaceSummary
import com.evchargebook.domain.ChargingStatistics
import com.evchargebook.domain.ChargingTripCoverage
import com.evchargebook.domain.ChargingTripCoverageInterval
import com.evchargebook.domain.MonthlyChargingBucket
import com.evchargebook.domain.MonthlyChargingTrend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class MainUiState(
    val vehicle: VehicleEntity? = null,
    val currentSoc: Int? = null,
    val currentSocUpdatedAtEpochMillis: Long? = null,
    val currentSocUpdateSource: String? = null,
    val currentMileageKm: Double? = null,
    val vehicles: List<VehicleEntity> = emptyList(),
    val catalogVehicles: List<VehicleCatalogEntity> = emptyList(),
    val bluetoothSettings: BluetoothPromptSettings = BluetoothPromptSettings(),
    val pairedBluetoothDevices: List<PairedBluetoothDevice> = emptyList(),
    val chargingRecords: List<ChargingRecordEntity> = emptyList(),
    val activeChargingSession: ChargingSessionEntity? = null,
    val pendingChargingSessions: List<ChargingSessionEntity> = emptyList(),
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
    val intervalSamples: List<ChargingIntervalSample> = emptyList(),
    val tripCoverageIntervalCount: Int = 0,
    val tripCoverageOdometerKm: Double = 0.0,
    val tripCoverageDistanceKm: Double = 0.0,
    val tripCoverageRatio: Double? = null,
    val tripCoverageIntervals: List<ChargingTripCoverageInterval> = emptyList(),
    val monthlyTrend: List<MonthlyChargingBucket> = emptyList(),
    val chargerTypeSummary: List<ChargerCategorySummary> = emptyList(),
    val chargingPlaceSummary: List<ChargingPlaceSummary> = emptyList(),
    val successMessage: String? = null,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(private val repository: ChargingRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    private val selectedTripId = MutableStateFlow<Long?>(null)

    init {
        viewModelScope.launch { repository.ensureDefaultVehicle() }
        viewModelScope.launch { repository.bluetoothSettings.collect { settings -> _uiState.value = _uiState.value.copy(bluetoothSettings = settings) } }
        viewModelScope.launch { repository.activeTrip.collect { trip -> _uiState.value = _uiState.value.copy(activeTrip = trip) } }
        viewModelScope.launch { repository.activeChargingSession.collect { session -> _uiState.value = _uiState.value.copy(activeChargingSession = session) } }
        viewModelScope.launch { repository.pendingChargingSessions.collect { sessions -> _uiState.value = _uiState.value.copy(pendingChargingSessions = sessions) } }
        viewModelScope.launch {
            repository.vehicleState.collect { vehicleState ->
                _uiState.value = _uiState.value.copy(
                    currentSoc = vehicleState?.currentSoc,
                    currentSocUpdatedAtEpochMillis = vehicleState?.updatedAtEpochMillis,
                    currentSocUpdateSource = vehicleState?.updateSource,
                    currentMileageKm = vehicleState?.currentMileage
                )
            }
        }
        viewModelScope.launch {
            combine(selectedTripId, repository.activeTrip) { selectedId, activeTrip ->
                selectedId ?: activeTrip?.id
            }.flatMapLatest { tripId ->
                tripId?.let { repository.observeTripPoints(it) } ?: flowOf(emptyList())
            }.collect { points ->
                _uiState.value = _uiState.value.copy(
                    selectedTripId = selectedTripId.value,
                    selectedTripPoints = points
                )
            }
        }
        viewModelScope.launch {
            combine(repository.vehicle, repository.vehicles, repository.catalogVehicles, repository.chargingRecords, repository.trips) { vehicle, vehicles, catalogVehicles, records, trips ->
                val zoneId = ZoneId.systemDefault()
                val now = Instant.now().atZone(zoneId)
                val month = YearMonth.from(now)
                val start = month.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                val end = month.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                val summary = ChargingStatistics.summarize(records, start, end)
                val intervals = ChargingIntervalAnalytics.summarize(records)
                val coverage = ChargingTripCoverage.summarize(records, trips)
                val monthlyTrend = MonthlyChargingTrend.summarize(records, month, zoneId, monthCount = 6)
                val chargerTypes = ChargerTypeAnalytics.summarize(records)
                val chargingPlaces = ChargingPlaceAnalytics.summarize(records)
                _uiState.value.copy(
                    vehicle = vehicle,
                    vehicles = vehicles,
                    catalogVehicles = catalogVehicles,
                    chargingRecords = records,
                    trips = trips,
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
                    intervalCostPer100Km = intervals.costPer100Km,
                    intervalSamples = intervals.samples,
                    tripCoverageIntervalCount = coverage.intervals.size,
                    tripCoverageOdometerKm = coverage.odometerDistanceKm,
                    tripCoverageDistanceKm = coverage.completedTripDistanceKm,
                    tripCoverageRatio = coverage.coverageRatio,
                    tripCoverageIntervals = coverage.intervals,
                    monthlyTrend = monthlyTrend,
                    chargerTypeSummary = chargerTypes,
                    chargingPlaceSummary = chargingPlaces
                )
            }.collect { _uiState.value = it }
        }
    }

    fun exportBackup(appVersion: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.exportBackup(appVersion) }
                .onSuccess { content ->
                    when {
                        _uiState.value.activeTrip != null -> _uiState.value = _uiState.value.copy(successMessage = "已生成备份；当前行程仍在进行，这是进行中快照")
                        _uiState.value.activeChargingSession != null -> _uiState.value = _uiState.value.copy(successMessage = "已生成备份；当前充电会话已包含在快照中")
                    }
                    onReady(content)
                }
                .onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message ?: "备份导出失败") }
        }
    }

    fun restoreBackup(content: String) {
        if (
            _uiState.value.activeTrip != null ||
            _uiState.value.activeChargingSession != null ||
            _uiState.value.pendingChargingSessions.isNotEmpty()
        ) {
            _uiState.value = _uiState.value.copy(errorMessage = "有进行中或待补录的行程/充电，请处理后再恢复备份")
            return
        }
        viewModelScope.launch {
            runCatching { repository.restoreBackup(content) }
                .onSuccess { _uiState.value = _uiState.value.copy(successMessage = "本地备份已恢复") }
                .onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message ?: "备份恢复失败") }
        }
    }

    fun startChargingSession(request: StartChargingSessionRequest) {
        viewModelScope.launch {
            runCatching { repository.startChargingSession(request) }
                .onSuccess { _uiState.value = _uiState.value.copy(successMessage = "充电已开始记录") }
                .onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message ?: "无法开始充电") }
        }
    }

    fun updateActiveChargingSession(session: ChargingSessionEntity) {
        viewModelScope.launch {
            runCatching { repository.updateActiveChargingSession(session) }
                .onSuccess { _uiState.value = _uiState.value.copy(successMessage = "充电信息已更新") }
                .onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message ?: "无法更新充电信息") }
        }
    }

    fun cancelChargingSession(sessionId: String) {
        viewModelScope.launch {
            runCatching { repository.cancelChargingSession(sessionId) }
                .onSuccess { _uiState.value = _uiState.value.copy(successMessage = "已取消本次充电记录") }
                .onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message ?: "无法取消充电") }
        }
    }

    suspend fun completeChargingSession(request: CompleteChargingSessionRequest): Long =
        runChargingCommand(
            successMessage = "充电已完成并写入账本",
            fallbackError = "无法结束充电",
        ) { repository.completeChargingSession(request) }

    suspend fun deferChargingCompletion(request: DeferChargingCompletionRequest) {
        runChargingCommand(
            successMessage = "充电已结束，等待补充电表数据",
            fallbackError = "无法结束充电",
        ) { repository.deferChargingCompletion(request) }
    }

    suspend fun updatePendingChargingDetails(request: DeferChargingCompletionRequest) {
        runChargingCommand(
            successMessage = "结束信息已更新",
            fallbackError = "无法更新结束信息",
        ) { repository.deferChargingCompletion(request) }
    }

    suspend fun backfillChargingSession(request: BackfillChargingSessionRequest): Long =
        runChargingCommand(
            successMessage = "电表数据已补录，充电账本已完成",
            fallbackError = "无法补充电表数据",
        ) { repository.backfillChargingSession(request) }

    suspend fun discardPendingChargingSession(sessionId: String) {
        runChargingCommand(
            successMessage = "已删除待补录充电",
            fallbackError = "无法删除待补录充电",
        ) { repository.discardPendingChargingSession(sessionId) }
    }

    private suspend fun <T> runChargingCommand(
        successMessage: String,
        fallbackError: String,
        block: suspend () -> T,
    ): T {
        return try {
            val result = block()
            _uiState.value = _uiState.value.copy(successMessage = successMessage, errorMessage = null)
            result
        } catch (error: Throwable) {
            _uiState.value = _uiState.value.copy(
                successMessage = null,
                errorMessage = error.message ?: fallbackError,
            )
            throw error
        }
    }

    fun startTrip() { val vehicleId = _uiState.value.vehicle?.id ?: return; viewModelScope.launch { runCatching { repository.startTrip(vehicleId) }.onSuccess { _uiState.value = _uiState.value.copy(successMessage = "行程已开始") }.onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message ?: "无法开始行程") } } }
    fun resumeTrip(tripId: Long) { viewModelScope.launch { runCatching { repository.resumeTrip(tripId) }.onSuccess { _uiState.value = _uiState.value.copy(successMessage = "行程记录已恢复") }.onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message ?: "无法恢复行程") } } }
    fun stopTrip(startSoc: Int, endSoc: Int, endMileageKm: Double?) { viewModelScope.launch { runCatching { repository.stopActiveTrip(startSoc, endSoc, endMileageKm) }.onSuccess { _uiState.value = _uiState.value.copy(successMessage = "行程已结束，车辆状态已更新") }.onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message ?: "无法结束行程") } } }
    fun openTripDetail(tripId: Long) { selectedTripId.value = tripId }
    fun closeTripDetail() { selectedTripId.value = null; _uiState.value = _uiState.value.copy(selectedTripId = null, selectedTripPoints = emptyList()) }
    fun deleteTrip(trip: TripSessionEntity) { viewModelScope.launch { runCatching { repository.deleteTrip(trip) }.onSuccess { if (selectedTripId.value == trip.id) closeTripDetail() }.onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message ?: "无法删除行程") } } }

    fun saveVehicleNickname(nickname: String?) {
        val current = _uiState.value.vehicle ?: return
        val normalized = nickname?.trim()?.takeIf { it.isNotEmpty() }
        viewModelScope.launch {
            runCatching { repository.saveVehicle(current.copy(nickname = normalized)) }
                .onSuccess { _uiState.value = _uiState.value.copy(successMessage = "车辆名称已保存") }
                .onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message ?: "无法保存车辆名称") }
        }
    }

    fun addVehicle(catalogVehicle: VehicleCatalogEntity, nickname: String?) {
        val normalized = nickname?.trim()?.takeIf { it.isNotEmpty() }
        viewModelScope.launch {
            runCatching {
                require(catalogVehicle.isActive) { "该车型已下架，请刷新车型库后重试" }
                val battery = catalogVehicle.batteryCapacityKwh ?: error("车型标准电池参数缺失，请先在 Web 管理端补全")
                val range = catalogVehicle.rangeKm ?: error("车型标准续航参数缺失，请先在 Web 管理端补全")
                repository.addVehicle(
                    brand = catalogVehicle.brand,
                    model = catalogVehicle.modelName,
                    battery = battery,
                    range = range,
                    catalogVehicleId = catalogVehicle.catalogId
                )
                if (normalized != null) {
                    val addedVehicle = repository.vehicles.first().maxByOrNull { it.id }
                        ?: error("车辆添加后未找到本地车辆记录")
                    repository.saveVehicle(addedVehicle.copy(nickname = normalized))
                }
            }
                .onSuccess { _uiState.value = _uiState.value.copy(successMessage = "车辆已添加并切换") }
                .onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message ?: "无法添加车辆") }
        }
    }

    fun selectVehicle(vehicleId: Long) {
        val activeTrip = _uiState.value.activeTrip
        val activeCharging = _uiState.value.activeChargingSession
        viewModelScope.launch {
            runCatching { repository.selectVehicle(vehicleId) }
                .onSuccess {
                    when {
                        activeTrip != null && activeTrip.vehicleId != vehicleId -> _uiState.value = _uiState.value.copy(successMessage = "已切换当前车辆；正在进行的行程仍归属原车辆")
                        activeCharging != null && activeCharging.vehicleId != vehicleId -> _uiState.value = _uiState.value.copy(successMessage = "已切换当前车辆；正在进行的充电仍归属原车辆")
                    }
                }
                .onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message) }
        }
    }

    fun archiveVehicle(vehicleId: Long) {
        if (_uiState.value.activeTrip?.vehicleId == vehicleId || _uiState.value.activeChargingSession?.vehicleId == vehicleId) {
            _uiState.value = _uiState.value.copy(errorMessage = "该车辆有进行中的行程或充电，请结束后再归档")
            return
        }
        viewModelScope.launch {
            runCatching { repository.archiveVehicle(vehicleId) }
                .onSuccess { _uiState.value = _uiState.value.copy(successMessage = "车辆已归档，历史记录仍保留") }
                .onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message ?: "无法归档车辆") }
        }
    }

    fun refreshPairedBluetoothDevices() { _uiState.value = _uiState.value.copy(pairedBluetoothDevices = repository.pairedBluetoothDevices()) }
    fun saveBluetoothPrompt(enabled: Boolean, address: String?, name: String?) { viewModelScope.launch { repository.saveBluetoothPrompt(enabled, address, name) } }
    fun addChargingRecord(startSoc: Int, endSoc: Int, energyKwh: Double, cost: Double, location: String?, chargerType: String?, remark: String?, chargeTime: Long, odometerKm: Double?, latitude: Double?, longitude: Double?, locationAccuracyMeters: Double?) {
        val id = _uiState.value.vehicle?.id ?: return
        viewModelScope.launch { runCatching { repository.addChargingRecord(id, startSoc, endSoc, energyKwh, cost, location, chargerType, remark, chargeTime, odometerKm, latitude, longitude, locationAccuracyMeters) }.onSuccess { _uiState.value = _uiState.value.copy(successMessage = "充电记录已保存") }.onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message) } }
    }
    fun deleteChargingRecord(record: ChargingRecordEntity) { viewModelScope.launch { repository.deleteChargingRecord(record) } }
    fun updateChargingRecord(record: ChargingRecordEntity) { viewModelScope.launch { runCatching { repository.updateChargingRecord(record) }.onSuccess { _uiState.value = _uiState.value.copy(successMessage = "充电记录已更新") }.onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message) } } }
    fun clearError() { _uiState.value = _uiState.value.copy(errorMessage = null) }
    fun clearSuccess() { _uiState.value = _uiState.value.copy(successMessage = null) }

    class Factory(private val repository: ChargingRepository): ViewModelProvider.Factory {
        override fun <T:ViewModel> create(modelClass:Class<T>):T = MainViewModel(repository) as T
    }
}
