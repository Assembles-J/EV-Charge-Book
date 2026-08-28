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

### Background Update Card

The release-only APK updater is part of the same Dashboard visual system, not a generic Material dialog.

Visual rules:

- float above the bottom navigation without a scrim;
- use the Dashboard horizontal gutter (8dp starting point);
- use `EVDesignTokens.Dark.surfaceElevated` with a subtle `EVDesignTokens.Dark.outline` hairline;
- use the large radius family (about 24dp) and no visible Material elevation;
- keep the icon in a compact circular tinted container;
- use energy green for normal/update-ready actions, warning only for permission state, and danger only for failures;
- keep primary copy near-white and supporting copy muted gray-green;
- prefer compact text/pill actions over large tonal buttons;
- downloading uses a thin green progress line at the card bottom;
- the card must remain non-modal and must never interrupt Local First usage.

State copy hierarchy:

1. `正在后台更新 <version>` — application remains usable while download and verification run.
2. `<version> 已准备好` — compact install action becomes available.
3. `允许安装后即可更新` — guide the Android unknown-source permission flow without changing card geometry.
4. `后台更新失败` — error accent plus retry, while keeping the same dark card surface.

The visual alignment issue is tracked in #125. Functional old-release -> new-release device acceptance remains tracked separately by #102.

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
