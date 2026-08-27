# EV Charge Book Roadmap

版本: v2.8.0
更新时间: 2026-08-27
状态: v0.5 Local Experience Hardening

## 0. 路线原则

以 `PROJECT_MASTER.md` / `PRODUCT.md` / `FEATURE_MATRIX.md` 为准。

继续坚持：

- 简单可维护
- Local First
- 真实数据优先
- 原始事实 / 当前状态 / 派生估算明确分层
- CI 通过与物理设备验收分离
- 已完成历史 gate 不作为当前 blocker
- 本地核心体验稳定后再恢复云同步扩展

---

## v0.1 - Local Charging Book

状态: Released / Accepted

- [x] Room / DAO / Repository / ViewModel
- [x] 车辆创建 / 编辑持久化
- [x] 充电记录新增 / 编辑 / 删除
- [x] SOC / 里程 / 电量 / 单价 / 费用 / 地点
- [x] Local First
- [x] JSON backup / restore
- [x] CSV export
- [x] 基础统计
- [x] Debug / Release CI

---

## 0.4 RC1 - Trip Reliability Baseline

状态: Released / Historical Gate Completed

- [x] Trip manual start / stop
- [x] foreground location service
- [x] elapsed / moving / stopped time model
- [x] GPS health / diagnostics
- [x] INTERRUPTED / resume
- [x] long gap route segmentation
- [x] release gate #54
- [x] historical RC reliability Issue #41 closed

说明：#41 不再是 active blocker。2026-08-27 的新真机问题由 v0.5 Issue 独立跟踪。

---

## v0.5 - Dark First Local Experience

状态: Core implementation landed / Physical acceptance in progress

### A. UI redesign baseline

已完成：

- [x] PR #71 Dark First redesign merged
- [x] Dark default + persisted Light mode
- [x] shared typography / spacing / shapes / surfaces
- [x] Dashboard / Records / Stats / Trip / Vehicle page hierarchy
- [x] local vehicle artwork mapping
- [x] no runtime Base64/network Hero image loading

当前 Hero 方向：

- 最终车辆、环境光、地面反射使用生成完成的 per-model image asset
- Android 只负责裁切/比例/布局展示
- 不继续用 Compose 代码模拟 photorealistic 极光/反射

### B. VehicleState foundation

状态: Implemented

- [x] VehicleState current SOC / mileage
- [x] Room migration/backfill
- [x] event-order-aware current-state rebuild
- [x] backup / restore
- [x] unknown historical values remain unknown

Owning PR: #79

### C. Smart Charge

状态: Implemented / Physical flow acceptance pending

- [x] start SOC inherits VehicleState
- [x] mileage inherits VehicleState
- [x] recent same-type price/preset reuse
- [x] charge type UX
- [x] charger energy vs vehicle-received energy estimate
- [x] charging-loss estimate / anomaly hint
- [x] current location preference
- [x] save/edit/delete updates/rebuilds VehicleState safely

Owning PR: #81

### D. Trip reliability second pass

状态: Code landed / Physical verification pending

