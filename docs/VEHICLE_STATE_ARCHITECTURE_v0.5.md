# EV Charge Book v0.5 Vehicle State Architecture

## Goal

EV Charge Book moves from a record-only app to a vehicle-state driven EV management app.

Core principle:

`VehicleState` is the single source of truth for dynamic vehicle information.

## Core Model

```
Vehicle
  |
VehicleState
  |
+-------------+-------------+
|                           |
ChargingRecord          TripRecord
|                           |
SOC increase           SOC decrease
```

## VehicleState

Stores current dynamic state:

- current SOC
- current mileage
- last update time
- update source

Update sources:

- CHARGE_RECORD
- TRIP_END
- MANUAL_UPDATE
- IMPORT

## State Update Rules

Charging completion:

```
VehicleState.currentSoc = ChargingRecord.endSoc
```

Trip completion:

```
VehicleState.currentSoc = TripRecord.endSoc
VehicleState.currentMileage = TripRecord.endMileage
```

## Design Principle

Every future page should consume VehicleState instead of asking users to repeatedly input the same information.
