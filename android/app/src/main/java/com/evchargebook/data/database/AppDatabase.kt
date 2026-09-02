package com.evchargebook.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.evchargebook.data.dao.AutoTripDetectionDao
import com.evchargebook.data.dao.ChargingRecordDao
import com.evchargebook.data.dao.ChargingSessionDao
import com.evchargebook.data.dao.TripDao
import com.evchargebook.data.dao.VehicleCatalogDao
import com.evchargebook.data.dao.VehicleDao
import com.evchargebook.data.dao.VehicleStateDao
import com.evchargebook.data.entity.AutoTripDetectionSessionEntity
import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.data.entity.ChargingSessionEntity
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
        ChargingSessionEntity::class,
        VehicleCatalogEntity::class,
        TripSessionEntity::class,
        TripPointEntity::class,
        TripDiagnosticEventEntity::class,
        VehicleStateEntity::class,
        AutoTripDetectionSessionEntity::class,
    ],
    version = 18,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun vehicleCatalogDao(): VehicleCatalogDao
    abstract fun chargingRecordDao(): ChargingRecordDao
    abstract fun chargingSessionDao(): ChargingSessionDao
    abstract fun tripDao(): TripDao
    abstract fun vehicleStateDao(): VehicleStateDao
    abstract fun autoTripDetectionDao(): AutoTripDetectionDao

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

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS trip_diagnostic_events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, tripId INTEGER NOT NULL, occurredAtEpochMillis INTEGER NOT NULL, type TEXT NOT NULL, provider TEXT, detail TEXT, FOREIGN KEY(tripId) REFERENCES trip_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trip_diagnostic_events_tripId ON trip_diagnostic_events(tripId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trip_diagnostic_events_occurredAtEpochMillis ON trip_diagnostic_events(occurredAtEpochMillis)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trip_diagnostic_events_type ON trip_diagnostic_events(type)")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS vehicle_state (vehicleId INTEGER NOT NULL, currentSoc INTEGER, currentMileage REAL, updatedAtEpochMillis INTEGER NOT NULL, updateSource TEXT NOT NULL, PRIMARY KEY(vehicleId))")
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO vehicle_state (vehicleId, currentSoc, currentMileage, updatedAtEpochMillis, updateSource)
                    SELECT
                        vehicles.id,
                        (
                            SELECT charging_records.endSoc
                            FROM charging_records
                            WHERE charging_records.vehicleId = vehicles.id AND charging_records.isDeleted = 0
                            ORDER BY charging_records.chargeTimeEpochMillis DESC, charging_records.id DESC
                            LIMIT 1
                        ),
                        (
                            SELECT charging_records.odometerKm
                            FROM charging_records
                            WHERE charging_records.vehicleId = vehicles.id
                              AND charging_records.isDeleted = 0
                              AND charging_records.odometerKm IS NOT NULL
                            ORDER BY charging_records.chargeTimeEpochMillis DESC, charging_records.id DESC
                            LIMIT 1
                        ),
                        CAST(strftime('%s','now') AS INTEGER) * 1000,
                        'MIGRATION'
                    FROM vehicles
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trip_sessions ADD COLUMN startSoc INTEGER")
                db.execSQL("ALTER TABLE trip_sessions ADD COLUMN endSoc INTEGER")
                db.execSQL("ALTER TABLE trip_sessions ADD COLUMN startMileageKm REAL")
                db.execSQL("ALTER TABLE trip_sessions ADD COLUMN endMileageKm REAL")
                db.execSQL("ALTER TABLE trip_sessions ADD COLUMN consumedEnergyKwh REAL")
                db.execSQL("ALTER TABLE trip_sessions ADD COLUMN averageConsumptionKwhPer100Km REAL")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vehicle_catalog ADD COLUMN heroArtworkKey TEXT")
                db.execSQL("ALTER TABLE vehicle_catalog ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trip_sessions ADD COLUMN startSocSnapshot INTEGER")
                db.execSQL("ALTER TABLE trip_sessions ADD COLUMN startSocOverride INTEGER")
                db.execSQL("UPDATE trip_sessions SET startSocSnapshot = startSoc WHERE startSoc IS NOT NULL")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS auto_trip_detection_sessions (
                        id TEXT NOT NULL,
                        vehicleId INTEGER NOT NULL,
                        deviceAddressHash TEXT NOT NULL,
                        deviceNameSnapshot TEXT,
                        connectionEpoch TEXT NOT NULL,
                        state TEXT NOT NULL,
                        connectedAtEpochMillis INTEGER NOT NULL,
                        ignoredAtEpochMillis INTEGER,
                        closedAtEpochMillis INTEGER,
                        tripId INTEGER,
                        updatedAtEpochMillis INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_auto_trip_detection_sessions_vehicleId ON auto_trip_detection_sessions(vehicleId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_auto_trip_detection_sessions_deviceAddressHash ON auto_trip_detection_sessions(deviceAddressHash)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_auto_trip_detection_sessions_state ON auto_trip_detection_sessions(state)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_auto_trip_detection_sessions_deviceAddressHash_connectionEpoch ON auto_trip_detection_sessions(deviceAddressHash, connectionEpoch)")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vehicles ADD COLUMN nickname TEXT DEFAULT NULL")
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vehicle_catalog ADD COLUMN brandId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE vehicle_catalog ADD COLUMN rangeStandard TEXT")
                db.execSQL("ALTER TABLE vehicle_catalog ADD COLUMN brandLogoLightUrl TEXT")
                db.execSQL("ALTER TABLE vehicle_catalog ADD COLUMN brandLogoLightVersion INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE vehicle_catalog ADD COLUMN brandLogoDarkUrl TEXT")
                db.execSQL("ALTER TABLE vehicle_catalog ADD COLUMN brandLogoDarkVersion INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ChargingMigration15To16.statements.forEach(db::execSQL)
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                TripCaptureMigration16To17.statements.forEach(db::execSQL)
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ChargingPendingMigration17To18.statements.forEach(db::execSQL)
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
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15,
                        MIGRATION_15_16,
                        MIGRATION_16_17,
                        MIGRATION_17_18,
                    )
                    .build()
                    .also { instance = it }
            }
    }
}
