package com.evchargebook.domain

/**
 * Cross-flow truth guard for one physical vehicle.
 *
 * A vehicle cannot truthfully be driving and actively charging at the same time. Keep this policy
 * independent from UI so manual, Bluetooth and automatic Trip starts share the same rule.
 */
object VehicleActivityConflictPolicy {
    fun chargingStartBlockReason(
        vehicleId: Long,
        activeTripVehicleId: Long?,
    ): String? = if (activeTripVehicleId == vehicleId) {
        "这辆车有进行中的行程，请先结束行程"
    } else {
        null
    }

    fun tripStartBlockReason(
        vehicleId: Long,
        activeChargingVehicleId: Long?,
    ): String? = if (activeChargingVehicleId == vehicleId) {
        "这辆车正在充电，请先结束或取消充电"
    } else {
        null
    }
}
