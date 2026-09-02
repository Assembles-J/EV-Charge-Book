# EV Charge Book Charging v0.7 Design and Implementation Plan

Status: Authority for #251
Updated: 2026-09-02
Current implementation baseline: `main@5878cf72ef8ca39a469570529190cc6af7e2a8e5`

## 1. Purpose

Charging v0.7 evolves the completed-record ledger into a broader Local First charging workflow **without removing manual charging record maintenance**.

The approved product model has two parallel creation paths:

1. `开始充电` — optional local lifecycle bookkeeping.
2. `充电记录维护` — independent manual add / backfill / edit of completed charging facts.

A user must never be forced to start a charging session just to maintain a historical record.

This document extends the v0.6 Records baseline. It does not rewrite the v0.6 visual closeout owned by #159/#164.

## 2. Locked UX principles

### 2.1 Records overview

Keep the compact Dark First visual language and restrained spacing. Preserve the ledger summary and dense historical list.

Expose two sibling primary entries:

- `开始充电`
- `充电记录维护`

When a local active session exists, Records may show one compact `充电中` card. When physical charging has ended but final meter/billing data is not yet available, Records shows a separate `待补录` section. Pending sessions are not completed charging records and do not enter completed count, kWh, cost, average-price or interval statistics.

### 2.2 Completed charging detail

A dedicated completed charging detail surface remains a future product option. Its absence does **not** block Charging v0.7 closeout as long as current Add/Edit/Completion/Pending surfaces remain truthful and usable.

If/when rendered, primary facts may include location / charger type, total cost, billed or meter energy, effective unit price, start/end time, explicitly known start/end SOC, odometer and notes.

Derived facts may include duration, average charging power and charging-loss estimate. They must use the centralized calculation contract and appear only when inputs are sufficient and semantically compatible.

### 2.3 Manual maintenance

Manual record maintenance remains first-class and independent of active-session lifecycle. Add/Edit uses the shared billing editor and must preserve raw typing/cursor stability.

Production UI must not expose internal/debug labels such as `手动输入`, `自动计算`, or `自动带入` beside every field. Show `估算` only when it is a user-relevant truth distinction.

## 3. Data-truth boundary

The app does not have an authoritative live SOC/BMS feed. Do not display a fake current SOC as if read from the car. Start/end SOC are manual/external facts unless a future authoritative source exists. Target SOC is intent, not measurement.

Current app VehicleState may be used as an editable candidate only. Finish prefill must not substitute target SOC for actual end SOC.

Start time defaults to now and remains editable. End time defaults to now and remains editable. Displayed completion duration must consume the shared calculation contract.

Location defaults to a real coordinate fix when permission/provider is available. Persist latitude, longitude, accuracy and a readable address when reverse geocoding succeeds. If the user edits address text manually, stale coordinate evidence must be cleared. Interactive map-point selection remains optional future work under #254.

Unknown meter energy / cost must remain missing. Never encode unknown as `0`.

SOC-delta × known battery capacity may be displayed as a vehicle-energy **estimate**. It is not measured/BMS vehicle-side energy and must not be persisted or relabeled as such without an authoritative source.

## 4. Current merged lifecycle

### 4.1 Calculation and manual editor

Merged authority:

- #268 — centralized `ChargeCalculationEngine` and focused tests.
- #261 — shared `ChargeBillingEditor` adopted by Add/Edit.

Billing priority:

```text
total cost > unit price > billed/meter energy
```

Authoritative user facts and calculated dependants remain distinct. Repeated edits must not accumulate display-rounding drift. Conflicting authoritative facts stay visible as a conflict instead of oscillating.

### 4.2 Persistence and exactly-once transaction

Merged #271 provides durable charging-session persistence and the transaction authority for completion.

The lifecycle now includes:

```text
ACTIVE -> COMPLETED
ACTIVE -> PENDING_DETAILS -> COMPLETED
ACTIVE -> CANCELLED
PENDING_DETAILS -> CANCELLED   (explicit discard, no historical row)
```

Key rules:

