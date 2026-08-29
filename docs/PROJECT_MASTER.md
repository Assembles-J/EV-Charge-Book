# EV Charge Book 项目总纲（PROJECT MASTER）

版本: v2.4.0
更新时间: 2026-08-29
状态: Authority Document / Single Source of Truth

## 1. 项目定位

EV Charge Book 是新能源车主的 Local First 车辆数据中心。

演进顺序:

1. 充电记账与成本
2. 多车辆与车型目录
3. 位置 / 驾驶行程数据
4. 充电与行程的数据闭环
5. 本地分析与可靠性
6. 跨设备同步 / 云恢复
7. Web / AI 增值能力

首个验证车型为零跑 C16，但产品、数据库和 UI 不绑定单一品牌。

---

## 2. 权威文档

- `PRODUCT.md`
- `FEATURE_MATRIX.md`
- `UIUX.md`
- `FRONTEND.md`
- `BACKEND.md`
- `DATABASE.md`
- `CI_CD.md`
- `APK_AUTO_UPDATE.md`
- `ROADMAP.md`
- `DEVELOPMENT.md`
- `LOCATION_TRIP.md`
- `TRIP_GPS_RELIABILITY_AND_SPEED_VISUALIZATION.md`
- `TRIP_V0.6_APPROVED_UI_BASELINE.md`
- `EVChargeBook_UI_Redesign_Task_Breakdown.md`
- `VEHICLE_CATALOG_MULTI_VEHICLE.md`
- `DATA_QUALITY_BACKUP.md`
- `SYNC_PROTOCOL.md`
- `LOCAL_AGENT_HANDOFF.md`
- `NEXT_PHASE_DESIGN.md`

实现与文档冲突时，先以当前代码、CI 与真机事实为准，再修正文档。

阶段状态必须严格区分:

- 代码已实现
- Android CI Green
- 真机功能验收
- 真机视觉验收
- Production Release 验收

不得用其中一个替代另一个；Open Issue 也不代表代码一定未实现。

---

## 3. 产品原则

- 简单可维护
- Local First / Offline First
- 真实数据来源
- 用户录入成本低
- 统计口径可解释
- 原始事实 / 派生值 / 估算值明确区分
- 不伪造实时 SOC / SOH / 续航
- 定位记录与地图 SDK 解耦
- GPS 缺失不通过假路线 / 假距离补齐
- coordinates 是位置事实，address 是派生展示
- 速度保留来源语义，不把 GNSS / derived / future OBD 混成无来源真值
- notification 是后台状态可见性，不是 Trip 是否能记录的业务前置条件
- 云同步不能成为 App 运行前提或唯一恢复路径
- 正式 APK 版本只在真正执行 Production Release 时生成

---

## 4. v0.1 - Local Charging Book

状态: **Released / Accepted**

已完成:

- Vehicle CRUD
- ChargingRecord add/edit/delete
- Dashboard / Records / Stats
- ChargingRecordRules / ChargingStatistics
- Android CI / Debug APK
- 真机核心 CRUD
- signed production APK / atomic release

---

## 5. v0.2 - Vehicle, Location & Trip Foundation

状态: **Core Implemented / Physical Reliability Revalidation**

### 5.1 已实现基础

- Multi Vehicle / selected vehicle context
- managed/offline-first Vehicle Catalog baseline
- Bluetooth selected-device -> Trip confirmation
- Android LocationManager + WGS84
- AddressResolver + Android Geocoder
- TripSession / TripPoint
- foreground tracking + ongoing notification
- Trip start / completion / interrupted resume
- distance / elapsed / moving / stopped / average / max speed
- speed / bearing / altitude / accuracy
- bad-point / jump filtering
- route geometry / no-basemap truthful preview
- Local Backup / Restore Trip coverage

### 5.2 post-RC Trip reliability

当前代码已经实现:

- runtime `TripGpsHealth`
- WAITING / GOOD / DEGRADED / LOST / LONG_GAP
- callback heartbeat 与 accepted-point heartbeat 分离
- `lastLocationCallbackAt` 与 accepted-point evidence 分离诊断
- provider / permission / service lifecycle / re-delivery evidence
- LONG_GAP route segmentation
- LONG_GAP 两端不计可信连续距离
- foreground Location callback 使用 time-based liveness
- `SAMPLE_DISTANCE_METERS = 0f`，不再依赖旧 8m displacement gate
- stationary write throttling 保留在 `TripSamplingRules`（约 15s heartbeat）
- coarse/network max-speed peak rejection
- trusted speed-colored route
- start/end 非纯颜色语义

