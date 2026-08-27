# EV Charge Book 项目总纲（PROJECT MASTER）

版本: v2.2.0
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
- 正式 APK 版本只在真正执行 Production Release 时生成；普通业务提交不得为了代码变化而升级正式版本

---

## 4. v0.1 状态: Released / Accepted

已完成充电记录 CRUD、车辆编辑、Dashboard/Records/Stats、核心规则测试、Android CI、真机 CRUD、signed production APK 与原子发布。

---

## 5. v0.2 状态: Core Accepted / Reliability Hardening

已完成并经过当前真机功能验证：

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

Trip #15 与 Bluetooth #21 已按此前真机功能反馈关闭，但真实长行程数据暴露出新的 reliability follow-up，不重新打开旧基础功能验收结论。

### 真实 Trip #7 新结论

2026-08-27 导出的真实 Trip #7 出现约 12 分 29 秒和 15 分 17 秒的 GPS gap。

因此：

- `COMPLETED` 只表示行程最终正常收口，不表示 GPS 全程连续。
- foreground service 存在不等于 Location callback 一定持续。
- MapLibre 不是当前优先级；必须先让 GPS continuity 可观察、可诊断、可解释。

PR #35 已将该方向写入正式设计；PR #36 第一阶段代码已实现并通过 Android Build Run #184。

已实现第一阶段：

- runtime `TripGpsHealth`
- 15s / 30s / 120s health thresholds
- ongoing notification 周期刷新 GPS health / last accepted age / provider / rejected count
- 长 gap route geometry 拆段，禁止假实线
- 全程均速 / 行驶均速 / 最高速度 / moving time 语义拆分

仍需 P0：

1. 长 gap 两端不参与可信距离累计
2. GPS / Network point 去重与择优
3. longest gap / provider counters / rejected reason 持久摘要
4. service restart / re-delivery evidence
5. 锁屏长行程真机复验

MapLibre 继续作为可选展示层，不是业务阶段门槛。

---

## 6. 速度与距离事实模型

### 6.1 当前距离

当前 Trip distance 由连续 accepted location point 的地理距离累计得到。

它不是道路里程，也不是车辆轮速里程，因此弯路、采样稀疏、provider switch、GPS gap 都可能影响结果。

原则：长 gap 两端不能继续被视为可信连续距离。

### 6.2 当前瞬时 / 最高速度

当前 TripPoint `speedMps` 和最高已记录速度主要来自 Android `Location.speed`，不是简单的两点直线距离 / 时间。

因此产品必须区分：

- `Location.speed`：定位系统报告的时刻速度
- derived segment speed：连续可信点的距离 / 时间及窗口统计
- whole-trip / moving average：依赖累计 recorded distance 的派生统计

短时峰值可能因为采样窗口没有命中而漏采。例如峰值只持续约 4 秒，而采样目标为 2-5 秒时，不能保证一定保存到该峰值。

UI 应称“最高已记录速度”，不得宣称未采样到的真实车辆峰值。

### 6.3 P1 速度可视化

后续 `TripSpeedSegment`：

- 基于连续可信 GPS segment
- 结合 `Location.speed` / speed accuracy / point-to-point evidence
- 不跨 LONG_GAP
- 连续红 -> 黄 -> 绿 -> 蓝表示本车速度分布
- 无道路类型 / 限速 / 交通数据前，不使用真实“拥堵/畅通”交通标签

---

## 7. v0.3 状态: Feature Complete Candidate / Reliability Follow-up

已实现充电区间、费用/100km、电量/100km、Trip 覆盖、SOC 可信度、月趋势、月环比、charger type、ChargingPlace、常用地点复用、CSV 与异常提示。

v0.3 当前不扩重型 chart、独立 ChargingPlace 表或无真实需求的期间筛选。Reliability follow-up 优先于继续堆统计 UI。

---

## 8. v0.4 Local First Sync Foundation

Vehicle + ChargingRecord 已具备 stable `syncId`、`updatedAtEpochMillis`、ChargingRecord tombstone、Room v6 -> v7 migration、旧数据补 identity、Backup schema v6 与 sync identity tests。

