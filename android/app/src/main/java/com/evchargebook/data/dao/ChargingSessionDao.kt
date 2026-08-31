package com.evchargebook.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.evchargebook.data.entity.ChargingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChargingSessionDao {
    @Query("SELECT * FROM charging_sessions WHERE vehicleId = :vehicleId AND status = 'ACTIVE' ORDER BY startedAtEpochMillis DESC LIMIT 1")
    fun observeActiveForVehicle(vehicleId: Long): Flow<ChargingSessionEntity?>

    @Query("SELECT * FROM charging_sessions WHERE vehicleId = :vehicleId AND status = 'ACTIVE' ORDER BY startedAtEpochMillis DESC LIMIT 1")
    suspend fun getActiveForVehicle(vehicleId: Long): ChargingSessionEntity?

    @Query("SELECT * FROM charging_sessions WHERE status = 'ACTIVE' ORDER BY startedAtEpochMillis DESC LIMIT 1")
    suspend fun getAnyActive(): ChargingSessionEntity?

    @Query("SELECT * FROM charging_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun get(sessionId: String): ChargingSessionEntity?

    @Query("SELECT * FROM charging_sessions ORDER BY startedAtEpochMillis, id")
    suspend fun getAll(): List<ChargingSessionEntity>

    @Insert
    suspend fun insert(session: ChargingSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<ChargingSessionEntity>)

    @Update
    suspend fun update(session: ChargingSessionEntity)

    @Query("DELETE FROM charging_sessions")
    suspend fun deleteAll()
}