PR #80 已完成 8m callback gate 的代码修复并通过 Android CI；Issue #77 现在只负责真实设备上的“另一 App 前台 / 2-3 分钟 stationary hold / genuine callback-provider loss / stationary write volume”验收。

### 5.3 Trip altitude / validity

已实现:

- start/end/min/max altitude
- trusted elevation gain/loss
- vertical accuracy filtering + jitter deadband
- elevation 不跨 LONG_GAP 拼接
- conservative Trip validity classification
- invalid/empty completed Trip 不进入 Dashboard/aggregate
- short real Trip 使用 REVIEW，不自动删除
- 删除由用户明确确认

---

## 6. Trip 距离与速度事实模型

### 6.1 距离

Trip distance 是连续可信 accepted location point 的地理距离累计，不是道路里程或车辆轮速里程。

LONG_GAP 两端不进入可信连续距离。原始 TripPoint 保留，派生统计只决定哪些证据可进入汇总。

### 6.2 速度

区分:

- `Location.speed`: Android/GNSS 报告速度
- derived segment evidence: 连续点距离/时间校验证据
- whole-trip / moving average: 基于可信累计距离的派生统计
- future OBD: 可选独立来源

最高速度只允许可信 GPS evidence 更新。UI 应理解为“最高已记录可信速度”，不得宣称车辆真实绝对峰值。

### 6.3 route speed visualization

当前 route 支持可信 GPS speed 颜色表达，同时保留:

- unknown/untrusted speed 中性语义
- LONG_GAP 断开
- legend / 区间说明
- “本车速度，不代表道路拥堵”说明

不为了 route 外观提前引入道路吸附、限速/拥堵语义或 MapLibre 依赖。

---

## 7. v0.3 - Local Analytics & Data Reliability

状态: **Feature Complete / Incremental Follow-up Only**

已实现:

- charging interval odometer distance
- cost / 100km estimate
- charged kWh / 100km estimate
- Trip + odometer coverage evidence
- SOC confidence hints
- monthly trends / month-over-month
- charger type mix
- ChargingPlace aggregation / common-place reuse
- charging CSV analysis export
- non-blocking anomaly hints
- Local JSON Backup / Restore
- Trip validity-aware analytics
- Charge facts 与 Trip SOC-derived energy estimate 分层展示

Issue #19 已完成关闭。

---

## 8. v0.4 - Local First Sync & Catalog

### 8.1 Sync Phase A

已完成:

- Vehicle `syncId` + `updatedAtEpochMillis`
- ChargingRecord `syncId` + `updatedAtEpochMillis`
- ChargingRecord tombstone
- explicit Room migration / old identity generation
- Backup / Restore sync metadata
- active UI/analytics 排除 tombstone
- pure JVM identity tests

后续由 #27 继续:

- protocol/schema runtime
- Vehicle / ChargingRecord envelope
- push/pull cursor
- idempotent upsert
- tombstone propagation
- simple conflict rule
- smallest HTTPS + Spring Boot monolith + PostgreSQL slice

第一批不做 TripSession / TripPoint cloud sync、CRDT、微服务、MQ。

### 8.2 Catalog

managed catalog runtime + Android offline-first refresh 已实现。#20 剩余是 source provenance、bulk import/validation、normalization、coverage quality。

---

## 9. v0.5 / v0.6 - Local Experience & Trip UI

状态: **Code Baseline Implemented / Physical Closeout**

### 9.1 Global UI

已实现:

- Dark First + persisted Light mode
- Dashboard / Records / Stats / Trip / Vehicle 五一级页面
- low-elevation / restrained-outline surface language
- responsive metric grids
- Dashboard dynamic Vehicle Hero
- Dashboard recent Trip card
- Records v0.6 dense ledger
- Stats v0.6 compact analytics hierarchy

#70/#94/#95/#159/#164/#165/#42/#22 保留真实设备视觉/可访问性 closeout。

### 9.2 Trip v0.6 information architecture

Trip v0.6 代码侧已完成，不再需要 broad redesign。

当前页面/状态:

1. Trip home/history
2. READY preparation
3. active/interrupted cockpit
4. completion form
5. completed detail `概览`
6. completed detail `轨迹`
7. completed detail `数据`

已落地:

