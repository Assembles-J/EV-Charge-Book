package com.evchargebook.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Process

enum class VehicleWidgetPinResult {
    REQUESTED,
    PROVIDER_UNAVAILABLE,
    LAUNCHER_UNSUPPORTED,
    REQUEST_REJECTED,
}

/**
 * Requests that the current launcher pin the existing EV Charge Book AppWidget directly.
 *
 * This deliberately bypasses OEM-specific card catalogs (for example ColorOS 卡片中心):
 * the home widget remains a standard Android AppWidget and the launcher decides whether it
 * supports the platform pin request.
 */
object VehicleWidgetPinning {
    fun request(context: Context): VehicleWidgetPinResult {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val provider = ComponentName(appContext, VehicleHomeWidgetProvider::class.java)

        val providerRegistered = manager
            .getInstalledProvidersForPackage(appContext.packageName, Process.myUserHandle())
            .any { it.provider == provider }
        if (!providerRegistered) return VehicleWidgetPinResult.PROVIDER_UNAVAILABLE

        if (!manager.isRequestPinAppWidgetSupported) {
            return VehicleWidgetPinResult.LAUNCHER_UNSUPPORTED
        }

        return if (manager.requestPinAppWidget(provider, null, null)) {
            VehicleWidgetPinResult.REQUESTED
        } else {
            VehicleWidgetPinResult.REQUEST_REJECTED
        }
    }

    fun message(result: VehicleWidgetPinResult): String = when (result) {
        VehicleWidgetPinResult.REQUESTED ->
            "已向桌面发起添加请求，请在系统弹窗中确认"
        VehicleWidgetPinResult.PROVIDER_UNAVAILABLE ->
            "系统暂未识别 EV Charge Book 小组件，请重新安装最新版本后重试"
        VehicleWidgetPinResult.LAUNCHER_UNSUPPORTED ->
            "当前桌面不支持应用内添加标准小组件，请使用桌面的标准小组件入口"
        VehicleWidgetPinResult.REQUEST_REJECTED ->
            "桌面拒绝了添加请求，请检查桌面锁定或应用锁后重试"
    }
}
