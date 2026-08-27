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
- Battery level
- Estimated range
- Monthly cost
- Recent charging summary

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

## Acceptance Criteria

- App has a unified visual language
- Dark theme looks production ready
- Pages no longer feel like simple card collections
- Trip becomes a first-class feature
- Theme switching works consistently
