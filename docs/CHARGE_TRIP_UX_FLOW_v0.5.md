# EV Charge Book v0.5 Charge and Trip UX Flow

## Charge Flow

Goal: reduce repeated input through remembered defaults.

Default behavior:

- start SOC comes from VehicleState.currentSoc
- end SOC comes from previous charging record end SOC, fallback 100
- price comes from previous same charging type
- location uses current location when available

Energy separation:

- chargedEnergy: energy delivered by charger/meter
- receivedEnergy: energy gained by vehicle battery
- energyLoss: charging loss

## Trip Flow

Start trip:

- read VehicleState
- create ActiveTripSession
- store start SOC and start mileage

End trip:

User confirms:

- end SOC
- end mileage

System calculates:

- distance
- consumed energy
- average consumption

After completion:

- create TripRecord
- update VehicleState

## UX Principle

Users confirm state changes instead of repeatedly entering known information.
