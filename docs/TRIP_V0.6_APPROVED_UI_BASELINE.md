# EV Charge Book Trip v0.6 Approved UI Baseline

Status: approved visual / interaction baseline from 2026-08-28 review, refined by the 2026-08-29 physical-device comparison and the completed-detail information-architecture correction.

Owning issue: #145

## Purpose

This document is the implementation authority for the Trip v0.6 UI pass. It refines the existing v0.5 Dark First language without changing Trip persistence, GPS truth semantics, or the current Local First product boundary.

The approved direction is **denser, clearer, and more data-led** than the earlier physical-device Trip UI. It should not look like a debug screen, a dashboard of unrelated cards, or an advertising surface.

## Design token authority

Use the existing Android token as the source of truth:

```kotlin
EVDesignTokens.Energy.green // 0xFF32F080
```

Do not introduce a second Trip-specific green.

### Color behavior

- Primary green is used for selected state, positive status, route/start accents, compact trend lines and interaction progress.
- Avoid bright outer glow, neon bloom, luminous rings or large halos.
- Red remains semantic: completed endpoint flag, danger or critical interruption only.
- Start and end must remain distinguishable by icon shape, not only by color.
- While a Trip is still recording, the latest point is a green `当前点`, not a red completed endpoint.
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

### 1. Trip home / history — #146, #175

Goal: make the Trip landing page compact and immediately useful.

The Trip home and READY preparation are separate states. Entering the Trip tab should not immediately expose the preparation form.

Display priority:

1. compact start action
2. latest completed Trip summary when available
3. date/time and completion state
4. explicit start/end presentation
5. distance + duration
6. average consumption when trustworthy

Rules:

- fit multiple recent Trips on one screen
- use predictable ellipsis for long addresses
- avoid oversized introductory copy
- avoid decorative status cards
- do not expose raw GPS diagnostics in the list
- use only persisted completed-Trip facts in the latest summary

### 2. READY / preparation — #147, #171, #175

Goal: tell the user what will be recorded immediately before the user starts recording.

Display priority:

1. selected vehicle identity
2. current SOC / mileage when known
3. compact GPS recording truth
4. one clear start interaction

GPS readiness / accuracy must only be shown when the app actually owns a real sample. Before recording starts, do not invent a GPS quality badge merely to match a mockup.

Interaction:

- READY is entered explicitly from Trip home
- back returns to Trip home without creating an empty Trip
- use a restrained slide-to-start control
- thumb and progress use the primary green family
- the animated progress edge remains rounded with the capsule track
- no bright glow
- partial drag returns naturally
- successful drag requires a meaningful threshold
- preserve an accessible semantic/click action so sliding is not the only usable input

Do not add feature-advertising cards such as “high accuracy / continuous recording / privacy safe”. These are implementation qualities, not promotional UI.

### 3. Active Trip cockpit — #148, #168, #171

Goal: a glanceable driving surface with a clear route focus.

Primary facts may include, when truthful:

- recorded distance
- current/recent trusted speed
- elapsed duration
- average speed
- max speed
- starting SOC

Point counts / altitude sample counts belong to supporting telemetry and should not duplicate the primary cockpit grid.

Do not show battery voltage in the Trip UI.

Supporting visualization:

- compact truthful route preview
- compact speed trend
- compact altitude trend when altitude samples are available
- sparse X elapsed-time and Y value references on trend plots

Interaction:

- restrained slide-to-end control
- the slide opens the final completion surface directly; do not stack a redundant generic confirmation alert in between
- preserve existing interrupted / pause / resume semantics
- no selected-destination implication while the Trip is active

### 4. Completed Trip overview — #149

Goal: compress the detail page into a useful summary before deeper diagnostics.

Endpoint summary:

- start + end belong in one card
- start icon: small primary-green play/start glyph
- completed end icon: small red flag
- no large endpoint rings or halos

Metric hierarchy may include:

- distance
- elapsed duration
- average speed
- average consumption
- start -> end SOC
- start -> end mileage

Missing values stay unavailable. Do not infer legacy data.

### 5. Route / map — #150, #168

Goal: recorded geometry is the visual focus.

Rules:

