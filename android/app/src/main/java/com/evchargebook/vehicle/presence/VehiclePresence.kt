package com.evchargebook.vehicle.presence

import android.content.Context
import com.evchargebook.autotrip.AutoTripCandidateResult
import com.evchargebook.autotrip.AutoTripPromptCoordinator

/**
 * A bounded observation that a configured vehicle-facing device is present or connected.
 *
 * Presence is intentionally not telemetry and not proof of driving. The existing per-vehicle
 * Bluetooth binding remains the authority that resolves a device address to a vehicle before any
 * Trip candidate can be created.
 */
data class VehiclePresenceEvent(
    val state: VehiclePresenceState,
    val source: VehiclePresenceSource,
    val deviceAddress: String,
    val deviceName: String? = null,
    val observedAtEpochMillis: Long = System.currentTimeMillis(),
) {
    init {
        require(deviceAddress.isNotBlank()) { "deviceAddress must not be blank" }
    }
}

enum class VehiclePresenceState {
    /** Device is nearby/observable, but a transport connection has not been established. */
    PRESENT,

    /** A transport connection that can feed the existing auto-Trip candidate flow is active. */
    CONNECTED,

    /** The previously connected transport is no longer connected. */
    DISCONNECTED,
}

enum class VehiclePresenceSource {
    /** Android Bluetooth ACL connect/disconnect broadcast. */
    CLASSIC_ACL,

    /** Future Android Companion Device presence/connection callback. */
    COMPANION_DEVICE,

    /** Foreground reconciliation against already-connected A2DP/HEADSET profiles. */
    FOREGROUND_CONNECTION_CHECK,
}

sealed interface VehiclePresenceDispatchResult {
    /** Presence-only observations are recorded as observations, not Trip candidates. */
    data object ObservedOnly : VehiclePresenceDispatchResult

    /** A connected observation was handed to the existing auto-Trip candidate authority. */
    data class Candidate(val result: AutoTripCandidateResult) : VehiclePresenceDispatchResult

    /** A disconnect was handed to the existing session close/cancel path. */
    data object Disconnected : VehiclePresenceDispatchResult
}

/**
 * Small boundary between platform-specific presence sources and the existing #235 auto-Trip flow.
 *
 * Providers must not create Trips themselves. CONNECTED observations are delegated to the existing
 * AutoTripPromptCoordinator, which still owns per-vehicle binding, session dedupe and the route to
 * TripStartCoordinator. PRESENT is deliberately non-authoritative in this first slice.
 */
class VehiclePresenceDispatcher(
    private val sink: VehiclePresenceCandidateSink,
) {
    suspend fun dispatch(event: VehiclePresenceEvent): VehiclePresenceDispatchResult =
        when (event.state) {
            VehiclePresenceState.PRESENT -> VehiclePresenceDispatchResult.ObservedOnly
            VehiclePresenceState.CONNECTED -> VehiclePresenceDispatchResult.Candidate(
                sink.onConnected(event)
            )
            VehiclePresenceState.DISCONNECTED -> {
                sink.onDisconnected(event)
                VehiclePresenceDispatchResult.Disconnected
            }
        }

    companion object {
        fun forAutoTrip(context: Context): VehiclePresenceDispatcher =
            VehiclePresenceDispatcher(
                AutoTripVehiclePresenceSink(
                    AutoTripPromptCoordinator(context.applicationContext)
                )
            )
    }
}

/** Injectable seam so the presence contract can be tested without Android framework state. */
interface VehiclePresenceCandidateSink {
    suspend fun onConnected(event: VehiclePresenceEvent): AutoTripCandidateResult
    suspend fun onDisconnected(event: VehiclePresenceEvent)
}

private class AutoTripVehiclePresenceSink(
    private val coordinator: AutoTripPromptCoordinator,
) : VehiclePresenceCandidateSink {
    override suspend fun onConnected(event: VehiclePresenceEvent): AutoTripCandidateResult =
        coordinator.onBluetoothConnected(
            deviceAddress = event.deviceAddress,
            deviceName = event.deviceName,
            now = event.observedAtEpochMillis,
        )

    override suspend fun onDisconnected(event: VehiclePresenceEvent) {
        coordinator.onBluetoothDisconnected(
            deviceAddress = event.deviceAddress,
            now = event.observedAtEpochMillis,
        )
    }
}
