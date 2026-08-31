package com.evchargebook.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.evchargebook.data.entity.AutoTripDetectionSessionEntity

@Dao
interface AutoTripDetectionDao {
    @Query("SELECT * FROM auto_trip_detection_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getById(sessionId: String): AutoTripDetectionSessionEntity?

    @Query(
        """
        SELECT * FROM auto_trip_detection_sessions
        WHERE deviceAddressHash = :deviceAddressHash
          AND closedAtEpochMillis IS NULL
        ORDER BY connectedAtEpochMillis DESC
        LIMIT 1
        """
    )
    suspend fun getOpenForDevice(deviceAddressHash: String): AutoTripDetectionSessionEntity?

    @Insert
    suspend fun insert(session: AutoTripDetectionSessionEntity)

    @Update
    suspend fun update(session: AutoTripDetectionSessionEntity)

    @Query(
        """
        UPDATE auto_trip_detection_sessions
        SET state = :state,
            ignoredAtEpochMillis = :ignoredAtEpochMillis,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :sessionId
          AND closedAtEpochMillis IS NULL
        """
    )
    suspend fun markIgnored(
        sessionId: String,
        state: String,
        ignoredAtEpochMillis: Long,
        updatedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE auto_trip_detection_sessions
        SET state = :state,
            closedAtEpochMillis = :closedAtEpochMillis,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :sessionId
          AND closedAtEpochMillis IS NULL
        """
    )
    suspend fun closeSession(
        sessionId: String,
        state: String,
        closedAtEpochMillis: Long,
        updatedAtEpochMillis: Long,
    ): Int
}
