package com.evchargebook.widget

/** Pure presentation rules that keep the widget honest about local vs live vehicle state. */
object VehicleWidgetTruthText {
    fun soc(snapshot: VehicleWidgetSnapshot): String =
        snapshot.currentSoc?.let { "$it%" } ?: "--"

    fun statusHeadline(snapshot: VehicleWidgetSnapshot): String = when {
        snapshot.activeTrip -> "● 行程记录中"
        snapshot.activeCharging -> "● 充电记录中"
        snapshot.bluetoothDetectionEnabled && snapshot.autoStartOnConnect -> "● 自动行程已就绪"
        snapshot.bluetoothDetectionEnabled -> "● 蓝牙检测已开启"
        snapshot.vehicleId != null -> "● 车辆已就绪"
        else -> "尚未添加车辆"
    }

    fun statusDetail(snapshot: VehicleWidgetSnapshot): String = when {
        snapshot.activeTrip -> "本地 Trip 正在记录"
        snapshot.activeCharging -> "本地充电记录正在进行"
        snapshot.bluetoothDetectionEnabled && snapshot.autoStartOnConnect ->
            "连接${deviceLabel(snapshot)}后尝试自动开始行程"
        snapshot.bluetoothDetectionEnabled ->
            "连接${deviceLabel(snapshot)}后提醒确认行程"
        snapshot.vehicleId != null -> "未开启蓝牙行程检测"
        else -> "打开 App 添加车辆"
    }

    fun stateLabel(
        snapshot: VehicleWidgetSnapshot,
        formatTime: (Long) -> String,
    ): String = when {
        snapshot.activeTrip -> "行程记录中 · 本地状态"
        snapshot.activeCharging -> "充电记录中 · 本地状态"
        snapshot.stateUpdatedAtEpochMillis != null ->
            "上次记录 · ${formatTime(snapshot.stateUpdatedAtEpochMillis)}"
        snapshot.vehicleId != null -> "尚无车辆状态记录"
        else -> "等待添加车辆"
    }

    fun tripAction(snapshot: VehicleWidgetSnapshot): String =
        if (snapshot.activeTrip) "打开行程" else "行程"

    fun appAction(snapshot: VehicleWidgetSnapshot): String =
        if (snapshot.activeCharging) "充电记录中" else "打开 App"

    private fun deviceLabel(snapshot: VehicleWidgetSnapshot): String =
        snapshot.bluetoothDeviceName?.takeIf { it.isNotBlank() } ?: "目标车载蓝牙"
}