- Trip home/history 与 READY 分离（PR #176）
- dense truthful history rows
- READY vehicle/SOC/mileage/GPS truth
- restrained slide-to-start
- active distance + trusted current speed hierarchy
- active real route + speed/altitude trends
- active primary metrics 去除重复 diagnostic facts
- slide-to-end 直接进入唯一 compact completion form（PR #174）
- completed overview + one endpoint card
- completed detail `概览 / 轨迹 / 数据` split（#178 / PR #179）
- completed start = green start/play
- completed end = small red flag
- active latest point = green current point
- LONG_GAP route/trends 不插值
- raw GPS diagnostics collapsed by default
- unavailable route/altitude 使用 truthful empty state
- 不加入 unsupported `充电` / `备注` / destination / fake basemap tabs

Trip UI implementation authority:

- `TRIP_V0.6_APPROVED_UI_BASELINE.md`
- #145 parent
- #168 device-fidelity corrections
- #178 detail-section split

当前 main 的 Trip UI behavior baseline 包含 PR #179；Android CI run `33198331727` Green。代码完成不能替代 #145/#168/#178 的真机 design-device comparison。

### 9.3 Trip SOC / mileage / energy -> VehicleState

当前代码已经实现:

- start snapshot current SOC / mileage
- explicit end SOC
- optional validated end mileage
- TripSession start/end SOC + mileage
- positive trustworthy SOC drop 才估算 consumed energy
- required inputs 足够才估算 kWh/100km
- completion 更新 VehicleState
- 删除 completed Trip 后 rebuild VehicleState
- 后发生 Charge/manual VehicleState 继续保持 authority

#124 负责真机数据闭环。

### 9.4 Location / address

已实现:

- coordinates-first
- Geocoder optional
- AddressResolver 分离
- successful geocode bounded cache
- failed/blank geocode 不缓存

#14 保留真机 permission / Geocoder / restore acceptance。

### 9.5 Lock-screen / background Trip UX

当前代码:

- ongoing notification = elapsed + trusted persisted distance
- notification tap / action -> active Trip
- permission/provider loss -> `INTERRUPTED`
- one-shot repair notification
- repair 后用户显式 resume
- Android 13+ notification permission 在 tracking 已开始后请求
- notification denial 不回滚 Trip
- lock screen 不显示精确坐标 / HOME / WORK
- notification 不直接 complete Trip

#26 保留真机 round-trip；#77 独立负责 callback/stationary reliability。

### 9.6 Production updater

已实现 discovery / DownloadManager / SHA-256 / unknown-source permission / Android installer / root wiring / Dashboard-style non-modal UI。

#102 保留 old-production -> newer-production 真实覆盖升级验收。

---

## 10. Map / destination / OBD 边界

### Map

当前真实 WGS84 route preview 已满足 Trip v0.6 数据可信展示。MapLibre 是 future optional renderer，不是当前完成门槛。

### Destination

#152 是 future capability。当前 Trip v0.6 不显示 destination selector / ETA / navigation preview / fake endpoint。

### OBD-II

OBD 仅作为 future optional adapter。首个 PoC 只验证标准 Vehicle Speed；不进入私有 CAN/BMS 逆向，不成为 Trip 必需依赖。

---

## 11. 恢复与隐私

- Local JSON Backup 长期保留
- Cloud Sync 不是唯一恢复路径
- restore 失败不得破坏当前数据
- 持续定位必须可见
- notification permission denial 不阻止 Trip tracking
- ongoing notification 不显示精确坐标 / HOME / WORK
- route share/export 前需要 Privacy Zone
- future cloud Trip sync 需要明确产品与隐私设计

---

## 12. CI / Release

基线:

- JDK 17
- Android SDK 36
- Build Tools 36.0.0
- repository Gradle Wrapper
- Android CI 与 Production Release 分离
- 普通业务提交不生成新的正式版本号
- Production Release 才生成正式 `versionCode/versionName`
- release 最后原子发布 `release-meta/latest.json`

近期关键 Trip/UI evidence:

- PR #80 Android CI run `33083631555` Green: stationary callback implementation slice
- PR #170 Build #482 Green: slider/trends/history/insets/endpoint corrections
- PR #172 Build #485 Green: READY/active density
- PR #174 Build #489 Green: compact completion flow
- PR #176 Build #490 Green: home vs READY split
- PR #179 Android CI run `33198331727` Green: completed detail sections

这些证据只证明 automated build/test；physical acceptance 独立存在。

---

## 13. 架构约束

Android 保持简单单体:

