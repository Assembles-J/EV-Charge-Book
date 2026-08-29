# EV Charge Book Roadmap

版本: v2.8.1
更新时间: 2026-08-29

## 0. 路线原则

以 `PROJECT_MASTER.md` / `PRODUCT.md` / `FEATURE_MATRIX.md` 为准。

继续坚持：简单可维护、Local First、真实数据、先验收后扩功能；原始事实、派生值与估算值必须区分。

阶段状态必须区分：

- 代码已实现
- Android CI 已通过
- 真机功能验收
- 真机视觉验收
- Production Release 验收

不得用 CI Green 代替真机结论，也不得因为 Issue 仍然 Open 就重复实现已经进入 `main` 的代码。

---

## v0.1 - Local Charging Book

状态: **Released / Accepted**

- [x] Room / DAO / Repository / ViewModel
- [x] 车辆创建 / 编辑持久化
- [x] 充电记录新增 / 编辑 / 删除
- [x] Dashboard / Records / Stats
- [x] Android CI / Debug APK
- [x] 真机核心 CRUD 验收
- [x] signed production APK / atomic server release

---

## v0.2 - Vehicle, Location & Trip Foundation

状态: **Core Implemented / Post-#184 Physical Reliability Revalidation**

### Trip / Location 基础

- [x] TripSession / TripPoint + Room migration
- [x] manual start / completion / interrupted resume
- [x] foreground location service + persistent notification
- [x] WGS84 / accuracy / speed / bearing / altitude
- [x] distance / elapsed / moving / stopped / average / max speed
- [x] runtime GPS health / callback heartbeat / accepted-point heartbeat
- [x] `lastLocationCallbackAt` 与 `lastAcceptedPointAt` 分离诊断
- [x] GPS / Network provider fallback
- [x] provider / permission / service lifecycle diagnostics
- [x] `START_REDELIVER_INTENT` evidence

### GPS continuity / data trust

- [x] callback registration 改为 time-based liveness，不再依赖 8m system displacement gate（PR #80）
- [x] stationary TripPoint 继续由 app-level 15s throttling 控制
- [x] 2026-08-29 新锁屏证据后，delayed callback freshness 从 15s 放宽到 10min（PR #184）
- [x] delayed fix 仍使用原始 `location.time`，倒序 historical point 继续拒绝
- [x] `LONG_GAP_SECONDS = 120` 未因 callback grace 放宽；真实 capture-time long gap 两端仍不累计可信距离/时长/速度
- [x] PR #184 Android CI run `33229162800` Green，merged as `bae3a21`
- [x] long gap 在 route preview 中保持断开，不画假实线
- [x] GPS health 区分 WAITING / GOOD / DEGRADED / LOST / LONG_GAP
- [x] coarse/network provider 不直接制造 Trip max-speed peak
- [x] extreme GPS peak 缺失 `speedAccuracy` 时不进入可信 max speed
- [x] raw TripPoint 保留，不通过派生统计“清洗掉”原始证据
- [x] 轨迹按可信本车速度着色，并保留 gap / unknown speed 语义
- [x] 起点 / 终点不只依赖颜色区分

### Trip analytics / data quality

- [x] 起终点 / 最低 / 最高海拔
- [x] 累计爬升 / 下降，过滤明显弱 vertical accuracy 和小幅 GPS 垂直抖动
- [x] 累计海拔变化不跨 LONG_GAP 拼接
- [x] Trip validity：明确空/异常完成行程从 Dashboard 与汇总统计排除
- [x] 极短真实行程只标 `REVIEW`，不自动删除、不自动排除
- [x] 用户仍通过显式确认删除无效/测试 Trip

### Physical reliability remaining

2026-08-29 新锁屏真机证据在 PR #80 后仍出现 2 个长缺口，因此完成了聚焦修复 PR #184。#77 现在保留为 **post-#184 真机复验 owner**，不是新的 broad tracking architecture 项目：

- [ ] 锁屏/另一 App 前台 5–10 分钟时，连续 capture 的 delayed fixes 不再因 >15s delivery age 被整体丢弃
- [ ] 原始 capture timestamps 连续时可信距离继续增加
- [ ] 真实 >=120s capture-time gap 仍产生断点且不补造距离
- [ ] 2–3 分钟停车/红灯能累计 stopped time，不产生 false LONG_GAP
- [ ] 真正 callback/provider loss 仍产生 LOST / LONG_GAP
- [ ] stationary heartbeat 不产生过量 TripPoint
- [ ] out-of-order delayed historical point 仍被 non-monotonic guard 拒绝

相关：#77、#67、#42、#26。

---

## v0.3 - Local Analytics & Data Reliability

状态: **Feature Complete / Incremental Follow-up Only**

已实现：

