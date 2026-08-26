package com.evchargebook.domain

object TripRules {
    fun elapsedSeconds(startedAtEpochMillis: Long, endedAtEpochMillis: Long): Long {
        require(endedAtEpochMillis >= startedAtEpochMillis) { "结束时间不能早于开始时间" }
        return (endedAtEpochMillis - startedAtEpochMillis) / 1000
    }

    fun requireCanStart(hasActiveTrip: Boolean) {
        require(!hasActiveTrip) { "已有进行中的行程，请先结束或恢复它" }
    }
}
