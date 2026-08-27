# EV Charge Book v0.5 UI Redesign Task Breakdown

更新时间: 2026-08-27
状态: Core redesign merged via PR #71; real-device polish remains

## Goal

Replace the prototype-style UI with a consistent EV consumer app experience without introducing unsupported telemetry or unrelated future scope.

## Phase P0 - Core Visual Upgrade

### Design System

- [x] EV Design Tokens
- [x] Dark Theme default
- [x] persisted Light Theme switch
- [x] centralized typography, spacing and shapes
- [x] compact shared empty states

### Dashboard

- [x] vehicle-focused Hero
- [x] local bundled vehicle artwork for exact supported matches
- [x] battery capacity and rated range from stored vehicle facts
- [x] monthly cost/energy cockpit
- [x] recent charging timeline
- [x] no fabricated live SOC or remaining range

Vehicle artwork policy:

- artwork is compiled into the APK under `res/drawable-nodpi`
- no runtime Base64 decode path
- no runtime network/hotlink dependency
- matching is strict enough to avoid wrong-model rendering
- unsupported models use the local fallback silhouette

Current exact artwork coverage:

- BYD / 比亚迪 base Seal / 海豹 2025
- Leapmotor / 零跑 C16 2026
- Xiaomi / 小米 SU7 2024
- Tesla / 特斯拉 Model 3

### Charging Records

- [x] timeline history
- [x] SOC transition
- [x] energy and cost emphasis
- [x] location/type/odometer when available
- [x] create/edit flows aligned to the same visual language

### Trip

- [x] READY / LIVE / INTERRUPTED cockpit states
- [x] history and detail hierarchy
- [x] route preview using real Trip geometry
- [x] GPS gap / diagnostic credibility treatment
- [x] no fake continuous line across large gaps

Trip UI acceptance does not replace the separate long-drive GPS reliability acceptance.

## Phase P1 - Information Enhancement

### Energy Statistics

- [x] monthly cost/energy cockpit
- [x] month-over-month comparison
- [x] charger mix
- [x] common places
- [x] lifetime totals
- [x] interval estimates and Trip coverage evidence

### Vehicle Information

- [x] vehicle Hero reuse
- [x] garage/switching
- [x] catalog/editor
- [x] Bluetooth prompt
- [x] backup / CSV utility rows

### Theme Switching

- [x] Dark First
- [x] explicit Light Theme
- [x] preference persistence

System-theme-following is not required for the accepted v0.5 baseline.

## Phase P2 - Polish

- [ ] selective animation/micro-interaction improvements
- [x] empty states
- [ ] five-page real-device visual pass
- [ ] large-font/small-screen acceptance
- [ ] Light mode contrast acceptance
- [ ] long-name/overflow acceptance

## Automated Acceptance

- [x] PR #71 merged into `main`
- [x] PR head included the then-current `main` baseline
- [x] Android Build Run #294 Green
- [x] Debug APK artifact produced
- [x] vehicle artwork strict-mapping regression tests

## Follow-up Ownership

Do not duplicate existing hardening work:

- Issue #42: accessibility, large font/small screen, dirty forms, active Trip guards, keyboard/IME
- Issue #22: final page-density/top-inset real-device review
- Issue #66: coordinate-first location/address fallback behavior
- Issue #14: physical Location/Geocoder acceptance
- Issue #26: background/lock-screen/permission notification UX
- Issue #20: scalable vehicle catalog coverage pipeline

## Development Rules

- Jetpack Compose
- reusable components/tokens where practical
- preserve MVVM/business behavior
- no business-logic changes purely for styling
- a black background alone is not sufficient; each page needs clear hierarchy/domain visualization
- CI Green is not equivalent to physical-device acceptance

## Acceptance Criteria

Core implementation is considered merged when:

- [x] app has a unified visual language
- [x] Dashboard has a vehicle visual focus
- [x] charging history uses a timeline hierarchy
- [x] Trip is a first-class surface
- [x] Dark/Light switching works and persists
- [x] unsupported artwork degrades safely
- [x] automated Android build/test baseline is Green

Final closeout of Issue #70 still requires:

- [ ] real-device visual acceptance of Dashboard / Records / Stats / Trip / Vehicle
- [ ] only small polish fixes found by that pass
