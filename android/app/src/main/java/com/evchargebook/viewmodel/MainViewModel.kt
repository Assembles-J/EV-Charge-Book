package com.evchargebook.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.data.entity.VehicleEntity
import com.evchargebook.data.repository.ChargingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    val errorMessage: String? = null
)

class MainViewModel(
    private val repository: ChargingRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureDefaultVehicle()
        }
        viewModelScope.launch {
            combine(repository.vehicle, repository.chargingRecords) { vehicle, records ->
                val now = Instant.now().atZone(ZoneId.systemDefault())
                val month = YearMonth.from(now)
                val monthStart = month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val nextMonthStart = month.plusMonths(1).atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val monthRecords = records.filter { it.chargeTimeEpochMillis in monthStart until nextMonthStart }
                val totalCost = records.sumOf { it.cost }
                val totalEnergy = records.sumOf { it.energyKwh }

                MainUiState(
                    vehicle = vehicle,
                    chargingRecords = records,
                    monthCost = monthRecords.sumOf { it.cost },
                    monthEnergy = monthRecords.sumOf { it.energyKwh },
                    averagePrice = if (totalEnergy > 0.0) totalCost / totalEnergy else 0.0,
                    chargingCount = monthRecords.size,
                    totalCost = totalCost,
                    totalEnergy = totalEnergy
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun addChargingRecord(
        startSoc: Int,
        endSoc: Int,
        energyKwh: Double,
        cost: Double,
        location: String?
    ) {
        val vehicleId = _uiState.value.vehicle?.id ?: return
        viewModelScope.launch {
            runCatching {
                repository.addChargingRecord(
                    vehicleId = vehicleId,
                    startSoc = startSoc,
                    endSoc = endSoc,
                    energyKwh = energyKwh,
                    cost = cost,
                    location = location
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(errorMessage = error.message)
            }
        }
    }

    fun deleteChargingRecord(record: ChargingRecordEntity) {
        viewModelScope.launch {
            repository.deleteChargingRecord(record)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    class Factory(
        private val repository: ChargingRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
