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
