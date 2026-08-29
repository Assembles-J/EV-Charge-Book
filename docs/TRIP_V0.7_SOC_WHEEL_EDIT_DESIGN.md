# Trip v0.7 SOC Wheel Edit Interaction Design

Status: Proposed UI/UX baseline
Owner: #205

## Goal

Improve Trip completion so EV users can correct imperfect SOC facts without losing data truth.

## Interaction

The completion screen directly contains SOC adjustment controls.

Do not use:

- text input
- secondary edit dialog
- bottom sheet picker
- extra confirmation step

Use:

- compact inline wheel selectors
- start SOC and end SOC side-by-side
- selected value centered
- only minimal nearby faded values

Example:

```
开始电量          结束电量
 77                60
[78%]            [61%]
 79                62
```

## SOC Rules

### Start SOC

Default source:

VehicleState snapshot.

Editable because:

- sentry/security drain
- app not active during part of journey
- user has more accurate knowledge

Data model direction:

- startSocSnapshot
- startSocOverride
- effectiveStartSoc

### End SOC

Default source priority:

1. Real vehicle/user provided value.
2. Historical consumption estimation.
3. Vehicle/model default consumption.
4. Generic EV fallback around 15 kWh/100km.

The estimated value initializes the wheel selection. It is not displayed as a separate remaining SOC card.

## Layout

Order:

1. Start/end endpoint component
2. Trip summary metrics
3. SOC wheel adjustment
4. Compact route preview
5. Save and finish action

## Route Preview

Use existing truthful Trip geometry:

- green start marker
- red completed endpoint flag
- LONG_GAP remains disconnected
- no fake route generation

## Data Truth

Estimated SOC must never be presented as BMS measured data.

The UI should distinguish:

- measured
- user corrected
- estimated

## Acceptance

- compact Android layout
- 320-360dp support
- fontScale 1.3 support
- existing TripEnergyCalculator semantics preserved
