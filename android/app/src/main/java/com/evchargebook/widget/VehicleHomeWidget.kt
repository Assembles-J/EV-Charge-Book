package com.evchargebook.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import com.evchargebook.MainActivity
import com.evchargebook.R
import com.evchargebook.bluetooth.VehicleBluetoothBindingPreferences
import com.evchargebook.data.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class VehicleWidgetSnapshot(
    val vehicleId: Long? = null,
    val displayName: String = "EV Charge Book",
    val currentSoc: Int? = null,
    val currentMileageKm: Double? = null,
    val stateUpdatedAtEpochMillis: Long? = null,
    val activeTrip: Boolean = false,
    val activeCharging: Boolean = false,
    val bluetoothDetectionEnabled: Boolean = false,
    val bluetoothDeviceName: String? = null,
    val autoStartOnConnect: Boolean = false,
)

/**
 * Reads only local app facts. Rated range/battery capacity are deliberately not used as live state.
 *
 * Bluetooth fields describe the user's configured detection rule only. They do not claim that the
 * vehicle is currently connected; live/presence truth remains owned by the presence pipeline.
 */
class VehicleWidgetSnapshotReader(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.getInstance(appContext)
    private val bindingPreferences = VehicleBluetoothBindingPreferences(appContext)

    suspend fun read(): VehicleWidgetSnapshot {
        val vehicles = database.vehicleDao().observeActive().first()
        val vehicle = vehicles.firstOrNull { it.isDefault } ?: vehicles.firstOrNull()
            ?: return VehicleWidgetSnapshot()
        val state = database.vehicleStateDao().get(vehicle.id)
        val activeTrip = database.tripDao().getActive()?.vehicleId == vehicle.id
        val activeCharging = database.chargingSessionDao().getActiveForVehicle(vehicle.id) != null
        val bluetoothBinding = bindingPreferences.bindings.first()
            .firstOrNull { it.vehicleId == vehicle.id }

        return VehicleWidgetSnapshot(
            vehicleId = vehicle.id,
            displayName = vehicle.displayName,
            currentSoc = state?.currentSoc,
            currentMileageKm = state?.currentMileage,
            stateUpdatedAtEpochMillis = state?.updatedAtEpochMillis,
            activeTrip = activeTrip,
            activeCharging = activeCharging,
            bluetoothDetectionEnabled = bluetoothBinding?.enabled == true,
            bluetoothDeviceName = bluetoothBinding?.deviceName,
            autoStartOnConnect = bluetoothBinding?.autoStartOnConnect == true,
        )
    }
}

class VehicleHomeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        updateAsync(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        updateAsync(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    private fun updateAsync(
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

object VehicleWidgetSizePolicy {
    private const val EXPANDED_MIN_WIDTH_DP = 300

    fun isCompact(minWidthDp: Int): Boolean =
        minWidthDp <= 0 || minWidthDp < EXPANDED_MIN_WIDTH_DP
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
            val minWidthDp = appWidgetManager.getAppWidgetOptions(appWidgetId)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val layoutId = if (VehicleWidgetSizePolicy.isCompact(minWidthDp)) {
                R.layout.widget_vehicle_home
            } else {
                R.layout.widget_vehicle_home_expanded
            }
            val views = renderHost(
                context = context,
                snapshot = snapshot,
                layoutId = layoutId,
                appWidgetId = appWidgetId,
            )
            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_stack)
        }
    }

    private fun renderHost(
        context: Context,
        snapshot: VehicleWidgetSnapshot,
        layoutId: Int,
        appWidgetId: Int,
    ): RemoteViews = RemoteViews(context.packageName, layoutId).apply {
        setTextViewText(R.id.widget_vehicle_name, snapshot.displayName)

        val serviceIntent = Intent(context, VehicleWidgetRemoteViewsService::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        serviceIntent.data = Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME))
        setRemoteAdapter(R.id.widget_stack, serviceIntent)
        setEmptyView(R.id.widget_stack, R.id.widget_empty)

        val openAppIntent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val openApp = PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP + appWidgetId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        setOnClickPendingIntent(R.id.widget_header, openApp)

        val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val pageTemplate = PendingIntent.getActivity(
            context,
            REQUEST_PAGE_TEMPLATE + appWidgetId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag,
        )
        setPendingIntentTemplate(R.id.widget_stack, pageTemplate)
    }

    private const val REQUEST_OPEN_APP = 30_100
    private const val REQUEST_PAGE_TEMPLATE = 70_100
}
