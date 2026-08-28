# EV Charge Book Records & Stats v0.6 Approved UI Baseline

Status: Approved design baseline  
Date: 2026-08-29  
Owning issue: #159

## 1. Purpose

This document locks the v0.6 visual and interaction direction for **Charging Records** and **Energy Statistics** before implementation.

The goal is to bring these two existing pages to the same level of visual maturity as the approved Trip v0.6 refinement without expanding product scope, changing the data model, or inventing new runtime facts.

The current application already owns the required data and business flows. v0.6 is therefore an **information hierarchy, density, visualization and interaction refinement**, not a new analytics architecture.

## 2. Existing implementation authority

Current `main` already provides:

### Records

- charging ledger cockpit
- cumulative cost / grid energy / record count / average price
- charging timeline
- location / timestamp / charger type
- cost / energy / price per kWh
- start/end SOC
- optional odometer snapshot
- Add Charge flow
- Edit Charge flow
- delete confirmation
- dirty-form protection
- responsive metric-grid baseline

Primary implementation files:

- `android/app/src/main/java/com/evchargebook/ui/records/RecordsScreen.kt`
- `android/app/src/main/java/com/evchargebook/ui/records/AddRecordScreen.kt`
- `android/app/src/main/java/com/evchargebook/ui/records/RecordEditScreen.kt`

### Stats

- current-month charging cost / grid energy / count / average price
- month-over-month comparison
- Trip SOC-derived estimated energy and kWh/100km
- recent monthly trend buckets
- charger-category mix
- common charging places
- lifetime charging totals
- charge-to-charge interval estimate
- Trip coverage evidence
- explicit charging-fact vs Trip-estimate source explanation

Primary implementation file:

- `android/app/src/main/java/com/evchargebook/ui/stats/StatsScreen.kt`

## 3. Design inheritance

This baseline inherits the existing product language:

> Dark First · Energy Focus · Vehicle Companion

It also inherits the Trip v0.6 refinement rules:

- primary product green remains `EVDesignTokens.Energy.green` / `#32F080`
- green is an accent and data-highlight color, not a large glowing background effect
- no blue primary action accent
- no neon bloom, advertising glow or decorative light halo
- low elevation, restrained outlines and compact surfaces
- visual polish must never weaken data credibility

Existing tokens remain authoritative:

| Token | Current value | v0.6 usage |
|---|---:|---|
| Dark background | `#090D0C` | page background |
| Dark surface | `#121716` | primary content surfaces |
| Dark elevated surface | `#19201D` | limited emphasis only |
| Primary text | `#EAF3EE` | dominant values / headings |
| Secondary text | `#9AA6A0` | context / metadata |
| Outline | `#26302C` | restrained separation |
| Energy green | `#32F080` | primary accent / energy fact |
| Success | `#23D18B` | success semantics only |
| Warning | `#FFB020` | warning semantics only |
| Danger | `#FF4D4F` | destructive/error semantics only |

Radii continue to follow the existing `10 / 16 / 24` family.

## 4. Records — information architecture

### 4.1 Default page hierarchy

The Records landing page should read in this order:

1. page identity
2. charging-ledger summary
3. recent charging timeline
4. compact add action

The page must not become a stack of visually equal cards.

### 4.2 Charging ledger hero

The ledger summary remains the single visual focus of the page.

Dominant value:

- cumulative charging cost

Secondary metrics:

- cumulative grid energy
- record count
- average price per kWh

Rules:

- one dominant number only
- secondary metrics remain visually subordinate
- no fake live battery state or remaining range
- no oversized decorative icon
- no strong drop shadow
- no multi-layer glow
- use responsive 3 → 2 metric layout when width/font scale requires it

### 4.3 Charging timeline

Each charging record should behave as a compact timeline row rather than an isolated card.

Recommended scan order:

1. location
2. timestamp + charger type
3. cost
4. energy added
5. SOC change
6. unit price
7. optional odometer / secondary facts

Visual rules:

- compact charging rail / bolt marker is allowed
- marker is supportive, not dominant
- cost can remain the strongest right-side value
- grid energy may use restrained energy green
- SOC is supporting evidence, not the row headline
- delete remains an explicit secondary action
- long locations ellipsize or use a bounded second line; they must not make rows arbitrarily tall
- target: normal phone should show several records without excessive scrolling

