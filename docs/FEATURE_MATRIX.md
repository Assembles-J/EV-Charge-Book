# EV Charge Book Feature Matrix

Version: v1.2.0
更新时间: 2026-08-29
状态说明: `[x]` 仅表示对应代码/交付项已完成；真机功能、真机视觉和 Production Release 验收单独列出，不从代码状态推导。

## v0.1 - Local Charging Book

### Vehicle
- [x] Vehicle profile persistence
- [x] Vehicle edit flow

### Charging
- [x] Add charging record
- [x] Charging history
- [x] Edit charging record
- [x] Date/time selection
- [x] Charger type / remark
- [x] Delete confirmation / save feedback

### Dashboard / Stats
- [x] Monthly cost
- [x] Monthly energy
- [x] Average electricity price
- [x] Charging count
- [x] Total cost / energy

### Delivery
- [x] Android CI baseline
- [x] Debug APK Artifact
- [x] First signed production APK
- [x] v0.1 physical core CRUD acceptance

---

## v0.2 - Vehicle, Location & Trip Foundation

### Multi Vehicle
- [x] Add multiple vehicles
- [x] Current/default vehicle switcher
- [x] Vehicle archive
- [x] Dashboard / Records / Stats scoped by selected vehicle
- [x] Trip bound to selected vehicle at start

