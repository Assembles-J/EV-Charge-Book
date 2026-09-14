package com.evchargebook.autotrip

data class AutoTripNotificationMessage(
    val title: String,
    val text: String,
    val actionLabel: String? = null,
)

/** Pure notification copy so Bluetooth automation stays truthful and unit-testable. */
object AutoTripNotificationCopy {
    fun candidate(
        vehicleLabel: String,
        autoStartUserActionRequired: Boolean,
    ): AutoTripNotificationMessage = if (autoStartUserActionRequired) {
        AutoTripNotificationMessage(
            title = "已连接 $vehicleLabel · 自动开始待确认",
            text = "系统限制后台自动启动，点击即可开始记录。",
            actionLabel = "开始行程",
        )
    } else {
        AutoTripNotificationMessage(
            title = "已连接 $vehicleLabel",
            text = "是否开始本次行程？",
            actionLabel = "立即开始",
        )
    }

    fun autoStarted(vehicleLabel: String): AutoTripNotificationMessage =
        AutoTripNotificationMessage(
            title = "$vehicleLabel 行程已自动开始",
            text = "由车辆蓝牙连接触发 · 正在记录",
        )
}
