package com.evchargebook.location

import android.location.Location

/**
 * Development-only GPS replay source.
 *
 * The first version intentionally keeps playback deterministic. The same Trip pipeline can be
 * exercised with recorded coordinates before validating on a physical drive.
 */
class ReplayTripLocationSource(
    private val locations: List<Location>
) : TripLocationSource {

    private var running = false

    override fun start(callback: (Location) -> Unit) {
        running = true
        locations.forEach { location ->
            if (!running) return
            callback(location)
        }
    }

    override fun stop() {
        running = false
    }
}
