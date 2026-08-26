# EV Charge Book 项目总纲（PROJECT MASTER）

版本: v1.6.0
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

## 4. 当前阶段: v0.1 Acceptance

v0.1 核心业务闭环已经完成实现：

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
- CI / Release / atomic deploy 基线

当前不再继续扩 v0.1 功能，进入验收门禁：

```text
Android CI Green
 -> Debug APK Artifact
 -> Physical Device Walkthrough
 -> assembleRelease Green
 -> Signed Production APK
 -> v0.1 Release Accepted
```

仅修复阻塞构建、安装、数据正确性和发布的问题。

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

### P0.5: Local Backup / Restore

在多车辆、地图、行程等功能继续扩展前，先保证用户本地数据可恢复：

- backup format 有版本号
- 备份包含 Vehicle / ChargingRecord
- 导出前后进行记录数 / 核心字段校验
- restore 明确覆盖 / 合并策略
- 破坏性恢复必须二次确认

### P1: 多车辆 / 车型目录

- UserVehicle 支持多辆车
- selected/default vehicle
- 本地车型目录 + 自定义兜底
- Dashboard / Records / Stats 按车辆隔离

### P2: 定位 / 行程

- Android Location API 为记录核心
- WGS84 原始坐标
- MapLibre 为地图渲染 adapter
- 用户主动开始 + foreground service
- 记录距离、时间、速度、GPS 海拔和 accuracy
- 区分 elapsed / moving / stopped time
- 支持 INTERRUPTED 行程恢复
- 控制采样频率、电量和数据库体积

### P2: 数据可靠性

- 关键数据标记 DataSource
- accuracy 使用系统真实精度字段
- 确定性规则优先发现异常
- ChargingPlace 支持常用地点分类

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
CI Green
 -> Debug APK Artifact
 -> Physical Device Acceptance
 -> Signed Production APK
 -> v0.1 Release Accepted
 -> odometerKm
 -> Local Backup / Restore
 -> Multi Vehicle / Vehicle Catalog
 -> Location / Trip Tracking
 -> Trip Recovery + Stop Time
 -> Data Quality / ChargingPlace
 -> Map Display
 -> Analytics
```

---

## 11. 决策记录

### v1.6.0

- commit `7204c56` 完成充电记录完整编辑与主要 UX 收口
- v0.1 正式从功能开发阶段切换到 Acceptance 阶段
- 当前 P0 为 CI Green、Debug APK 与真机走查
- v0.2 第一优先级固定为 odometerKm，随后 Local Backup / Restore
- 地图 / 行程继续后置，避免提前扩大复杂度

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
