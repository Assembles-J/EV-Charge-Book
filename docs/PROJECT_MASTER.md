# EV Charge Book 项目总纲（PROJECT MASTER）

版本: v2.3.0
更新时间: 2026-08-28
状态: Authority Document / Single Source of Truth

## 1. 项目定位

EV Charge Book 是新能源车主的 Local First 车辆数据中心。

演进顺序：

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

- PRODUCT.md
- FEATURE_MATRIX.md
- UIUX.md
- FRONTEND.md
- BACKEND.md
- DATABASE.md
- CI_CD.md
- APK_AUTO_UPDATE.md
- ROADMAP.md
- DEVELOPMENT.md
- LOCATION_TRIP.md
- TRIP_GPS_RELIABILITY_AND_SPEED_VISUALIZATION.md
- VEHICLE_CATALOG_MULTI_VEHICLE.md
- DATA_QUALITY_BACKUP.md
- SYNC_PROTOCOL.md
- LOCAL_AGENT_HANDOFF.md
- NEXT_PHASE_DESIGN.md

实现与文档冲突时，先以当前代码、CI 与真机事实为准，再修正文档。

阶段状态必须区分：代码已实现、CI Green、真机功能验收、真机视觉验收和 Production Release 验收。不得用其中一个替代另一个。

---

## 3. 产品原则

- 简单可维护
- Local First
- 真实数据来源
- 用户录入成本低
- 统计口径可解释
- 原始事实 / 派生值 / 估算值明确区分
- 不伪造实时 SOC / SOH / 续航
- 定位记录与地图 SDK 解耦
- 车型目录与用户车辆分离
- 外部供应商可替换
- 云同步不能成为唯一恢复路径
- 无网络和云端故障不能阻塞本地记账 / Trip
- GPS 缺失不得通过假路线或假距离静默补齐
- 速度必须保留来源语义，不把 GNSS / 派生值 / future OBD 混成无来源真值
- 地址是坐标的派生展示，不能反过来成为 Location 事实的前置条件
- notification 是后台状态可见性，不得成为 Trip 是否能记录的业务前置条件
- 正式 APK 版本只在真正执行 Production Release 时生成；普通业务提交不得为了代码变化而升级正式版本

---

## 4. v0.1 状态: Released / Accepted

已完成充电记录 CRUD、车辆编辑、Dashboard / Records / Stats、核心规则测试、Android CI、真机 CRUD、signed production APK 与原子发布。

---

## 5. v0.2 状态: Core Implemented / Physical Reliability Revalidation

基础能力已经实现：

- odometer + migration
- Local Backup / Restore
- Multi Vehicle / Vehicle Catalog
- Bluetooth 指定设备连接 / 已连接状态 -> Trip 用户确认
- Android LocationManager + WGS84 + accuracy
- AddressResolver + Android Geocoder
- TripSession / TripPoint
- foreground tracking / notification
- Trip start / completion / interrupted resume / detail
- distance / elapsed / moving / stopped
- speed / bearing / GPS altitude / accuracy
- GPS bad-point / jump filtering
- app-level stationary write throttling
- TripRouteGeometry + 无底图真实轨迹预览

### 5.1 post-RC Trip reliability 当前事实

真实 Trip #7 和后续 2026-08-27 设备数据证明：`COMPLETED` 只表示业务收口，不代表 GPS 全程连续；foreground service 存在也不等于 Location callback 必然持续。

当前代码已经继续补齐：

- runtime `TripGpsHealth`
- callback heartbeat 与 accepted-point heartbeat 分离
- WAITING / GOOD / DEGRADED / LOST / LONG_GAP
- long-gap route segmentation，不画假实线
- long-gap 两端不计可信连续距离
- foreground Location callback 使用 time-based liveness，不再依赖 8m displacement gate
- stationary write throttling 继续留在 `TripSamplingRules`
- provider / permission / service lifecycle / re-delivery diagnostics
- coarse/network max-speed peak rejection
- extreme GPS speed 缺失 speedAccuracy 时降低信任
- trusted speed-colored route presentation
- 起点/终点形状 + 文本语义

Issue #77 保留为 post-RC 真机可靠性验收：另一 App 前台、2–3 分钟 stationary hold、真实 callback/provider loss 和 stationary write volume 必须通过真实设备验证。不得因为当前 CI Green 就关闭。

### 5.2 海拔与 Trip validity

当前 `main` 已实现：

- Trip start/end/min/max altitude
- trusted cumulative elevation gain / loss
- vertical accuracy filtering + jitter deadband
- cumulative elevation 不跨 LONG_GAP
- conservative Trip validity classification
- 明确空/异常完成 Trip 从 Dashboard 与汇总 analytics 排除
- 极短真实 Trip 只进入 REVIEW，不自动删除、不自动排除
- 删除始终由用户显式确认