- [x] callback liveness separated from stationary persistence (#80)
- [x] LocationManager minDistance 8m -> 0m
- [x] 15s app-level stationary heartbeat retained
- [x] trusted GPS max-speed gate (#82)
- [x] network/coarse speed cannot create max peak
- [x] altitude / endpoint address / semantic markers (#83)
- [x] trusted speed-colored route (#85)
- [x] untrusted/partial speed segment stays gray
- [x] long GPS gaps remain disconnected

Physical gates:

- [ ] #77 other-app background callback/distance continuity
- [ ] #77 2-3 minute red-light stoppedSeconds / false LONG_GAP
- [ ] #78 actual vehicle peak vs recorded max
- [ ] #67 route colors / gray uncertainty / Dark-Light readability
- [ ] #66 endpoint address device path
- [ ] #69 altitude display / future elevation analytics
- [ ] #42 endpoint/accessibility/large-font/TalkBack

### E. Trip SOC / Energy / VehicleState loop

状态: Implemented / Physical end-to-end acceptance pending

- [x] start SOC / mileage snapshot from VehicleState
- [x] explicit end SOC
- [x] end mileage GPS-derived prefill + correction
- [x] SOC-based consumed energy estimate
- [x] estimated kWh/100km
- [x] unchanged/increased SOC -> no fabricated consumption
- [x] atomic Trip completion + VehicleState update
- [x] backup compatibility
- [x] Trip detail SOC / estimated-energy summary
- [x] READY screen uses real known state; no fake pre-start map/route
- [x] notification cannot bypass end-SOC completion

Owning implementation: #87, follow-up summary #88. Superseded #84 is closed.

Physical gate:

```text
READY
 -> start snapshot
 -> LIVE GPS
 -> end SOC/mileage
 -> estimated energy when meaningful
 -> VehicleState refresh
 -> Trip detail summary
```

### F. Home / Vehicle / Stats state binding

状态: Active next slice

- [ ] Dashboard/Home display current VehicleState SOC / mileage where available
- [ ] Vehicle page support current-state maintenance without duplicate manual entry
- [ ] Stats/Energy distinguish Charge fact energy from Trip SOC-based estimate
- [ ] cross-page state refresh after charge/trip completion
- [ ] no live value shown unless backed by local state or real telemetry

### G. UI physical closeout

- [ ] Dashboard Dark / Light pass
- [ ] Records Dark / Light pass
- [ ] Stats Dark / Light pass
- [ ] Trip Dark / Light pass
- [ ] Vehicle Dark / Light pass
- [ ] 320dp / small-screen pass
- [ ] 1.3x / large-font pass
- [ ] TalkBack / content-description pass
- [ ] IME / dirty-form / active-state safety pass
- [ ] final per-model Hero image asset integration

Owning Issues include #70 / #22 / #42.

### H. CI governance

- [x] Android code PR build/test artifact flow
- [x] docs-only PR path filter
- [x] Production Release separated from normal CI
- [ ] protect `main`
- [ ] require Android Build for `android/**` PRs

Owning Issue: #75.

---

## v0.5 Close Condition

v0.5 local experience can be considered ready for release planning only when:

1. Trip background/stationary/max-speed/route physical checks pass.
2. Charge and Trip both update VehicleState correctly on device.
3. Home/Vehicle/Stats consume the same current-state semantics.
4. Five primary pages complete Dark/Light physical visual pass.
5. No P0 data-quality / active-state / backup regression remains.
6. Documentation, Issues and `main` describe the same stage.

---

## Deferred after local v0.5

### Sync expansion (#27/#28)

保留已完成 sync identity foundation，但继续延后协议/服务端扩展，直到本地 v0.5 收口。

### Optional / later

- long-window `TripSpeedSegmentBuilder`
- MapLibre basemap renderer
- OBD-II standard speed PoC
- advanced battery health
- sync conflict UX
- AI / social / community features

明确不做：

- 微服务 / Kafka / MQ 为了“架构完整”而引入
- CRDT 作为首版同步前提
- cloud becoming mandatory for local recording
- fake GPS / fake SOC / fake energy for visual completeness

---

## Current execution order

```text
1. #77/#78/#67/#66/#69/#42 physical Trip acceptance
2. Trip READY -> LIVE -> completion -> VehicleState device pass
3. Home / Vehicle / Stats VehicleState binding
4. five-page Dark/Light/accessibility closeout
5. final generated Hero image assets
6. #75 main protection / required checks
7. v0.5 release decision
8. resume #27/#28 sync expansion
```

---

## Change log

### v2.8.0

- 同步 #79/#81/#80/#82/#83/#85/#87/#88 已合入主线。
- 将 v0.5 当前 blocker 明确为 physical acceptance 与 VehicleState cross-page binding。
- 删除历史 #41 作为当前 dependency 的错误表达。
- 明确 Hero 复杂视觉使用最终生成 image asset。
- 保留 branch protection #75 作为 CI governance gap。

### v2.6.0

- 0.4 RC1 发布后进入 v0.5 local experience hardening。
