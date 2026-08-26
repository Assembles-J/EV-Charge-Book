# EV Charge Book 项目总纲（PROJECT MASTER）

版本: v1.7.0
更新时间: 2026-08-26
状态: Authority Document / Single Source of Truth

## 1. 项目定位

EV Charge Book 是新能源车主的 Local First 车辆数据中心。

演进顺序:

1. 充电记账与成本
2. 多车辆与车型目录
3. 位置 / 驾驶行程数据
4. 充电与行程的数据闭环
5. 数据分析
6. 云同步与 AI

首个验证车型仍为零跑 C16，但产品、数据库和 UI 不绑定单一品牌。

---

## 2. 权威文档体系

1. PRODUCT.md
2. FEATURE_MATRIX.md
3. UIUX.md
4. FRONTEND.md
5. BACKEND.md
6. DATABASE.md
7. CI_CD.md
8. ROADMAP.md
9. DEVELOPMENT.md
10. LOCATION_TRIP.md
11. VEHICLE_CATALOG_MULTI_VEHICLE.md
12. DATA_QUALITY_BACKUP.md
13. LOCAL_AGENT_HANDOFF.md - 当前本地 Agent 接手入口

实现与文档冲突时，先以当前代码事实确认状态，再修正文档。

---

## 3. 产品原则

- 简单可维护
- Local First
- 真实数据来源
- 用户录入成本低
- 统计口径可解释
- 原始事实 / 派生值 / 估算值必须可区分
- 不伪造实时 SOC / SOH / 续航
- 定位记录与地图 SDK 解耦
- 车型目录与用户车辆分离
- 关键外部供应商必须可替换
- Local First 必须具备可恢复路径，而不是只依赖未来云同步

---

## 4. v0.1 状态: Released / Accepted

v0.1 已完成正式发布和验收：

- Room Vehicle / ChargingRecord
- DAO / AppDatabase / Repository
- MainViewModel + StateFlow
- 车辆编辑持久化
- 充电记录新增 / 编辑 / 删除
- 日期 / 时间选择
- charger type / remark
- 删除确认
- 保存成功 / 错误反馈
- 空状态
- Dashboard / Records / Stats 真实数据
- ChargingRecordRules / ChargingStatistics
- 核心规则与统计单元测试
- Material 3 主题与主要页面视觉重构
- Gradle Wrapper
- Android CI / Debug APK
- 真机核心 CRUD 验收
- signed production APK
- server atomic release

首次正式发布见 `docs/ROADMAP.md`。

历史 Issue #1 / #2 已关闭，不再作为当前工作入口。

---

## 5. 当前阶段: v0.2 Vehicle & Trip Foundation

### P0: odometer foundation

已完成并通过 Android Build Run #56：

- ChargingRecord nullable `odometerKm`
- Room v1 -> v2 migration
- Add/Edit 支持里程
- Records 展示里程
- 同车辆、早于当前记录时间的上一条可靠里程选择
- 里程倒退只提示、不阻塞
- odometer rules / previous-reading tests

Issue #18 保持 open，只追踪 migration test、正式 charging interval calculation 和 v0.3 统计口径尾项。

### P0.5: Local Backup / Restore

第一版代码已经实现：

- JSON backup
- schemaVersion / exportedAt / appVersion
- Vehicle + ChargingRecord
- SAF system file picker
- restore overwrite confirmation
- schemaVersion validation
- Room transaction restore
- export / restore count validation

Android Build Run #65 已通过，Debug APK Artifact 已生成，并已完成真机验收。

Run ID: `32942654435`
Commit: `94127ae874c33015eb88bb461be37f3869618b0f`

Multi Vehicle 已验收，当前进入 P1 Vehicle Catalog；完成该阶段验收前不得开始 Location / Trip 新功能。

### P1: 多车辆 / 车型目录

Backup / Restore 与 Multi Vehicle 已验收，当前开始 Vehicle Catalog：

- selected/default vehicle
- Dashboard / Records / Stats 按车辆隔离
- 多车辆创建 / 归档
- 本地车型目录 + 自定义兜底

Multi Vehicle 已通过 Android Build Run #66 与真机验收；当前进入 Vehicle Catalog。

实现顺序优先 Multi Vehicle context，再接 Vehicle Catalog，避免 catalog 绑死 UserVehicle。

### P2: 定位 / 行程

- Android Location API 为记录核心
- WGS84 原始坐标
- MapLibre 为地图渲染 adapter
- 用户主动开始 + foreground service
- 记录距离、时间、速度、GPS 海拔和 accuracy
- 区分 elapsed / moving / stopped time
- 支持 INTERRUPTED 行程恢复
- 控制采样频率、电量和数据库体积

---

## 6. 数据可信度原则

允许来源:

- MANUAL
- GPS
- OCR
- CATALOG
- VEHICLE_API
- OBD
- DERIVED

不为所有字段制造统一 confidence 分数。

优先保存:

- 原始值
- 数据来源
- 可获得的 accuracy
- 计算口径

AI 后续只能在这套可解释数据基础上做总结和建议。

---

## 7. 车型覆盖原则

采用:

```text
VehicleCatalog reference data
   +
UserVehicle snapshot/override
   +
Custom Vehicle fallback
```

不承诺依赖单个免费第三方 API 永久覆盖所有电车。

---

## 8. 隐私与备份

- 默认本地保存
- 持续定位记录必须可见
- 云同步轨迹需要明确同意
- 分享轨迹后续支持 Privacy Zone
- 在账号云同步之前提供 Local Backup / Restore
- 恢复失败不得破坏现有本地数据

---

## 9. Android / 发布基线

- JDK 17
- Android SDK 36
- Build Tools 36.0.0
- repository Gradle Wrapper
- CI 与 Production Release 分离
- production Environment
- signed APK
- Actions Artifact
- `/opt/ev-charge-book/releases`
- `.part` + SHA / apksigner + atomic activation
- latest 只在成功后切换

---

## 10. 当前执行顺序

```text
Vehicle Catalog (#16)
 -> Bluetooth connection prompt discovery
 -> Location foundation (#14)
 -> Manual Trip Tracking (#15)
 -> Data Reliability / ChargingPlace (#19 remaining)
 -> Map Display
 -> v0.3 Analytics
```

---

## 11. 本地 Agent 接手规则

本地 Agent 开始前至少阅读：

1. `docs/PROJECT_MASTER.md`
2. `docs/ROADMAP.md`
3. `docs/LOCAL_AGENT_HANDOFF.md`
4. 对应功能子文档

保持当前简单结构：

```text
Compose -> MainViewModel -> ChargingRepository -> Room DAO -> Room
```

当前阶段不要引入 Hilt/Koin、多 module Clean Architecture 或无明确收益的抽象。

每轮开发结束必须：

- 本地 Gradle test/build
- GitHub CI Green
- 更新对应 Issue
- 阶段变化时更新 ROADMAP / PROJECT_MASTER
- 不把“代码写完”等同于“功能验收完成”

---

## 12. 决策记录

### v1.7.0

- v0.1 正式标记 Released / Accepted
- odometer foundation 已实现并通过 Android Build Run #56
- Local Backup / Restore 已完成 CI 与真机验收
- Multi Vehicle #17 已验收并关闭；Local Agent 当前任务为 Vehicle Catalog (#16)
- 清理历史 Issue #1 / #2；#18 / #19 更新为真实当前状态

### v1.6.0

- commit `7204c56` 完成充电记录完整编辑与主要 UX 收口
- v0.1 从功能开发阶段切换到 Acceptance 阶段
- v0.2 第一优先级固定为 odometerKm，随后 Local Backup / Restore
