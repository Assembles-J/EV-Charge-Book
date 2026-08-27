package com.evchargebook.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.evchargebook.data.entity.TripDiagnosticEventEntity
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

    @Query("SELECT * FROM trip_sessions WHERE vehicleId = :vehicleId AND status = 'COMPLETED' AND endedAtEpochMillis IS NOT NULL AND endSoc IS NOT NULL ORDER BY endedAtEpochMillis DESC, id DESC LIMIT 1")
    suspend fun getLatestCompletedWithSocForVehicle(vehicleId: Long): TripSessionEntity?

    @Query("SELECT * FROM trip_sessions WHERE vehicleId = :vehicleId AND status = 'COMPLETED' AND endedAtEpochMillis IS NOT NULL AND endMileageKm IS NOT NULL ORDER BY endedAtEpochMillis DESC, id DESC LIMIT 1")
    suspend fun getLatestCompletedWithMileageForVehicle(vehicleId: Long): TripSessionEntity?

    @Query("SELECT * FROM trip_sessions ORDER BY startedAtEpochMillis")
    suspend fun getAllSessions(): List<TripSessionEntity>

    @Query("SELECT * FROM trip_points ORDER BY tripId, capturedAtEpochMillis")
    suspend fun getAllPoints(): List<TripPointEntity>

    @Query("SELECT * FROM trip_points WHERE tripId = :tripId ORDER BY capturedAtEpochMillis")
    fun observePoints(tripId: Long): Flow<List<TripPointEntity>>

    @Query("SELECT * FROM trip_points WHERE tripId = :tripId ORDER BY capturedAtEpochMillis")
    suspend fun getPoints(tripId: Long): List<TripPointEntity>

    @Query("SELECT * FROM trip_diagnostic_events WHERE tripId = :tripId ORDER BY occurredAtEpochMillis, id")
    fun observeDiagnosticEvents(tripId: Long): Flow<List<TripDiagnosticEventEntity>>

    @Query("SELECT * FROM trip_diagnostic_events WHERE tripId = :tripId ORDER BY occurredAtEpochMillis, id")
    suspend fun getDiagnosticEvents(tripId: Long): List<TripDiagnosticEventEntity>

    @Query("SELECT * FROM trip_diagnostic_events ORDER BY tripId, occurredAtEpochMillis, id")
    suspend fun getAllDiagnosticEvents(): List<TripDiagnosticEventEntity>

    @Insert
    suspend fun insertSession(session: TripSessionEntity): Long

    @Insert
    suspend fun insertSessions(sessions: List<TripSessionEntity>)

    @Update
    suspend fun updateSession(session: TripSessionEntity)

    @Insert
    suspend fun insertPoint(point: TripPointEntity): Long

    @Insert
    suspend fun insertPoints(points: List<TripPointEntity>)

    @Insert
    suspend fun insertDiagnosticEvent(event: TripDiagnosticEventEntity): Long

    @Insert
    suspend fun insertDiagnosticEvents(events: List<TripDiagnosticEventEntity>)

    @Delete
    suspend fun deleteSession(session: TripSessionEntity)

    @Query("DELETE FROM trip_sessions")
    suspend fun deleteAllSessions()
}
