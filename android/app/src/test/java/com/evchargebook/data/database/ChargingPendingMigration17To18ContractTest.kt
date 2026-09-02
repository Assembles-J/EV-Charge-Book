package com.evchargebook.data.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

class ChargingPendingMigration17To18ContractTest {
    @Test
    fun `v17 to v18 preserves existing session facts and adds nullable pending fields`() {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { db ->
            createMinimalV17ChargingSessions(db)
            db.createStatement().use { statement ->
                statement.execute(
                    """
                    INSERT INTO charging_sessions (
                        id, vehicleId, startedAtEpochMillis, startSoc, targetSoc, chargerType,
                        unitPricePerKwh, location, status, endedAtEpochMillis, completedRecordId,
                        updatedAtEpochMillis
                    ) VALUES (
                        'legacy-active', 1, 1700000000000, 20, 80, '家充',
                        0.61, 'Home', 'ACTIVE', NULL, NULL, 1700000000000
                    )
                    """.trimIndent()
                )
                ChargingPendingMigration17To18.statements.forEach { sql -> statement.execute(sql) }
            }

            val columns = tableColumns(db, "charging_sessions")
            listOf(
                "endSoc",
                "odometerKm",
                "pendingMeterEnergyKwh",
                "pendingTotalCost",
                "pendingVehicleEnergyKwh",
            ).forEach { name ->
                assertTrue(name in columns)
                assertEquals(false, columns.getValue(name).notNull)
            }

            db.createStatement().use { statement ->
                statement.executeQuery(
                    """
                    SELECT status, startSoc, targetSoc, chargerType, unitPricePerKwh, location,
                           endSoc, odometerKm, pendingMeterEnergyKwh, pendingTotalCost,
                           pendingVehicleEnergyKwh
                    FROM charging_sessions WHERE id = 'legacy-active'
                    """.trimIndent()
                ).use { row ->
                    assertTrue(row.next())
                    assertEquals("ACTIVE", row.getString("status"))
                    assertEquals(20, row.getInt("startSoc"))
                    assertEquals(80, row.getInt("targetSoc"))
                    assertEquals("家充", row.getString("chargerType"))
                    assertEquals(0.61, row.getDouble("unitPricePerKwh"), 0.000001)
                    assertEquals("Home", row.getString("location"))
                    assertNull(row.getObject("endSoc"))
                    assertNull(row.getObject("odometerKm"))
                    assertNull(row.getObject("pendingMeterEnergyKwh"))
                    assertNull(row.getObject("pendingTotalCost"))
                    assertNull(row.getObject("pendingVehicleEnergyKwh"))
                }

                statement.execute(
                    """
                    UPDATE charging_sessions
                    SET status = 'PENDING_DETAILS', endedAtEpochMillis = 1700003600000,
                        endSoc = 80, odometerKm = 12345.6
                    WHERE id = 'legacy-active'
                    """.trimIndent()
                )
                statement.executeQuery(
                    "SELECT status, endSoc, odometerKm FROM charging_sessions WHERE id = 'legacy-active'"
                ).use { row ->
                    assertTrue(row.next())
                    assertEquals("PENDING_DETAILS", row.getString("status"))
                    assertEquals(80, row.getInt("endSoc"))
                    assertEquals(12345.6, row.getDouble("odometerKm"), 0.000001)
                }
            }
        }
    }

    private fun createMinimalV17ChargingSessions(db: Connection) {
        db.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE charging_sessions (
                    id TEXT NOT NULL PRIMARY KEY,
                    vehicleId INTEGER NOT NULL,
                    startedAtEpochMillis INTEGER NOT NULL,
                    startSoc INTEGER,
                    targetSoc INTEGER,
                    chargerType TEXT,
                    unitPricePerKwh REAL,
                    location TEXT,
                    remark TEXT,
                    latitude REAL,
                    longitude REAL,
                    locationAccuracyMeters REAL,
                    status TEXT NOT NULL,
                    endedAtEpochMillis INTEGER,
                    completedRecordId INTEGER,
                    updatedAtEpochMillis INTEGER NOT NULL
                )
                """.trimIndent()
            )
            statement.execute("CREATE INDEX index_charging_sessions_vehicleId ON charging_sessions(vehicleId)")
            statement.execute("CREATE INDEX index_charging_sessions_status ON charging_sessions(status)")
            statement.execute("CREATE INDEX index_charging_sessions_startedAtEpochMillis ON charging_sessions(startedAtEpochMillis)")
        }
    }

    private fun tableColumns(db: Connection, table: String): Map<String, ColumnInfo> {
        val columns = linkedMapOf<String, ColumnInfo>()
        db.createStatement().use { statement ->
            statement.executeQuery("PRAGMA table_info($table)").use { rows ->
                while (rows.next()) {
                    columns[rows.getString("name")] = ColumnInfo(
                        type = rows.getString("type"),
                        notNull = rows.getInt("notnull") == 1,
                    )
                }
            }
        }
        return columns
    }

    private data class ColumnInfo(
        val type: String,
        val notNull: Boolean,
    )
}
