package com.evchargebook.data.database

/**
 * Room v17 -> v18 contract for ended charging sessions whose meter data arrives later.
 *
 * Every new field is nullable so existing ACTIVE / COMPLETED / CANCELLED sessions retain their
 * original facts without inference or fake zero values.
 */
object ChargingPendingMigration17To18 {
    val statements = listOf(
        "ALTER TABLE charging_sessions ADD COLUMN endSoc INTEGER",
        "ALTER TABLE charging_sessions ADD COLUMN odometerKm REAL",
        "ALTER TABLE charging_sessions ADD COLUMN pendingMeterEnergyKwh REAL",
        "ALTER TABLE charging_sessions ADD COLUMN pendingTotalCost REAL",
        "ALTER TABLE charging_sessions ADD COLUMN pendingVehicleEnergyKwh REAL",
    )
}
