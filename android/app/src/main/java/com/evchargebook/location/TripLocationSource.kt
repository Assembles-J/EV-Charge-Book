package com.evchargebook.location

import android.location.Location

/**
 * Source abstraction for Trip tracking.
 *
 * Production can provide real device locations. Development builds can replay recorded traces
 * without requiring a vehicle drive session.
 */
interface TripLocationSource {
    fun start(callback: (Location) -> Unit)

    fun stop()
}
