package com.evchargebook.location

import android.location.Location
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Bridges location sources into TripTrackingService without coupling the source lifecycle
 * to business processing.
 */
class TripLocationCallbackDispatcher(
    private val scope: CoroutineScope,
    private val onLocation: suspend (Location) -> Unit
) {
    fun callback(): (Location) -> Unit = { location ->
        scope.launch {
            onLocation(location)
        }
    }
}
