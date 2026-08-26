# EV Charge Book 项目总纲（PROJECT MASTER）

版本: v1.5.0
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
10. LOCATION_TRIP.md - 定位、地图、行程追踪
11. VEHICLE_CATALOG_MULTI_VEHICLE.md - 车型目录与多车辆
12. DATA_QUALITY_BACKUP.md - 数据来源、异常规则、地点复用、备份恢复与隐私

实现与文档冲突时先更新文档再继续实现。

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

## 4. 当前阶段: v0.1 Local Charging Book

已落地:

- Room Vehicle / ChargingRecord
- DAO / AppDatabase / Repository
- MainViewModel + StateFlow
- 车辆编辑持久化
- 新增 / 删除充电记录
- 真实 Dashboard / Records / Stats
- Gradle Wrapper
- CI / Release / atomic deploy 基线

仍需完成:

- 充电记录更新闭环
- 日期时间 / 充电类型 / remark
- 删除确认 / 保存反馈
- Debug CI Green / APK Artifact
- signed production APK 首次验收

地图、驾驶轨迹、车型库、多车辆、odometer、备份等不得阻塞 v0.1 发布。

---

## 5. v0.2 Vehicle & Trip Foundation

### P0: 使用成本数据闭环

ChargingRecord 增加可选 `odometerKm`，优先建立:

```text
Charging Record
  + odometer
  + vehicleId
       ↓
Driving interval / Trip data
       ↓
真实里程区间
       ↓
百公里成本 / 充入电量分析基础
```

充电与 Trip 不建立错误的一对一硬外键；通过车辆、时间、里程和行程汇总进行分析关联。

### 多车辆 / 车型目录

- UserVehicle 支持多辆车
- selected/default vehicle
- 本地车型目录 + 自定义兜底
- Dashboard / Records / Stats 按车辆隔离

### 定位 / 行程

- Android Location API 为记录核心
- WGS84 原始坐标
- MapLibre 为地图渲染 adapter
- 用户主动开始 + foreground service
- 记录距离、时间、速度、GPS 海拔和 accuracy
- 区分 elapsed / moving / stopped time
- 支持 INTERRUPTED 行程恢复
- 控制采样频率、电量和数据库体积

### P1: 数据可靠性

- 关键数据标记 DataSource
- accuracy 使用系统真实精度字段
- 确定性规则优先发现异常
- ChargingPlace 支持常用地点分类
- Local Backup / Restore 在云同步之前提供

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

目标是持续提高新能源车型覆盖率，但不承诺一个免费第三方 API 永久覆盖所有电车。

采用:

```text
VehicleCatalog reference data
   +
UserVehicle snapshot/override
   +
Custom Vehicle fallback
```

---

## 8. 隐私与备份

轨迹属于敏感数据。

原则:

- 默认本地保存
- 持续记录时必须可见
- 云同步轨迹需要明确同意
- 分享轨迹后续支持 Privacy Zone
- 在账号云同步之前提供 Local Backup / Restore

---

## 9. Android / 发布基线

继续遵循 Assembles-J 组织现有 production release 逻辑:

- CI 与 Production Release 分离
- production Environment
- signed APK
- Actions Artifact
- `/opt/ev-charge-book/releases`
- `.part` + SHA / apksigner + atomic activation
- latest 只在成功后切换

当前 Android 基线:

- JDK 17
- Android SDK 36
- Build Tools 36.0.0
- repository Gradle Wrapper

CI 详细状态由 Issue #7 跟踪。

---

## 10. 当前执行顺序

```text
Charging Record full CRUD
 -> Android CI Green
 -> Debug APK
 -> Signed Production APK
 -> v0.1 Acceptance
 -> odometerKm
 -> Multi Vehicle / Vehicle Catalog
 -> Location / Trip Tracking
 -> Trip Recovery + Stop Time
 -> Local Backup / Data Quality
 -> Map Display
 -> Analytics
```

---

## 11. 决策记录

### v1.5.0

- odometerKm 提升为 v0.2 P0
- 建立 ChargingRecord + Trip 的数据闭环原则
- 增加 DataSource / accuracy 数据可信度设计
- 增加 ChargingPlace 演进
- Local Backup / Restore 前移至云同步之前
- 行程增加中断恢复、停车时间和采样体积控制
- 新增 DATA_QUALITY_BACKUP 权威子文档

### v1.4.0

- 地图、定位、驾驶行程、车型目录和多车辆进入正式路线
- 新需求统一放到 v0.2，不扩张 v0.1