- reject a second ACTIVE session for the same vehicle;
- persist edits to known ACTIVE facts;
- completion creates exactly one `ChargingRecordEntity` and links it to the session;
- repeated completion/backfill returns the already-created result rather than inserting a duplicate;
- cancel/discard creates no completed historical record;
- pending end SOC/odometer may participate in VehicleState reconstruction while pending billing remains excluded from completed statistics.

### 4.3 Start / active UI

Merged #276 provides:

- separate `开始充电` and `充电记录维护` entries;
- editable start time;
- editable start SOC / optional target SOC;
- charger type / price inputs;
- current-location capture with editable address fallback;
- persisted compact `充电中` card;
- edit active session;
- explicit cancel.

No fake live SOC/power/BMS telemetry is shown.

Merged #292 further compacts the Start/Finish hierarchy, prefills Start SOC from app-known VehicleState when available, removes primary remark clutter, carries charger type from Start into Finish and keeps SOC × battery-capacity energy visibly estimate-only.

### 4.4 Completion UI

Merged #277 provides the active-session finish flow:

- editable end time;
- actual end SOC confirmation;
- target SOC remains reference-only;
- shared billing editor for cost / price / meter energy;
- exactly-once repository transaction remains completion authority;
- editing location clears stale coordinate evidence.

Merged #313 removes the remaining screen-local completion-duration formula:

- displayed duration consumes `ChargeCalculationEngine.durationMillis`;
- invalid `end <= start` consumes shared `END_NOT_AFTER_START`;
- UI formatting remains presentation-only.

### 4.5 Delayed home-meter backfill

Merged #294 adds durable `PENDING_DETAILS` persistence, Room v17 -> v18 migration coverage and Backup v10 support.

Merged #297 adds the truthful next-day-meter path:

- physical charging can end even when meter/grid usage is not yet available;
- known end facts persist as `PENDING_DETAILS`;
- unknown meter/cost stays null;
- a meter value derived only from other billing fields is not accepted as a physical meter reading;
- only user-confirmed meter/charger kWh may finalize the completed record;
- pending releases the ACTIVE slot so a later charge can start;
- backfill finalizes exactly once;
- explicit pending delete produces no historical row.

Merged #302 adds `修改结束信息` for pending sessions:

- edit end time;
- edit end SOC;
- edit location;
- edit ending odometer;
- preserve pending billing facts;
- rebuild VehicleState after correction.

### 4.6 UI command boundary

Merged #305 routes selected-vehicle pending state and complete/defer/update-pending/backfill/discard commands through `MainViewModel`.

`RecordsScreen` and `CompleteChargingScreen` no longer construct `AppDatabase`, `ChargingRepository`, or Room DAO access. Existing repository/session transactions remain the only persistence authority.

### 4.7 Tariff reuse truth

Merged #310 makes Start Charging tariff reuse conservative and source-aware:

- silent auto-fill uses stored `ChargingSession.unitPricePerKwh` only;
- automatic reuse requires the same vehicle + normalized location + charger type;
- same charger type at another location is suggestion-only;
- historical completed-record `cost / energy` effective prices are suggestion-only and are not assumed to be original tariffs;
- once the user edits, clears or explicitly selects a price, later location/type changes must not silently overwrite that choice;
- an untouched auto-filled price is cleared when its context no longer matches.

## 5. Derived calculation contract

Authority issue: #252.

Derived duration:

```text
duration = endTime - startTime
```

If `endTime <= startTime`, duration is unavailable/invalid. Current Completion UI consumes this result from `ChargeCalculationEngine` via merged #313.

Derived average power:

```text
averagePowerKw = billedEnergyKwh / durationHours
```

This is an energy/time average, not charger telemetry or peak power.

Charging loss, only when compatible meter and vehicle-side energy exist:

```text
lossRate = (meterEnergy - vehicleEnergy) / meterEnergy * 100%
```

Guardrails:

- meter energy <= 0 -> unavailable;
- vehicle energy < 0 -> unavailable;
- vehicle energy > meter energy -> inconsistent;
- SOC-derived vehicle energy remains estimate semantics and cannot become measured vehicle-side energy.