### 4.4 Empty and large-data states

Must remain usable for:

- zero records
- one record
- 100+ records

Empty state keeps the existing direct `记录第一次充电` path.

Large history must stay a normal lazy list; the redesign must not introduce expensive per-row effects.

## 5. Add Charge — fast-entry baseline

The Add flow should be organized by the user's task rather than by raw database fields.

### Group A — vehicle context

At the top:

- current vehicle
- compact switch affordance if already supported by the flow
- clear note that VehicleState may provide current SOC / odometer context

Do not imply that the app can read BMS state automatically.

### Group B — charging facts

Primary entry fields:

- charging time
- start SOC
- end SOC
- energy kWh
- cost
- price per kWh where applicable

The visual hierarchy should make SOC / kWh / cost easy to scan without turning every value into a separate large card.

### Group C — location and charger type

- charger type
- location text
- `使用当前位置` / retry behavior

Location lookup remains non-blocking. Failure to resolve an address must never block saving a real charging record.

### Group D — vehicle snapshot and note

- odometer if present in the current flow
- optional note

### Save action

- one clear primary save action
- energy green family
- no blue action color
- no decorative glow
- >=48dp interaction target

## 6. Edit Charge — verification baseline

Edit should feel like data verification rather than a second unrelated form design.

At the top, show a compact saved-record summary:

- timestamp
- location
- SOC change
- energy
- cost

Then reuse the same grouped field hierarchy as Add.

Preserve existing behavior:

- dirty-form protection
- save success leaves without another dirty warning
- delete requires explicit confirmation
- destructive action uses danger semantics only

A short explanatory block may state that modifying a charging record can update derived summaries and rebuild VehicleState according to existing event-authority rules. It must not claim unsupported calculations.

## 7. Stats — information architecture

The Stats page should answer progressively:

1. **What happened this month?**
2. **How does it compare with last month?**
3. **What can Trip evidence estimate?**
4. **What is the recent trend?**
5. **How and where do I charge?**
6. **What does the lifetime / coverage evidence say?**
7. **What is fact vs estimate?**

Do not place every analytic block at equal visual weight.

## 8. Stats — monthly overview

The current-month summary is the page visual focus.

Dominant value:

- current-month charging cost

Secondary metrics:

- current-month grid energy
- charging count
- average price per kWh

Use the same hierarchy grammar as the Records ledger hero so the two tabs feel like one product family.

## 9. Stats — month comparison

Current vs previous month should be compact and immediately scannable.

Metrics:

- cost
- grid energy
- charging count

Rules:

- show direction/rate only when mathematically meaningful
- if previous value is zero, keep the existing `--`/unavailable semantics rather than inventing a percentage
- use green only when it communicates product accent or a neutral data highlight; do not equate green with "financially good" for every metric
- responsive layout must not rely on a permanently fixed 3-column row at large font scales

## 10. Stats — Trip SOC energy estimate

This block remains deliberately separated from charging facts.

It may show:

- estimated consumed energy
- eligible Trip distance
- weighted estimated kWh/100km
- eligible / excluded Trip counts

Required copy boundary:

> Derived from explicit Trip SOC snapshots and configured battery capacity; not BMS measurement.

Rules:

- display an `估算` label/badge
- unavailable inputs remain unavailable
- no invented SOC interpolation
- no fake charging efficiency calculation
- no direct subtraction of Trip estimate from charger/grid kWh as if they were the same measurement system

## 11. Stats — lightweight trend visualization

Recent monthly charging data should become easier to interpret than a text-only list.

Approved lightweight visualization:

- simple line / sparkline for recent monthly trend
- subtle grid lines only if necessary
- restrained energy green plus neutral secondary line
- concise labels and exact values remain available nearby

Implementation rule:

- prefer a small Compose Canvas implementation or existing primitives
- do not introduce a heavy chart dependency only for this screen
- missing months / unavailable values must be represented honestly

## 12. Stats — charging mix

Charger-type mix should use compact horizontal bars or equivalent low-noise visualization.

The UI may expose existing dimensions such as:

- charging count share
- grid-energy share
- cost share

Do not collapse all three into a fabricated single "charging efficiency" score.

## 13. Stats — common places

Common charging places should be shown as a dense ranking, not large individual cards.

Each row may show:

