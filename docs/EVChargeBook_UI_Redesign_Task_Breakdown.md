# EV Charge Book v0.5 UI Redesign Task Breakdown

Issue: UI redesign for v0.5 Dark First experience

## Goal

Replace the current prototype-style card layout with a polished EV consumer app experience.

## Phase P0 - Core Visual Upgrade

### Design System

- Create EV Design Tokens
- Create Dark Theme default
- Prepare Light Theme switching
- Standardize typography and spacing

### Dashboard

Target:

Hero Vehicle Dashboard

Requirements:

- Vehicle visual focus
- Prefer real manufacturer artwork for supported exact vehicle matches
- Battery capacity and rated range from stored vehicle facts
- Monthly cost
- Recent charging summary

Vehicle artwork policy:

- Official manufacturer media sources are preferred over generic stock images
- Matching must be strict enough to avoid showing the wrong model
- Official remote artwork may be cached by the Android image loader
- Unsupported models fall back to the local EV illustration
- Do not fabricate live SOC or estimated remaining range when the app has no runtime source

Current supported official artwork mapping:

- BYD / 比亚迪 + base SEAL / 海豹 model -> BYD Media Hub official SEAL artwork

### Charging Records

Target:

Timeline based charging history

Requirements:

- Home charging / fast charging distinction
- SOC change
- Energy amount
- Cost
- Location

### Trip

Target:

Main v0.5 differentiating page

Requirements:

- Route map card
- Start and destination
- Distance
- Duration
- Average speed
- Energy consumption
- Recent trips list

## Phase P1 - Information Enhancement

### Energy Statistics

Upgrade:

- Monthly cost overview
- Energy trend charts
- Charging distribution
- Consumption metrics

### Vehicle Information

Upgrade:

- Vehicle hero card
- Battery information
- Range
- Mileage
- Charging statistics

### Theme Switching

Add:

- Dark First
- Light Theme
- System theme support

## Phase P2 - Polish

- Animation improvements
- Empty states
- Loading states
- Micro interactions

## Development Rules

- Use Jetpack Compose components
- Keep UI components reusable
- Avoid business logic changes
- Follow existing MVVM structure
- A black background alone does not satisfy the redesign: each key page needs a visual focus, hierarchy and domain-specific visualization

## Acceptance Criteria

- App has a unified visual language
- Dark theme looks production ready
- Pages no longer feel like simple card collections
- Dashboard vehicle hero can render an exact supported model image on a real device
- Unsupported vehicle models degrade gracefully to a local fallback graphic
- Trip becomes a first-class feature
- Theme switching works consistently

---

# Trip v0.6 Approved Follow-up

The 2026-08-28 Trip design review approved a denser Trip-specific refinement of the v0.5 Dark First system. The 2026-08-29 device comparison then exposed concrete fidelity regressions; those are tracked as correction slices instead of starting another redesign. Completed Trip detail was subsequently split into separate reading surfaces by #178 / PR #179, and later device/static review produced two focused corrections: PR #184 for endpoint/reliability fidelity and #187 / PR #189 for completion narrow-width/IME accessibility.

Authority: `docs/TRIP_V0.6_APPROVED_UI_BASELINE.md`
Owning issue: #145
Documentation owner: #6

## Original implementation slices

- [x] #146 — Trip list density + endpoint hierarchy — PR #154
- [x] #147 — READY preparation + slide-to-start — PR #155
- [x] #148 — active Trip cockpit + speed/altitude trends — PR #156
- [x] #149 — completed Trip overview + single endpoint card — PR #158
- [x] #150 — route/map marker and trajectory polish — landed with PR #158
- [x] #151 — diagnostics summary + progressive disclosure — PR #161
- [ ] #152 — future destination selection capability; explicitly not part of current v0.6

## Physical-device correction slices

