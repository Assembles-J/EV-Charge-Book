package com.evchargebook.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.evchargebook.data.entity.VehicleCatalogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleCatalogDao {
    @Query("SELECT * FROM vehicle_catalog ORDER BY brand, series, modelYear DESC, trimName")
    fun observeAll(): Flow<List<VehicleCatalogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<VehicleCatalogEntity>)
}
