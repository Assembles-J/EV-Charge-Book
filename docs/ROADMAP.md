# EV Charge Book Roadmap

版本: v2.6.0
更新时间: 2026-08-27

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

状态: **Core Accepted / Reliability Hardening**

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

状态: Core Accepted; Reliability Follow-up Active

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

真实长行程 follow-up：

- [x] P0 runtime GPS health / accepted-point heartbeat
- [x] P0 GPS LOST / LONG_GAP ongoing notification state
- [x] P0 long GPS gap 在 route preview 中断开，不画假实线
- [x] P1 全程均速 / 行驶均速 / 最高速度口径明确区分
- [ ] P0 长 gap 两端不参与可信距离累计
- [ ] P0 GPS / Network 去重与择优
- [ ] P0 longest gap / provider counters / rejected reason 持久摘要
- [ ] P0 service lifecycle / restart / re-delivery evidence
- [ ] P0 锁屏长行程真机复验
- [ ] P1 TripSpeedSegment 派生模型
- [ ] P1 连续速度颜色映射
- [ ] P1 短时峰值与 segment speed 分开展示

PR #36 第一阶段代码已通过 Android Build Run #184：Build/Test + Debug APK Green。

详细设计：`docs/TRIP_GPS_RELIABILITY_AND_SPEED_VISUALIZATION.md` 与 `docs/LOCATION_TRIP.md`。

---

## v0.3 - Local Analytics & Reliability

状态: **Feature Complete Candidate / Reliability Follow-up**

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

- [x] extreme unit price warning
- [x] energy > 135% battery capacity warning
- [x] nearly-flat SOC with meaningful energy warning
- [x] Add/Edit live warnings
- [x] warnings never block save or mutate raw facts
- [x] JVM rules tests

### Reliability priority change after real Trip data

真实 Trip #7 观察到 12-15 分钟级 GPS gap。该问题优先级高于 MapLibre 和更丰富的统计展示。

已完成第一阶段：

- [x] runtime GPS health
- [x] lock-screen ongoing notification 显示 GPS 状态与最近有效 fix
- [x] long gap route discontinuity
- [x] speed UI 口径修正

下一阶段先修数据可信度，再做彩色速度：

- [ ] long gap distance aggregation 修正
- [ ] GPS / Network provider fusion / dedupe
- [ ] Trip data completeness summary
- [ ] segmented speed + continuous color route

### Non-blocking follow-up

- [ ] Privacy Zone before any route export/share
- [ ] structured HOME / WORK / PUBLIC / HIGHWAY place type only if actual use proves value
- [ ] DataSource metadata only where it changes interpretation
- [ ] Room migration instrumentation QA

No heavy charting framework and no new ChargingPlace Room table until real usage justifies them.

---

## v0.4 - Cloud & Catalog Sync

状态: Foundation started; feature expansion deferred until Trip P0 reliability is observable

已存在的 Local First Sync Foundation 不回退：stable identity / tombstone / protocol 文档继续保留。

Candidate scope:

- [ ] Spring Boot monolith
- [ ] PostgreSQL
- [ ] account / device identity
- [ ] vehicle / charging / trip sync
- [ ] catalog update pipeline (#20)
- [ ] conflict / offline-first sync rules

Cloud sync must not become the only recovery path. Existing local JSON backup remains supported。

---

## P3 - Optional Vehicle Data Source / OBD-II

状态: Future Exploration / Not Product Blocker

目标不是做 CAN 逆向平台，而是验证“车本身的数据是否能补强 GPS Trip”。

首个最小 PoC：

- [ ] 定义轻量 `VehicleSpeedSource` 边界
- [ ] 外接 OBD-II Bluetooth/BLE/Wi-Fi adapter
- [ ] 查询标准 Vehicle Speed 支持能力
- [ ] 读取 OBD Vehicle Speed
- [ ] 与 GNSS `Location.speed` 对照

明确不进入当前主线：

- 厂商私有 CAN ID 逆向
- 私有 BMS PID 逆向
- 单车型复杂协议维护
- OBD 成为 Trip 必需依赖

只有 Vehicle Speed PoC 证明稳定价值后，才评估 SOC / 电压 / 电流 / 电池温度等更多车辆事实。

---

## 当前执行顺序

```text
Trip P0 reliability
  -> long-gap distance correction
  -> GPS / Network dedupe + quality policy
  -> Trip completeness summary / diagnostics
  -> physical lock-screen long-drive verification
  -> Trip P1 segmented speed + colored route
  -> v0.3 reliability closeout
  -> resume v0.4 sync expansion
  -> P3 OBD-II optional PoC when product value justifies it
```

MapLibre 继续保持低优先级；没有必要为了彩色速度轨迹先引入地图 SDK。

---

## 变更记录

### v2.6.0

- PR #36 GPS health / notification / route-gap / speed semantics passed Android Build Run #184
- clarified current peak speed comes primarily from `Location.speed`, not point-to-point straight-line division
- recorded that average speed still inherits GPS distance-quality limitations
- promoted long-gap distance correction and GPS/Network dedupe ahead of colored route work
- added OBD-II as P3 optional VehicleSpeedSource PoC, explicitly excluding private CAN/BMS reverse engineering from current product scope

### v2.5.0

- based on real Trip #7, promoted 12-15 minute GPS gaps to P0 reliability work
- added GPS callback / provider / service lifecycle diagnostics before more feature expansion
- added segmented speed / continuous color visualization as P1
- clarified speed colors describe this vehicle's speed, not real traffic congestion
- clarified long GPS gaps must not be rendered as trustworthy solid routes
- deferred v0.4 execution until Trip P0 reliability becomes observable

### v2.4.0

- Trip #15 and Bluetooth #21 closed after latest physical functional verification
- synced PR #24/#25 visual work as non-blocking business baseline
- ChargingPlace derived aggregation and common-place entry reuse completed
- CSV analysis export completed; Build Run #169 Green
- added non-blocking charging anomaly warnings
- v0.3 moved to Feature Complete Candidate pending final cumulative CI
- next major phase identified as v0.4 Cloud & Catalog Sync
