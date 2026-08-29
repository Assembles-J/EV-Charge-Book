package com.evchargebook.location

import android.location.Location

/**
 * Small lifecycle wrapper used while migrating TripTrackingService away from
 * concrete Android location APIs.
 *
 * The service owns the session; the source owns how locations are produced.
 */
class TripLocationSourceSession(
    private val source: TripLocationSource
) {
    fun start(onLocation: (Location) -> Unit) {
        source.start(onLocation)
    }

    fun stop() {
        source.stop()
    }
}
