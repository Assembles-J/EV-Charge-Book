package com.evchargebook.domain.trip

/**
 * Trip v0.7 SOC adjustment foundation.
 *
 * Keeps original measured snapshots separate from user corrections.
 * The UI may present an editable SOC value without overwriting the original fact.
 */
data class TripSocAdjustmentV07(
    val startSocSnapshot: Int?,
    val startSocOverride: Int?,
    val endSocSnapshot: Int?,
    val endSocOverride: Int?
) {
    val effectiveStartSoc: Int?
        get() = startSocOverride ?: startSocSnapshot

    val effectiveEndSoc: Int?
        get() = endSocOverride ?: endSocSnapshot
}

enum class TripSocSourceV07 {
    VEHICLE_SNAPSHOT,
    USER_OVERRIDE,
    ESTIMATED
}
