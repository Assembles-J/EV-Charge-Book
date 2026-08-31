# EV Charge Book Charging v0.7 Design and Implementation Plan

Status: Draft authority for #251
Updated: 2026-08-31

## 1. Purpose

Charging v0.7 evolves the current completed-record ledger into a broader Local First charging workflow **without removing manual charging record maintenance**.

The approved product model has two parallel creation paths:

1. `开始充电` — optional lifecycle recording / preset flow.
2. `充电记录维护` — manual add, backfill and edit of completed charging facts.

A user must never be forced to start a live/local charging session just to maintain a historical record.

This document extends the v0.6 Records baseline. It does not rewrite the existing v0.6 visual closeout owned by #159/#164.

---

## 2. Locked UX principles

### 2.1 Records overview

Keep:
- Dark First visual language.
- compact density and restrained spacing.
- useful range tabs such as `全部 / 近7天 / 近30天 / 近12个月`.
- a `充电中` tab/state only when there is a truthful active local charging session.
- compact ledger summary.
- dense historical charging list.

Add two explicit entries near the top:
- `开始充电`
- `充电记录维护`

The entries are siblings. `开始充电` is not a replacement for manual maintenance.

### 2.2 Completed charging detail

A completed charging record gets a dedicated detail surface.

Primary facts:
- location / charger type
- total cost
- billed/meter energy
- effective unit price
- start time
- end time when present
- duration derived from start/end
- start SOC / end SOC when explicitly entered
- odometer / mileage facts
- notes

Derived facts may include:
- average charging power
- charging-loss estimate

Derived facts are shown only when inputs are sufficient and semantically compatible.

### 2.3 Manual maintenance

Manual record maintenance remains fully supported:
- add historical record
- edit existing record
- backfill missing completion facts
- edit start SOC
- edit start/end time
- edit address
- edit billed energy / cost / unit price through the coupled-field rules

The UI must not show implementation/debug labels such as `手动输入`, `自动计算`, `自动带入` beside every field. Calculation provenance belongs in domain rules and documentation, with `估算` shown only when it is a user-relevant semantic distinction.

---

## 3. Data-truth boundary

### 3.1 SOC

The app currently does not have an authoritative live SOC feed.

Therefore:
- do not display a fake `当前 SOC` as if read from the car;
- start SOC is manual/external input unless a future authoritative source exists;
- target SOC is optional user intent, not measured vehicle state;
- end SOC is manual/external input unless a future authoritative source exists.

### 3.2 Current vehicle

Use the app's currently selected vehicle automatically when entering charging flows.

The user may explicitly switch vehicle where appropriate.

### 3.3 Time

- start time defaults to the current time;
- start time remains editable;
- end time on completion defaults to the current time;
- end time remains editable;
- duration is derived from start/end and is not an independently authoritative field.

### 3.4 Location

Use the current real coordinate fix when permission/provider is available.

Persist:
- latitude
- longitude
- accuracy
- readable address when reverse geocoding succeeds

Address text remains editable.

If reverse geocoding fails:
- keep coordinates;
- allow manual address text;
- do not fabricate a location.

Interactive map-point selection is deferred to #254.

---

## 4. Optional start-charge lifecycle

`开始充电` is an optional bookkeeping flow. It does not remotely control a charger or vehicle.

### 4.1 Start screen

Default inputs:
- current selected vehicle
- start time = now
- current real location if available
- remembered charger type / price rule when appropriate

Editable inputs:
- start SOC (manual)
- target SOC (optional)
- charger type
- price rule / unit price
- charging address
- notes

Actions:
- `开始充电`
- `保存为预设`

Preset semantics:
- store reusable environment inputs only;
- do not pre-commit future actual meter energy, end time, final cost or end SOC as facts.

### 4.2 Active charging overview

If an active local charging session exists, the Records overview may show one compact `充电中` card.

Allowed facts:
- vehicle
- start time
- elapsed time
- location / charger type
- manually supplied start SOC / target SOC
- explicitly supplied interim bookkeeping facts

Not allowed:
- fake live SOC
- fake live charger power
- fake BMS energy

### 4.3 Completion / backfill

`结束充电（补录）` opens the mature record editor with known start facts prefilled.

Completion facts may include:
- end time
- billed/meter energy
- vehicle-side energy if explicitly available
- total cost
- unit price
- end SOC
- odometer
- final address/coordinates
- notes

All dependent metrics update through the centralized calculation engine in #252.

---

## 5. Coupled-field calculation rules

Authority issue: #252.

### 5.1 Billing priority

The product priority is:

```text
total cost > unit price > billed/meter energy
```

This means:

#### User edits total cost
- total cost becomes the highest-priority fact;
- keep unit price stable when valid;
- recompute billed energy = total cost / unit price.

#### User edits unit price
- keep total cost stable;
- recompute billed energy = total cost / unit price.

#### User edits billed energy
- do not silently overwrite a higher-priority user fact;
- update lower-priority derived outputs;
- if the billing trio becomes inconsistent, handle it explicitly instead of creating bidirectional value oscillation.

### 5.2 Calculation-state requirements

