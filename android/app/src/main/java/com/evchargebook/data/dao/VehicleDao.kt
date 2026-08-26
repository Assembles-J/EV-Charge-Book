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
    @Query("SELECT * FROM vehicles ORDER BY id LIMIT 1")
    fun observePrimaryVehicle(): Flow<VehicleEntity?>

    @Query("SELECT * FROM vehicles ORDER BY id")
    suspend fun getAll(): List<VehicleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vehicle: VehicleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vehicles: List<VehicleEntity>)

    @Update
    suspend fun update(vehicle: VehicleEntity)

    @Query("DELETE FROM vehicles")
    suspend fun deleteAll()
}
