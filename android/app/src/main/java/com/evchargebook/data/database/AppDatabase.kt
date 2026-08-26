package com.evchargebook.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.evchargebook.data.dao.ChargingRecordDao
import com.evchargebook.data.dao.TripDao
import com.evchargebook.data.dao.VehicleDao
import com.evchargebook.data.dao.VehicleCatalogDao
import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.data.entity.TripPointEntity
import com.evchargebook.data.entity.TripSessionEntity
import com.evchargebook.data.entity.VehicleCatalogEntity
import com.evchargebook.data.entity.VehicleEntity

@Database(
    entities = [VehicleEntity::class, ChargingRecordEntity::class, VehicleCatalogEntity::class, TripSessionEntity::class, TripPointEntity::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun vehicleCatalogDao(): VehicleCatalogDao
    abstract fun chargingRecordDao(): ChargingRecordDao
    abstract fun tripDao(): TripDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE charging_records ADD COLUMN odometerKm REAL")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vehicles ADD COLUMN isDefault INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE vehicles ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE vehicles ADD COLUMN createdAtEpochMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE vehicles SET isDefault = 1, createdAtEpochMillis = id WHERE id = (SELECT MIN(id) FROM vehicles)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vehicles ADD COLUMN catalogVehicleId TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS vehicle_catalog (catalogId TEXT NOT NULL, source TEXT NOT NULL, brand TEXT NOT NULL, series TEXT NOT NULL, modelName TEXT NOT NULL, modelYear INTEGER, trimName TEXT, powertrainType TEXT NOT NULL, batteryCapacityKwh REAL, rangeKm INTEGER, sourceUpdatedAtEpochMillis INTEGER NOT NULL, PRIMARY KEY(catalogId))")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE charging_records ADD COLUMN latitude REAL")
                db.execSQL("ALTER TABLE charging_records ADD COLUMN longitude REAL")
                db.execSQL("ALTER TABLE charging_records ADD COLUMN locationAccuracyMeters REAL")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS trip_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, vehicleId INTEGER NOT NULL, startedAtEpochMillis INTEGER NOT NULL, endedAtEpochMillis INTEGER, distanceMeters REAL NOT NULL, elapsedSeconds INTEGER NOT NULL, movingSeconds INTEGER, stoppedSeconds INTEGER, averageSpeedMps REAL, maxSpeedMps REAL, startLatitude REAL, startLongitude REAL, endLatitude REAL, endLongitude REAL, startAltitudeMeters REAL, endAltitudeMeters REAL, minAltitudeMeters REAL, maxAltitudeMeters REAL, status TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trip_sessions_vehicleId ON trip_sessions(vehicleId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trip_sessions_startedAtEpochMillis ON trip_sessions(startedAtEpochMillis)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trip_sessions_status ON trip_sessions(status)")
                db.execSQL("CREATE TABLE IF NOT EXISTS trip_points (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, tripId INTEGER NOT NULL, capturedAtEpochMillis INTEGER NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, altitudeMeters REAL, speedMps REAL, bearingDegrees REAL, horizontalAccuracyMeters REAL, verticalAccuracyMeters REAL, speedAccuracyMps REAL, provider TEXT, FOREIGN KEY(tripId) REFERENCES trip_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trip_points_tripId ON trip_points(tripId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trip_points_capturedAtEpochMillis ON trip_points(capturedAtEpochMillis)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vehicles ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE vehicles ADD COLUMN updatedAtEpochMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE vehicles SET syncId = lower(hex(randomblob(16))) WHERE syncId = ''")
                db.execSQL("UPDATE vehicles SET updatedAtEpochMillis = CASE WHEN createdAtEpochMillis > 0 THEN createdAtEpochMillis ELSE CAST(strftime('%s','now') AS INTEGER) * 1000 END WHERE updatedAtEpochMillis = 0")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_vehicles_syncId ON vehicles(syncId)")

                db.execSQL("ALTER TABLE charging_records ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE charging_records ADD COLUMN updatedAtEpochMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE charging_records ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE charging_records SET syncId = lower(hex(randomblob(16))) WHERE syncId = ''")
                db.execSQL("UPDATE charging_records SET updatedAtEpochMillis = chargeTimeEpochMillis WHERE updatedAtEpochMillis = 0")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_charging_records_syncId ON charging_records(syncId)")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ev-charge-book.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .build()
                    .also { instance = it }
            }
    }
}
