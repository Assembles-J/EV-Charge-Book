package com.evchargebook.location

/**
 * Small lifecycle holder used while migrating TripTrackingService away from direct
 * Android location APIs.
 *
 * The service owns the lifecycle. This class only keeps the active source reference
 * so replay and production sources share the same start/stop contract.
 */
class TripLocationSourceLifecycle {
    private var source: TripLocationSource? = null

    fun attach(newSource: TripLocationSource) {
        source?.stop()
        source = newSource
    }

    fun start(callback: (android.location.Location) -> Unit) {
        source?.start(callback)
    }

    fun stop() {
        source?.stop()
    }
}
