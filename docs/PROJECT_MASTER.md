# EV Charge Book 项目总纲（PROJECT MASTER）

版本: v1.4.0
更新时间: 2026-08-26
状态: Authority Document / Single Source of Truth

## 1. 项目定位

EV Charge Book 是新能源车主的 Local First 车辆数据中心。

演进顺序:

1. 充电记账与成本
2. 多车辆与车型目录
3. 位置 / 驾驶行程数据
4. 数据分析
5. 云同步与 AI

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

实现与文档冲突时先更新文档再继续实现。

---

## 3. 产品原则

- 简单可维护
- Local First
- 真实数据来源
- 用户录入成本低
- 统计口径可解释
- 不伪造实时 SOC / SOH / 续航
- 定位记录与地图 SDK 解耦
- 车型目录与用户车辆分离
- 关键外部供应商必须可替换

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

新增的地图、驾驶轨迹、车型库、多车辆不得阻塞 v0.1 发布。

---

## 5. 下一阶段: v0.2 Vehicle & Trip Foundation

### 多车辆 / 车型目录

- UserVehicle 支持多辆车
- selected/default vehicle
- 本地车型目录 + 自定义兜底
- Dashboard / Records / Stats 按车辆隔离

### 定位 / 地图

- Android Location API 为记录核心
- 原始坐标统一 WGS84
- MapLibre 为首选地图渲染 adapter
- map tile/style provider 与记录引擎分离
- 高德等供应商仅作为可选 adapter

### 行程

采用“用户主动开始 + location foreground service”。

记录:

- 经纬度
- GPS 海拔及精度
- speed / bearing
- 时间

形成:

- 距离
- 耗时
- 平均 / 最高速度
- 轨迹地图

第一版不做无提示后台自动追踪。

---

## 6. 车型覆盖原则

目标是持续提高新能源车型覆盖率，但不承诺一个免费第三方 API 永久覆盖“所有电车”。

采用:

```text
VehicleCatalog reference data
   +
UserVehicle snapshot/override
   +
Custom Vehicle fallback
```

官方公开车型/产品公告可作为目录构建来源之一，但必须经过面向消费者的车型归一化。

---

## 7. Android 构建基线

当前工程已经提交 Gradle Wrapper。

CI / Release 使用:

- JDK 17
- Android SDK 36（与当前 compileSdk 一致）
- Build Tools 36.0.0
- repository Gradle Wrapper

2026-08-26 已确认此前最新 CI 失败发生在 SDK 安装阶段：workflow 仍请求不存在的 `platforms;android-37`。该配置已修正为 SDK 36；新的 CI 结果继续由 Issue #7 验收。

---

## 8. 发布基线

继续遵循 Assembles-J / Third-Hand 同类逻辑:

- CI 与 Production Release 分离
- production Environment
- signed APK
- Actions Artifact
- `/opt/ev-charge-book/releases`
- `.part` + SHA / apksigner + atomic activation
- latest 只在成功后切换

---

## 9. 当前执行顺序

```text
Charging Record full CRUD
 -> Android CI Green
 -> Debug APK
 -> Signed Production APK
 -> v0.1 Acceptance
 -> Multi Vehicle / Vehicle Catalog
 -> Location / Trip Tracking
 -> Map Display
 -> Analytics
```

---

## 10. 决策记录

### v1.4.0

- 完成一次文档 / PR / Issue 状态对账
- 地图、定位、驾驶行程、车型目录和多车辆进入正式产品路线
- 新需求统一放到 v0.2，不扩张 v0.1
- 定位记录与地图展示解耦，MapLibre 为首选开源渲染 adapter
- 行程第一版采用用户主动开始 + foreground service
- 车型采用 Catalog + UserVehicle snapshot + Custom fallback
- CI 与 compileSdk 统一到 Android SDK 36，并恢复使用已提交 Gradle Wrapper

### v1.3.0

- Room / Repository / ViewModel / 真实统计落地
- 禁止伪造实时车辆数据
