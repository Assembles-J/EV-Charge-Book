# EV Charge Book Trip v0.6 Approved UI Baseline

Status: approved visual / interaction baseline from 2026-08-28 physical-device review.

Owning issue: #145

## Purpose

This document is the implementation authority for the Trip v0.6 UI pass. It refines the existing v0.5 Dark First language without changing Trip persistence, GPS truth semantics, or the current Local First product boundary.

The approved direction is **denser, clearer, and more data-led** than the current physical-device Trip UI. It should not look like a debug screen, a dashboard of unrelated cards, or an advertising surface.

## Design token authority

Use the existing Android token as the source of truth:

```kotlin
EVDesignTokens.Energy.green // 0xFF32F080
```

Do not introduce a second Trip-specific green.

### Color behavior

- Primary green is used for selected state, positive status, route/start accents, compact trend lines and interaction progress.
- Avoid bright outer glow, neon bloom, luminous rings or large halos.
- Red remains semantic: endpoint flag, danger or critical interruption only.
- Start and end must remain distinguishable by icon shape, not only by color.
- Dark surfaces remain low-noise so actual route/data keeps visual priority.

## Product boundary

The current app cannot preselect a destination before a Trip begins.

Therefore v0.6 must not add:

- destination input
- route planning
- navigation preview
- ETA to a selected destination
- fake endpoint preview

A future capability is tracked separately in #152.

Completed Trips may show the recorded end address when available. Coordinate fallback is technical evidence, not a substitute for fabricated place names.

## Six implementation surfaces

### 1. Trip list / history — #146

Goal: make the Trip landing page compact and immediately useful.

Display priority:

1. date/time and completion state
2. start -> end presentation
3. distance + duration
4. average consumption when trustworthy
5. additional technical facts only when they improve scanning

Rules:

- fit multiple recent Trips on one screen
- use predictable ellipsis for long addresses
- avoid oversized introductory copy
- avoid decorative status cards
- do not expose raw GPS diagnostics in the list

### 2. READY / preparation — #147

Goal: tell the user what will be recorded and whether recording can start.

Display priority:

1. selected vehicle identity
2. current SOC / mileage when known
3. GPS readiness + accuracy context
4. one clear start interaction

Interaction:

- use a restrained slide-to-start control
- thumb and progress use the primary green family
- no bright glow
- partial drag returns naturally
- successful drag requires a meaningful threshold
- preserve an accessible semantic/click action so sliding is not the only usable input

Do not add feature-advertising cards such as “high accuracy / continuous recording / privacy safe”. These are implementation qualities, not promotional UI.

### 3. Active Trip cockpit — #148

Goal: a glanceable driving surface with a clear route focus.

Primary facts may include, when truthful:

- recorded distance
- elapsed duration
- current/recent speed
- average speed
- SOC snapshot / current known SOC
- estimated consumption from existing Trip logic

Do not show battery voltage in the Trip UI.

Supporting visualization:

- compact route preview
- compact speed trend
- compact altitude trend when altitude samples are available

Interaction:

- restrained slide-to-end control
- preserve existing interrupted / pause / resume semantics
- no selected-destination implication while the Trip is active

### 4. Completed Trip overview — #149

Goal: compress the current long detail page into a useful summary before deeper diagnostics.

Endpoint summary:

- start + end belong in one card
- start icon: small primary-green play/start glyph
- end icon: small red flag
- no large endpoint rings or halos

Metric hierarchy may include:

- distance
- elapsed duration
- average speed
- average consumption
- start -> end SOC
- start -> end mileage

Missing values stay unavailable. Do not infer legacy data.

### 5. Route / map — #150

Goal: recorded geometry is the visual focus.

Rules:

- dark, low-noise map surface
- restrained primary-green route treatment where reliable
- keep large-gap / continuity semantics from the existing reliability work
- start marker is compact and green
- end marker is a small red flag only
- map controls stay secondary
- no animated route pulse or neon route glow

Speed coloring may be retained only when it remains readable and truthful. A legend should explain any color encoding.

### 6. Diagnostics / trends — #151

Goal: progressive disclosure instead of a raw GPS log.

Default summary may include:

- track continuity / reliability state
- point count
- location accuracy context
- long-gap summary
- start/end/min/max altitude
- cumulative ascent/descent
- speed trend
- altitude trend

Raw recent GPS points must not be expanded by default. Provide an explicit troubleshooting action such as `查看轨迹点`.

If altitude is unavailable or untrustworthy, omit the metric rather than showing a synthetic value.

## Typography and density

Continue the v0.5 typography system. The Trip pass should improve density by hierarchy and spacing, not by making text tiny.

Recommended behavior:

- page title remains clearly dominant
- major Trip number is visually strong
- labels use secondary text
- values use primary text
- 4dp spacing grid remains authoritative
- compact cards should still preserve >=48dp interactive targets where applicable
- fontScale 1.3 must remain usable; do not solve density by shrinking text below the shared design system

## Animation policy

Animation is allowed only where it communicates state or interaction.

Approved:

- slide thumb drag
- progress fill during slide
- subtle spring/snap return after an incomplete slide
- subtle completion transition

Not approved:

- pulsing buttons
- breathing neon rings
- looping route glow
- animated decorative sparks
- large bloom around controls

## Data truth and reliability

The redesign must preserve existing domain authority:

- no new Trip schema solely for visuals
- UI does not recompute energy if the existing Trip layer already owns the estimate
- large GPS gaps stay visibly non-continuous
- unknown addresses / SOC / mileage / altitude / energy remain unavailable
- estimated energy must not be presented as BMS measurement
- active Trip remains bound to its original vehicleId

## PR strategy

Implement the work as narrow PRs mapped to #146–#151. A PR should avoid mixing unrelated Trip surfaces unless a shared component makes separation unreasonable.

Suggested order:

1. #146 Trip list
2. #147 READY + slide interaction foundation
3. #148 active Trip cockpit
4. #149 completed overview
5. #150 route/map marker polish
6. #151 diagnostics + trends

Shared component extraction is encouraged when it reduces duplication, but avoid a speculative component framework.

## Acceptance gate

Every implementation PR requires:

- Android CI green
- no regression to Trip persistence/state transitions
- no regression to GPS continuity semantics
- Dark First visual comparison on a real device
- no bright glow / neon treatment introduced
- primary green remains `EVDesignTokens.Energy.green`

Final closure of #145 requires one physical-device pass across all six surfaces.
