# Trip Location Migration Checklist

## Goal

Replace direct LocationManager usage in TripTrackingService with TripLocationSource while keeping trip calculation rules unchanged.

## Keep unchanged

- TripContinuityRules
- TripSamplingRules
- trusted distance calculation
- LONG_GAP semantics
- database schema

## Migration steps

1. Replace direct LocationManager registration with TripLocationSourceSession.
2. Route callbacks through TripLocationCallbackDispatcher.
3. Keep handleLocation as the single point-processing entry.
4. Verify replay GPS before physical driving acceptance.
5. Verify Fused Location on lock screen and background app scenarios.

## Acceptance

- Replay route can complete without driving.
- Screen-off test does not lose callbacks unexpectedly.
- Real vehicle test confirms Android background behavior.
