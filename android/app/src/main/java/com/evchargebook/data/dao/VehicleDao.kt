package com.evchargebook.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.evchargebook.data.entity.VehicleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles WHERE isArchived = 0 ORDER BY isDefault DESC, createdAtEpochMillis ASC")
    fun observeActive(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles ORDER BY id")
    suspend fun getAll(): List<VehicleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vehicle: VehicleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vehicles: List<VehicleEntity>)

    @Update
    suspend fun update(vehicle: VehicleEntity)

    @Query("UPDATE vehicles SET isDefault = CASE WHEN id = :vehicleId THEN 1 ELSE 0 END")
    suspend fun setDefault(vehicleId: Long)

    @Query("DELETE FROM vehicles")
    suspend fun deleteAll()
}