- charging interval odometer distance
- cost / 100km estimate
- charged kWh / 100km estimate
- Trip + odometer coverage evidence
- SOC confidence hints
- six-month cost / energy trend
- month-over-month comparison
- charger type mix
- ChargingPlace aggregation + common-place reuse
- selected-vehicle charging CSV analysis export
- non-blocking charging anomaly hints
- Local JSON Backup / Restore
- Trip validity-aware analytics
- Trip SOC energy estimate 与桩端充电事实分层展示

Issue #19 已完成关闭。HOME / WORK 结构化地点、Privacy Zone、DataSource metadata 等只在真实需求出现时增量实现，不为了“数据治理完整度”新增重表或框架。

---

## v0.4 - Cloud & Catalog Sync

状态: **Phase A Complete / Expansion Deferred Until v0.5 Physical Closeout**

### 已完成 Phase A

- [x] Vehicle stable `syncId`
- [x] Vehicle `updatedAtEpochMillis`
- [x] ChargingRecord stable `syncId`
- [x] ChargingRecord `updatedAtEpochMillis`
- [x] ChargingRecord tombstone
- [x] Room migration + old-row identity generation
- [x] Backup / Restore 保留 sync metadata
- [x] active UI / analytics 排除 tombstone
- [x] pure JVM sync identity tests

Issue #28 已完成关闭；后续由父 Issue #27 跟踪。

### 下一阶段 #27

- [ ] protocol/schema version runtime implementation
- [ ] Vehicle / ChargingRecord sync envelope DTO
- [ ] push changed entities
- [ ] pull changes since cursor/revision
- [ ] idempotent upsert by `syncId`
- [ ] explicit delete/tombstone propagation
- [ ] last successful sync cursor/status
- [ ] conflict policy pure Kotlin tests
- [ ] smallest HTTPS + Spring Boot monolith + PostgreSQL slice

约束不变：云端不能成为 App 运行前提或唯一恢复路径；第一批不做 TripSession / TripPoint cloud sync，不做 CRDT / 微服务 / MQ。

### Catalog #20

现有本地 versioned JSON seed、离线搜索、自定义车辆 fallback 继续使用。完整车型管道仍是独立长期任务：

- [ ] 可追溯公开数据来源
- [ ] 品牌 / 车系 / 年款 / 配置归一化
- [ ] 可重复导入 / 校验工具
- [ ] 增量更新、弃用和纠错
- [ ] catalog 更新不自动改写用户 Vehicle snapshot

---

## v0.5 - Local Experience & VehicleState Closure

状态: **Code Baseline Implemented / Physical Closeout**

### UI baseline

- [x] Dark First design language / persisted Light mode
- [x] Dashboard / Records / Stats / Trip / Vehicle 核心页面重构（#71）
- [x] Dashboard dynamic Vehicle Hero（#96）
- [x] Dashboard recent Trip card（#100）
- [x] release updater UI 对齐 Dashboard dark/green language（#126）
- [x] Trip v0.6 information-dense baseline and device-fidelity corrections（#145 / #168）
- [x] Trip home/history 与 READY preparation 分离（PR #176）
- [x] READY / active / completion density and interaction corrections（PR #170 / #172 / #174）
- [x] completed Trip detail 分为 `概览` / `轨迹` / `数据`（#178 / PR #179，Android CI run `33198331727` Green）
- [x] completed route endpoint red-flag visual language further unified by PR #184
- [x] Trip v0.6 authority docs synchronized through PR #180

### VehicleState / SOC / mileage

- [x] VehicleState 当前 SOC / 当前里程作为当前车辆事实层
- [x] Trip start 保存当前 SOC / mileage 快照
- [x] Trip completion 要求 explicit end SOC
- [x] end mileage 可按 start mileage + GPS distance 预填并允许修正
- [x] Trip end SOC / mileage 回写 VehicleState
- [x] 正 SOC drop 才估算 consumed kWh / kWh per 100km
- [x] 删除完成 Trip 后重新构建 VehicleState
- [x] 后发生的 Charge / manual VehicleState 保持 authority

代码基线由 #87/#89 等已合并 PR 提供；真机闭环由 #124 验收。

### Location / address

- [x] coordinates 是事实，address 是派生展示
- [x] Add Charge 自动请求当前位置
- [x] Geocoder failure 不阻塞坐标保存
- [x] LocationProvider / AddressResolver 分离
- [x] successful geocode process-local bounded cache
- [x] failed / blank geocode 不缓存，允许真实 retry（#129）

真机权限 / Geocoder / restore 验收仍由 #14 保留。

### Lock-screen / background notification

- [x] ongoing notification 显示 Trip elapsed time + trusted persisted distance（#130）
- [x] notification tap / `打开行程` 直接进入 active Trip（#130）
- [x] provider / Location permission runtime loss -> Trip `INTERRUPTED` + one-shot repair notification（#131）
- [x] repair action deep-link 到 App 权限设置或系统 Location 设置（#131）
- [x] 修复后由用户明确 resume，不自动后台恢复（#131）
- [x] Android 13+ Trip 通知权限在 tracking 已开始/恢复后再请求（#132）
- [x] 通知权限拒绝不阻塞、不回滚 Trip（#132）

