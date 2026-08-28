package com.evchargebook

import android.app.Application
import com.evchargebook.data.database.AppDatabase
import com.evchargebook.data.repository.VehicleCatalogSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class EvChargeBookApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            // Catalog networking is best-effort metadata refresh only. A timeout, malformed response,
            // DNS failure or server outage must never block or fail normal local app startup.
            runCatching {
                VehicleCatalogSync(AppDatabase.getInstance(this@EvChargeBookApplication))
                    .refresh(BuildConfig.VEHICLE_CATALOG_URL)
            }
        }
    }
}
