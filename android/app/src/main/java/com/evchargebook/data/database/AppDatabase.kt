package com.evchargebook.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.evchargebook.data.dao.ChargingRecordDao
import com.evchargebook.data.dao.VehicleDao
import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.data.entity.VehicleEntity

@Database(
    entities = [VehicleEntity::class, ChargingRecordEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun chargingRecordDao(): ChargingRecordDao

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

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ev-charge-book.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
