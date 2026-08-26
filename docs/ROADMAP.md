# EV Charge Book Roadmap

版本: v2.1.0
更新时间: 2026-08-26

## 0. 路线原则

以 PROJECT_MASTER / PRODUCT / FEATURE_MATRIX 为准。

当前阶段继续坚持：简单可维护、Local First、真实数据、先验收后扩功能。

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

首次正式发布：Android Release Run #1，Artifact `ev-charge-book-0.1.1`。

---

## v0.2 - Vehicle & Trip Foundation

### P0 odometer foundation (#18)

状态: Implemented / CI Accepted

- [x] ChargingRecord nullable `odometerKm`
- [x] Room v1 -> v2 migration
- [x] Add/Edit / Records 支持里程
- [x] 上一条可靠里程与下降提示
- [x] Android Build Run #56 Green

后续尾项：
- [ ] Room migration instrumentation test
- [ ] charging interval distance
- [ ] cost/100km / charged kWh/100km
- [ ] TripSession 与 odometer 交叉校验

### P0.5 Local Backup / Restore (#19)

状态: Accepted

- [x] JSON + schemaVersion
- [x] SAF export/import
- [x] 覆盖确认 + Room transaction
- [x] Vehicle / ChargingRecord / odometer
- [x] Location lat/lng/accuracy
- [x] TripSession / TripPoint
- [x] 引用关系与数量校验
- [x] 真机恢复验收

### P1 Multi Vehicle (#17)

状态: Released / Accepted

- [x] selectedVehicleId persisted
- [x] multi-vehicle switch
- [x] Dashboard / Records / Stats isolation
- [x] vehicle archive with history retention

### P1 Vehicle Catalog (#16)

状态: Implemented

- [x] local versioned catalog
- [x] brand / series / year / trim search
- [x] user override
- [x] custom vehicle fallback

### P1.5 Bluetooth connection prompt (#21)

状态: Implemented / Device Acceptance Pending

- [x] paired-device selection / persistence
- [x] Android 12+ BLUETOOTH_CONNECT
- [x] Android 13+ notification permission
- [x] specified-device ACL_CONNECTED notification
- [x] notification action opens Trip page
- [x] Trip page requires explicit user confirmation
- [x] never auto-start location from BroadcastReceiver
- [ ] physical vehicle Bluetooth acceptance
- [ ] permission-denied / disabled / non-selected device acceptance

### P2 Location (#14)

状态: Foundation Implemented / Device Acceptance Pending

- [x] `LocationProvider` abstraction
- [x] Android LocationManager provider
- [x] current position
- [x] ChargingRecord optional lat/lng/accuracy
- [x] Room v4 -> v5 migration
- [x] `AddressResolver` abstraction
- [x] Android Geocoder reverse geocoding
- [x] address failure falls back to raw coordinates/manual text
- [x] Android Build Run #80 foundation Green
- [ ] physical current-location / reverse-geocode acceptance
- [ ] MapProvider abstraction
- [ ] MapLibre route prototype

Data rule: WGS84 lat/lng/accuracy is authoritative fact; address text is optional display metadata and must never replace coordinates.

### P2 Trip (#15)

状态: Active Development / Core Tracking Implemented

Core:
- [x] TripSession / TripPoint
- [x] Room v5 -> v6 migration
- [x] manual start / stop
- [x] bind selected vehicle
- [x] prevent concurrent active trips
- [x] `RECORDING / INTERRUPTED / COMPLETED`
- [x] foreground location service
- [x] persistent notification + stop action
- [x] 4s / 8m sampling baseline
- [x] lat/lng/time/accuracy
- [x] speed / bearing / altitude and supported accuracy fields
- [x] basic invalid-point filtering
- [x] distance / elapsed / moving / stopped baseline
- [x] average / max speed baseline
- [x] start/end/min/max altitude baseline
- [x] Trip backup / restore
- [x] service-start failure -> INTERRUPTED instead of process crash
- [x] explicit foreground location service type
- [x] Build Run #104 crash-hardening CI Green

Recovery / detail:
- [x] Repository `resumeTrip`
- [x] recovery requires location permission again
- [x] interrupted trip resumes same TripSession
- [x] Trip detail point stream
- [x] detail UI: duration / distance / speed / altitude / start/end coordinates / recent points
- [ ] cumulative CI acceptance for recovery/detail batch
- [ ] physical "start trip no crash" acceptance
- [ ] physical lock-screen foreground tracking
- [ ] real-drive distance/speed/altitude plausibility

Next Trip work after recovery/detail CI Green:
- [ ] adaptive stationary lower-frequency sampling
- [ ] route presentation model / MapProvider
- [ ] route rendering through MapLibre
- [ ] stronger GPS jump/outlier rules
- [ ] Privacy Zone for future export/share

### P2 Data Reliability (#19 remaining)

- [ ] DataSource contract
- [ ] source / accuracy semantics
- [ ] extreme price / GPS jump rules
- [ ] ChargingPlace
- [ ] CSV analysis export
- [ ] Privacy Zone later

---

## v0.3 - Analytics

- [ ] monthly cost / energy trend
- [ ] fast/slow charge ratio
- [ ] monthly comparison
- [ ] charging interval real distance
- [ ] cost/100km
- [ ] charged kWh/100km estimate
- [ ] Trip + Charging correlation

Analytics must distinguish raw fact, derived value and estimate.

---

## v0.4 - Cloud & Catalog Sync

- [ ] Spring Boot monolith
- [ ] PostgreSQL
- [ ] account
- [ ] vehicle / charging / trip sync
- [ ] catalog update

Cloud sync must not become the only recovery path.

---

## 当前执行顺序

```text
Trip recovery + detail CI
  -> physical Trip / Bluetooth / Location acceptance (user testing in parallel)
  -> adaptive sampling + GPS reliability
  -> MapProvider / route display
  -> ChargingPlace / Data Reliability
  -> v0.3 Analytics
```

---

## 变更记录

### v2.1.0

- 同步 Location foundation、AddressResolver / Android Geocoder
- 同步 Bluetooth notification -> Trip confirmation 链路
- 同步 Trip foreground tracking、crash hardening、backup coverage
- 增加 interrupted recovery 与 Trip detail 当前开发状态
- 删除已经过期的“本地 Agent 首任务是修 Run #64”说明
