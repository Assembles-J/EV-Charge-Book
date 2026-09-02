package com.evchargebook.vehicle.telemetry

import com.evchargebook.data.entity.VehicleEntity

/**
 * Read-only vehicle facts supplied by an explicitly configured provider.
 *
 * This contract does not grant permission to update VehicleState, Trip history or Charging history.
 * A later resolver owns any business-state integration after real source validation.
 */
interface VehicleTelemetryProvider {
    val providerId: String

    suspend fun capabilities(vehicle: VehicleEntity): VehicleTelemetryCapabilities

    suspend fun readSnapshot(vehicle: VehicleEntity): VehicleTelemetryReadResult
}

enum class VehicleTelemetryField {
    SOC_PERCENT,
    ODOMETER_KM,
    ESTIMATED_RANGE_KM,
    CHARGING_STATE,
    CHARGING_POWER_KW,
    PLUG_STATE,
    LOCK_STATE,
}

data class VehicleTelemetryCapabilities(
    val supportedFields: Set<VehicleTelemetryField>,
    val supportsSnapshotCaptureTime: Boolean = false,
    val supportsFieldCaptureTime: Boolean = false,
    val requiresNetwork: Boolean = false,
    val requiresExternalHardware: Boolean = false,
    val minimumRefreshIntervalMillis: Long? = null,
) {
    init {
        require(minimumRefreshIntervalMillis == null || minimumRefreshIntervalMillis >= 0L) {
            "minimumRefreshIntervalMillis must be non-negative"
        }
    }
}

enum class TelemetryValueSemantic {
    /** Direct/local measurement such as a decoded read-only OBD value. */
    DIRECT_MEASUREMENT,

    /** A factual state/value reported by an authorized provider. */
    PROVIDER_REPORTED,

    /** A provider/manufacturer estimate; never use as a measured energy/SOC substitute. */
    OEM_ESTIMATE,
}

/**
 * Field-level value metadata prevents one freshly updated field from making older fields look fresh.
 * `resolution` is the smallest known meaningful step in the field's native unit when known.
 */
data class TelemetryValue<T>(
    val value: T,
    val capturedAtEpochMillis: Long? = null,
    val resolution: Double? = null,
    val semantic: TelemetryValueSemantic = TelemetryValueSemantic.PROVIDER_REPORTED,
) {
    init {
        require(resolution == null || (resolution.isFinite() && resolution > 0.0)) {
            "resolution must be finite and positive"
        }
    }
}

enum class VehicleChargingState {
    NOT_CHARGING,
    CHARGING,
    COMPLETE,
    UNKNOWN,
}

enum class VehiclePlugState {
    DISCONNECTED,
    CONNECTED,
    UNKNOWN,
}

enum class VehicleLockState {
    UNLOCKED,
    LOCKED,
    UNKNOWN,
}

/**
 * One provider read. Provider/account/device identity must be opaque metadata, never an auth token,
 * client certificate or secret copied into persistence/logging.
 */
