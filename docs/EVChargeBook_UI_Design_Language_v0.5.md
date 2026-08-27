# EV Charge Book v0.5 UI Design Language

版本: v1.1.0
更新时间: 2026-08-27
状态: Implemented baseline on `main` via PR #71; real-device polish remains

## Overview

EV Charge Book v0.5 uses a Dark First EV cockpit visual language while keeping the existing Local First product scope.

Core direction:

> Dark First · Energy Focus · Vehicle Companion · Real Data Only

The visual redesign does not introduce AI, battery prediction or fabricated vehicle telemetry.

## Scope

Included:

- Dashboard
- Charging Records
- Trip
- Energy Statistics
- Vehicle Information
- Add/Edit charge flows
- Vehicle catalog/editor/Bluetooth/data utility surfaces

Excluded:

- AI assistant
- battery/SOH prediction
- fabricated live SOC or remaining range
- cloud-dependent UI as a runtime requirement

## Theme Strategy

### Dark First

Dark is the default product appearance.

Current core tokens are implemented in `EVDesignTokens.kt`:

| Token | Value | Usage |
|---|---:|---|
| Background | `#090D0C` | app background |
| Surface | `#121716` | normal surface |
| Surface Elevated | `#19201D` | higher-emphasis surface |
| Primary Text | `#EAF3EE` | primary content |
| Secondary Text | `#9AA6A0` | supporting content |
| Outline | `#26302C` | restrained separation |
| Energy Green | `#32F080` | primary energy/accent |
| Success | `#23D18B` | positive state |
| Warning | `#FFB020` | caution |
| Danger | `#FF4D4F` | error/destructive state |

### Light Theme

Light mode is implemented as an explicit persisted user choice. It keeps the same information architecture and business semantics.

System-theme-following is not part of the accepted v0.5 baseline. If added later it should be a third appearance mode rather than silently changing the existing Dark First default.

## Typography and Spacing

The merged theme centralizes typography, shapes and spacing instead of redefining them per screen.

Spacing scale:

- xxs: 4dp
- xs: 8dp
- sm: 12dp
- md: 16dp
- lg: 24dp
- xl: 32dp
- minimum general touch target: 48dp

Large numeric values are reserved for primary cost/energy/trip metrics; secondary facts use title/body levels to avoid card-dashboard visual noise.

## Core Components

### Hero Vehicle Card

Purpose: vehicle identity and primary visual focus.

Contains only stored or otherwise trusted vehicle facts:

- brand/model
- local bundled vehicle artwork when an exact mapping exists
- battery capacity
- rated range
- recordability/current app state

It must not invent live SOC or remaining range when the app has no runtime source.

Vehicle artwork is compiled into the APK under `android/app/src/main/res/drawable-nodpi/`. Runtime Base64/network image loading is not used.

Current bundled mappings:

- BYD / 比亚迪 base Seal / 海豹 2025
- Leapmotor / 零跑 C16 2026
- Xiaomi / 小米 SU7 2024
- Tesla / 特斯拉 Model 3

Matching remains strict; nearby unsupported models use the local fallback silhouette instead of borrowing the wrong image.

### Charging Timeline

Charging history emphasizes:

- time/place
- SOC transition
- energy added
- cost
- unit price
- charger type / odometer when available

### Trip Cockpit

Trip is a first-class v0.5 surface. READY / LIVE / INTERRUPTED states expose only useful, trustworthy driving information. Route rendering must preserve GPS-gap semantics and never draw a large missing interval as trustworthy continuous data.

### Analytics

Statistics use a small number of hierarchy levels:

1. current month cockpit
2. comparison/trend/mix/place analytics
3. lifetime and interval evidence

Derived/estimated values must remain distinguishable from recorded facts.

### Vehicle Garage

Vehicle management uses the same Hero language, then compact switch/settings rows. Archiving is preferred over destructive deletion when historical data exists.

## Navigation

Current primary navigation is five destinations:

- 总览
- 记录
- 统计
- 行程
- 车辆

The bottom navigation uses a restrained flat treatment and green selected state instead of the default Material selected pill.

## Empty / Error / Warning States

- empty states should be compact and action-oriented
- GPS/warning states must include text/icon semantics, not color alone
- missing address must not imply missing coordinates
- unsupported artwork falls back gracefully
- lack of real data must show an empty/unknown state instead of generated values

## Implementation Principles

- Jetpack Compose first
- shared tokens/components before one-off styling
- existing MVVM/business rules remain authoritative
- Dark and Light share the same information structure
- visual polish must not weaken Local First or data-trust rules
- no new heavy framework purely for visual effects

## Acceptance Status

Merged through PR #71.

Automated baseline: Android Build Run #294 passed with Debug APK artifact.

Still requires real-device review for:

- five primary pages
- long names and large font
- 320-360dp widths
- Light mode contrast
- active Trip usability
- final spacing/animation polish
