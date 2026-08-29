package com.evchargebook.location

import android.location.Location

/**
 * Small adapter used while migrating TripTrackingService away from direct
 * Android location APIs.
 *
 * Keeps the callback contract identical for real and replay sources.
 */
class TripLocationSourceAdapter(
    private val source: TripLocationSource
) {
    fun start(onLocation: (Location) -> Unit) {
        source.start(onLocation)
    }

    fun stop() {
        source.stop()
    }
}