对应代码工作 #69/#68 已完成关闭。

MapLibre 继续作为可选展示层，不是当前业务阶段门槛。

---

## 6. 速度与距离事实模型

### 6.1 距离

Trip distance 来自连续可信 accepted location point 的地理距离累计。

它不是道路里程，也不是车辆轮速里程，因此弯路、采样稀疏、provider switch、GPS gap 都可能影响结果。

当前规则：LONG_GAP 两端不能继续作为可信连续距离；原始 TripPoint 证据保留，派生统计只决定“什么可以进入汇总”。

### 6.2 瞬时 / 最高速度

TripPoint `speedMps` 主要来自 Android `Location.speed`，不是简单的两点直线距离 / 时间。

产品继续区分：

- `Location.speed`：定位系统报告的时刻速度
- derived segment evidence：连续可信点的距离 / 时间及窗口证据
- whole-trip / moving average：依赖累计 trusted distance 的派生统计

最高速度只允许可信 GPS evidence 更新；coarse/network provider 和缺失关键 accuracy evidence 的 extreme peak 不应成为 max-speed 真值。

UI 应称“最高已记录速度”，不得宣称未采样到的真实车辆峰值。

### 6.3 速度轨迹

当前 route 已能按可信 GPS speed 进行颜色表达，同时保留：

- unknown speed 灰色语义
- LONG_GAP 断开
- legend / 区间说明
- “颜色表示本车速度，不代表真实道路交通状态”说明

不为了速度轨迹提前引入 MapLibre 或道路限速/拥堵语义。

---

## 7. v0.3 状态: Feature Complete / Incremental Follow-up Only

已实现：

- charging interval odometer distance
- cost / 100km estimate
- charged kWh / 100km estimate
- Trip + odometer coverage evidence
- SOC confidence hints
- month trend / month-over-month
- charger type mix
- ChargingPlace aggregation / common-place reuse
- charging CSV analysis export
- non-blocking anomaly hints
- Local JSON Backup / Restore
- Trip validity-aware analytics
- Charge 桩端事实与 Trip SOC energy estimate 分层展示

Issue #19 已完成关闭。

不为了“数据治理框架完整”新增独立 ChargingPlace Room 表、统一 confidence 分数或不必要的 source metadata。Privacy Zone 等能力在真实 route export/share 出现前再推进。

---

## 8. v0.4 Local First Sync Foundation

Phase A 已完成：

- Vehicle `syncId` + `updatedAtEpochMillis`
- ChargingRecord `syncId` + `updatedAtEpochMillis`
- ChargingRecord tombstone
- explicit Room migration / old identity generation
- Backup / Restore 保留 sync metadata
- active UI / analytics 排除 tombstone
- pure JVM identity tests

Issue #28 已完成关闭；父 Issue #27 继续承担 Phase B / Phase C。

`SYNC_PROTOCOL.md` 保留第一版 payload / idempotency / tombstone / conflict / cursor 边界。

下一步仍坚持最小方案：Vehicle + ChargingRecord 的 envelope、push/pull cursor、幂等 upsert、tombstone propagation、简单 conflict policy，然后才是 HTTPS + Spring Boot monolith + PostgreSQL。

当前不做：TripSession / TripPoint cloud sync、CRDT、微服务、Kafka/MQ、云端成为唯一备份。

---

## 9. v0.5 Local Experience / VehicleState 状态: Code Baseline Implemented / Physical Closeout

### 9.1 UI

已合并 Dark First 基线与核心页面重构（#71），并继续补齐：

- Dashboard dynamic Vehicle Hero（#96）
- Dashboard recent Trip card（#100）
- current SOC / current mileage VehicleState presentation
- release updater Dashboard dark/green visual alignment（#126）

#70/#94/#95/#42/#22 仍保留真机视觉 / accessibility / large-font / small-screen closeout，不从 CI 推导视觉验收。

### 9.2 Trip SOC / mileage / energy -> VehicleState

PR #87 等当前基线已经实现：

- Trip start snapshot 当前 VehicleState SOC / mileage
- completion 要求 explicit end SOC
- end mileage 默认按 start mileage + GPS distance 预填并允许修正
- TripSession 保存 start/end SOC + mileage
- 只有正向可信 SOC drop 才估算 consumed energy
- required inputs 足够时才估算 kWh / 100km
- Trip completion 后 end SOC / mileage 回写 VehicleState
- 删除完成 Trip 后重新构建 VehicleState
- 后发生的 Charge / manual VehicleState 保持 authority，不被更老 Trip 回滚

Issue #124 专门负责真机数据闭环验收，不再创建重复实现 PR。

### 9.3 Location / address optionality

当前原则已经落地：coordinates 是事实，address 是派生展示。