- [x] #168 — slider progress / trend axes / history density / inset ownership / recording endpoint semantics — PR #170
- [x] #171 — READY information density + active cockpit metric de-duplication — PR #172
- [x] #173 — compact Trip completion form + remove redundant intermediate confirmation — PR #174
- [x] #175 — split Trip home/history from READY preparation — PR #176
- [x] #178 — split completed Trip detail into `概览` / `轨迹` / `数据` supported sections — PR #179, merged as `88e1e2b`, Android CI run `33198331727` Green
- [x] PR #184 — completed route endpoint converged on the compact four-corner red flag; redundant long SOC/energy helper copy removed; delayed callback reliability fix kept separate from UI acceptance — merged as `bae3a21`, Android CI run `33229162800` Green
- [x] #187 / PR #189 — completion dialog responsive evidence layout, IME/large-text scrollability and >=48dp continue/save actions — merged as `536fc80`, Android CI run `33236915039` Green

Current Trip code baseline is in `main` through `536fc80`. These Issues remain open only where their explicit physical-device checks are still pending.

## Completed Trip detail section ownership

The selected/completed Trip no longer stacks all supporting evidence into one long scroll.

- `概览` owns completed summary + the compact start/end endpoint card
- `轨迹` owns truthful route geometry + trusted speed/altitude trends
- `数据` owns altitude/reliability evidence + raw GPS point progressive disclosure
- detail opens on `概览`
- switching sections is presentation-only and must not mutate Trip data
- missing route/altitude uses truthful compact unavailable states
- no unsupported `充电`, `备注`, destination or navigation tabs
- no fake basemap

## Trip v0.6 guardrails

- retain `EVDesignTokens.Energy.green` (`#32F080`) as the only primary Trip green
- no bright glow, neon bloom or advertising-style decorative light effects
- no voltage on Trip surfaces
- no destination selector until the product actually owns destination planning
- Trip home/history and READY preparation are separate interaction states
- do not invent GPS readiness/accuracy before a real sample exists
- slide progress keeps a rounded capsule edge at every drag position
- slide-to-end goes directly to one explicit completion form; do not stack a redundant generic alert
- completion validation / `TripEnergyCalculator` remain business-authority behavior, not UI-derived logic
- completion width <380dp or fontScale >=1.3 uses a compact evidence layout instead of squeezing three facts into one row
- completion compact mode stacks `继续行驶` / `保存并结束`; both keep >=48dp height
- completion body remains vertically scrollable under keyboard/IME or large-text pressure
- start marker uses a compact green start/play shape
- completed end marker uses the same compact four-corner red-flag visual language in endpoint card and route
- active latest point stays green and is not described as a completed endpoint
- raw GPS point lists are secondary troubleshooting UI, not default detail content
- speed and altitude trends include sparse axis context and omit unavailable/untrustworthy data
- long GPS gaps remain disconnected
- nested Trip Scaffolds must not double-apply the root system-bar inset

## Current acceptance gate

Remaining work is physical, not another broad code rewrite:

1. Trip home/history — latest summary, 5+ rows, address truncation
2. READY — back/start behavior, compact GPS truth, slide gesture
3. Active — live route, current speed, trends, interrupted state
4. Completion — normal-width visual hierarchy plus 320–360dp / fontScale 1.3+ / keyboard-IME reachability; `继续行驶` must not end the Trip and only valid `保存并结束` may stop it
5. Completed detail `概览` — endpoint card and metric hierarchy
6. Route `轨迹` — real geometry, unified four-corner completed endpoint, start/current marker semantics, LONG_GAP discontinuity, trusted trends
7. Data `数据` — altitude/reliability evidence, collapsed diagnostics, `查看轨迹点` explicit
8. Detail tabs — switching remains readable and stable on 320–360dp / fontScale 1.3
9. Dark/Light — shared-theme contrast/readability where supported
10. #77 separately revalidates post-#184 lock-screen/background callback reliability; CI or UI acceptance must not close it

Do not close #145 from CI alone. Use the latest Debug APK from current `main` for the final physical comparison against the approved v0.6 board.
