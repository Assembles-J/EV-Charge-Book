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
import com.evchargebook.data.dao.VehicleStateDao
import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.data.entity.TripDiagnosticEventEntity
import com.evchargebook.data.entity.TripPointEntity
import com.evchargebook.data.entity.TripSessionEntity
import com.evchargebook.data.entity.VehicleCatalogEntity
import com.evchargebook.data.entity.VehicleEntity
import com.evchargebook.data.entity.VehicleStateEntity

@Database(
    entities = [
        VehicleEntity::class,
        ChargingRecordEntity::class,
        VehicleCatalogEntity::class,
        TripSessionEntity::class,
        TripPointEntity::class,
        TripDiagnosticEventEntity::class,
        VehicleStateEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun vehicleCatalogDao(): VehicleCatalogDao
    abstract fun chargingRecordDao(): ChargingRecordDao
    abstract fun tripDao(): TripDao
    abstract fun vehicleStateDao(): VehicleStateDao

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
                db.execSQL("CREATE TABLE IF NOT EXISTS trip_points (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, tripId INTEGER NOT NULL, capturedAtEpochMillis INTEGER NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, altitudeMeters REAL, speedMps REAL, bearingDegrees REAL, horizontalAccuracyMeters REAL, verticalAccuracyMeters REAL, speedAccuracyMps REAL, provider TEXT)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {}
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {}
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS vehicle_state (vehicleId INTEGER NOT NULL PRIMARY KEY, currentSoc INTEGER, currentMileage REAL, updatedAtEpochMillis INTEGER NOT NULL, updateSource TEXT NOT NULL)")
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
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9
                    )
                    .build()
                    .also { instance = it }
            }
    }
}
