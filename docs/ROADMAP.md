# EV Charge Book Roadmap

版本: v2.3.0
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

首次正式发布：Android Release Run #1，Artifact `ev-charge-book-0.1.1`。

---

## v0.2 - Vehicle, Location & Trip Foundation

状态: **Code Complete / CI Accepted in core paths / Device Acceptance in parallel**

### Odometer & charging data loop (#18)

状态: Completed / Issue Closed

- [x] nullable `odometerKm`
- [x] Room v1 -> v2 migration
- [x] Add/Edit/Records 支持里程
- [x] 上一条可靠里程与下降提示
- [x] ChargingIntervalAnalytics
- [x] 区间距离 / cost per 100km / charged kWh per 100km
- [x] 无效区间显式排除并计数
- [x] Trip completed distance coverage evidence
- [x] SOC delta / estimate confidence hints
- [x] 区间明细 UI
- [x] JVM tests

非阻塞测试增强：Room migration instrumentation test 后续归入数据库 QA，不再阻塞 v0.3。

### Local Backup / Restore (#19)

状态: Accepted

- [x] JSON + schemaVersion
- [x] SAF export/import
- [x] 覆盖确认 + Room transaction
- [x] Vehicle / ChargingRecord / odometer
- [x] Location lat/lng/accuracy
- [x] TripSession / TripPoint
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

状态: Implemented / Device Acceptance Pending

- [x] paired-device selection / persistence
- [x] Android 12+ Nearby Devices permission
- [x] Android 13+ notification permission
- [x] selected-device ACL_CONNECTED notification
- [x] notification opens Trip confirmation flow
- [x] never auto-start location from BroadcastReceiver
- [ ] physical vehicle Bluetooth acceptance
- [ ] denied/disabled/non-selected-device acceptance

### Location (#14)

状态: Foundation + Route Preview Implemented / Device Acceptance Pending

- [x] LocationProvider abstraction + Android LocationManager
- [x] current position
- [x] ChargingRecord lat/lng/accuracy
- [x] AddressResolver + Android Geocoder
- [x] address failure preserves coordinates/manual text
- [x] TripRouteGeometry presentation model
- [x] no-basemap real trajectory preview
- [ ] physical current-location / reverse-geocode acceptance

MapLibre remains optional visualization work and does not block analytics:

- [ ] external MapProvider adapter
- [ ] MapLibre map tiles/style prototype
- [ ] route/start/end rendering on real map

### Trip (#15)

状态: Core Implemented / Device Acceptance Pending

- [x] TripSession / TripPoint + Room migration
- [x] manual start/stop
- [x] foreground location service
- [x] persistent notification + stop action
- [x] lat/lng/time/accuracy/speed/bearing/altitude
- [x] distance / elapsed / moving / stopped
- [x] average/max speed + altitude range
- [x] crash hardening: foreground failure -> INTERRUPTED
- [x] interrupted resume uses same TripSession
- [x] Trip detail + raw point inspection
- [x] TripSamplingRules
- [x] poor accuracy / impossible speed / GPS jump filtering
- [x] stationary point throttling
- [x] no-basemap route preview
- [ ] physical start-trip / lock-screen tracking acceptance
- [ ] real-drive distance/speed/altitude plausibility
- [ ] interrupted resume physical acceptance

### Data Reliability remaining

These are incremental quality features, not v0.3 blockers:

- [ ] ChargingPlace / common-place reuse
- [ ] CSV analysis export
- [ ] Privacy Zone before route sharing/export
- [ ] source metadata only where it creates user value

---

## v0.3 - Analytics

状态: **Active Development / Cumulative CI Green**

Already implemented:

- [x] charging interval actual odometer distance
- [x] cost/100km estimate
- [x] charged kWh/100km estimate
- [x] Trip + odometer coverage evidence
- [x] SOC confidence hints
- [x] interval detail samples
- [x] six-month charging cost / energy trend
- [x] charger type classification and mix
- [x] home / public slow / public fast / other shares
- [x] supercharging classified as public fast
- [x] charger-type count / energy / cost comparison
- [x] month-over-month comparison card
- [x] zero-baseline sparse-data handling: keep absolute values, suppress meaningless infinite percentage
- [x] Android Build Run #142 cumulative Green + Debug APK

Release verification:

- [x] Android Release Run #3 signed APK build
- [x] Actions artifact
- [x] server upload
- [x] atomic activation

Next work:

- [ ] ChargingPlace derived common-place aggregation from existing location text
- [ ] common-place reuse in record entry only after aggregation proves useful
- [ ] CSV analysis export
- [ ] analytics wording polish for sparse samples and estimates
- [ ] selected-period filters only if needed by actual usage
- [ ] Privacy Zone before any route sharing/export

Do not add heavy charting frameworks yet; Compose primitives are sufficient until data density proves otherwise.

---

## v0.4 - Cloud & Catalog Sync

- [ ] Spring Boot monolith
- [ ] PostgreSQL
- [ ] account
- [ ] vehicle / charging / trip sync
- [ ] catalog update pipeline (#20)

Cloud sync must not become the only recovery path.

---

## 当前执行顺序

```text
ChargingPlace derived aggregation
  -> cumulative Android CI
  -> CSV analysis export
  -> physical Trip / Bluetooth / Location acceptance continues in parallel
  -> Privacy Zone before route sharing/export
  -> v0.4 cloud/catalog only after local analytics is useful
```

---

## 变更记录

### v2.3.0

- cumulative Android Build Run #142 Green
- charger type fix and diagnostic coverage accepted
- month-over-month comparison completed
- zero-baseline sparse comparison handled without fake growth percentages
- charger type cost / energy comparison already present in Stats
- Android Release Run #3 signed build/upload/atomic activation accepted
- #18 confirmed closed; removed stale acceptance gates
- next active work moved to ChargingPlace derived aggregation

### v2.2.0

- v0.2 core code moved to Code Complete; physical-device acceptance remains parallel
- synced ChargingIntervalAnalytics, Trip coverage, SOC confidence and interval detail work
- synced Trip route geometry / no-basemap preview and Run #118
- v0.3 officially marked Active Development
- synced six-month trend and charger-type analytics
- MapLibre explicitly kept non-blocking
- Room migration instrumentation test moved to non-blocking database QA

### v2.1.0

- synced Location foundation and reverse geocoding
- synced Bluetooth -> Trip confirmation
- synced foreground Trip tracking, recovery/detail and sampling-quality work