```text
Compose -> MainViewModel -> Repository/domain -> Room DAO -> Room
TripTrackingService -> LocationManager -> sampling/trust -> Room
```

future sync 第一版:

```text
Android Room
   <-> HTTPS Sync API
Spring Boot Monolith
   -> PostgreSQL
```

不要引入 Hilt/Koin、多 module Clean Architecture、微服务、MQ、第二套 Trip service 或为了 notification 增加 WorkManager。

---

## 14. 当前执行顺序

```text
v0.5 physical acceptance bundle
  -> #145 / #168 / #178 Trip v0.6 design-device comparison
  -> #77 background / stationary Trip reliability
  -> #124 Trip SOC -> VehicleState
  -> #26 lock-screen + repair notification
  -> #14 Location / Geocoder
  -> #70 / #94 / #95 / #159 / #164 / #165 / #42 / #22 UI-device checks
  -> #102 old-production -> new-production updater
  -> only fix concrete device regressions
  -> resume #27 minimal Local First sync
  -> advance #20 catalog pipeline when justified
  -> optional MapLibre / destination / OBD only when product evidence exists
```

---

## 15. 开发验收规则

每轮业务代码必须:

- Android Gradle test/build
- current-head GitHub CI Green
- owning Issue / PR 同步
- execution-stage 变化同步 ROADMAP / PROJECT_MASTER
- schema change 必须 explicit Migration
- 不把“代码已写”标成“CI Accepted”
- 不把 CI Green 标成“真机已验收”
- GPS `COMPLETED` 不等于 continuity accepted
- notification available 不等于 tracking data accepted
- Open Issue 不等于“代码还没写”

正式 APK:

- 不发布 -> 不升级正式版本
- 需要下发 -> current-head CI Green -> manual Production Release

---

## 16. 当前 Issues

### 代码主体已完成、等待真机/视觉验收

- #14 Location / Geocoder
- #22 top spacing / density
- #26 background / lock-screen / repair notification
- #42 accessibility / large font / small screen / state safety
- #67 trajectory presentation device check
- #70 core UI physical closeout
- #77 background callback / stationary hold
- #94 Dashboard dynamic Hero
- #95 recent Trip card
- #102 Production APK auto-update
- #124 Trip SOC -> VehicleState
- #145 Trip v0.6 parent design-device matrix
- #146 Trip history density
- #147 READY slide interaction
- #148 active Trip cockpit live update
- #149 completed overview
- #150 route surface
- #151 diagnostics/trends
- #168 Trip device-fidelity corrections
- #171 READY/active density
- #173 completion form
- #175 home vs READY
- #178 completed detail tabs
- #159/#164/#165 Records/Stats v0.6 physical closeout

### Future / expansion

- #27 Local First Sync Phase B / smallest cloud slice
- #20 scalable Vehicle Catalog pipeline
- #152 destination selection / pre-trip planning

### Repository / maintenance

- #6 authoritative docs ongoing maintenance
- #75 main branch protection / required CI; requires repository administration, not a code PR

---

## 17. 决策记录

### v2.4.0

- added Trip v0.6 approved UI baseline and implementation breakdown to authority set
- reconciled master with current Trip home/READY/active/completion/detail architecture
- recorded #178 / PR #179 `概览 / 轨迹 / 数据` detail split
- recorded PR #80 stationary callback implementation as code-complete with #77 physical-only acceptance
- aligned execution order with ROADMAP v2.8.0: Trip design-device closeout before feature expansion
- clarified MapLibre / destination / OBD remain optional/future and are not Trip v0.6 blockers

### v2.3.0

- reconciled reliability/data-quality work through #123/#127/#128/#129
- recorded Trip SOC/mileage/energy -> VehicleState authority and #124
- recorded Dashboard Hero / recent Trip (#96/#100)
- recorded updater wiring/UI (#103/#126)
- recorded lock-screen Trip progress, repair flow and notification permission semantics (#130/#131/#132)
- moved execution to physical acceptance bundle before sync expansion

### v2.2.0

- added `APK_AUTO_UPDATE.md` to authority
- formalized Production Release-only version generation and atomic `latest.json`

### v2.1.0

- promoted GPS continuity to P0 based on real Trip evidence
- separated Location.speed / derived speed / average speed semantics
- established OBD-II as optional future source

### v2.0.1

- added `SYNC_PROTOCOL.md` to authority
- fixed first sync payload / idempotency / tombstone / conflict / cursor boundaries
