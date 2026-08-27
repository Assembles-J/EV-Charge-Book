# Charge Smart Defaults v0.5

## Goal

Reduce repeated charging input by using VehicleState and previous records.

## Default Rules

Start SOC:

```
VehicleState.currentSoc
```

End SOC:

```
Last charging record endSoc

or

100 when no history exists
```

## Energy Model

Two values are maintained:

- chargedEnergy: charger/meter input
- receivedEnergy: battery energy increase calculated from SOC

Loss:

```
energyLoss = chargedEnergy - receivedEnergy
```

## Save Flow

```
Create ChargingRecord
        |
Calculate energy values
        |
Update VehicleState.currentSoc
```