#26 保持 Open，只做上述行为的真机验收；trusted speed / battery optimization guidance 为 evidence-driven optional，不是代码 blocker。

### Production updater

- [x] updater infrastructure / latest.json / SHA-256 / installer handoff
- [x] root composition wiring（#103）
- [x] Dashboard-style update card（#126）
- [ ] old production APK -> newer production APK 真机覆盖升级（#102）

### v0.5 physical closeout

仍需真实设备验收，不由 CI 代替：

- #145 Trip v0.6 parent physical matrix
- #168 Trip device-fidelity correction acceptance
- #178 completed-detail tabs / 320–360dp / fontScale 1.3 / Dark-Light comparison
- #70 五个一级页面 + Light mode
- #94 Dashboard Hero
- #95 recent Trip card
- #42 accessibility / large font / small screen / active-Trip state safety
- #22 top spacing / density
- #14 Location / Geocoder
- #77 post-#184 lock-screen / delayed callback / stationary hold
- #124 Trip SOC -> VehicleState
- #26 lock-screen / repair notifications
- #102 release APK updater

---

## P3 - Optional Vehicle Data Source / OBD-II

状态: **Future Exploration / Not Product Blocker**

首个最小 PoC：

- [ ] 定义轻量 `VehicleSpeedSource` 边界
- [ ] 外接 OBD-II Bluetooth / BLE / Wi-Fi adapter
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
v0.5 physical acceptance bundle
  -> #145 / #168 / #178 Trip v0.6 design-device comparison
  -> #77 post-#184 lock-screen / delayed callback / stationary reliability revalidation
  -> #124 Trip SOC -> VehicleState
  -> #26 lock-screen + repair notification
  -> #14 Location / Geocoder
  -> #70 / #94 / #95 / #42 / #22 remaining UI-device checks
  -> #102 old-production -> new-production updater flow
  -> only fix concrete device regressions
  -> resume #27 minimal Local First sync protocol/runtime
  -> advance #20 catalog pipeline when source/coverage work is justified
  -> P3 OBD-II optional PoC only when product value justifies it
```

MapLibre 继续保持低优先级；当前已有真实 WGS84 route preview，不为了“看起来完整”提前引入地图 SDK。

---

## 变更记录

### v2.8.1

- recorded 2026-08-29 post-PR #80 physical evidence: a lock-screen Trip still showed 2 long gaps
- recorded focused PR #184: delayed callback freshness 15s -> 10min while preserving original capture timestamps, non-monotonic rejection and the unchanged 120s LONG_GAP trust boundary
- recorded PR #184 Android CI run `33229162800` Green and merge `bae3a21`
- changed #77 from generic background physical acceptance to explicit post-#184 lock-screen/delayed-callback revalidation
- recorded the small completed endpoint red-flag visual convergence from PR #184

### v2.8.0

- synchronized Roadmap with the approved Trip v0.6 design authority and current `main`
- recorded Trip device-fidelity correction series #168 / PR #170/#172/#174/#176
- recorded completed Trip detail information architecture #178 / PR #179 (`概览` / `轨迹` / `数据`) and Android CI run `33198331727` Green
- recorded documentation synchronization through PR #180
- added #145/#168/#178 to the explicit physical acceptance bundle; CI remains insufficient to close them

### v2.7.0

- reconciled ROADMAP with current `main` after Trip reliability/data-quality work through #123/#127/#128/#129
- recorded Trip SOC/mileage/energy -> VehicleState baseline from #87/#89 and physical acceptance owner #124
- recorded v0.5 Dashboard Hero/recent-Trip implementation (#96/#100)
- recorded updater wiring/UI implementation (#103/#126) while preserving #102 physical release acceptance
- recorded lock-screen Trip progress and direct active-Trip navigation (#130)
- recorded provider/permission repair notifications and explicit INTERRUPTED -> user resume semantics (#131)
- recorded Android 13+ non-blocking Trip notification permission flow (#132)
- moved the active stage from “implement Trip P0 slices” to “physical acceptance bundle, then resume minimal sync”

### v2.6.0

- PR #36 GPS health / notification / route-gap / speed semantics passed Android Build Run #184
- clarified current peak speed comes primarily from `Location.speed`, not point-to-point straight-line division
- recorded that average speed still inherits GPS distance-quality limitations
- promoted long-gap distance correction and GPS/Network dedupe ahead of colored route work
- added OBD-II as P3 optional VehicleSpeedSource PoC, explicitly excluding private CAN/BMS reverse engineering from current product scope

### v2.5.0

- based on real Trip #7, promoted 12-15 minute GPS gaps to P0 reliability work
- added segmented speed / continuous color visualization as P1
- clarified speed colors describe this vehicle's speed, not real traffic congestion
- clarified long GPS gaps must not be rendered as trustworthy solid routes
- deferred v0.4 execution until Trip P0 reliability becomes observable