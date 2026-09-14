package com.evchargebook.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
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
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PREVIOUS_PAGE,
            ACTION_NEXT_PAGE,
            -> {
                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID,
                )
                if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

                val store = VehicleWidgetPageStore(context)
                val currentPage = store.get(appWidgetId)
                val nextPage = if (intent.action == ACTION_NEXT_PAGE) {
                    VehicleWidgetPageNavigator.next(currentPage)
                } else {
                    VehicleWidgetPageNavigator.previous(currentPage)
                }
                store.set(appWidgetId, nextPage)
                updateAsync(
                    context = context,
                    appWidgetManager = AppWidgetManager.getInstance(context),
                    appWidgetIds = intArrayOf(appWidgetId),
                )
                return
            }
        }
        super.onReceive(context, intent)
    }

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

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val store = VehicleWidgetPageStore(context)
        appWidgetIds.forEach(store::clear)
        super.onDeleted(context, appWidgetIds)
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

    companion object {
        internal const val ACTION_PREVIOUS_PAGE = "com.evchargebook.widget.PREVIOUS_PAGE"
        internal const val ACTION_NEXT_PAGE = "com.evchargebook.widget.NEXT_PAGE"
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
        val pageStore = VehicleWidgetPageStore(context)
        appWidgetIds.forEach { appWidgetId ->
            val minWidthDp = appWidgetManager.getAppWidgetOptions(appWidgetId)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val layoutId = if (VehicleWidgetSizePolicy.isCompact(minWidthDp)) {
                R.layout.widget_vehicle_home
            } else {
                R.layout.widget_vehicle_home_expanded
            }
            val pageIndex = pageStore.get(appWidgetId)
            appWidgetManager.updateAppWidget(
                appWidgetId,
                render(context, snapshot, layoutId, pageIndex, appWidgetId),
            )
        }
    }

    internal fun render(
        context: Context,
        snapshot: VehicleWidgetSnapshot,
        layoutId: Int,
        pageIndex: Int,
        appWidgetId: Int,
    ): RemoteViews = RemoteViews(context.packageName, layoutId).apply {
        val normalizedPage = VehicleWidgetPageNavigator.normalize(pageIndex)
        val formatTime: (Long) -> String = { timestamp ->
            DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
                .format(Date(timestamp))
        }
        val mileage = snapshot.currentMileageKm?.let(::formatMileage) ?: "-- km"
        val bluetoothDevice = snapshot.bluetoothDeviceName?.takeIf { it.isNotBlank() }
        val targetDevice = bluetoothDevice ?: "目标车载蓝牙"

        setTextViewText(R.id.widget_vehicle_name, snapshot.displayName)
        setTextViewText(
            R.id.widget_page_indicator,
            VehicleWidgetPageNavigator.indicator(normalizedPage),
        )
        setViewVisibility(
            R.id.widget_status_detail,
            if (layoutId == R.layout.widget_vehicle_home) View.GONE else View.VISIBLE,
        )

        when (normalizedPage) {
            VehicleWidgetPageNavigator.PAGE_TRIP -> {
                setTextViewText(
                    R.id.widget_status_badge,
                    when {
                        snapshot.activeTrip -> "● 行程记录中"
                        snapshot.bluetoothDetectionEnabled && snapshot.autoStartOnConnect ->
                            "● 自动行程已就绪"
                        snapshot.bluetoothDetectionEnabled -> "● 蓝牙检测已开启"
                        snapshot.vehicleId != null -> "● 行程待命"
                        else -> "尚未添加车辆"
                    },
                )
                setTextViewText(
                    R.id.widget_status_detail,
                    when {
                        snapshot.activeTrip -> "本地 Trip 正在记录"
                        snapshot.bluetoothDetectionEnabled && snapshot.autoStartOnConnect ->
                            "连接${targetDevice}后尝试自动开始行程"
                        snapshot.bluetoothDetectionEnabled ->
                            "连接${targetDevice}后提醒确认行程"
                        snapshot.vehicleId != null -> "未开启蓝牙行程检测"
                        else -> "打开 App 添加车辆"
                    },
                )
                setTextViewText(R.id.widget_primary_label, "当前行程")
                setTextViewText(
                    R.id.widget_primary_value,
                    if (snapshot.activeTrip) "记录中" else "未开始",
                )
                setTextViewText(R.id.widget_secondary_label, "绑定设备")
                setTextViewText(R.id.widget_secondary_value, bluetoothDevice ?: "未绑定")
                setTextViewText(
                    R.id.widget_state_label,
                    when {
                        snapshot.activeTrip -> "行程记录中 · 本地状态"
                        snapshot.bluetoothDetectionEnabled -> "蓝牙规则 · 本地设置"
                        snapshot.vehicleId != null -> "等待开始行程"
                        else -> "等待添加车辆"
                    },
                )
                setTextViewText(R.id.widget_trip_action, VehicleWidgetTruthText.tripAction(snapshot))
                setTextViewText(R.id.widget_app_action, "打开 App")
            }

            VehicleWidgetPageNavigator.PAGE_CHARGING -> {
                setTextViewText(
                    R.id.widget_status_badge,
                    if (snapshot.activeCharging) "● 充电记录中" else "● 充电账本",
                )
                setTextViewText(
                    R.id.widget_status_detail,
                    if (snapshot.activeCharging) {
                        "本地充电记录正在进行"
                    } else {
                        "仅展示 EV Charge Book 已记录的充电状态"
                    },
                )
                setTextViewText(R.id.widget_primary_label, "SOC")
                setTextViewText(R.id.widget_primary_value, VehicleWidgetTruthText.soc(snapshot))
                setTextViewText(R.id.widget_secondary_label, "当前充电")
                setTextViewText(
                    R.id.widget_secondary_value,
                    if (snapshot.activeCharging) "记录中" else "未开始",
                )
                setTextViewText(
                    R.id.widget_state_label,
                    when {
                        snapshot.activeCharging -> "充电记录中 · 本地状态"
                        snapshot.stateUpdatedAtEpochMillis != null ->
                            "上次记录 · ${formatTime(snapshot.stateUpdatedAtEpochMillis)}"
                        snapshot.vehicleId != null -> "尚无车辆状态记录"
                        else -> "等待添加车辆"
                    },
                )
                setTextViewText(R.id.widget_trip_action, "充电记录")
                setTextViewText(R.id.widget_app_action, "打开 App")
            }

            else -> {
                setTextViewText(
                    R.id.widget_status_badge,
                    VehicleWidgetTruthText.statusHeadline(snapshot),
                )
                setTextViewText(
                    R.id.widget_status_detail,
                    VehicleWidgetTruthText.statusDetail(snapshot),
                )
                setTextViewText(R.id.widget_primary_label, "SOC")
                setTextViewText(R.id.widget_primary_value, VehicleWidgetTruthText.soc(snapshot))
                setTextViewText(R.id.widget_secondary_label, "APP 当前里程")
                setTextViewText(R.id.widget_secondary_value, mileage)
                setTextViewText(
                    R.id.widget_state_label,
                    VehicleWidgetTruthText.stateLabel(snapshot, formatTime),
                )
                setTextViewText(R.id.widget_trip_action, VehicleWidgetTruthText.tripAction(snapshot))
                setTextViewText(R.id.widget_app_action, VehicleWidgetTruthText.appAction(snapshot))
            }
        }

        setTextColor(R.id.widget_status_badge, statusColor(snapshot))

        val openApp = PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP + appWidgetId,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openTrip = PendingIntent.getActivity(
            context,
            REQUEST_OPEN_TRIP + appWidgetId,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_ACTIVE_TRIP, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val previousPage = pageIntent(
            context = context,
            appWidgetId = appWidgetId,
            action = VehicleHomeWidgetProvider.ACTION_PREVIOUS_PAGE,
            requestCode = REQUEST_PREVIOUS_PAGE + appWidgetId,
        )
        val nextPage = pageIntent(
            context = context,
            appWidgetId = appWidgetId,
            action = VehicleHomeWidgetProvider.ACTION_NEXT_PAGE,
            requestCode = REQUEST_NEXT_PAGE + appWidgetId,
        )

        setOnClickPendingIntent(R.id.widget_root, openApp)
        setOnClickPendingIntent(R.id.widget_page_previous, previousPage)
        setOnClickPendingIntent(R.id.widget_page_next, nextPage)
        setOnClickPendingIntent(
            R.id.widget_trip_action,
            if (normalizedPage == VehicleWidgetPageNavigator.PAGE_CHARGING) openApp else openTrip,
        )
        setOnClickPendingIntent(R.id.widget_app_action, openApp)
    }

    private fun pageIntent(
        context: Context,
        appWidgetId: Int,
        action: String,
        requestCode: Int,
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, VehicleHomeWidgetProvider::class.java)
            .setAction(action)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun statusColor(snapshot: VehicleWidgetSnapshot): Int = Color.parseColor(
        when {
            snapshot.vehicleId == null -> "#8D989A"
            snapshot.activeTrip || snapshot.activeCharging || snapshot.bluetoothDetectionEnabled -> "#52F58B"
            else -> "#8EB7FF"
        }
    )

    private fun formatMileage(value: Double): String =
        String.format(Locale.getDefault(), "%,.0f km", value)

    private const val REQUEST_OPEN_APP = 30_100
    private const val REQUEST_OPEN_TRIP = 40_100
    private const val REQUEST_PREVIOUS_PAGE = 50_100
    private const val REQUEST_NEXT_PAGE = 60_100
}
