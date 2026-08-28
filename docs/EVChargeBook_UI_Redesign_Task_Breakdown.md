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

The 2026-08-28 Trip design review approved a denser Trip-specific refinement of the v0.5 Dark First system. The 2026-08-29 device comparison then exposed concrete fidelity regressions; those are now tracked as correction slices instead of starting another redesign.

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

Code-side Trip v0.6 work above is in `main`. These Issues remain open only where their explicit physical-device checks are still pending.

## Trip v0.6 guardrails

- retain `EVDesignTokens.Energy.green` (`#32F080`) as the only primary Trip green
- no bright glow, neon bloom or advertising-style decorative light effects
- no voltage on Trip surfaces
- no destination selector until the product actually owns destination planning
- Trip home/history and READY preparation are separate interaction states
- do not invent GPS readiness/accuracy before a real sample exists
- slide progress keeps a rounded capsule edge at every drag position
- slide-to-end goes directly to one explicit completion form; do not stack a redundant generic alert
- start marker uses a compact green start/play shape
- completed end marker uses a compact red flag without outer ring
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
4. Completion — keyboard/IME, 320–360dp, fontScale 1.3, continue/save safety
5. Completed detail — endpoint card and metric hierarchy
6. Route — real geometry, start/end/current marker semantics, LONG_GAP discontinuity
7. Trends — X/Y references, speed/altitude truth, no interpolation
8. Diagnostics — collapsed by default, `查看轨迹点` explicit

Do not close #145 from CI alone. Use the latest Debug APK from `main` for the final physical comparison against the approved v0.6 board.
