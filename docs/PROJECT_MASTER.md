# EV Charge Book 项目总纲（PROJECT MASTER）

版本: v2.3.1
更新时间: 2026-08-27
状态: Authority Document / Single Source of Truth

## 1. 项目定位

EV Charge Book 是新能源车主的 Local First 车辆数据中心。

演进顺序：

1. 充电记账与成本
2. 多车辆与车型目录
3. 位置 / 驾驶行程数据
4. 充电与行程的数据闭环
5. 本地分析与可靠性
6. 产品级 UI/UX 与真实使用 hardening
7. 跨设备同步 / 云恢复
8. Web / AI 增值能力

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

实现与文档冲突时，先以当前代码、CI、Issue 和真机事实为准，再修正文档。

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
- 车辆图片只能精确匹配；不支持车型宁可 fallback，也不能展示错误近似车型
- 外部供应商可替换
- 云同步不能成为唯一恢复路径
- 无网络和云端故障不能阻塞本地记账 / Trip
- GPS 缺失不得通过假路线或假距离静默补齐
- 地址文本不是定位事实；坐标/accuracy/provider/timestamp 才是定位事实基础
- 速度必须保留来源语义，不把 GNSS / 派生值 / future OBD 混成无来源真值
- 正式 APK 版本只在真正执行 Production Release 时生成；普通业务提交不得为了代码变化而升级正式版本

---

## 4. v0.1 状态: Released / Accepted

已完成充电记录 CRUD、车辆编辑、Dashboard/Records/Stats、核心规则测试、Android CI、真机 CRUD、signed production APK 与原子发布。

---

## 5. v0.2 状态: Core Accepted

Vehicle / Location / Trip 基础能力已进入稳定产品基线：

- odometer + migration
- Local Backup / Restore
- Multi Vehicle / Vehicle Catalog
- Bluetooth 指定设备连接 / 已连接状态 -> Trip 用户确认
- Android LocationManager + WGS84 + accuracy
- AddressResolver + Android Geocoder
- TripSession / TripPoint
- foreground tracking / notification
- Trip start / stop / interrupted resume / detail
- distance / elapsed / moving / stopped
- speed / bearing / GPS altitude / accuracy
- GPS bad-point / jump filtering
- stationary throttling
- TripRouteGeometry + 无底图真实轨迹预览

历史 Trip reliability hardening 已通过 0.4 RC1 收口：

- runtime Trip diagnostics
- service lifecycle evidence
- long-gap no-fake-distance/no-fake-route rules
- lock-screen long-drive physical verification
- Bluetooth-assisted start physical verification

Issue #41 已完成并关闭；Release gate #54 已标记 0.4 RC1 released。后续 v0.5 Location/Trip improvements 不应继续把 #41 写成当前 blocker。

---

## 6. 速度、距离与定位事实模型

### 6.1 距离

Trip distance 来自连续 accepted location point 的可信地理距离，不是道路里程或车辆轮速里程。

大 GPS gap、低质量点、provider 切换不能被静默视为可信连续距离。

### 6.2 速度

当前 TripPoint `speedMps` / 最高已记录速度主要来自 Android `Location.speed`。

必须区分：

- `Location.speed`
- derived segment speed
- whole-trip / moving average
- future optional OBD speed

UI 应称“最高已记录速度”，不能宣称未被采样的真实车辆峰值。

### 6.3 定位与地址

v0.5 开始明确：

```text
Location facts
- latitude
- longitude
- accuracy
- altitude(optional)
- timestamp
- provider
- quality

Presentation enhancement
- address(optional)
```

地址解析失败不得让已有 GPS 坐标被当作“无位置”。Issue #66 负责把 coordinate-first fallback 做成稳定产品行为。

---

## 7. v0.3 状态: Feature Complete Candidate / Incremental Follow-up

已实现充电区间、费用/100km、电量/100km、Trip 覆盖、SOC 可信度、月趋势、月环比、charger type、ChargingPlace、常用地点复用、CSV 与异常提示。

继续只做真实使用证明有价值的可靠性增量，不为“完整分析平台”过度设计。

---

## 8. v0.4 状态: RC1 Released / Sync Foundation Retained

0.4 RC1 release gate 已完成（#41 / #54）。

Vehicle + ChargingRecord 已具备：

- stable `syncId`
- `updatedAtEpochMillis`
- ChargingRecord tombstone
- Room migration / old-data identity
- Backup schema sync metadata
- sync identity tests
- first protocol boundary in `SYNC_PROTOCOL.md`

云同步扩展继续保留，但不是当前 v0.5 本地体验 blocker。

---

## 9. v0.5 Product UI/UX Baseline

状态: **Core UI implementation merged / experience hardening active**

PR #71 已合并到 `main`。其最终 head Android Build Run #294 Green，并产出 Debug APK artifact。

已落地：

- Dark First EV cockpit 设计语言
- 显式 Light 模式切换并持久化
- 五个一级入口：总览 / 记录 / 统计 / 行程 / 车辆
- Dashboard vehicle Hero + energy cockpit + recent charging
- Records timeline + Add/Edit charge hierarchy
- Trip READY / LIVE / INTERRUPTED + detail / GPS gap visual semantics
- Stats month / comparison / mix / place / lifetime / interval hierarchy
- Vehicle garage / catalog / editor / Bluetooth / backup / CSV 一致化
- compact empty states
- 本地 drawable 车型图片与严格映射测试