- rank
- display location
- charging count
- latest charge date
- grid energy
- cost
- average price where helpful

Limit default emphasis to the most useful places (current implementation already uses a small top subset).

Long names must truncate predictably.

## 14. Stats — lifetime and coverage evidence

Lifetime charging totals remain compact supporting information.

Charge-to-charge / Trip coverage analytics should emphasize **evidence quality**:

- calculable interval count
- total possible interval count when available
- Trip coverage percentage or equivalent existing evidence
- confidence / unavailable state where already owned by the domain model

A simple progress bar is allowed as a visual aid, but it must never imply precision beyond the stored evidence.

## 15. Data-source explanation

The Stats page keeps an explicit source boundary near the lower analytic sections:

- charging-record kWh = charger / meter-side charging fact entered or stored by the user
- Trip kWh/100km = estimate derived from Trip SOC change + configured battery capacity

These sources remain separate.

The UI must not present them as direct BMS telemetry or silently combine them into a false efficiency metric.

## 16. Shared responsive rules

Records and Stats must be checked at:

- 320–360dp width
- fontScale 1.0
- fontScale 1.3
- fontScale 1.5
- Dark theme
- Light theme

Rules:

- important metric grids may adapt 3 → 2
- typography should not be shrunk merely to make a layout fit
- major values may wrap only when semantically safe
- labels / units should stay attached to their values where possible
- clickable rows and icon-only actions require >=48dp effective touch targets
- icon-only actions require meaningful `contentDescription`

The implementation remains under #42 for final accessibility/device acceptance.

## 17. Interaction and motion

Motion should be restrained:

- small state/content transitions are acceptable
- no pulsing cards
- no looping chart animation
- no glowing borders
- no motion that competes with charging/energy numbers

Records remains optimized for fast entry and review. Stats remains optimized for reading and comparison.

## 18. Out of scope

This baseline does **not** add:

- database schema changes
- cloud analytics
- new backend APIs
- BMS / OBD telemetry
- charging-efficiency inference
- AI summaries
- battery prediction
- new bottom-navigation destinations
- map/route capability
- a heavy charting framework mandate

If implementation appears to require one of these, the scope must be reviewed rather than silently added.

## 19. Implementation mapping

Expected first implementation slices:

### Records slice

- `RecordsScreen.kt`
- `AddRecordScreen.kt`
- `RecordEditScreen.kt`
- shared lightweight UI components only when reuse is clear

### Stats slice

- `StatsScreen.kt`
- small reusable chart/bar primitives if needed

Expected unchanged layers unless a concrete bug is discovered:

- Room schema
- DAOs
- repository architecture
- sync protocol
- Trip tracking service
- release/update flow

## 20. Acceptance checklist

### Records

- [ ] ledger hero has one dominant value and compact secondary metrics
- [ ] charging history reads as a dense timeline rather than equal cards
- [ ] long location names do not destroy row density
- [ ] Add/Edit share one field hierarchy
- [ ] current vehicle context is obvious
- [ ] location failure remains non-blocking
- [ ] dirty-form and delete confirmation remain intact

### Stats

- [ ] month summary is the visual focus
- [ ] month comparison is compact and mathematically truthful
- [ ] Trip estimate is clearly labeled as estimate / non-BMS
- [ ] recent-month trend is visually scannable without a heavy dependency
- [ ] charger mix is readable without inventing a composite efficiency score
- [ ] common places are dense and useful
- [ ] lifetime / interval / coverage evidence remains truthful
- [ ] charging facts and Trip estimates remain visibly separated

### Shared

- [ ] primary green remains `#32F080` token authority
- [ ] no blue primary accent / neon glow regression
- [ ] 320–360dp layout pass
- [ ] fontScale 1.0 / 1.3 / 1.5 pass
- [ ] Dark / Light contrast pass
- [ ] 48dp touch-target pass
- [ ] no data-model or business-scope expansion hidden inside UI work

## 21. Issue boundaries

- #159 owns the Records + Stats v0.6 refinement.
- #70 remains the existing v0.5 five-page physical visual closeout owner.
- #42 remains accessibility / large-font / small-screen / touch-target / state-safety acceptance.
- #22 remains global top-inset / spacing physical polish.

This separation prevents a visual refinement PR from falsely closing device acceptance or rewriting already-correct business behavior.
