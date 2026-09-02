package com.evchargebook.location

import android.location.Location

/**
 * Low-volume source-level signal used for diagnostics only.
 *
 * A signal never changes Trip truth rules by itself. Location points remain the only input to
 * continuity, sampling and persistence decisions in TripTrackingService.
 */
data class TripLocationSourceSignal(
    val source: String,
    val availability: Boolean? = null,
    val detail: String? = null,
)

/**
 * Location-source boundary for Trip tracking.
 *
 * Production currently uses FusedLocationProviderClient. The service keeps all
 * continuity, sampling and persistence decisions outside this source.
 */
interface TripLocationSource {
    fun start(
        callback: (Location) -> Unit,
        signalCallback: (TripLocationSourceSignal) -> Unit = {},
    )

    fun stop()
}