data class VehicleTelemetrySnapshot(
    val vehicleId: Long,
    val providerId: String,
    val providerVehicleIdentity: String? = null,
    val snapshotCapturedAtEpochMillis: Long? = null,
    val receivedAtEpochMillis: Long,
    val socPercent: TelemetryValue<Double>? = null,
    val odometerKm: TelemetryValue<Double>? = null,
    val estimatedRangeKm: TelemetryValue<Double>? = null,
    val chargingState: TelemetryValue<VehicleChargingState>? = null,
    val chargingPowerKw: TelemetryValue<Double>? = null,
    val plugState: TelemetryValue<VehiclePlugState>? = null,
    val lockState: TelemetryValue<VehicleLockState>? = null,
) {
    init {
        require(vehicleId > 0L) { "vehicleId must be positive" }
        require(providerId.isNotBlank()) { "providerId must not be blank" }
        require(receivedAtEpochMillis > 0L) { "receivedAtEpochMillis must be positive" }
        require(socPercent == null || (socPercent.value.isFinite() && socPercent.value in 0.0..100.0)) {
            "SOC must be finite and within 0..100"
        }
        require(odometerKm == null || (odometerKm.value.isFinite() && odometerKm.value >= 0.0)) {
            "odometer must be finite and non-negative"
        }
        require(
            estimatedRangeKm == null ||
                (estimatedRangeKm.value.isFinite() && estimatedRangeKm.value >= 0.0)
        ) {
            "estimated range must be finite and non-negative"
        }
        require(
            estimatedRangeKm == null ||
                estimatedRangeKm.semantic == TelemetryValueSemantic.OEM_ESTIMATE
        ) {
            "estimated range must remain estimate semantics"
        }
        require(
            chargingPowerKw == null ||
                (chargingPowerKw.value.isFinite() && chargingPowerKw.value >= 0.0)
        ) {
            "charging power must be finite and non-negative"
        }
    }

    fun availableFields(): Set<VehicleTelemetryField> = buildSet {
        if (socPercent != null) add(VehicleTelemetryField.SOC_PERCENT)
        if (odometerKm != null) add(VehicleTelemetryField.ODOMETER_KM)
        if (estimatedRangeKm != null) add(VehicleTelemetryField.ESTIMATED_RANGE_KM)
        if (chargingState != null) add(VehicleTelemetryField.CHARGING_STATE)
        if (chargingPowerKw != null) add(VehicleTelemetryField.CHARGING_POWER_KW)
        if (plugState != null) add(VehicleTelemetryField.PLUG_STATE)
        if (lockState != null) add(VehicleTelemetryField.LOCK_STATE)
    }
}

enum class VehicleTelemetryUnavailableReason {
    NOT_SUPPORTED,
    NOT_CONFIGURED,
    AUTHORIZATION_REQUIRED,
    OFFLINE,
    VEHICLE_ASLEEP,
    EXTERNAL_HARDWARE_MISSING,
}

sealed interface VehicleTelemetryReadResult {
    data class Success(val snapshot: VehicleTelemetrySnapshot) : VehicleTelemetryReadResult

    data class Unavailable(
        val reason: VehicleTelemetryUnavailableReason,
        val detail: String? = null,
    ) : VehicleTelemetryReadResult

    data class Failure(
        val detail: String? = null,
        val retryable: Boolean = true,
    ) : VehicleTelemetryReadResult
}

enum class TelemetryTimeAuthority {
    FIELD_CAPTURE_TIME,
    SNAPSHOT_CAPTURE_TIME,
    RECEIVE_TIME_ONLY,
}

data class TelemetryFreshness(
    val observedAtEpochMillis: Long,
    val ageMillis: Long,
    val authority: TelemetryTimeAuthority,
)

/**
 * Freshness is explicit about whether it is based on vehicle/provider capture time or only receipt.
 */
object VehicleTelemetryFreshness {
    fun <T> evaluate(
        snapshot: VehicleTelemetrySnapshot,
        value: TelemetryValue<T>,
        nowEpochMillis: Long,
    ): TelemetryFreshness {
        val fieldCapturedAt = value.capturedAtEpochMillis
        val snapshotCapturedAt = snapshot.snapshotCapturedAtEpochMillis
        val observedAt: Long
        val authority: TelemetryTimeAuthority
        when {
            fieldCapturedAt != null -> {
                observedAt = fieldCapturedAt
                authority = TelemetryTimeAuthority.FIELD_CAPTURE_TIME
            }
            snapshotCapturedAt != null -> {
                observedAt = snapshotCapturedAt
                authority = TelemetryTimeAuthority.SNAPSHOT_CAPTURE_TIME
            }
            else -> {
                observedAt = snapshot.receivedAtEpochMillis
                authority = TelemetryTimeAuthority.RECEIVE_TIME_ONLY
            }
        }
        return TelemetryFreshness(
            observedAtEpochMillis = observedAt,
            ageMillis = (nowEpochMillis - observedAt).coerceAtLeast(0L),
            authority = authority,
        )
    }
}
