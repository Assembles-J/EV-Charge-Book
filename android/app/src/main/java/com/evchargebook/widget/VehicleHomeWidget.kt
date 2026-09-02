package com.evchargebook.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.evchargebook.MainActivity
import com.evchargebook.R
import com.evchargebook.data.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

data class VehicleWidgetSnapshot(
    val vehicleId: Long? = null,
    val displayName: String = "EV Charge Book",
    val currentSoc: Int? = null,
    val currentMileageKm: Double? = null,
    val stateUpdatedAtEpochMillis: Long? = null,
    val activeTrip: Boolean = false,
    val activeCharging: Boolean = false,
)

/**
 * Reads only local app facts. Rated range/battery capacity are deliberately not used as live state.
 */
class VehicleWidgetSnapshotReader(context: Context) {
    private val database = AppDatabase.getInstance(context.applicationContext)

    suspend fun read(): VehicleWidgetSnapshot {
        val vehicle = database.vehicleDao().observeActive().first().firstOrNull()
            ?: return VehicleWidgetSnapshot()
        val state = database.vehicleStateDao().get(vehicle.id)
        val activeTrip = database.tripDao().getActive()?.vehicleId == vehicle.id
        val activeCharging = database.chargingSessionDao().getActiveForVehicle(vehicle.id) != null

        return VehicleWidgetSnapshot(
            vehicleId = vehicle.id,
            displayName = vehicle.displayName,
            currentSoc = state?.currentSoc,
            currentMileageKm = state?.currentMileage,
            stateUpdatedAtEpochMillis = state?.updatedAtEpochMillis,
            activeTrip = activeTrip,
            activeCharging = activeCharging,
        )
    }
}

class VehicleHomeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                VehicleHomeWidgetUpdater.update(
                    context = context.applicationContext,
                    appWidgetManager = appWidgetManager,
                    appWidgetIds = appWidgetIds,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}

object VehicleHomeWidgetUpdater {
    suspend fun updateAll(context: Context) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val ids = manager.getAppWidgetIds(
            ComponentName(appContext, VehicleHomeWidgetProvider::class.java)
        )
        if (ids.isNotEmpty()) {
            update(appContext, manager, ids)
        }
    }

    suspend fun update(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val snapshot = VehicleWidgetSnapshotReader(context).read()
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(
                appWidgetId,
                render(context, snapshot),
            )
        }
    }

    internal fun render(context: Context, snapshot: VehicleWidgetSnapshot): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_vehicle_home).apply {
            setTextViewText(R.id.widget_vehicle_name, snapshot.displayName)
            setTextViewText(R.id.widget_soc, VehicleWidgetTruthText.soc(snapshot))
            setTextViewText(
                R.id.widget_mileage,
                snapshot.currentMileageKm?.let(::formatMileage) ?: "-- km",
            )
            setTextViewText(
                R.id.widget_state_label,
                VehicleWidgetTruthText.stateLabel(snapshot) { timestamp ->
                    DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
                        .format(Date(timestamp))
                },
            )
            setTextViewText(R.id.widget_trip_action, VehicleWidgetTruthText.tripAction(snapshot))
            setTextViewText(R.id.widget_app_action, VehicleWidgetTruthText.appAction(snapshot))

            val openApp = PendingIntent.getActivity(
                context,
                REQUEST_OPEN_APP,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val openTrip = PendingIntent.getActivity(
                context,
                REQUEST_OPEN_TRIP,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(MainActivity.EXTRA_OPEN_ACTIVE_TRIP, true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            setOnClickPendingIntent(R.id.widget_root, openApp)
            setOnClickPendingIntent(R.id.widget_trip_action, openTrip)
            setOnClickPendingIntent(R.id.widget_app_action, openApp)
        }

    private fun formatMileage(value: Double): String =
        String.format(Locale.getDefault(), "%,.0f km", value)

    private const val REQUEST_OPEN_APP = 30_100
    private const val REQUEST_OPEN_TRIP = 30_101
}
