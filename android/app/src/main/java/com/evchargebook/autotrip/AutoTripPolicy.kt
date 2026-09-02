package com.evchargebook.autotrip

/**
 * Evidence-policy behavior for vehicle Bluetooth automation.
 *
 * This pure Kotlin policy is intentionally distinct from the production per-vehicle
 * `autoStartOnConnect` setting. That setting is a user-defined direct automation shortcut; it must
 * not be described as VERIFIED_AUTO_START or as proof that the app detected real driving.
 */
enum class AutoTripMode {
    OFF,
    PROMPT_ONLY,
    VERIFIED_AUTO_START,
}

/**
 * Product-level lifecycle for one Bluetooth detection session.
 */
enum class AutoTripDetectionState {
    IDLE,
    BLUETOOTH_CANDIDATE,
    VERIFYING_DRIVE,
    READY_TO_START,
    STARTING,
    RECORDING,
    POSSIBLE_END,
    END_CONFIRMATION,
    IGNORED,
    EXPIRED,
    START_FAILED,
    BLOCKED,
}

/**
 * Stable, machine-readable reasons used by policy decisions, tests and future local audit logs.
 */
enum class AutoTripReasonCode {
    OFF,
    DEVICE_NOT_BOUND,
    BLUETOOTH_NOT_CONNECTED,
    ACTIVE_TRIP_EXISTS,
    SESSION_IGNORED,
    SESSION_EXPIRED,
    SESSION_NOT_ELIGIBLE,
    COOLDOWN_ACTIVE,
    NOTIFICATION_PERMISSION_MISSING,
    LOCATION_PERMISSION_MISSING,
    PROVIDER_UNAVAILABLE,
    FGS_START_NOT_ALLOWED,
    PROMPT_ONLY,
    WAITING_FOR_EVIDENCE,
    VERIFIED_BY_MOVEMENT,
    VERIFIED_BY_MOVEMENT_AND_ACTIVITY,
}

/**
 * Pre-classified evidence produced by a future bounded evidence collector.
 *
 * Thresholds do not live here. The collector is responsible for deciding whether movement is
 * trusted according to the current location quality rules. This keeps policy free of magic
 * distance/speed constants and lets Phase 0/Shadow Mode calibrate them separately.
 */
data class DriveEvidenceSummary(
    val bluetoothConnected: Boolean,
    val hasTrustedMovement: Boolean,
    val inVehicleDetected: Boolean = false,
)

/**
 * Complete policy input. Callers must provide current capability/permission state instead of
 * allowing the policy to reach into Android framework APIs.
 */
data class AutoTripEligibilityInput(
    val mode: AutoTripMode,
    val deviceBound: Boolean,
    val detectionState: AutoTripDetectionState = AutoTripDetectionState.IDLE,
    val activeTripExists: Boolean = false,
    val cooldownActive: Boolean = false,
    val notificationPermissionGranted: Boolean = true,
    val locationPermissionGranted: Boolean = true,
    val locationProviderAvailable: Boolean = true,
    val foregroundServiceStartAllowed: Boolean = true,
    val evidence: DriveEvidenceSummary,
)

sealed class AutoTripDecision(open val reason: AutoTripReasonCode) {
    data class Ignore(override val reason: AutoTripReasonCode) : AutoTripDecision(reason)

    data class Prompt(override val reason: AutoTripReasonCode) : AutoTripDecision(reason)

    data class Verify(override val reason: AutoTripReasonCode) : AutoTripDecision(reason)

    data class Start(override val reason: AutoTripReasonCode) : AutoTripDecision(reason)

    data class Block(override val reason: AutoTripReasonCode) : AutoTripDecision(reason)
}

/**
 * Pure decision policy for #235.
 *
 * Important boundaries:
 * - Bluetooth is a candidate signal, never proof of driving by itself.
 * - PROMPT_ONLY never returns Start.
 * - VERIFIED_AUTO_START fails closed when required capabilities are unavailable.
 * - FGS background-start denial downgrades to a user-visible prompt rather than a silent start.
 * - Failed/terminal sessions never silently retry themselves.
 * - No Trip is created here; a future TripStartCoordinator owns that authority.
 */
object AutoTripEligibilityPolicy {
    fun decide(input: AutoTripEligibilityInput): AutoTripDecision {
        if (input.mode == AutoTripMode.OFF) {
            return AutoTripDecision.Ignore(AutoTripReasonCode.OFF)
        }

        if (!input.deviceBound) {
            return AutoTripDecision.Ignore(AutoTripReasonCode.DEVICE_NOT_BOUND)
        }

        if (!input.evidence.bluetoothConnected) {
            return AutoTripDecision.Ignore(AutoTripReasonCode.BLUETOOTH_NOT_CONNECTED)
        }

        if (input.activeTripExists) {
            return AutoTripDecision.Block(AutoTripReasonCode.ACTIVE_TRIP_EXISTS)
        }

        when (input.detectionState) {
            AutoTripDetectionState.IGNORED ->
                return AutoTripDecision.Ignore(AutoTripReasonCode.SESSION_IGNORED)

            AutoTripDetectionState.EXPIRED ->
                return AutoTripDecision.Ignore(AutoTripReasonCode.SESSION_EXPIRED)

            AutoTripDetectionState.STARTING,
            AutoTripDetectionState.RECORDING,
            AutoTripDetectionState.POSSIBLE_END,
            AutoTripDetectionState.END_CONFIRMATION,
            AutoTripDetectionState.START_FAILED,
            AutoTripDetectionState.BLOCKED,
            -> return AutoTripDecision.Block(AutoTripReasonCode.SESSION_NOT_ELIGIBLE)

            else -> Unit
        }

        if (input.cooldownActive) {
            return AutoTripDecision.Ignore(AutoTripReasonCode.COOLDOWN_ACTIVE)
        }

        if (!input.notificationPermissionGranted) {
            return AutoTripDecision.Block(AutoTripReasonCode.NOTIFICATION_PERMISSION_MISSING)
        }

        if (input.mode == AutoTripMode.PROMPT_ONLY) {
            return AutoTripDecision.Prompt(AutoTripReasonCode.PROMPT_ONLY)
        }

        if (!input.locationPermissionGranted) {
            return AutoTripDecision.Block(AutoTripReasonCode.LOCATION_PERMISSION_MISSING)
        }

        if (!input.locationProviderAvailable) {
            return AutoTripDecision.Block(AutoTripReasonCode.PROVIDER_UNAVAILABLE)
        }

        if (!input.foregroundServiceStartAllowed) {
            return AutoTripDecision.Prompt(AutoTripReasonCode.FGS_START_NOT_ALLOWED)
        }

        if (!input.evidence.hasTrustedMovement) {
            return AutoTripDecision.Verify(AutoTripReasonCode.WAITING_FOR_EVIDENCE)
        }

        return if (input.evidence.inVehicleDetected) {
            AutoTripDecision.Start(AutoTripReasonCode.VERIFIED_BY_MOVEMENT_AND_ACTIVITY)
        } else {
            AutoTripDecision.Start(AutoTripReasonCode.VERIFIED_BY_MOVEMENT)
        }
    }
}
