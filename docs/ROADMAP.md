# EV Charge Book Roadmap

版本: v2.7.1
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

状态: Core Accepted

基础 Location / Trip / Bluetooth 能力已进入稳定基线。历史 reliability RC 工作已在 0.4 RC1 收口，不再把 #41 当作当前 blocker。

已完成：

- [x] TripSession / TripPoint + migration
- [x] manual start/stop
- [x] foreground location service + persistent notification
- [x] WGS84 / accuracy / speed / bearing / altitude
- [x] distance / elapsed / moving / stopped / average / max speed
- [x] interrupted resume same TripSession
- [x] GPS quality / jump filtering + stationary throttling
- [x] GPS/Network provider fallback
- [x] runtime GPS health / accepted-point heartbeat
- [x] GPS LOST / LONG_GAP ongoing notification
- [x] long GPS gap route break / no fake solid route
- [x] trusted-distance long-gap correction
- [x] persistent Trip runtime diagnostics
- [x] lock-screen long-drive physical verification
- [x] Bluetooth-assisted Trip start physical verification

Release evidence:

- Issue #41 completed in 0.4 RC1
- Release gate #54 closed as released

后续 Location/Trip 工作进入 v0.5 experience improvement issues，不重新打开 RC1。

---

## v0.3 - Local Analytics & Reliability

状态: Feature Complete Candidate / incremental follow-up

已实现：

- charging interval odometer distance
- cost/100km estimate
- charged kWh/100km estimate
- Trip + odometer coverage evidence
- SOC confidence hints
- six-month cost / energy trend
- month-over-month comparison
- charger type mix
- ChargingPlace aggregation + common-place reuse
- selected-vehicle charging CSV analysis export
- non-blocking anomaly hints

剩余只做真实使用证明有价值的可靠性增量，不扩重型 chart 或独立数据模型。

---

## v0.4 - RC1 / Local First Sync Foundation

状态: **0.4 RC1 released; sync foundation retained**

0.4 RC1 已完成 Trip reliability release gate（#41 / #54）。

同时 Vehicle + ChargingRecord 的 stable identity / tombstone / protocol foundation 保留：

- stable `syncId`
- `updatedAtEpochMillis`
- ChargingRecord tombstone
- Backup compatibility
- first sync protocol boundary

更完整 cloud sync expansion 继续保持后续阶段，不影响 v0.5 本地体验工作。

---

## v0.5 - Dark First Product Experience

状态: **Core UI merged / experience hardening active**

PR #71 已合并到 `main`。最终 head Android Build Run #294 Green，并产出 Debug APK artifact。

### Core UI completed

- [x] Dark First design tokens / typography / spacing / surfaces
- [x] persisted explicit Light mode
- [x] 五个一级入口：总览 / 记录 / 统计 / 行程 / 车辆
- [x] Dashboard vehicle Hero + energy cockpit + recent charging
- [x] local drawable vehicle artwork; runtime Base64/network artwork removed
- [x] Records ledger cockpit + timeline
- [x] Add/Edit Charge hierarchy
- [x] Trip READY / LIVE / INTERRUPTED + history / detail / GPS gap semantics
- [x] Stats monthly / comparison / mix / place / interval hierarchy
- [x] Vehicle garage / catalog / editor / Bluetooth / backup / CSV consistency
- [x] compact empty states
- [x] strict vehicle-artwork mapping tests

### Current v0.5 P0 — correctness and state safety

1. **#66 Location fallback**
   - coordinates remain the source of truth
   - address resolution must be asynchronous/optional
   - distinguish waiting GPS / resolving address / unavailable

2. **#42 active-Trip and form safety**
   - Restore/Archive/Switch must not corrupt active Trip context
   - dirty Add/Edit forms need Back protection
   - warning/error must not depend on color only

3. **#68 invalid Trip handling**
   - explicit invalid/incomplete classification
   - user-reviewable cleanup
   - invalid Trips must not silently pollute statistics

### Current v0.5 P1 — device UX and presentation

4. **#70 + #22 final physical UI pass**
   - five primary pages
   - top inset / density
   - Light mode contrast
   - only local visual polish after device findings

5. **#42 accessibility / adaptive layout**
   - fontScale 1.3/1.5
   - 320-360dp
   - TalkBack/contentDescription
   - >=48dp touch targets
   - long text / IME behavior

6. **#67 route continuity presentation**
   - improve small temporary gaps without fabricating route data
   - keep large gaps visually distinct

7. **#26 background/lock-screen recovery UX**
   - permission/provider/battery-optimization repair paths
   - notification deep links and interrupted-state visibility

### Current v0.5 P2 — analytics/coverage

8. **#69 altitude/elevation analytics** after location quality semantics are stable
9. **#20 scalable vehicle catalog/artwork coverage pipeline** instead of UI hardcoding
10. resume **#27/#28 sync expansion** after v0.5 local experience stabilizes

---

## P3 - Optional Vehicle Data Source / OBD-II

状态: Future Exploration / Not Product Blocker

首个最小 PoC：

- [ ] 定义轻量 `VehicleSpeedSource` 边界
- [ ] 外接 OBD-II Bluetooth/BLE/Wi-Fi adapter
- [ ] 查询标准 Vehicle Speed 支持能力
- [ ] 读取 OBD Vehicle Speed
- [ ] 与 GNSS `Location.speed` 对照

明确不进入当前主线：厂商私有 CAN ID 逆向、私有 BMS PID 逆向、单车型复杂协议维护、OBD 成为 Trip 必需依赖。

---

## 当前执行顺序

```text
#66 coordinate-first location fallback
  -> #42 P0 active-Trip guards + dirty-form safety
  -> #68 invalid Trip classification/cleanup
  -> #70/#22 five-page physical UI closeout
  -> #42 accessibility / large-font / small-screen / IME
  -> #67 route continuity presentation
  -> #26 background/permission repair UX
  -> #69 altitude analytics
  -> #20 catalog/artwork scalable coverage
  -> resume #27/#28 sync expansion
```

原则：先修真实数据/状态安全，再做展示增强，最后才扩分析和云同步。

---

## 变更记录

### v2.7.1

- corrected stale roadmap wording after verifying #41 is completed and #54 is released
- made v0.5 the active product-experience milestone
- prioritized #66, #42 and #68 ahead of additional visual/analytics work
- kept #70 open only for physical visual closeout
- separated #67 presentation, #69 analytics and #20 catalog coverage from core UI implementation

### v2.7.0

- recorded PR #71 Dark First v0.5 UI baseline merged
- recorded Android Build Run #294 Green + Debug APK
- synced vehicle artwork to local drawable exact mapping
- narrowed post-merge work to physical UX/usability hardening

### v2.6.0

- PR #36 GPS health / notification / route-gap / speed semantics passed Android Build Run #184
- documented speed-source semantics and optional OBD-II boundary