- Add Charge 有权限时自动请求当前位置
- Geocoder failure 不阻塞坐标事实
- 可继续手填地点
- `LocationProvider` / `AddressResolver` 分离
- successful geocode 使用 bounded process-local cache
- failed / blank result 不缓存，下次用户 retry 会重新调用 Geocoder

Issue #66 已完成；真实权限 / Geocoder / backup-restore 继续由 #14 真机验收。

### 9.4 Lock-screen / background Trip UX

PR #130/#131/#132 已形成当前必要代码基线：

- ongoing notification 显示 Trip elapsed time + trusted persisted distance
- notification tap / `打开行程` 直接进入 active Trip
- 锁屏文本不泄露精确坐标 / 地址
- runtime Location permission loss -> `INTERRUPTED` + one-shot repair notification
- no usable location provider -> `INTERRUPTED` + system Location repair action
- repair 后不会自动恢复 tracking；用户回 Trip 明确 resume
- stale repair warning 在 resume/start 后清除
- Android 13+ Trip notification permission 在 tracking 已开始/恢复后请求
- notification denial 不阻塞、不回滚 Trip
- Trip 场景只做一次 notification permission prompt，避免重复打扰
- 通知不能直接 complete Trip，结束仍进入 App 输入 end SOC

Issue #26 现在只保留真机 acceptance，以及 trusted current speed / battery optimization guidance 两项 evidence-driven optional。

### 9.5 Production APK updater

当前已实现：

- release manifest `latest.json`
- higher version discovery
- DownloadManager
- SHA-256 verification
- unknown-source permission flow
- Android system installer handoff
- root app composition wiring（#103）
- Dashboard-style non-modal updater UI（#126）

Issue #102 保留 old-production APK -> newer-production APK 的真实覆盖升级验收。不能只凭 Debug CI 关闭。

---

## 10. P3 Optional Vehicle Data / OBD-II

OBD-II 是未来可选数据源，不是当前产品依赖。

```text
VehicleSpeedSource
├── GnssSpeedSource      当前默认
├── DerivedSpeedSource   分段派生/校验
└── ObdSpeedSource       P3 optional
```

第一个 PoC 只验证：

- 外接 Bluetooth / BLE / Wi-Fi OBD-II adapter
- 查询标准 Vehicle Speed 支持
- 读取标准 OBD Vehicle Speed
- 与 GNSS speed 对照

当前明确不做：厂商私有 CAN ID 逆向、私有 BMS PID 逆向、单车型复杂协议维护、让 OBD 成为 Trip 必需依赖。

只有 Vehicle Speed PoC 稳定且有产品价值后，才评估 SOC / 电压 / 电流 / 电池温度等更多车辆事实。

---

## 11. 恢复与隐私

- Local JSON Backup 长期保留
- Cloud Sync 不是唯一恢复路径
- restore 失败不得破坏当前数据
- 持续定位必须可见；通知权限被拒绝也不得阻止 Trip 本身记录
- ongoing notification 不显示精确经纬度 / HOME / WORK 地址
- 路线公开分享 / 导出之前实现 Privacy Zone
- 云同步轨迹需要明确产品同意与隐私说明

---

## 12. 发布 / CI / APK 自动升级基线

- JDK 17
- Android SDK 36
- Build Tools 36.0.0
- repository Gradle Wrapper
- Android CI 与 Production Release 分离
- signed APK
- Actions Artifact
- 普通 Android Build 使用 dev 版本，不生成正式版本号
- 只有手动 Production Release 才生成正式 `versionCode/versionName`
- Production Release 最后原子发布 `release-meta/latest.json`
- release App 自动检查更高 `versionCode`
- DownloadManager 下载 immutable APK
- 下载后 SHA-256 校验
- 最终由 Android 系统安装器完成覆盖安装

近期 current-head evidence：

- #123 Android Build #408 Green：extreme Trip max-speed trust
- #127 Android Build #414 Green：Trip elevation analytics
- #128 Android Build #417 Green：Trip validity / cleanup
- #129 Android Build #419 Green：geocode cache / retry
- #130 Android Build #422 Green：lock-screen Trip progress / active-Trip deep link
- #131 Android Build #424 Green：provider / permission repair notifications
- #132 Android Build #426 Green：Android 13+ non-blocking Trip notification permission

这些 CI 只证明自动化 build/test；#77/#124/#26/#14/#70/#94/#95/#42/#22/#102 的真机 acceptance 仍独立存在。

---

## 13. 架构约束

Android 保持：

```text
Compose -> MainViewModel -> ChargingRepository -> Room DAO -> Room
```

未来 v0.4 云端第一版保持：

```text
Android Room
   <-> HTTPS Sync API
Spring Boot Monolith
   -> PostgreSQL
```

