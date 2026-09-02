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

    @Query("SELECT * FROM charging_sessions WHERE vehicleId = :vehicleId AND status = 'PENDING_DETAILS' ORDER BY endedAtEpochMillis DESC, startedAtEpochMillis DESC")
    fun observePendingForVehicle(vehicleId: Long): Flow<List<ChargingSessionEntity>>

    @Query("SELECT * FROM charging_sessions WHERE vehicleId = :vehicleId AND status = 'PENDING_DETAILS' ORDER BY endedAtEpochMillis DESC, startedAtEpochMillis DESC")
    suspend fun getPendingForVehicle(vehicleId: Long): List<ChargingSessionEntity>

    /**
     * Stored session tariff memories that are still truthful enough to reuse.
     *
     * Pending sessions remain their own current facts. A completed session is eligible only while
     * its linked, non-deleted ChargingRecord still matches the reusable completion context. If the
     * user later corrects or deletes the historical record, the old session tariff stops being an
     * automatic/suggestion source instead of silently surviving that correction.
     */
    @Query(
        """
        SELECT s.*
        FROM charging_sessions AS s
        WHERE s.vehicleId = :vehicleId
          AND s.unitPricePerKwh IS NOT NULL
          AND s.unitPricePerKwh >= 0
          AND (
            s.status = 'PENDING_DETAILS'
            OR (
              s.status = 'COMPLETED'
              AND s.completedRecordId IS NOT NULL
              AND EXISTS (
                SELECT 1
                FROM charging_records AS r
                WHERE r.id = s.completedRecordId
                  AND r.isDeleted = 0
                  AND r.vehicleId = s.vehicleId
                  AND r.chargeTimeEpochMillis = s.startedAtEpochMillis
                  AND ABS(r.energyKwh - s.pendingMeterEnergyKwh) <= 0.000001
                  AND ABS(r.cost - s.pendingTotalCost) <= 0.000001
                  AND LOWER(TRIM(COALESCE(r.chargerType, ''))) = LOWER(TRIM(COALESCE(s.chargerType, '')))
                  AND LOWER(TRIM(COALESCE(r.location, ''))) = LOWER(TRIM(COALESCE(s.location, '')))
              )
            )
          )
        ORDER BY COALESCE(s.endedAtEpochMillis, s.startedAtEpochMillis) DESC,
                 s.updatedAtEpochMillis DESC
        """
    )
    fun observePricedForVehicle(vehicleId: Long): Flow<List<ChargingSessionEntity>>

    @Query("SELECT * FROM charging_sessions WHERE vehicleId = :vehicleId AND status = 'PENDING_DETAILS' AND endSoc IS NOT NULL ORDER BY endedAtEpochMillis DESC, startedAtEpochMillis DESC LIMIT 1")
    suspend fun getLatestPendingWithEndSocForVehicle(vehicleId: Long): ChargingSessionEntity?

    @Query("SELECT * FROM charging_sessions WHERE vehicleId = :vehicleId AND status = 'PENDING_DETAILS' AND odometerKm IS NOT NULL ORDER BY endedAtEpochMillis DESC, startedAtEpochMillis DESC LIMIT 1")
    suspend fun getLatestPendingWithOdometerForVehicle(vehicleId: Long): ChargingSessionEntity?

    @Query("SELECT * FROM charging_sessions WHERE status = 'ACTIVE' ORDER BY startedAtEpochMillis DESC LIMIT 1")
    suspend fun getAnyActive(): ChargingSessionEntity?

    @Query("SELECT * FROM charging_sessions WHERE status IN ('ACTIVE', 'PENDING_DETAILS') ORDER BY updatedAtEpochMillis DESC LIMIT 1")
    suspend fun getAnyUnfinished(): ChargingSessionEntity?

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
