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

The 2026-08-28 physical-device Trip review approved a denser Trip-specific refinement of the v0.5 Dark First system.

Authority: `docs/TRIP_V0.6_APPROVED_UI_BASELINE.md`
Owning issue: #145

Implementation slices:

- #146 — Trip list density + endpoint hierarchy
- #147 — READY preparation + slide-to-start
- #148 — active Trip cockpit + speed/altitude trends
- #149 — completed Trip overview + single endpoint card
- #150 — route/map marker and trajectory polish
- #151 — diagnostics summary + progressive disclosure
- #152 — future destination selection capability; explicitly not part of current v0.6 implementation

Trip v0.6 guardrails:

- retain `EVDesignTokens.Energy.green` (`#32F080`) as the only primary Trip green
- no bright glow, neon bloom or advertising-style decorative light effects
- no voltage on Trip surfaces
- no destination selector until the product actually owns destination planning
- start marker uses a compact green start/play shape
- end marker uses a compact red flag without outer ring
- raw GPS point lists are secondary troubleshooting UI, not default detail content
- speed and altitude trends are supporting evidence and must omit unavailable/untrustworthy data

---

# Records & Stats v0.6 Approved Follow-up

The 2026-08-29 Records / Stats review extends the same restrained v0.6 refinement to the two existing data-heavy tabs without changing product scope.

Authority: `docs/RECORDS_STATS_V0.6_APPROVED_UI_BASELINE.md`  
Owning issue: #159

Implementation direction:

- Records — one dominant ledger summary, denser charging timeline, compact add action
- Add/Edit Charge — group fields by charging facts, location/type and vehicle snapshot while preserving existing business behavior
- Stats overview — month summary first, then month comparison and clearly labeled Trip SOC estimate
- Stats evidence — lightweight recent-month trend, charger mix, common-place ranking, lifetime totals and charge-to-charge/Trip coverage evidence
- keep charging-record kWh facts and Trip SOC-derived energy estimates visibly separate

Records & Stats v0.6 guardrails:

- retain `EVDesignTokens.Energy.green` (`#32F080`) as the primary product green, used with restrained area/intensity
- no blue primary action accent, bright glow, neon bloom or advertising-style chart effects
- no schema, backend, cloud analytics, BMS/OBD or new business capability hidden inside visual work
- unavailable evidence remains unavailable; do not fabricate charging efficiency, live SOC or BMS energy
- prefer small Compose-native trend/bar primitives over a heavy chart dependency
- keep #70 as physical v0.5 visual closeout, #42 as accessibility/state-safety acceptance and #22 as global spacing/inset polish