The editor must:
- track which fields are current authoritative inputs for the editing transaction;
- avoid Compose recomposition loops;
- avoid rounding drift from repeated conversions;
- centralize formulas in a pure domain/state component;
- keep Composables focused on rendering and user intent.

### 5.3 Derived duration

```text
duration = endTime - startTime
```

If endTime <= startTime, duration is unavailable/invalid.

### 5.4 Average charging power

When billed/meter energy and positive duration exist:

```text
averagePowerKw = billedEnergyKwh / durationHours
```

This is an average derived from energy/time, not charger telemetry or peak power.

### 5.5 Charging-loss estimate

When both billed/meter energy and compatible vehicle-side energy exist:

```text
lossRate = (meterEnergy - vehicleEnergy) / meterEnergy * 100%
```

Guardrails:
- meter energy <= 0 -> unavailable
- vehicle energy < 0 -> unavailable
- vehicle energy > meter energy -> inconsistent; do not show a fake negative loss
- SOC-derived vehicle energy remains an estimate and must not be relabeled as BMS measured energy

---

## 6. Current implementation baseline

Current `ChargingRecordEntity` stores:
- vehicleId
- one charge timestamp
- energyKwh
- cost
- start/end SOC
- charger type
- location
- remark
- odometer
- optional coordinates / accuracy

It does not currently store:
- active charging session status
- end time
- explicit vehicle-side energy
- preset lifecycle state

Therefore v0.7 requires a focused persistence design and migration rather than UI-only placeholders.

Current Add Record already provides useful foundations:
- start time defaults to current time;
- current-location request and reverse geocoding;
- manual location fallback;
- recent defaults;
- partial energy/price/cost coupling;
- dirty-form protection.

These foundations should be reused.

---

## 7. Proposed implementation slices

### Slice A — authority + model design

Files primarily involved:
- `docs/CHARGING_V0.7_DESIGN_AND_IMPLEMENTATION_PLAN.md`
- `docs/CHARGE_TRIP_UX_FLOW_v0.5.md` or later successor sync
- `docs/FEATURE_MATRIX.md`
- `docs/ROADMAP.md`
- `docs/DATABASE.md`

Deliverables:
- frozen UX/data-truth rules
- selected persistence model
- migration/backup impact

### Slice B — calculation engine

Likely new/changed areas:
- `android/app/src/main/java/com/evchargebook/domain/charge/...`
- unit tests under `android/app/src/test/.../domain/charge/`

Deliverables:
- coupled-field reducer/state model
- duration/power/loss derivations
- deterministic edit-sequence tests

### Slice C — persistence / active session

Likely areas:
- entity/database/DAO/repository
- backup codec
- MainViewModel state

Deliverables:
- active session survives process death
- completed record creation does not duplicate
- historical records remain compatible

### Slice D — Records overview + detail + manual maintenance

Likely areas:
- `ui/records/RecordsScreen.kt`
- new charging detail composable/screen
- `AddRecordScreen.kt`
- `RecordEditScreen.kt`
- `MainActivity.kt` navigation state

Deliverables:
- two entry points
- optional active card
- completed detail
- mature editor without debug calculation badges

### Slice E — start-charge / preset UI

Likely areas:
- new `StartChargingScreen.kt` / related state holder
- Records navigation

Deliverables:
- selected vehicle reused automatically
- start time now/editable
- location current fix/editable
- manual SOC only
- preset stores reusable inputs only

### Slice F — location follow-up

Owned by #254.

First version:
- current coordinate default
- reverse geocode
- manual address edit

Later:
- map-point picker

---

## 8. Migration and backup requirements

Before code lands:
- define schema migration path from current database;
- define how historical records obtain/omit new end-time or energy fields;
- do not synthesize fake historical end times;
- backup JSON must preserve new fields and remain backward compatible where practical;
- restore must not create duplicate active/completed sessions.

---

## 9. Acceptance

### Data truth
- no fake live SOC/BMS/power values
- missing historical facts remain missing
- derived metrics clearly follow available inputs

### Editing maturity
- cost -> energy recompute respects priority
- price -> energy recompute respects priority
- energy change refreshes dependent power/loss without value flicker
- time changes refresh duration/power
- repeated edits do not accumulate rounding drift

### Workflow
- manual completed-record path still works independently
- optional start-charge path works independently
- active session survives process recreation
- completion produces one completed record
- current vehicle/time/location defaults behave as specified

### UI
- Dark First
- compact density
- no internal calculation/debug badges
- 320–360dp readable
- fontScale 1.0 / 1.3 / 1.5
- Light mode remains legible where supported

### Engineering
- Android CI Green
- migration tests Green
- backup/restore tests Green
- no unrelated Trip/Hero/catalog rewrite

---

## 10. Issue ownership

- #251 — parent product/implementation owner
- #252 — coupled calculation engine + derived metrics
- #253 — optional active charging / preset lifecycle
- #254 — current-location behavior + future map picker
- #14 — existing Location / Geocoder physical acceptance foundation
- #42 — accessibility / dirty forms / state safety
- #159 / #164 — v0.6 Records visual closeout only