不要引入 Hilt/Koin、多 module Clean Architecture、微服务、MQ、第二套 Trip tracking service、为了通知增加 WorkManager 或其他当前没有收益的基础设施。

OBD 未来也必须是 optional adapter，不允许反向污染核心 Trip 架构。

---

## 14. 当前执行顺序

```text
v0.5 physical acceptance bundle
  -> #77 background / stationary Trip reliability
  -> #124 Trip SOC -> VehicleState data closure
  -> #26 lock-screen / repair notification round-trip
  -> #14 Location / Geocoder device behavior
  -> #70 / #94 / #95 / #42 / #22 UI-device checks
  -> #102 old-production -> new-production updater flow
  -> only fix concrete device regressions
  -> resume #27 minimal sync protocol/runtime
  -> advance #20 catalog pipeline when data-source/coverage work is justified
  -> P3 OBD-II Vehicle Speed PoC only when justified
```

APK 自动升级属于发布基础设施；它随 Production Release 验收独立推进。

MapLibre 继续保持低优先级；已有真实 WGS84 route preview，不为了路线“看起来完整”提前引入地图 SDK。

---

## 15. 开发验收规则

每轮业务代码必须：

- Android Gradle test/build
- GitHub CI Green
- 更新 owning Issue / PR
- 阶段变化同步 ROADMAP / PROJECT_MASTER
- schema 改动必须显式 Migration
- 不把“代码已写”标成“CI Accepted”
- 不把 CI 通过标成“真机已验收”
- GPS `COMPLETED` 不等同于 continuity accepted
- notification available 不等同于 tracking data accepted
- Open Issue 不等同于“代码还没写”；先检查 owning PR / current `main`

正式 APK 版本规则：

- 不发布 APK -> 不触发 Android Release -> 不升级正式版本
- 需要下发 APK -> current-head Android CI Green -> 手动 Android Release -> 自动生成正式版本

---

## 16. 当前 Issues

### 代码主体已完成、等待真机/视觉验收

- #14 Location / Geocoder
- #22 top spacing / density
- #26 background / lock-screen / repair notification
- #42 accessibility / large font / small screen / state safety
- #67 trajectory presentation device check
- #70 v0.5 core UI physical closeout
- #77 background callback / stationary hold
- #94 Dashboard dynamic Hero
- #95 recent Trip card
- #102 Production APK auto-update
- #124 Trip SOC -> VehicleState

### 后续真正业务扩展

- #27 Local First Sync Phase B / smallest cloud slice
- #20 scalable Vehicle Catalog pipeline

### Repository / maintenance

- #6 authoritative docs ongoing maintenance
- #75 main branch protection / required CI，需要 repository administration 权限；不通过代码 PR 伪实现

---

## 17. 决策记录

### v2.3.0

- reconciled PROJECT_MASTER with current `main` after reliability/data-quality work through #123/#127/#128/#129
- recorded Trip SOC/mileage/energy -> VehicleState authority and #124 physical acceptance boundary
- recorded dynamic Dashboard Hero / recent Trip baseline (#96/#100)
- recorded updater wiring/UI baseline (#103/#126) while retaining #102 Production APK acceptance
- recorded lock-screen Trip progress and direct active-Trip navigation (#130)
- recorded explicit provider/permission interruption repair flow (#131)
- recorded Android 13+ non-blocking Trip notification permission semantics (#132)
- changed current execution from “implement Trip P0 slices” to “physical acceptance bundle, then resume minimal Local First sync”
- retained simple architecture boundaries; no WorkManager/second tracking service/MapLibre requirement introduced

### v2.2.0

- `APK_AUTO_UPDATE.md` 纳入权威文档体系
- 正式 APK 版本改为 Production Release-only generation
- Android Release 增加 `release_series`
- release server 新增 per-version JSON + `latest.json`
- `latest.json` 成为 App 更新发现的最后原子指针
- release App 增加自动检查、一键下载、SHA-256 校验和系统安装器流程
- APK 更新基础设施不改变 Trip reliability 当前 P0

### v2.1.0

- 基于真实 Trip #7 将 GPS continuity 提升为当前 P0
- PR #36 第一阶段通过 Build Run #184
- 明确 Location.speed / derived speed / average speed 的不同来源与可信度
- 明确 long gap distance correction 与 GPS/Network dedupe 先于彩色轨迹
- OBD-II 作为 P3 optional VehicleSpeedSource，仅从标准 Vehicle Speed PoC 开始
- v0.4 sync foundation 保留，但 feature expansion 暂缓到 Trip reliability 真机复验之后

### v2.0.1

- `SYNC_PROTOCOL.md` 纳入权威文档体系
- 固定第一版 payload / idempotency / tombstone / conflict / cursor 边界