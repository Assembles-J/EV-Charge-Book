package com.evchargebook.location

import android.content.Context

/**
 * Keeps TripTrackingService independent from the concrete location source.
 *
 * Production defaults to fused location. Replay remains an explicit development/testing source.
 */
object TripLocationSourceFactory {
    fun production(context: Context): TripLocationSource =
        FusedTripLocationSource(context)
}
