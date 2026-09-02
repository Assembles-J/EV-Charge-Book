package com.evchargebook

import android.app.Application
import androidx.room.InvalidationTracker
import com.evchargebook.data.database.AppDatabase
import com.evchargebook.data.repository.VehicleCatalogSync
import com.evchargebook.widget.VehicleHomeWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class EvChargeBookApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val widgetObserver = object : InvalidationTracker.Observer(
        "vehicles",
        "vehicle_state",
        "trip_sessions",
        "charging_sessions",
    ) {
        override fun onInvalidated(tables: Set<String>) {
            applicationScope.launch {
                runCatching {
                    VehicleHomeWidgetUpdater.updateAll(this@EvChargeBookApplication)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getInstance(this)
        database.invalidationTracker.addObserver(widgetObserver)

        applicationScope.launch {
            // Widget state is Local First and should be useful even when catalog networking fails.
            runCatching {
                VehicleHomeWidgetUpdater.updateAll(this@EvChargeBookApplication)
            }

            // Catalog networking is best-effort metadata refresh only. A timeout, malformed response,
            // DNS failure or server outage must never block or fail normal local app startup.
            runCatching {
                VehicleCatalogSync(database)
                    .refresh(BuildConfig.VEHICLE_CATALOG_URL)
            }
        }
    }
}