Current v0.7 does not need to fabricate average-power/loss displays when compatible measured facts are unavailable. Any later completed-record detail/analysis display must consume this shared contract rather than introduce a second formula path.

## 6. Preset boundary

Reusable charging preset persistence/UX is **explicitly deferred out of Charging v0.7** to #311.

The current v0.7 workflow instead relies on:

- current-location/default-time behavior;
- reusable/common places;
- source-aware, location-safe tariff memory/suggestions from #310.

Do not add another preset persistence/schema layer before #253/#289 physical lifecycle acceptance.

If #311 is implemented later, a preset may store reusable environment/setup inputs only. It must never pre-commit future actual meter energy, measured vehicle energy, end time, actual end SOC, final cost or odometer.

## 7. Implementation ownership

- #251 — parent product / end-to-end acceptance owner.
- #252 — shared calculation contract + physical semantic acceptance.
- #253 — active / pending / completion lifecycle and physical acceptance.
- #260 — manual Add/Edit editor physical acceptance.
- #289 — compact Start/Finish + delayed-meter current-main physical acceptance.
- #254 — current-location behavior + optional future map-point picker.
- #311 — reusable preset follow-up after v0.7 acceptance; not a v0.7 blocker.
- #14 — Location / Geocoder physical acceptance foundation.
- #42 — accessibility / dirty forms / state safety.
- #159 / #164 — v0.6 Records visual closeout only.
- #6 — documentation/status authority maintenance.

Merged implementation evidence:

- #268 — calculation engine.
- #261 — Add/Edit billing editor.
- #271 — charging persistence / exactly-once foundation.
- #276 — Start / Active UI.
- #277 — completion UI.
- #292 — compact physical-feedback Start/Finish UX.
- #294 — durable pending persistence / Room v18 / Backup v10.
- #297 — delayed-meter pending lifecycle UI.
- #302 — pending end-fact revision.
- #305 — MainViewModel lifecycle boundary.
- #310 — location-safe tariff reuse truth.
- #313 — shared completion-duration/time-validity wiring.

## 8. Migration and backup requirements

Historical fields must remain truthful; migrations must not synthesize fake end time, vehicle-side energy, meter energy or cost.

Backup must include charging sessions and extended completed-record facts. Restore must not duplicate active/pending/completed sessions and must remain blocked while local unfinished lifecycle state would make overwrite unsafe.

Automated migration/backup tests are evidence, but real historical-install/database-open behavior remains a physical acceptance gate under #253/#289.

## 9. Remaining acceptance

Implementation and v0.7 scope decisions are now resolved. Remaining closeout is **current-main physical acceptance**, not another broad implementation round.

### Lifecycle/device — #253 / #289

- ACTIVE survives force-stop/process-kill/relaunch;
- active edits survive relaunch;
- PENDING_DETAILS survives relaunch with correct confirmed end facts;
- pending releases the ACTIVE slot and stays outside completed statistics;
- complete/backfill produces exactly one historical row and retry/relaunch does not duplicate;
- pending end-fact revision persists truthfully and rebuilds VehicleState;
- cancel/discard produces no historical row;
- backup/restore guards behave truthfully;
- historical/current database opens on a real Android device;
- current vehicle/time/location/SOC/tariff defaults behave truthfully.

### UI/editor — #252 / #260 / #289

- Dark / Light readability;
- 320–360dp width;
- fontScale 1.3+;
- keyboard does not make primary actions unreachable;
- raw decimal edits (`1.`, clear, retype) remain stable;
- no P0 cursor jump, recomposition loop, rounding drift or value flicker;
- billing conflict preserves authoritative user facts;
- completion date/time edits refresh shared duration correctly;
- back navigation / unsaved-edit behavior passes.

### Future, non-blocking follow-ups

- #254 interactive map-point picker, if still desired;
- #311 reusable charging presets, if real usage still shows meaningful setup friction;
- dedicated completed charging detail/analytics, if later product UX requires it.

## 10. Status rule

Current `main` is runtime authority. Merged PRs and current-head CI are implementation evidence. Open Issues own remaining acceptance/future work. CI Green does **not** substitute for physical-device acceptance.