- dark, low-noise route surface
- restrained primary-green route treatment where reliable
- keep large-gap / continuity semantics from the existing reliability work
- start marker is compact and green
- completed end marker is a small red flag only
- recording latest point stays green; interrupted/non-final latest point is not presented as a completed endpoint
- map controls stay secondary
- no animated route pulse or neon route glow
- do not draw a fake basemap when the product does not own one

Speed coloring may be retained only when it remains readable and truthful. A legend should explain any color encoding.

### 6. Diagnostics / trends — #151, #168

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

Trend plots must provide sparse axis context rather than an unlabeled decorative line. Long gaps stay disconnected and are never interpolated.

Raw recent GPS points must not be expanded by default. Provide an explicit troubleshooting action such as `查看轨迹点`.

If altitude is unavailable or untrustworthy, omit the metric rather than showing a synthetic value.

## Completed Trip detail information architecture — #178 / PR #179

The approved board treats completed/selected Trip detail as separate reading surfaces rather than one long scroll. This presentation correction is now implemented in `main` by PR #179, merged as `88e1e2b` after Android CI run `33198331727` passed.

Supported sections:

- `概览` — completed Trip summary + one compact start/end endpoint card
- `轨迹` — truthful route preview + trusted speed/altitude trends
- `数据` — altitude summary + GPS reliability and raw-point progressive disclosure

Rules:

- detail opens on `概览`
- switching sections is presentation-only and must not mutate Trip data
- `概览` stays materially shorter than the old all-in-one scroll
- `轨迹` owns route and trend evidence
- `数据` owns reliability / diagnostic evidence
- unavailable route or altitude uses a compact truthful empty state
- no unsupported `充电`, `备注`, destination-planning or navigation tabs are added merely because an early mock contained them
- no fake basemap is introduced
- completed red-flag, active green `当前点`, LONG_GAP discontinuity and raw-point collapsed-by-default semantics remain unchanged

Physical comparison against approved detail-board screens remains required before final UI acceptance.

## Completion flow — #173

The final completion form is part of the Trip interaction language even though it is not one of the six browsing surfaces.

After slide-to-end:

1. show GPS distance + starting SOC / mileage evidence
2. collect ending SOC / optional ending mileage
3. show the existing SOC-derived estimate only when trustworthy
4. expose explicit `继续行驶` and `保存并结束`

Rules:

- one explicit completion confirmation is enough; do not stack a second generic alert before it
- ending SOC remains 0..100
- ending mileage remains optional, numeric, non-negative and never below starting mileage
- existing start-mileage + GPS-distance prefill may remain, but is editable
- estimate remains clearly non-BMS
- dismiss / continue never ends the Trip

## System inset ownership

The root `MainActivity` owns system safe-area padding for the normal tab content. Nested Trip Scaffolds / TopAppBars must not claim a second top system inset. The 2026-08-29 device comparison showed that double ownership creates a large blank band above Trip content.

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
- rounded progress fill during slide
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

## Implementation history

Original slices:

1. #146 / PR #154 — Trip list
2. #147 / PR #155 — READY + slide foundation
3. #148 / PR #156 — active Trip cockpit
4. #149/#150 / PR #158 — completed overview + route markers
5. #151 / PR #161 — diagnostics + trends

Physical-device corrections:

- #168 / PR #170 — rounded slider progress, labeled trends, history density, inset ownership, recording endpoint semantics
- #171 / PR #172 — compact READY information group + active metric de-duplication
- #173 / PR #174 — compact completion flow + removal of redundant intermediate confirmation
- #175 / PR #176 — separate Trip home/history from READY preparation
- #178 / PR #179 — split completed Trip detail into `概览` / `轨迹` / `数据` supported surfaces; Android CI Green

#152 remains the future destination-selection capability and is not part of v0.6.

## Acceptance gate

Every implementation PR requires:

- Android CI green
- no regression to Trip persistence/state transitions
- no regression to GPS continuity semantics
- Dark First visual comparison on a real device
- no bright glow / neon treatment introduced
- primary green remains `EVDesignTokens.Energy.green`

Final closure of #145 requires one physical-device pass across:

1. Trip home/history
2. READY preparation
3. active cockpit
4. completion form
5. completed overview / `概览`
6. route / `轨迹`
7. speed + altitude trends
8. diagnostics / `数据` disclosure
9. detail-section switching and truthful unavailable states
10. Dark / Light readability where the shared theme supports both

Also verify 320–360dp width and fontScale 1.3 before declaring UI acceptance complete.
