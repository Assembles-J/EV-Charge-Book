# EV Charge Book Next Product Track

## Purpose

Re-open product development after RC1 reliability work while keeping release blockers and feature work separated.

This document defines the next business-focused track. It does not replace RC1 acceptance requirements.

## Product Direction

EV Charge Book continues with:

- Local First data ownership
- real vehicle usage data
- explainable calculations
- simple maintainable architecture

## Priority 1: Trip Experience Improvement

### Trip reliability experience

Goals:

- make GPS interruptions understandable
- avoid users thinking the app silently stopped
- provide clear recording status

Planned:

- GPS recovery hints
- clearer foreground service state
- Trip diagnostic summary
- better interrupted-trip explanation

### Bluetooth assisted vehicle start

Goals:

Provide optional vehicle connection assistance.

Flow:

```
vehicle Bluetooth detected
        |
        v
user receives status hint
        |
        v
optional Trip start action
```

Requirements:

- no hard dependency on Bluetooth
- no automatic unsafe start behavior
- user remains in control

## Priority 2: Trip Speed Analysis

Introduce derived speed segments:

- segment average speed
- total average speed
- moving average speed
- maximum speed

Visualization direction:

- speed is vehicle speed data, not traffic judgment
- colors represent speed ranges only
- segments must respect GPS quality

## Priority 3: Location Intelligence

Add address interpretation layer:

```
latitude + longitude
        |
        v
reverse geocoding
        |
        v
human readable location
```

Requirements:

- cost controlled
- cache results
- protect user privacy
- do not block Trip recording

## Priority 4: UI Experience

Improve:

- navigation back behavior
- excessive top spacing
- information density
- Trip detail readability

## Execution Rule

Order:

1. RC1 reliability blockers
2. physical acceptance
3. Trip user experience improvements
4. analytics visualization
5. optional integrations

Do not start cloud sync, MapLibre dependency, or OBD-II product dependency before core Trip experience is stable.
