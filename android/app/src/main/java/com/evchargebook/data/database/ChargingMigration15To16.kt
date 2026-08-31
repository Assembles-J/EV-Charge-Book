package com.evchargebook.data.database

/**
 * Single SQL authority for the charging v15 -> v16 migration.
 *
 * Keep the statements Android-free so the JVM migration contract test can execute the exact
 * production SQL against SQLite without requiring an emulator. Physical/Room-open acceptance is
 * still tracked separately under #253.
 */
internal object ChargingMigration15To16 {
    val statements: List<String> = listOf(
        "ALTER TABLE charging_records ADD COLUMN endedAtEpochMillis INTEGER",
        "ALTER TABLE charging_records ADD COLUMN vehicleEnergyKwh REAL",
        """
        CREATE TABLE IF NOT EXISTS charging_sessions (
            id TEXT NOT NULL,
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
            updatedAtEpochMillis INTEGER NOT NULL,
            PRIMARY KEY(id)
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS index_charging_sessions_vehicleId ON charging_sessions(vehicleId)",
        "CREATE INDEX IF NOT EXISTS index_charging_sessions_status ON charging_sessions(status)",
        "CREATE INDEX IF NOT EXISTS index_charging_sessions_startedAtEpochMillis ON charging_sessions(startedAtEpochMillis)",
    )
}
