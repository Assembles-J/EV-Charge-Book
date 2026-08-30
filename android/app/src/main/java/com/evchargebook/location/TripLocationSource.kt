package com.evchargebook.location

import android.location.Location

/**
 * Location-source boundary for Trip tracking.
 *
 * Production currently uses FusedLocationProviderClient. The service keeps all
 * continuity, sampling and persistence decisions outside this source.
 */
interface TripLocationSource {
    fun start(callback: (Location) -> Unit)
    fun stop()
}
