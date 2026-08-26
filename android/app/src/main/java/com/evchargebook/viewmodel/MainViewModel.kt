package com.evchargebook.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.data.entity.VehicleEntity
import com.evchargebook.data.repository.ChargingRepository
import com.evchargebook.domain.ChargingStatistics
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.YearMonth

data class MainUiState(
    val vehicle: VehicleEntity? = null,
    val chargingRecords: List<ChargingRecordEntity> = emptyList(),
    val monthCost: Double = 0.0,
    val monthEnergy: Double = 0.0,
    val averagePrice: Double = 0.0,
    val chargingCount: Int = 0,
    val totalCost: Double = 0.0,
    val totalEnergy: Double = 0.0,
    val successMessage: String? = null,
    val errorMessage: String? = null
)

class MainViewModel(private val repository: ChargingRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { repository.ensureDefaultVehicle() }
        viewModelScope.launch {
            combine(repository.vehicle, repository.chargingRecords) { vehicle, records ->
                val now = Instant.now().atZone(ZoneId.systemDefault())
                val month = YearMonth.from(now)
                val start = month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val end = month.plusMonths(1).atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val summary = ChargingStatistics.summarize(records, start, end)
                MainUiState(
                    vehicle = vehicle,
                    chargingRecords = records,
                    monthCost = summary.monthCost,
                    monthEnergy = summary.monthEnergy,
                    averagePrice = summary.averagePrice,
                    chargingCount = summary.chargingCount,
                    totalCost = summary.totalCost,
                    totalEnergy = summary.totalEnergy
                )
            }.collect { _uiState.value = it }
        }
    }

    fun saveVehicle(brand: String, model: String, battery: Double, range: Int) {
        val current = _uiState.value.vehicle ?: return
        viewModelScope.launch {
            runCatching {
                repository.saveVehicle(current.copy(
                    brand = brand.trim(),
                    model = model.trim(),
                    batteryCapacityKwh = battery,
                    rangeKm = range
                ))
            }.onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message) }
        }
    }

    fun addChargingRecord(startSoc:Int,endSoc:Int,energyKwh:Double,cost:Double,location:String?,chargerType:String?,remark:String?,chargeTime:Long) {
        val id = _uiState.value.vehicle?.id ?: return
        viewModelScope.launch {
            runCatching { repository.addChargingRecord(id,startSoc,endSoc,energyKwh,cost,location,chargerType,remark,chargeTime) }
                .onSuccess { _uiState.value = _uiState.value.copy(successMessage = "充电记录已保存") }
                .onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message) }
        }
    }

    fun deleteChargingRecord(record: ChargingRecordEntity) { viewModelScope.launch { repository.deleteChargingRecord(record) } }
    fun updateChargingRecord(record: ChargingRecordEntity) {
        viewModelScope.launch {
            runCatching { repository.updateChargingRecord(record) }
                .onSuccess { _uiState.value = _uiState.value.copy(successMessage = "充电记录已更新") }
                .onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message) }
        }
    }
    fun clearError() { _uiState.value = _uiState.value.copy(errorMessage = null) }
    fun clearSuccess() { _uiState.value = _uiState.value.copy(successMessage = null) }

    class Factory(private val repository: ChargingRepository): ViewModelProvider.Factory {
        override fun <T:ViewModel> create(modelClass:Class<T>):T {
            return MainViewModel(repository) as T
        }
    }
}