`SYNC_PROTOCOL.md` 已固定第一版 payload / idempotency / tombstone / conflict / cursor 边界。

当前策略：不删除、不回退现有 sync foundation，但暂停继续扩 Spring Boot / PostgreSQL / Trip cloud sync，直到 Trip P0 reliability 具备可观察性并完成一次锁屏长行程复验。

---

## 9. P3 Optional Vehicle Data / OBD-II

OBD-II 是未来可选数据源，不是当前产品依赖。

```text
VehicleSpeedSource
├── GnssSpeedSource      当前默认
├── DerivedSpeedSource   分段派生/校验
└── ObdSpeedSource       P3 optional
```

第一个 PoC 只验证：

- 外接 Bluetooth/BLE/Wi-Fi OBD-II adapter
- 查询标准 Vehicle Speed 支持
- 读取 OBD Vehicle Speed
- 与 GNSS speed 对照

当前明确不做：厂商私有 CAN ID 逆向、私有 BMS PID 逆向、单车型复杂协议维护、让 OBD 成为 Trip 必需依赖。

如果 Vehicle Speed PoC 稳定且有产品价值，再评估 SOC / 电压 / 电流 / 电池温度等更多车辆事实。

---

## 10. 恢复与隐私

- Local JSON Backup 长期保留
- Cloud Sync 不是唯一恢复路径
- restore 失败不得破坏当前数据
- 持续定位必须可见
- ongoing notification 不显示精确经纬度 / HOME / WORK 地址
- 路线公开分享/导出之前实现 Privacy Zone
- 云同步轨迹需要明确产品同意与隐私说明

---

## 11. 发布 / CI / APK 自动升级基线

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

详细规则见 `CI_CD.md` 与 `APK_AUTO_UPDATE.md`。

近期关键验收：

- Build Run #169：ChargingPlace + CSV 基线 Green
- Build Run #184：PR #36 Trip GPS health / route gap / speed semantics，Build/Test + Debug APK Green
- APK auto-update Android CI：当前等待最新 updater cumulative build 完成

---

## 12. 架构约束

Android 保持：

```text
Compose -> MainViewModel -> ChargingRepository -> Room DAO -> Room
```

v0.4 云端第一版保持：

```text
Android Room
   <-> HTTPS Sync API
Spring Boot Monolith
   -> PostgreSQL
```

不要引入 Hilt/Koin、多 module Clean Architecture、微服务、MQ 或其他当前没有收益的基础设施。

OBD 未来也必须是 optional adapter，不允许反向污染核心 Trip 架构。

---

## 13. 当前执行顺序

```text
Trip P0 reliability
  -> long-gap distance correction
  -> GPS / Network dedupe + quality policy
  -> Trip completeness / persistent diagnostics
  -> lock-screen long-drive physical verification
  -> Trip P1 segmented speed + continuous color route
  -> v0.3 reliability closeout
  -> resume v0.4 sync expansion
  -> P3 OBD-II Vehicle Speed PoC only when justified
```

APK 自动升级属于发布基础设施，不改变当前业务优先级；它随 Production Release 验收独立推进。

---

## 14. 开发验收规则

每轮业务代码必须：

- Android Gradle test/build
- GitHub CI Green
- 更新 owning Issue / PR
- 阶段变化同步 ROADMAP / PROJECT_MASTER
- schema 改动必须显式 Migration
- 不把“代码已写”标成“CI Accepted”
- 不把 CI 通过标成“真机已验收”
- GPS `COMPLETED` 不等同于 continuity accepted

正式 APK 版本规则：

- 不发布 APK -> 不触发 Android Release -> 不升级正式版本
- 需要下发 APK -> Android CI Green -> 手动 Android Release -> 自动生成正式版本

---

## 15. 当前 Issues

- #19 Data Reliability：只保留增量可靠性尾项
- #22 UI polish：主要视觉工作已合并，真机视觉复核非阻塞
- #26 Background activity / lock-screen Trip status / notification center
- #27 v0.4 Sync 主线：foundation 保留，扩展暂缓
- #28 Sync Phase A 实现与验收
- #20 Catalog Coverage：后续可持续车型目录管道

---

## 16. 决策记录

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