车辆图运行时 Base64/network 加载已经移除。当前只覆盖少量精确车型；规模化车型/图片覆盖必须回到 #20 的可维护目录管道。

### 当前 v0.5 P0

1. **#66 coordinate-first Location fallback**
   - GPS 坐标先保存/先成立
   - 地址异步解析和可失败
   - waiting fix / resolving address / unavailable 明确区分

2. **#42 state safety**
   - active Trip 下 Restore / archive / current vehicle switch guard
   - dirty Add/Edit form Back protection
   - warning/error 不能只靠颜色

3. **#68 invalid Trip handling**
   - invalid/incomplete classification
   - 显式用户清理
   - 无效 Trip 不污染统计

### 当前 v0.5 P1

- #70 / #22 五个一级页面最终真机视觉与顶部密度复核
- #42 fontScale / small screen / TalkBack / touch target / long text / IME
- #67 小 GPS gap 的连续性展示优化，大 gap 继续保持可信断开
- #26 锁屏/background/permission/battery-optimization repair UX

### 当前 v0.5 P2

- #69 altitude/elevation analytics
- #20 scalable vehicle catalog/artwork pipeline
- resume #27/#28 sync expansion after local experience stabilizes

CI Green 不等于真机视觉/可用性已通过，因此 Issue #70 在最终 device pass 前保持 open。

---

## 10. P3 Optional Vehicle Data / OBD-II

OBD-II 是未来可选数据源，不是当前产品依赖。

```text
VehicleSpeedSource
├── GnssSpeedSource
├── DerivedSpeedSource
└── ObdSpeedSource       P3 optional
```

首个 PoC 只验证标准 Vehicle Speed。当前明确不做厂商私有 CAN/BMS 逆向，也不让 OBD 成为 Trip 必需依赖。

---

## 11. 恢复与隐私

- Local JSON Backup 长期保留
- Cloud Sync 不是唯一恢复路径
- restore 失败不得破坏当前数据
- 持续定位必须可见
- ongoing notification 不显示精确经纬度 / HOME / WORK 地址
- 路线公开分享/导出之前实现 Privacy Zone
- 云同步轨迹需要明确产品同意与隐私说明

---

## 12. 发布 / CI / APK 自动升级基线

- JDK 17
- Android SDK 36
- Build Tools 36.0.0
- repository Gradle Wrapper
- Android CI 与 Production Release 分离
- debug application ID 与 release 分离，可并存安装
- signed APK
- Actions Artifact
- 普通 Android Build 使用 dev 版本，不生成正式版本号
- 只有手动 Production Release 才生成正式 `versionCode/versionName`
- Production Release 最后原子发布 `release-meta/latest.json`
- release App 自动检查更高 `versionCode`
- DownloadManager 下载 immutable APK
- 下载后 SHA-256 校验
- 最终由 Android 系统安装器完成覆盖安装

近期关键验收：

- Build Run #169：ChargingPlace + CSV baseline Green
- Build Run #184：Trip GPS health / route gap / speed semantics Green
- 0.4 RC1：Issue #41 accepted, release gate #54 released
- Build Run #294：PR #71 v0.5 UI cumulative baseline Green + Debug APK

---

## 13. 架构约束

Android 保持：

```text
Compose -> MainViewModel -> ChargingRepository -> Room DAO -> Room
```

未来 cloud sync 第一版保持：

```text
Android Room
   <-> HTTPS Sync API
Spring Boot Monolith
   -> PostgreSQL
```

不要引入 Hilt/Koin、多 module Clean Architecture、微服务、MQ 或其他当前没有收益的基础设施。

---

## 14. 当前执行顺序

```text
#66 coordinate-first Location fallback
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

原则：先修真实数据与状态安全，再做展示增强，最后扩分析/目录/云同步。

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
- GPS `COMPLETED` 不等于 continuity accepted
- UI merge 不等于 accessibility / large-font / small-screen / active-Trip state-safety accepted

正式 APK 版本规则：

- 不发布 APK -> 不触发 Android Release -> 不升级正式版本
- 需要下发 APK -> Android CI Green -> 手动 Android Release -> 自动生成正式版本

---

## 16. 当前 Issues

- #70 v0.5 UI redesign：核心实现已 merge，仅剩真机视觉 closeout
- #66 coordinate-first Location fallback
- #42 UI/UX hardening：state safety / accessibility / large font / small screen / dirty form / IME
- #68 invalid Trip classification / cleanup
- #67 trajectory continuity presentation
- #26 background / lock-screen / permission repair UX
- #69 altitude/elevation analytics
- #14 Location / Geocoder physical acceptance
- #22 top spacing 最终真机复核
- #20 scalable vehicle catalog coverage
- #27/#28 sync expansion 后续恢复

---

## 17. 决策记录

### v2.3.1

- verified #41 is completed and #54 is released; removed stale wording that treated Trip RC work as current blocker
- v0.5 becomes the active local product-experience milestone
- prioritized #66, #42 and #68 as current P0 before more presentation/analytics work
- kept #70 open only for final physical visual closeout

### v2.3.0

- PR #71 v0.5 Dark First UI baseline merged into `main`
- Build Run #294 Green + Debug APK
- vehicle artwork moved to APK-local drawable exact mapping
- post-merge UI work narrowed to real-device polish and usability/state-safety hardening
