package com.evchargebook.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.evchargebook.data.entity.ChargingRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChargingRecordDao {
    @Query("SELECT * FROM charging_records WHERE isDeleted = 0 ORDER BY chargeTimeEpochMillis DESC")
    fun observeAll(): Flow<List<ChargingRecordEntity>>

    @Query("SELECT * FROM charging_records WHERE vehicleId = :vehicleId AND isDeleted = 0 ORDER BY chargeTimeEpochMillis DESC")
    fun observeForVehicle(vehicleId: Long): Flow<List<ChargingRecordEntity>>

    @Query("SELECT * FROM charging_records WHERE vehicleId = :vehicleId AND isDeleted = 0 ORDER BY chargeTimeEpochMillis DESC, id DESC LIMIT 1")
    suspend fun getLatestForVehicle(vehicleId: Long): ChargingRecordEntity?

    @Query("SELECT * FROM charging_records WHERE vehicleId = :vehicleId AND isDeleted = 0 AND odometerKm IS NOT NULL ORDER BY chargeTimeEpochMillis DESC, id DESC LIMIT 1")
    suspend fun getLatestWithOdometerForVehicle(vehicleId: Long): ChargingRecordEntity?

    @Query("SELECT * FROM charging_records ORDER BY id")
    suspend fun getAll(): List<ChargingRecordEntity>

    @Insert
    suspend fun insert(record: ChargingRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<ChargingRecordEntity>)

    @Update
    suspend fun update(record: ChargingRecordEntity)

    @Query("UPDATE charging_records SET isDeleted = 1, updatedAtEpochMillis = :updatedAtEpochMillis WHERE id = :recordId")
    suspend fun markDeleted(recordId: Long, updatedAtEpochMillis: Long)

    @Query("DELETE FROM charging_records")
    suspend fun deleteAll()

    @Query("SELECT COALESCE(SUM(cost), 0) FROM charging_records WHERE isDeleted = 0 AND chargeTimeEpochMillis >= :start AND chargeTimeEpochMillis < :end")
    fun observeCostBetween(start: Long, end: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(energyKwh), 0) FROM charging_records WHERE isDeleted = 0 AND chargeTimeEpochMillis >= :start AND chargeTimeEpochMillis < :end")
    fun observeEnergyBetween(start: Long, end: Long): Flow<Double>
}
