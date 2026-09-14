package com.evchargebook.trip

import com.evchargebook.domain.TripGpsHealthSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TripGpsHealthFeed {
    private val mutableSnapshot = MutableStateFlow<TripGpsHealthSnapshot?>(null)
    val snapshot: StateFlow<TripGpsHealthSnapshot?> = mutableSnapshot.asStateFlow()

    fun publish(snapshot: TripGpsHealthSnapshot) {
        mutableSnapshot.value = snapshot
    }

    fun clear() {
        mutableSnapshot.value = null
    }
}
