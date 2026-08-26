# EV Charge Book Roadmap

版本: v2.4.0
更新时间: 2026-08-26

## 0. 路线原则

以 PROJECT_MASTER / PRODUCT / FEATURE_MATRIX 为准。

继续坚持：简单可维护、Local First、真实数据、先验收后扩功能；原始事实、派生值与估算值必须区分。

---

## v0.1 - Local Charging Book

状态: Released / Accepted

- [x] Room / DAO / Repository / ViewModel
- [x] 车辆创建 / 编辑持久化
- [x] 充电记录新增 / 编辑 / 删除
- [x] Dashboard / Records / Stats
- [x] Android CI / Debug APK
- [x] 真机核心 CRUD 验收
- [x] signed production APK / atomic server release

---

## v0.2 - Vehicle, Location & Trip Foundation

状态: **Core Accepted**

### Odometer & charging data loop (#18)

- [x] nullable `odometerKm`
- [x] Room migration
- [x] Add/Edit/Records 支持里程
- [x] 上一条可靠里程与下降提示
- [x] ChargingIntervalAnalytics
- [x] 区间距离 / cost per 100km / charged kWh per 100km
- [x] Trip coverage evidence
- [x] SOC confidence hints
- [x] 区间明细 + JVM tests

### Local Backup / Restore (#19)

- [x] JSON + schemaVersion
- [x] SAF export/import
- [x] 覆盖确认 + Room transaction
- [x] Vehicle / ChargingRecord / odometer / Location / Trip
- [x] 引用关系与数量校验
- [x] 真机恢复验收

### Multi Vehicle / Vehicle Catalog (#17 / #16)

- [x] selectedVehicleId persisted
- [x] multi-vehicle switch
- [x] Dashboard / Records / Stats isolation
- [x] archive with history retention
- [x] local versioned catalog
- [x] search / override / custom fallback

### Bluetooth connection prompt (#21)

状态: Accepted / Issue Closed

- [x] paired-device selection / persistence
- [x] Android 12+ Nearby Devices permission
- [x] Android 13+ notification permission
- [x] selected-device ACL_CONNECTED notification
- [x] A2DP / HEADSET already-connected state detection
- [x] notification / app-resume opens Trip confirmation flow
- [x] never silently auto-start location
- [x] latest physical functional verification accepted

### Location (#14)

状态: Foundation Implemented

- [x] LocationProvider + Android LocationManager
- [x] current position
- [x] ChargingRecord lat/lng/accuracy
- [x] AddressResolver + Android Geocoder
- [x] address failure preserves coordinates/manual text
- [x] TripRouteGeometry / no-basemap route preview

Optional, non-blocking:
- [ ] external MapProvider / MapLibre real map rendering

### Trip (#15)

状态: Accepted / Issue Closed

- [x] TripSession / TripPoint + migration
- [x] manual start/stop
- [x] foreground location service + persistent notification
- [x] WGS84 / accuracy / speed / bearing / altitude
- [x] distance / elapsed / moving / stopped / average / max speed
- [x] interrupted resume same TripSession
- [x] Trip detail + raw point inspection
- [x] GPS quality / jump filtering + stationary throttling
- [x] main-Looper LocationListener + GPS/Network provider fallback
- [x] provider failure -> INTERRUPTED instead of crash
- [x] latest physical functional verification accepted

---

## v0.3 - Local Analytics & Reliability

状态: **Feature Complete Candidate / CI finalizing**

### Analytics

- [x] charging interval odometer distance
- [x] cost/100km estimate
- [x] charged kWh/100km estimate
- [x] Trip + odometer coverage evidence
- [x] SOC confidence hints
- [x] interval detail samples
- [x] six-month cost / energy trend
- [x] month-over-month comparison + zero-baseline handling
- [x] charger type classification / count / energy / cost mix
- [x] supercharging classified as public fast

### Charging places

- [x] ChargingPlaceAnalytics from existing location text
- [x] location normalization + count / energy / cost / average price
- [x] Stats Top common places
- [x] Add Record Top 5 common-place quick reuse
- [x] reuse copies text only; historical coordinates are never reused
- [x] manual place edit clears unsaved GPS fix to prevent coordinate/text mismatch

### Export

- [x] full JSON backup remains recovery format
- [x] selected-vehicle charging CSV analysis export
- [x] UTF-8 BOM for Excel compatibility
- [x] CSV escaping + derived price/kWh
- [x] odometer / coordinate / accuracy fields included
- [x] CSV JVM tests
- [x] Android Build Run #169 Green + Debug APK

### Data anomaly hints

Implemented; cumulative CI pending:

- [x] extreme unit price warning
- [x] energy > 135% battery capacity warning
- [x] nearly-flat SOC with meaningful energy warning
- [x] Add/Edit live warnings
- [x] warnings never block save or mutate raw facts
- [x] JVM rules tests
- [ ] latest cumulative Android CI Green

### Non-blocking follow-up

- [ ] Privacy Zone before any route export/share
- [ ] structured HOME / WORK / PUBLIC / HIGHWAY place type only if actual use proves value
- [ ] DataSource metadata only where it changes interpretation
- [ ] Room migration instrumentation QA

No heavy charting framework and no new ChargingPlace Room table until real usage justifies them.

---

## v0.4 - Cloud & Catalog Sync

状态: Next Major Phase / Not Started

Candidate scope:

- [ ] Spring Boot monolith
- [ ] PostgreSQL
- [ ] account / device identity
- [ ] vehicle / charging / trip sync
- [ ] catalog update pipeline (#20)
- [ ] conflict / offline-first sync rules

Cloud sync must not become the only recovery path. Existing local JSON backup remains supported.

---

## 当前执行顺序

```text
anomaly-warning cumulative CI
  -> v0.3 code/document closeout
  -> Privacy Zone only when route export/share starts
  -> v0.4 sync/catalog design
  -> implement smallest useful cloud sync slice
```

---

## 变更记录

### v2.4.0

- Trip #15 and Bluetooth #21 closed after latest physical functional verification
- synced PR #24/#25 visual work as non-blocking business baseline
- ChargingPlace derived aggregation and common-place entry reuse completed
- CSV analysis export completed; Build Run #169 Green
- added non-blocking charging anomaly warnings
- v0.3 moved to Feature Complete Candidate pending final cumulative CI
- next major phase identified as v0.4 Cloud & Catalog Sync

### v2.3.0

- cumulative Android Build Run #142 Green
- month-over-month and charger-type analytics completed
- next active work moved to ChargingPlace derived aggregation
