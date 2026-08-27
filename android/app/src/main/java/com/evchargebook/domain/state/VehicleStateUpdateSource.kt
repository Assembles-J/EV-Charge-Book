package com.evchargebook.domain.state

enum class VehicleStateUpdateSource {
    CHARGE_RECORD,
    TRIP_END,
    MANUAL_UPDATE,
    IMPORT,
    MIGRATION
}
