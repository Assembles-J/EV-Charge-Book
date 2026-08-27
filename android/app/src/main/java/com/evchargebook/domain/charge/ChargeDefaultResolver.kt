package com.evchargebook.domain.charge

/**
 * Resolves smart defaults for charge creation.
 * UI should ask the resolver instead of forcing repeated input.
 */
class ChargeDefaultResolver {

    fun resolveEndSoc(
        lastChargeEndSoc: Int?
    ): Int = lastChargeEndSoc ?: 100

    fun resolveStartSoc(
        currentVehicleSoc: Int?
    ): Int? = currentVehicleSoc
}
