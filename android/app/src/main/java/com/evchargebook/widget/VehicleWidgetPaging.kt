package com.evchargebook.widget

import android.content.Context

/** Pure three-page navigation used by the standard Android home-screen widget. */
object VehicleWidgetPageNavigator {
    const val PAGE_VEHICLE = 0
    const val PAGE_TRIP = 1
    const val PAGE_CHARGING = 2
    private const val PAGE_COUNT = 3

    fun next(pageIndex: Int): Int = normalize(pageIndex + 1)

    fun previous(pageIndex: Int): Int = normalize(pageIndex - 1)

    fun indicator(pageIndex: Int): String = "${normalize(pageIndex) + 1}/$PAGE_COUNT"

    fun normalize(pageIndex: Int): Int {
        val remainder = pageIndex % PAGE_COUNT
        return if (remainder < 0) remainder + PAGE_COUNT else remainder
    }
}

/** Persists the selected page independently for each widget instance. */
class VehicleWidgetPageStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun get(appWidgetId: Int): Int = VehicleWidgetPageNavigator.normalize(
        preferences.getInt(key(appWidgetId), VehicleWidgetPageNavigator.PAGE_VEHICLE)
    )

    fun set(appWidgetId: Int, pageIndex: Int) {
        preferences.edit()
            .putInt(key(appWidgetId), VehicleWidgetPageNavigator.normalize(pageIndex))
            .apply()
    }

    fun clear(appWidgetId: Int) {
        preferences.edit().remove(key(appWidgetId)).apply()
    }

    private fun key(appWidgetId: Int): String = "widget_page_$appWidgetId"

    private companion object {
        const val PREFS_NAME = "vehicle_widget_pages"
    }
}
