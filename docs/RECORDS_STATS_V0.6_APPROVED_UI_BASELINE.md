# Records + Stats v0.6 approved UI baseline

Status: approved implementation baseline for #159.

This document locks the presentation hierarchy for the existing Records and Stats facts. It does not authorize new persistence, cloud dependencies, BMS assumptions, or fabricated analytics.

## Shared visual rules

- Keep Dark First and the current bottom navigation.
- Use `EVDesignTokens.Energy.green` (`#32F080`) as a restrained accent/state color, not a full-page paint treatment.
- Prefer `surfaceContainerLow` / low-elevation sections and thin dividers over large decorative gradient cards.
- Remove decorative English eyebrow copy when it competes with the actual value hierarchy.
- Keep page spacing compact and consistent with Trip v0.6.
- No blue primary accents, neon bloom, pulse, or advertising-style lighting.
- Responsive metric groups must remain readable at 320–360dp and larger font scales; never solve overflow by shrinking important text into unreadability.
- Existing dirty-form, delete-confirmation, keyboard/IME, and accessibility guards remain authoritative.

## Records

### Page hierarchy

1. title + compact add action
2. one-glance ledger summary
3. dense chronological charging timeline
4. Add/Edit flows as task-grouped forms

### Ledger summary

The summary should be a compact flat surface rather than a large gradient hero.

Primary fact:
- cumulative charging cost

Supporting facts:
- cumulative grid energy
- record count
- average charger-side price

Do not add invented savings, battery health, charging efficiency, or live vehicle state.

### Charging timeline

Each row should scan quickly without looking like an independent card stack.

Primary row facts:
- location or truthful unavailable fallback
- date/time
- cost
- grid energy

Secondary row facts when present:
- SOC start -> end
- charger-side price per kWh
- charger type
- odometer snapshot

Rules:
- compact rail/icon is acceptable; it must not dominate the row
- green may emphasize energy/active semantics, not every number
- delete remains explicit and keeps the existing confirmation
- the whole row remains the edit affordance
- long location names truncate predictably
- missing values stay unavailable; never infer them

### Add/Edit grouping

Do not rewrite business logic. Presentation groups existing fields by task:

1. charging facts: time, energy, cost / price
2. location & charger: place, location helper, charger type
3. vehicle snapshot: SOC and odometer facts

Existing validation, dirty-form protection, current-location behavior, and save semantics remain unchanged.

## Stats

### Page hierarchy

1. current-month charging summary
2. month-over-month comparison
3. Trip SOC-derived energy estimate
4. recent charging trend
5. charging mix + common places
6. lifetime charging facts
7. charge-to-charge interval / coverage evidence
8. compact data-source explanation

The first screen should answer "what happened this month?" before exposing lower-priority diagnostics.

### Current-month summary

Use a compact flat summary surface.

Primary fact:
- current-month charging cost

Supporting facts:
- grid energy
- charging count
- average charger-side price

### Month comparison

Show current value plus restrained change context. When the previous period is zero, keep growth unavailable rather than displaying a misleading percentage.

### Trip energy estimate

Keep charging facts and Trip estimates visually and semantically separate.

- Trip kWh / kWh/100km remains an estimate from explicit SOC snapshots and configured battery capacity.
- Never label it BMS measured.
- If there are no eligible Trips, say why the estimate is unavailable.
- Do not mix Trip estimated energy with charger-side kWh to fabricate charging efficiency.

### Lightweight visualizations

Use Compose primitives only; no chart framework is required.

Allowed:
- restrained mini bars / progress rows for recent monthly charging, charging mix, and place share
- primary-green strokes/fills at low visual intensity
- text values remain the authority

Not allowed:
- decorative charts without data value
- gradients/glow that overpower labels
- smoothing/interpolation that invents missing data

### Lifetime / evidence sections

Lifetime cost, grid energy, average price, charge-to-charge interval estimates, Trip coverage and confidence remain supporting sections below the current-month story.

Reliability language must keep `unavailable`, `low confidence`, and `healthy evidence` distinct.

## Data truth boundary

- ChargingRecord kWh is charger/meter-side recorded energy.
- Trip energy is an SOC-derived bookkeeping estimate.
- Current SOC/mileage values are only shown where an existing authoritative state exists.
- Missing location, SOC, mileage, price, Trip coverage or interval evidence remains missing.
- UI code must not recompute domain rules that already exist in analytics/domain classes.

## Delivery

Implement as small PRs:

1. Records v0.6 hierarchy and timeline density
2. Stats v0.6 hierarchy and lightweight visual evidence

Do not combine this work with Trip persistence, updater, Hero, catalog, sync, MapLibre, or new backend capability.

## Acceptance

Code-side:
- existing business/data flow remains unchanged
- Android CI green
- no new schema
- no fabricated facts
- Records and Stats materially reduce decorative card weight and improve scan density

Physical-device closeout:
- Dark and Light visual pass
- 320–360dp width
- fontScale 1.0 / 1.3 / 1.5
- long location strings
- empty / sparse / many-record states
- touch targets and TalkBack remain usable

Related: #159 #70 #42 #22
