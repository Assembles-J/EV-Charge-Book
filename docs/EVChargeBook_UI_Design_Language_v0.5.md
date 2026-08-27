# EV Charge Book v0.5 UI Design Language

## Overview

EV Charge Book v0.5 UI redesign aims to upgrade the current functional UI into a mainstream EV consumer application experience.

Core direction:

> Dark First · Energy Focus · Vehicle Companion

The redesign keeps the existing v0.5 product scope and does not introduce future capabilities.

## Scope

Included:

- Dashboard
- Charging Records
- Trip
- Energy Statistics
- Vehicle Information

Excluded:

- AI assistant
- Battery prediction
- Future intelligent features

## Theme Strategy

### Dark First

Default experience:

- Deep black background
- Dark surface cards
- Bright energy green highlights
- High contrast information hierarchy

### Light Theme

Optional theme switch:

- Same information structure
- Same components
- Different surface and contrast tokens

## Color Tokens

### Dark Theme

| Token | Value | Usage |
|---|---|---|
| Background | #0A0E0D | App background |
| Surface | #121716 | Cards |
| Primary Text | #E6F0EC | Main content |
| Secondary Text | #9AA6A0 | Supporting text |
| Energy Green | #32F080 | Energy information |
| Success | #23D18B | Completed state |
| Warning | #FFB020 | Warning state |
| Danger | #FF4D4F | Error state |

## Typography

- Headline: 20sp Semibold
- Title: 17sp Semibold
- Body: 15sp Regular
- Caption: 12sp Regular

## Spacing

Base unit: 4dp

- xs: 4dp
- sm: 8dp
- md: 16dp
- lg: 24dp
- xl: 32dp
- xxl: 48dp

## Core Components

### Hero Vehicle Card

Purpose:

Primary vehicle identity and status display.

Contains:

- Vehicle image
- Battery status
- Range
- Current state

### Metric Card

For:

- Cost
- Energy
- Consumption

### Charging Timeline Item

Displays:

- Charge type
- SOC change
- Energy
- Cost
- Location

### Trip Map Card

Displays:

- Route
- Distance
- Duration
- Speed
- Energy consumption

### Chart Card

Displays:

- Monthly trends
- Cost analysis
- Energy distribution

## Navigation

Bottom navigation:

- 首页
- 充电
- 行程
- 能耗
- 车辆

## Implementation Principles

- Prefer reusable Compose components
- Avoid one-off UI implementations
- Keep theme tokens centralized
- Build pages from shared components
