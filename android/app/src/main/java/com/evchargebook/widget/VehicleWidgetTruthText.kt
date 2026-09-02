package com.evchargebook.widget

/** Pure presentation rules that keep the Stage 1 widget honest about local vs live vehicle state. */
object VehicleWidgetTruthText {
    fun soc(snapshot: VehicleWidgetSnapshot): String =
        snapshot.currentSoc?.let { "$it%" } ?: "--"

    fun stateLabel(
        snapshot: VehicleWidgetSnapshot,
        formatTime: (Long) -> String,
    ): String = when {
        snapshot.activeTrip -> "行程记录中 · 本地状态"
        snapshot.activeCharging -> "充电记录中 · 本地状态"
        snapshot.stateUpdatedAtEpochMillis != null ->
            "上次记录 · ${formatTime(snapshot.stateUpdatedAtEpochMillis)}"
        snapshot.vehicleId != null -> "尚无车辆状态记录"
        else -> "打开 App 添加车辆"
    }

    fun tripAction(snapshot: VehicleWidgetSnapshot): String =
        if (snapshot.activeTrip) "打开行程" else "行程"

    fun appAction(snapshot: VehicleWidgetSnapshot): String =
        if (snapshot.activeCharging) "充电记录中" else "打开 App"
}
