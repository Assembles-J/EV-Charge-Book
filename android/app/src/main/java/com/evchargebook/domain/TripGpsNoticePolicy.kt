package com.evchargebook.domain

object TripGpsNoticePolicy {
    fun statusText(status: TripGpsHealthStatus): String = when (status) {
        TripGpsHealthStatus.WAITING -> "正在等待首次定位"
        TripGpsHealthStatus.GOOD -> "GPS 正常"
        TripGpsHealthStatus.DEGRADED -> "GPS 信号较弱，正在恢复定位"
        TripGpsHealthStatus.LOST -> "GPS 信号已中断，行程仍在记录"
        TripGpsHealthStatus.LONG_GAP -> "GPS 长时间中断，请检查定位/省电设置"
    }

    fun transitionNotice(
        previous: TripGpsHealthStatus?,
        current: TripGpsHealthStatus,
    ): String? {
        if (previous == null || previous == current) return null
        if (
            current == TripGpsHealthStatus.GOOD &&
            previous in setOf(
                TripGpsHealthStatus.DEGRADED,
                TripGpsHealthStatus.LOST,
                TripGpsHealthStatus.LONG_GAP,
            )
        ) {
            return "GPS 已恢复"
        }
        return when (current) {
            TripGpsHealthStatus.DEGRADED,
            TripGpsHealthStatus.LOST,
            TripGpsHealthStatus.LONG_GAP -> statusText(current)
            TripGpsHealthStatus.WAITING,
            TripGpsHealthStatus.GOOD -> null
        }
    }
}
