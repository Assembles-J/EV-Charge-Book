# Trip v0.7 SOC Wheel Edit Interaction Design

Status: Implemented through PR #211; physical-device acceptance pending
Owner: #205
Implementation baseline: `113c15eb06d5c919465e28daae5fe87e5f80d801`

## Goal

Improve Trip completion so EV users can correct imperfect SOC facts without losing the original start-SOC snapshot or presenting an estimate as a BMS measurement.

## Approved interaction

The completion screen directly contains SOC adjustment controls.

Do not use:

- SOC text input
- secondary edit dialog
- bottom sheet picker
- a separate estimated-remaining-SOC card
- a secondary bottom `继续行驶` button

Use:

- compact inline wheel selectors
- start SOC and end SOC side-by-side
- selected value centered
- only one nearby faded value above and below
- one primary `保存并结束` action
- the screen back action to return to the active Trip without stopping it

Example:

```text
开始电量          结束电量
 77                60
[78%]            [61%]
 79                62
```

## Start SOC

The Trip start captures the current VehicleState SOC as both the effective start SOC and the original `startSocSnapshot`.

The completion wheel is editable for cases such as:

- sentry/security drain;
- the app was not active during part of the journey;
- the user knows a more accurate starting SOC.

When the saved wheel value differs from the original snapshot, the completed Trip stores:

- `startSocSnapshot`: original captured fact;
- `startSocOverride`: user correction;
- `startSoc`: effective value used by existing Trip energy calculations.

Room schema v12 backfills historical `startSocSnapshot` from the old `startSoc` value.

## End SOC initialization

There is no separate `预计剩余电量` component.

The estimated end SOC is only the initial value of the end-SOC wheel. The user may adjust it before saving, and save is the explicit confirmation of the final value.

The current v0.7 implementation uses `TripEndSocEstimator`:

1. require a valid start SOC;
2. use Trip distance and battery capacity when available;
3. use a supplied reasonable average-consumption value when one is available;
4. otherwise use the generic EV fallback `15 kWh/100km`;
5. clamp the result to `0..100`.

The estimator is deliberately not labeled or persisted as a BMS measurement source.

### Future estimator refinement

Historical per-vehicle/model consumption learning is not part of the current implementation. It can later replace the generic fallback without changing the approved wheel interaction.

## Completion layout

Order:

1. compact start/end endpoint component;
2. Trip summary metrics: distance, duration and calculated average consumption;
3. side-by-side SOC wheel adjustment;
4. compact truthful route preview;
5. one `保存并结束` action.

### Endpoint address truth

PR #211 closes the remaining endpoint fidelity gap while preserving the location truth boundary:

- start/end are derived only from persisted Trip points;
- the existing `AndroidGeocoderAddressResolver` attempts a real Chinese-readable reverse-geocoded address;
- while resolution is running, the UI shows `地址解析中…`;
- when Android Geocoder is unavailable or returns no usable result, the UI falls back to the persisted latitude/longitude;
- no synthetic or guessed address is ever displayed;
- the resolver uses the existing in-memory geocode cache and does not change stored Trip coordinates.

## Route preview

Use persisted Trip points only:

- green start marker;
- red completed endpoint flag;
- segments with a capture-time gap of at least `TripContinuityRules.LONG_GAP_SECONDS` (120s) remain disconnected;
- no synthetic route generation;
- no fake basemap.

## Data truth and persistence

PR #210 preserves the existing `TripEnergyCalculator` semantics. It receives the effective corrected start SOC and the user-confirmed end SOC.

The new start-SOC snapshot/override fields are included in local backup encode/decode. Older backups without the fields restore `startSocSnapshot` from their existing `startSoc` value.

GPS collection, location freshness rules and continuity decisions are unchanged by v0.7 completion UI work. PR #211 only improves the presentation of endpoint coordinates through the existing address resolver.

## Implementation

### PR #210 — editable SOC completion flow

Merged as `947edbca548aacae612bd7dcf527b3258fe25270`.

Android CI #532:

- unit tests: Green;
- Debug assemble: Green;
- Debug APK artifact upload: Green.

The earlier foundation PRs #207, #208 and #209 were superseded by #210 and closed without merging.

### PR #211 — endpoint address fidelity

Merged as `113c15eb06d5c919465e28daae5fe87e5f80d801`.

Android CI #534:

- Build and test: Green;
- Debug APK artifact upload: Green.

## Remaining acceptance

Do not close #205 solely from CI. Physical-device checks remain:

- wheel gesture feel and accidental-scroll resistance;
- start and end SOC can both reach 0% and 100%;
- 320-360dp width;
- fontScale 1.3;
- Dark/Light readability;
- real endpoint Geocoder result readability, long-address truncation and coordinate fallback;
- route preview remains readable with sparse points and a real LONG_GAP;
- back action returns to the active Trip without stopping it;
- save persists the corrected start SOC and confirmed end SOC and updates VehicleState from the completed Trip.

Location/Geocoder device availability itself remains accepted under #14.
