package com.evchargebook.autotrip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoTripEligibilityPolicyTest {
    @Test
    fun `off mode always ignores`() {
        val decision = decide(mode = AutoTripMode.OFF)

        assertDecision<AutoTripDecision.Ignore>(decision, AutoTripReasonCode.OFF)
    }

    @Test
    fun `unbound device is ignored`() {
        val decision = decide(deviceBound = false)

        assertDecision<AutoTripDecision.Ignore>(decision, AutoTripReasonCode.DEVICE_NOT_BOUND)
    }

    @Test
    fun `disconnected target cannot create candidate`() {
        val decision = decide(bluetoothConnected = false)

        assertDecision<AutoTripDecision.Ignore>(decision, AutoTripReasonCode.BLUETOOTH_NOT_CONNECTED)
    }

    @Test
    fun `active trip blocks another start path`() {
        val decision = decide(activeTripExists = true)

        assertDecision<AutoTripDecision.Block>(decision, AutoTripReasonCode.ACTIVE_TRIP_EXISTS)
    }

    @Test
    fun `ignored session remains suppressed for current epoch`() {
        val decision = decide(detectionState = AutoTripDetectionState.IGNORED)

        assertDecision<AutoTripDecision.Ignore>(decision, AutoTripReasonCode.SESSION_IGNORED)
    }

    @Test
    fun `expired session is ignored`() {
        val decision = decide(detectionState = AutoTripDetectionState.EXPIRED)

        assertDecision<AutoTripDecision.Ignore>(decision, AutoTripReasonCode.SESSION_EXPIRED)
    }

    @Test
    fun `recording lifecycle state cannot start again`() {
        val decision = decide(detectionState = AutoTripDetectionState.RECORDING)

        assertDecision<AutoTripDecision.Block>(decision, AutoTripReasonCode.SESSION_NOT_ELIGIBLE)
    }

    @Test
    fun `cooldown suppresses a new candidate`() {
        val decision = decide(cooldownActive = true)

        assertDecision<AutoTripDecision.Ignore>(decision, AutoTripReasonCode.COOLDOWN_ACTIVE)
    }

    @Test
    fun `prompt only never auto starts even with trusted movement`() {
        val decision = decide(
            mode = AutoTripMode.PROMPT_ONLY,
            hasTrustedMovement = true,
            inVehicleDetected = true,
        )

        assertDecision<AutoTripDecision.Prompt>(decision, AutoTripReasonCode.PROMPT_ONLY)
    }

    @Test
    fun `missing notification permission blocks visible automation`() {
        val decision = decide(notificationPermissionGranted = false)

        assertDecision<AutoTripDecision.Block>(
            decision,
            AutoTripReasonCode.NOTIFICATION_PERMISSION_MISSING,
        )
    }

    @Test
    fun `verified auto start requires location permission`() {
        val decision = decide(locationPermissionGranted = false)

        assertDecision<AutoTripDecision.Block>(decision, AutoTripReasonCode.LOCATION_PERMISSION_MISSING)
    }

    @Test
    fun `verified auto start requires location provider`() {
        val decision = decide(locationProviderAvailable = false)

        assertDecision<AutoTripDecision.Block>(decision, AutoTripReasonCode.PROVIDER_UNAVAILABLE)
    }

    @Test
    fun `background FGS denial downgrades to prompt`() {
        val decision = decide(
            foregroundServiceStartAllowed = false,
            hasTrustedMovement = true,
        )

        assertDecision<AutoTripDecision.Prompt>(decision, AutoTripReasonCode.FGS_START_NOT_ALLOWED)
    }

    @Test
    fun `bluetooth alone waits for driving evidence`() {
        val decision = decide()

        assertDecision<AutoTripDecision.Verify>(decision, AutoTripReasonCode.WAITING_FOR_EVIDENCE)
    }

    @Test
    fun `activity recognition alone is not enough to start`() {
        val decision = decide(inVehicleDetected = true)

        assertDecision<AutoTripDecision.Verify>(decision, AutoTripReasonCode.WAITING_FOR_EVIDENCE)
    }

    @Test
    fun `trusted movement can verify start without activity recognition`() {
        val decision = decide(hasTrustedMovement = true)

        assertDecision<AutoTripDecision.Start>(decision, AutoTripReasonCode.VERIFIED_BY_MOVEMENT)
    }

    @Test
    fun `trusted movement plus in vehicle evidence records stronger reason`() {
        val decision = decide(
            hasTrustedMovement = true,
            inVehicleDetected = true,
        )

        assertDecision<AutoTripDecision.Start>(
            decision,
            AutoTripReasonCode.VERIFIED_BY_MOVEMENT_AND_ACTIVITY,
        )
    }

    private fun decide(
        mode: AutoTripMode = AutoTripMode.VERIFIED_AUTO_START,
        deviceBound: Boolean = true,
        bluetoothConnected: Boolean = true,
        detectionState: AutoTripDetectionState = AutoTripDetectionState.VERIFYING_DRIVE,
        activeTripExists: Boolean = false,
        cooldownActive: Boolean = false,
        notificationPermissionGranted: Boolean = true,
        locationPermissionGranted: Boolean = true,
        locationProviderAvailable: Boolean = true,
        foregroundServiceStartAllowed: Boolean = true,
        hasTrustedMovement: Boolean = false,
        inVehicleDetected: Boolean = false,
    ): AutoTripDecision = AutoTripEligibilityPolicy.decide(
        AutoTripEligibilityInput(
            mode = mode,
            deviceBound = deviceBound,
            detectionState = detectionState,
            activeTripExists = activeTripExists,
            cooldownActive = cooldownActive,
            notificationPermissionGranted = notificationPermissionGranted,
            locationPermissionGranted = locationPermissionGranted,
            locationProviderAvailable = locationProviderAvailable,
            foregroundServiceStartAllowed = foregroundServiceStartAllowed,
            evidence = DriveEvidenceSummary(
                bluetoothConnected = bluetoothConnected,
                hasTrustedMovement = hasTrustedMovement,
                inVehicleDetected = inVehicleDetected,
            ),
        ),
    )

    private inline fun <reified T : AutoTripDecision> assertDecision(
        decision: AutoTripDecision,
        reason: AutoTripReasonCode,
    ) {
        assertTrue("Expected ${T::class.simpleName}, got ${decision::class.simpleName}", decision is T)
        assertEquals(reason, decision.reason)
    }
}
