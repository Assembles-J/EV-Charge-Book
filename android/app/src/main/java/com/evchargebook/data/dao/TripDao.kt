package com.evchargebook.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.evchargebook.data.entity.TripPointEntity
import com.evchargebook.data.entity.TripSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trip_sessions WHERE vehicleId = :vehicleId ORDER BY startedAtEpochMillis DESC")
    fun observeForVehicle(vehicleId: Long): Flow<List<TripSessionEntity>>

    @Query("SELECT * FROM trip_sessions WHERE status IN ('RECORDING', 'INTERRUPTED') ORDER BY startedAtEpochMillis DESC LIMIT 1")
    fun observeActive(): Flow<TripSessionEntity?>

    @Query("SELECT * FROM trip_sessions WHERE status IN ('RECORDING', 'INTERRUPTED') ORDER BY startedAtEpochMillis DESC LIMIT 1")
    suspend fun getActive(): TripSessionEntity?

    @Query("SELECT * FROM trip_sessions WHERE id = :tripId LIMIT 1")
    suspend fun getSession(tripId: Long): TripSessionEntity?

    @Query("SELECT * FROM trip_points WHERE tripId = :tripId ORDER BY capturedAtEpochMillis")
    fun observePoints(tripId: Long): Flow<List<TripPointEntity>>

    @Query("SELECT * FROM trip_points WHERE tripId = :tripId ORDER BY capturedAtEpochMillis")
    suspend fun getPoints(tripId: Long): List<TripPointEntity>

    @Insert
    suspend fun insertSession(session: TripSessionEntity): Long

    @Update
    suspend fun updateSession(session: TripSessionEntity)

    @Insert
    suspend fun insertPoint(point: TripPointEntity): Long

    @Delete
    suspend fun deleteSession(session: TripSessionEntity)
}
