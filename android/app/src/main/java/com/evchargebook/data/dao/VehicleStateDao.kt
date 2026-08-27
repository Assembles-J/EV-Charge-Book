package com.evchargebook.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.evchargebook.data.entity.VehicleStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleStateDao {

    @Query("SELECT * FROM vehicle_state WHERE vehicleId = :vehicleId")
    fun observe(vehicleId: Long): Flow<VehicleStateEntity?>

    @Query("SELECT * FROM vehicle_state WHERE vehicleId = :vehicleId")
    suspend fun get(vehicleId: Long): VehicleStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: VehicleStateEntity)
}
