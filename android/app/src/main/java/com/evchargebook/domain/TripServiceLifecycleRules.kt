package com.evchargebook.domain

import com.evchargebook.data.entity.TripStatus

object TripServiceLifecycleRules {
    fun shouldRecordUnexpectedDestroy(status: String?): Boolean =
        status == TripStatus.RECORDING || status == TripStatus.INTERRUPTED
}