### Vehicle Catalog
- [x] Bundled/offline catalog seed
- [x] Brand / series / model-year / trim search baseline
- [x] Catalog parameter confirmation/override
- [x] Custom vehicle fallback
- [x] managed/offline-first catalog refresh platform
- [ ] broader auditable coverage / bulk data pipeline (#20)

### Location
- [x] Get current location
- [x] Charging record location capture
- [x] WGS84 raw coordinate persistence
- [x] AddressResolver / Android Geocoder fallback
- [x] Geocoder failure does not block coordinate facts
- [ ] physical permission / Geocoder / restore acceptance (#14)

### Trip tracking core
- [x] Manual start / completion
- [x] `RECORDING / INTERRUPTED / COMPLETED`
- [x] Location foreground service
- [x] Record lat/lng/altitude/speed/bearing/accuracy/time
- [x] Distance / elapsed / moving / stopped / average / max speed
- [x] Trip history and detail
- [x] Local Backup / Restore Trip coverage
- [x] provider / permission / lifecycle diagnostics
- [x] callback heartbeat separated from accepted-point heartbeat
- [x] time-based callback liveness (`SAMPLE_DISTANCE_METERS = 0f`)
- [x] app-level stationary heartbeat/write throttling
- [x] LONG_GAP continuity semantics
- [x] real route geometry preview without fake map
- [x] trusted speed coloring and legend
- [x] elevation start/end/min/max/gain/loss
- [x] validity/review cleanup semantics
- [ ] background-other-app / stationary physical reliability acceptance (#77)

---

## v0.3 - Local Analytics & Data Reliability

- [x] Cost / energy trends
- [x] Fast/slow charger mix
- [x] Monthly comparison
- [x] Cost per 100 km estimate
- [x] charged kWh per 100 km estimate
- [x] Trip / charging interval evidence
- [x] common charging places
- [x] CSV analysis export
- [x] Local JSON Backup / Restore
- [x] non-blocking anomaly warnings
- [x] Trip validity-aware analytics
- [x] Charge facts vs Trip SOC-derived energy estimate separation

---

## v0.4 - Local First Sync & Catalog

### Sync foundation
- [x] Vehicle stable `syncId`
- [x] Vehicle `updatedAtEpochMillis`
- [x] ChargingRecord stable `syncId`
- [x] ChargingRecord tombstone
- [x] Room migration / old-row identity generation
- [x] Backup / Restore sync metadata
- [ ] protocol/schema runtime implementation (#27)
- [ ] push/pull cursor
- [ ] idempotent upsert / tombstone propagation
- [ ] smallest HTTPS + Spring Boot + PostgreSQL slice

TripSession / TripPoint cloud sync is intentionally not part of the first cloud slice.

### Catalog data pipeline
- [x] managed catalog runtime/update platform
- [x] Android offline-first refresh
- [ ] source provenance
- [ ] repeatable bulk import/validation
- [ ] broader production coverage

---

## v0.5 / v0.6 - Local Experience & Trip UI Closeout

### Global UI
- [x] Dark First design system
- [x] persisted Light mode
- [x] Dashboard / Records / Stats / Trip / Vehicle core rebuild
- [x] shared density / low-elevation surface language
- [ ] final five-page physical visual pass (#70)
- [ ] accessibility / large font / small screen / touch-target pass (#42 / #22)

### Dashboard
- [x] dynamic Vehicle Hero
- [x] current VehicleState SOC / mileage
- [x] information-dense latest completed Trip card
- [ ] physical Hero/recent-Trip visual closeout (#94 / #95)

### Records / Stats v0.6
- [x] compact ledger summary + dense charging timeline
- [x] compact analytics hierarchy + lightweight evidence bars
- [ ] Dark/Light + 320-360dp + fontScale physical pass (#159 / #164 / #165)

### Trip v0.6 UI
- [x] Trip home/history separated from READY preparation
- [x] dense truthful history rows
- [x] READY vehicle/SOC/mileage/GPS preparation
- [x] restrained slide-to-start
- [x] active cockpit with trusted route/speed/altitude trends
- [x] active primary metric de-duplication
- [x] slide-to-end -> single compact completion form
- [x] completed overview + compact endpoint card
- [x] completed detail split into `概览` / `轨迹` / `数据`
- [x] route start / completed end / active current-point semantics
- [x] LONG_GAP remains disconnected
- [x] sparse X/Y context on speed/altitude trends
- [x] raw GPS diagnostics collapsed by default
- [x] no fake destination / route planning / fake basemap introduced
- [ ] full design-device matrix (#145 / #168)
- [ ] completed detail tabs / narrow width / fontScale / Dark-Light pass (#178)

### Trip -> VehicleState
- [x] start SOC / mileage snapshot
- [x] explicit end SOC
- [x] optional validated end mileage
- [x] SOC-derived energy estimate when trustworthy
- [x] Trip completion updates VehicleState
- [x] deletion rebuilds VehicleState
- [ ] physical data-closure acceptance (#124)

### Background / notification
- [x] ongoing elapsed time + trusted distance
- [x] active Trip deep link
- [x] provider/permission interruption -> repair notification
- [x] explicit user resume after repair
- [x] Android 13+ non-blocking notification permission flow
- [ ] physical lock-screen / repair round-trip (#26)

---

## Production Updater

- [x] `latest.json` discovery
- [x] DownloadManager
- [x] SHA-256 verification
- [x] unknown-source permission flow
- [x] Android installer handoff
- [x] root composition wiring
- [x] Dashboard-style non-modal update UI
- [ ] old-production -> new-production physical upgrade acceptance (#102)

---

## Future / Optional

### Destination planning
- [ ] optional destination selection / pre-trip endpoint planning (#152)

Not part of Trip v0.6 and must not appear as placeholder UI before product capability exists.

### Map renderer
- [ ] MapProvider abstraction when needed
- [ ] MapLibre real-map prototype when justified

Current truthful WGS84 route preview is sufficient for Trip v0.6; map SDK is not a completion blocker.

### OBD-II
- [ ] optional Vehicle Speed PoC
- [ ] evaluate supported telemetry only if product evidence justifies it

---

## Current physical acceptance bundle

- [ ] #145 Trip v0.6 design-device matrix
- [ ] #168 Trip device-fidelity corrections
- [ ] #178 completed-detail tabs / responsive readability
- [ ] #77 background callback / stationary hold
- [ ] #124 Trip SOC -> VehicleState
- [ ] #26 lock-screen / repair notifications
- [ ] #14 Location / Geocoder
- [ ] #70 / #94 / #95 / #42 / #22 UI closeout
- [ ] #102 production updater

---

## Change Log

### v1.2.0
- reconciled stale v0.1/v0.2 unchecked items with current `main`
- recorded current Trip reliability, analytics and VehicleState code baselines
- added Trip v0.6 home/READY/active/completion/detail information architecture
- separated code completion from physical acceptance owners
- clarified MapLibre/destination/OBD remain optional or future, not current Trip blockers

### v1.1.0
- reconciled early v0.1 progress
- added initial v0.2 multi-vehicle / catalog / location / trip scope
