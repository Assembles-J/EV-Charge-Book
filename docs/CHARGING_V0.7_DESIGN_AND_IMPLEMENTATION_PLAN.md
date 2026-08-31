# EV Charge Book Charging v0.7 Design and Implementation Plan

Status: Authority for #251
Updated: 2026-08-31

## 1. Purpose

Charging v0.7 evolves the current completed-record ledger into a broader Local First charging workflow **without removing manual charging record maintenance**.

The approved product model has two parallel creation paths:

1. `开始充电` — optional lifecycle recording / preset flow.
2. `充电记录维护` — manual add, backfill and edit of completed charging facts.

A user must never be forced to start a live/local charging session just to maintain a historical record.

This document extends the v0.6 Records baseline. It does not rewrite the existing v0.6 visual closeout owned by #159/#164.

## 2. Locked UX principles

### 2.1 Records overview

Keep Dark First visual language, compact density and restrained spacing. Preserve useful range tabs. Add a truthful `充电中` state only when an active local charging session exists. Keep the ledger summary and dense historical charging list.

Expose two sibling primary entries:

- `开始充电`
- `充电记录维护`

`开始充电` is not a replacement for manual maintenance.

### 2.2 Completed charging detail

A completed charging record gets a dedicated detail surface.

Primary facts may include location / charger type, total cost, billed or meter energy, effective unit price, start time, end time when present, start/end SOC when explicitly entered, odometer/mileage facts and notes.

Derived facts may include duration, average charging power and charging-loss estimate. Derived facts are shown only when inputs are sufficient and semantically compatible.

### 2.3 Manual maintenance

Manual record maintenance remains fully supported: add historical record, edit existing record, backfill missing completion facts, edit start SOC/time, edit end time/address, and edit billed energy/cost/unit price through the coupled-field rules.

The production UI must not expose internal/debug labels such as `手动输入`, `自动计算`, `自动带入` beside every field. Show `估算` only when it is a user-relevant semantic distinction.

## 3. Data-truth boundary

The app currently does not have an authoritative live SOC feed. Do not display a fake current SOC as if read from the car. Start/end SOC are manual/external facts unless a future authoritative source exists. Target SOC is user intent, not measurement.

Use the currently selected vehicle automatically when entering charging flows, while still allowing explicit switching where appropriate.

Start time defaults to now and remains editable. End time on completion defaults to now and remains editable. Duration is derived from start/end time.

Location defaults to the current real coordinate fix when permission/provider is available. Persist latitude, longitude, accuracy and a readable address when reverse geocoding succeeds. Address text remains editable. If reverse geocoding fails, retain coordinates and allow manual address maintenance. Interactive map-point selection remains under #254.

## 4. Optional start-charge lifecycle

`开始充电` is bookkeeping only. It does not remotely control a charger or vehicle.

Default inputs may include the current selected vehicle, start time = now, current real location and remembered charger/price defaults. Editable inputs may include manual start SOC, optional target SOC, charger type, price rule/unit price, address and notes.

`保存为预设` stores reusable environment inputs only. It must not pre-commit future actual meter energy, end time, final cost or end SOC as facts.

If an active local charging session exists, Records may show one compact `充电中` card containing only known or derivable local facts. Do not show fake live SOC, fake live power or fake BMS energy.

Completion opens the same mature record editor with known start facts prefilled and completion facts pending.

## 5. Coupled-field calculation rules

Authority issue: #252.

Product priority:

```text
total cost > unit price > billed/meter energy
```

When the user edits total cost, keep a valid unit price stable and recompute billed energy. When the user edits unit price, keep total cost stable and recompute billed energy. When the user edits billed energy, do not silently overwrite higher-priority user facts; update lower-priority derived outputs and surface an explicit inconsistency when the trio no longer agrees.

The editor must avoid bidirectional recomposition loops and rounding drift. Formulas belong in one pure domain/state component, not duplicated across Composables.

Derived duration:

```text
duration = endTime - startTime
```

If endTime <= startTime, duration is unavailable/invalid.

Derived average power:

```text
averagePowerKw = billedEnergyKwh / durationHours
```

This is an energy/time average, not charger telemetry or peak power.

Charging-loss estimate, only when compatible meter and vehicle-side energy exist:

```text
lossRate = (meterEnergy - vehicleEnergy) / meterEnergy * 100%
```

Guardrails: meter energy <= 0 -> unavailable; vehicle energy < 0 -> unavailable; vehicle energy > meter energy -> inconsistent; SOC-derived vehicle energy remains estimated and must not be relabeled as BMS measurement.

## 6. Current implementation baseline

The current completed-record model stores vehicle identity, one charge timestamp, energy, cost, start/end SOC, charger type, location, remark, odometer and optional coordinates/accuracy. It does not yet represent active-session lifecycle, explicit end time, explicit vehicle-side energy or presets.

The existing Add Record flow already provides useful foundations: now-time default, current-location request/reverse geocoding, manual location fallback, recent defaults, partial energy/price/cost coupling and dirty-form protection. Reuse these foundations.

As of 2026-08-31, Draft PR #258 establishes the first pure calculation-engine code slice for #252. It is not yet runtime authority until merged, and it does not by itself complete #252 because Add/Edit UI/state adoption remains required.

## 7. Implementation ownership

- #251 — parent product/implementation owner
- #252 — centralized coupled calculation engine + derived metrics
- #253 — optional active charging / preset lifecycle and persistence
- #254 — current-location behavior + future map-point picker
- #14 — existing Location / Geocoder physical acceptance foundation
- #42 — accessibility / dirty forms / state safety
- #159 / #164 — v0.6 Records visual closeout only

Implementation order should preserve small slices: authority/model design -> calculation engine -> persistence/session migration -> Records overview/detail/manual maintenance -> optional start-charge/preset UI -> location/map follow-up -> migration/backup + physical acceptance.

## 8. Migration and backup requirements

Before persistence changes land, define the schema migration path, historical-field semantics and backup compatibility. Do not synthesize fake historical end times. Restore must not duplicate active/completed charging sessions.

## 9. Acceptance

Data truth: no fake live SOC/BMS/power values; missing facts remain missing; derived metrics follow sufficient inputs only.

Editing maturity: cost/price/energy precedence is deterministic; time changes refresh duration/power; dependent loss/power refresh without value flicker; repeated edits do not accumulate rounding drift.

Workflow: manual completed-record maintenance remains independent; optional start-charge remains independent; active session survives process recreation; completion produces one completed record; current vehicle/time/location defaults behave as specified.

UI: compact Dark First hierarchy; no internal calculation/debug badges; 320–360dp and large-font usability; Light mode readable where supported.

Engineering: Android CI Green, migration tests Green, backup/restore tests Green, and no unrelated Trip/Hero/catalog rewrite.
