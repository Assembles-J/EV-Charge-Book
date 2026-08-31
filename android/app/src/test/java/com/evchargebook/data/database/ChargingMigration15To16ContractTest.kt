package com.evchargebook.data.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

class ChargingMigration15To16ContractTest {
    @Test
    fun `v15 to v16 SQL preserves legacy charging facts and creates session schema`() {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { db ->
            createMinimalV15ChargingRecords(db)
            db.createStatement().use { statement ->
                statement.execute(
                    """
                    INSERT INTO charging_records (
                        id, vehicleId, chargeTimeEpochMillis, energyKwh, cost, startSoc, endSoc,
                        location, chargerType, remark, odometerKm, latitude, longitude,
                        locationAccuracyMeters, syncId, updatedAtEpochMillis, isDeleted
                    ) VALUES (
                        7, 3, 1700000000000, 42.5, 51.0, 20, 80,
                        'Legacy station', '公共快充', 'kept', 12345.6, 31.2, 121.5,
                        8.0, 'legacy-sync', 1700000000000, 0
                    )
                    """.trimIndent()
                )
                ChargingMigration15To16.statements.forEach { sql -> statement.execute(sql) }
            }

            db.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT vehicleId, energyKwh, cost, endedAtEpochMillis, vehicleEnergyKwh FROM charging_records WHERE id = 7"
                ).use { row ->
                    assertTrue(row.next())
                    assertEquals(3, row.getInt("vehicleId"))
                    assertEquals(42.5, row.getDouble("energyKwh"), 0.000001)
                    assertEquals(51.0, row.getDouble("cost"), 0.000001)
                    assertNull(row.getObject("endedAtEpochMillis"))
                    assertNull(row.getObject("vehicleEnergyKwh"))
                }
            }

            val columns = tableColumns(db, "charging_sessions")
            assertEquals(
                setOf(
                    "id", "vehicleId", "startedAtEpochMillis", "startSoc", "targetSoc",
                    "chargerType", "unitPricePerKwh", "location", "remark", "latitude",
                    "longitude", "locationAccuracyMeters", "status", "endedAtEpochMillis",
                    "completedRecordId", "updatedAtEpochMillis"
                ),
                columns.keys
            )
            assertEquals(ColumnInfo("TEXT", notNull = true, primaryKey = true), columns.getValue("id"))
            assertEquals(ColumnInfo("INTEGER", notNull = true), columns.getValue("vehicleId"))
            assertEquals(ColumnInfo("INTEGER", notNull = true), columns.getValue("startedAtEpochMillis"))
            assertEquals(ColumnInfo("TEXT", notNull = true), columns.getValue("status"))
            assertEquals(ColumnInfo("INTEGER", notNull = true), columns.getValue("updatedAtEpochMillis"))
            assertEquals(ColumnInfo("INTEGER"), columns.getValue("endedAtEpochMillis"))
            assertEquals(ColumnInfo("INTEGER"), columns.getValue("completedRecordId"))

            val indexes = mutableSetOf<String>()
            db.createStatement().use { statement ->
                statement.executeQuery("PRAGMA index_list(charging_sessions)").use { rows ->
                    while (rows.next()) indexes += rows.getString("name")
                }
            }
            assertTrue("index_charging_sessions_vehicleId" in indexes)
            assertTrue("index_charging_sessions_status" in indexes)
            assertTrue("index_charging_sessions_startedAtEpochMillis" in indexes)

            db.createStatement().use { statement ->
                statement.execute(
                    """
                    INSERT INTO charging_sessions (
                        id, vehicleId, startedAtEpochMillis, status, updatedAtEpochMillis
                    ) VALUES ('session-1', 3, 1700001000000, 'ACTIVE', 1700001000000)
                    """.trimIndent()
                )
                statement.executeQuery(
                    "SELECT status, endedAtEpochMillis, completedRecordId FROM charging_sessions WHERE id = 'session-1'"
                ).use { row ->
                    assertTrue(row.next())
                    assertEquals("ACTIVE", row.getString("status"))
                    assertNull(row.getObject("endedAtEpochMillis"))
                    assertNull(row.getObject("completedRecordId"))
                }
            }
        }
    }

    private fun createMinimalV15ChargingRecords(db: Connection) {
        db.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE charging_records (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    vehicleId INTEGER NOT NULL,
                    chargeTimeEpochMillis INTEGER NOT NULL,
                    energyKwh REAL NOT NULL,
                    cost REAL NOT NULL,
                    startSoc INTEGER NOT NULL,
                    endSoc INTEGER NOT NULL,
                    location TEXT,
                    chargerType TEXT,
                    remark TEXT,
                    odometerKm REAL,
                    latitude REAL,
                    longitude REAL,
                    locationAccuracyMeters REAL,
                    syncId TEXT NOT NULL DEFAULT '',
                    updatedAtEpochMillis INTEGER NOT NULL DEFAULT 0,
                    isDeleted INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            statement.execute("CREATE UNIQUE INDEX index_charging_records_syncId ON charging_records(syncId)")
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
                        primaryKey = rows.getInt("pk") == 1,
                    )
                }
            }
        }
        return columns
    }

    private data class ColumnInfo(
        val type: String,
        val notNull: Boolean = false,
        val primaryKey: Boolean = false,
    )
}
