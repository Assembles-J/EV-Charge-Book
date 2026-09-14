package com.evchargebook.widget

import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.evchargebook.MainActivity
import com.evchargebook.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class VehicleWidgetRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        VehicleWidgetRemoteViewsFactory(applicationContext)
}

private class VehicleWidgetRemoteViewsFactory(
    private val context: android.content.Context,
) : RemoteViewsService.RemoteViewsFactory {
    private var snapshot = VehicleWidgetSnapshot()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        snapshot = runBlocking(Dispatchers.IO) {
            VehicleWidgetSnapshotReader(context).read()
        }
    }

    override fun onDestroy() = Unit

    override fun getCount(): Int = VehicleWidgetStackSpec.pageCount()

    override fun getViewAt(position: Int): RemoteViews {
        val page = VehicleWidgetStackSpec.page(position)
        val views = RemoteViews(context.packageName, R.layout.widget_vehicle_page)
        val stateUpdatedAtEpochMillis = snapshot.stateUpdatedAtEpochMillis
        val formatTime: (Long) -> String = { timestamp ->
            DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
                .format(Date(timestamp))
        }

        views.setTextViewText(R.id.widget_page_indicator, "${position + 1}/${VehicleWidgetStackSpec.pageCount()}")
        views.setOnClickFillInIntent(R.id.widget_page_root, Intent())

        when (page) {
            VehicleWidgetStackPage.VEHICLE -> {
                views.setTextViewText(R.id.widget_page_title, "车况")
                views.setTextViewText(
                    R.id.widget_status_badge,
                    VehicleWidgetTruthText.statusHeadline(snapshot),
                )
                views.setTextViewText(R.id.widget_primary_label, "SOC")
                views.setTextViewText(R.id.widget_primary_value, VehicleWidgetTruthText.soc(snapshot))
                views.setTextViewText(R.id.widget_secondary_label, "APP 里程")
                views.setTextViewText(
                    R.id.widget_secondary_value,
                    snapshot.currentMileageKm?.let(::formatMileage) ?: "-- km",
                )
                views.setTextViewText(
                    R.id.widget_state_label,
                    VehicleWidgetTruthText.stateLabel(snapshot, formatTime),
                )
            }

            VehicleWidgetStackPage.TRIP -> {
                views.setTextViewText(R.id.widget_page_title, "行程")
                views.setTextViewText(
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
                views.setTextViewText(R.id.widget_primary_label, "当前行程")
                views.setTextViewText(
                    R.id.widget_primary_value,
                    if (snapshot.activeTrip) "记录中" else "未开始",
                )
                views.setTextViewText(R.id.widget_secondary_label, "自动行程")
                views.setTextViewText(
                    R.id.widget_secondary_value,
                    when {
                        snapshot.bluetoothDetectionEnabled && snapshot.autoStartOnConnect -> "自动"
                        snapshot.bluetoothDetectionEnabled -> "提醒"
                        else -> "关闭"
                    },
                )
                views.setTextViewText(
                    R.id.widget_state_label,
                    when {
                        snapshot.activeTrip -> "行程记录中 · 本地状态"
                        snapshot.bluetoothDetectionEnabled && !snapshot.bluetoothDeviceName.isNullOrBlank() ->
                            "${snapshot.bluetoothDeviceName} · 本地规则"
                        snapshot.bluetoothDetectionEnabled -> "车载蓝牙 · 本地规则"
                        snapshot.vehicleId != null -> "未开启蓝牙行程检测"
                        else -> "等待添加车辆"
                    },
                )
            }

            VehicleWidgetStackPage.CHARGING -> {
                views.setTextViewText(R.id.widget_page_title, "充电")
                views.setTextViewText(
                    R.id.widget_status_badge,
                    if (snapshot.activeCharging) "● 充电记录中" else "● 充电账本",
                )
                views.setTextViewText(R.id.widget_primary_label, "SOC")
                views.setTextViewText(R.id.widget_primary_value, VehicleWidgetTruthText.soc(snapshot))
                views.setTextViewText(R.id.widget_secondary_label, "当前充电")
                views.setTextViewText(
                    R.id.widget_secondary_value,
                    if (snapshot.activeCharging) "记录中" else "未开始",
                )
                views.setTextViewText(
                    R.id.widget_state_label,
                    when {
                        snapshot.activeCharging -> "充电记录中 · 本地状态"
                        stateUpdatedAtEpochMillis != null ->
                            "上次记录 · ${formatTime(stateUpdatedAtEpochMillis)}"
                        snapshot.vehicleId != null -> "尚无车辆状态记录"
                        else -> "等待添加车辆"
                    },
                )
            }
        }

        views.setTextColor(R.id.widget_status_badge, statusColor(snapshot))

        val actionLabel = VehicleWidgetStackSpec.actionLabel(position)
        if (actionLabel.isBlank()) {
            views.setViewVisibility(R.id.widget_page_action, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_page_action, View.VISIBLE)
            views.setTextViewText(R.id.widget_page_action, actionLabel)
            val actionIntent = Intent()
            if (VehicleWidgetStackSpec.actionDestination(position) == "TRIP") {
                actionIntent.putExtra(MainActivity.EXTRA_OPEN_ACTIVE_TRIP, true)
            }
            views.setOnClickFillInIntent(R.id.widget_page_action, actionIntent)
        }

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true

    private fun formatMileage(value: Double): String =
        String.format(Locale.getDefault(), "%,.0f km", value)

    private fun statusColor(snapshot: VehicleWidgetSnapshot): Int = Color.parseColor(
        when {
            snapshot.vehicleId == null -> "#8D989A"
            snapshot.activeTrip || snapshot.activeCharging || snapshot.bluetoothDetectionEnabled -> "#52F58B"
            else -> "#8EB7FF"
        }
    )
}
