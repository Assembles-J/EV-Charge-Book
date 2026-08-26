# EV Charge Book 前端设计

版本: v1.4.0
更新时间: 2026-08-26

## 1. 技术方案

Android Native:

- Kotlin
- Jetpack Compose / Material 3
- ViewModel + StateFlow
- Room / SQLite
- Coroutines
- Repository Gradle Wrapper
- DataStore（v0.2 selectedVehicleId）
- Android Location API / foreground service（v0.2）
- MapLibre Native adapter（v0.2 地图展示）

当前包名 `com.evchargebook`。继续避免为早期功能引入不必要的 DI 或形式化 Clean Architecture 层级。

---

## 2. v0.1 当前数据流

```text
Compose
 -> MainViewModel
 -> ChargingRepository
 -> Room DAO
 -> Flow
 -> UI
```

Dashboard / Records / Stats 共享同一真实数据口径。

v0.1 已完成：

- 车辆读取 / 编辑 / 保存
- 充电记录新增 / 编辑 / 删除
- 日期 / 时间选择
- charger type / remark
- 删除确认
- success / error Snackbar
- empty state
- Material 3 theme
- edge-to-edge
- ChargingRecordRules
- ChargingStatistics
- domain rules / statistics unit tests

当前前端阶段不是继续扩功能，而是通过 CI / APK / 真机走查验证完整闭环。

---

## 3. v0.1 页面状态

### Dashboard

- 真实车辆信息
- 本月费用 / 电量 / 次数 / 平均电价
- Energy Hero 使用月充电量与车辆电池容量做等效展示
- 最近充电记录
- 无记录时 EmptyState

Energy Hero 是展示型派生指标，不代表车辆实时 SOC、剩余电量或 SOH。

### Records

- Room 实时记录列表
- 新增入口
- 点击进入编辑
- 删除前确认

### Record Edit / Add

字段：

- charge time
- start SOC
- end SOC
- energy kWh
- final paid cost
- charger type
- location
- remark

保存前统一通过 ChargingRecordRules 校验。

### Vehicle

- 读取 Room Vehicle
- 编辑品牌 / 车型 / 电池容量 / 标称续航
- 不展示无法从当前数据源获得的实时 SOC / SOH / 实时续航

---

## 4. Domain Rules

v0.1 已将核心规则从 UI / Repository 内联判断中抽出。

ChargingRecordRules 负责：

- startSoc 0..100
- endSoc 0..100
- endSoc >= startSoc
- energyKwh > 0
- cost >= 0

ChargingStatistics 负责基础聚合：

- monthCost
- monthEnergy
- chargingCount
- totalCost
- totalEnergy
- averagePrice

UI 不重复实现这些计算规则。

---

## 5. v0.2 模块扩展

推荐新增:

```text
com.evchargebook
├── data
│   ├── local
│   │   ├── trip
│   │   └── catalog
│   └── repository
│       ├── VehicleRepository
│       └── TripRepository
├── location
│   ├── LocationProvider
│   ├── AndroidLocationProvider
│   └── TripTrackingService
├── map
│   ├── MapProvider
│   └── MapLibreProvider
└── ui
    ├── trip
    └── vehiclecatalog
```

接口以实际替换需求为目的，不创建多层空抽象。

---

## 6. 多车辆 App State

v0.2 引入统一 `selectedVehicleId`。

```text
DataStore selectedVehicleId
 -> VehicleRepository
 -> App/ViewModel State
 -> Dashboard / Records / Stats / Add Record / Trip
```

页面不各自保存当前车辆。

任何新增充电/行程都必须显式绑定 vehicleId。

---

## 7. Vehicle Catalog

车型目录与 UserVehicle 分离。

v0.2 首先采用本地版本化 JSON/Room seed，实现离线搜索，不让 App 依赖在线免费 API 才能添加车辆。

流程:

```text
Catalog Search
 -> Select model
 -> User confirms/overrides values
 -> Save UserVehicle snapshot
```

必须保留 Custom Vehicle fallback。

---

## 8. LocationProvider

定位核心定义最小接口:

- getCurrentLocation()
- startUpdates(config)
- stopUpdates()

核心业务使用统一 LocationSample，不让 ViewModel 直接依赖地图 SDK 的 location object。

建议 LocationSample 包含:

- lat/lng
- altitude?
- speed?
- bearing?
- accuracy
- timestamp

原始数据库坐标统一 WGS84。

---

## 9. TripTrackingService

v0.2 使用用户主动启动的 location foreground service。

职责:

- 启动/结束 TripSession
- 订阅定位更新
- 过滤明显无效点
- 批量写入 TripPoint
- 更新 ongoing notification
- 处理进程/服务异常结束状态

UI 不承担持续采样逻辑。

Android 10+ 声明 `foregroundServiceType="location"`；持续记录过程中必须有通知。

v0.2 不默认申请“永久后台自动位置追踪”来做自动开车识别。

---

## 10. Map Adapter

地图只消费已有轨迹数据:

```text
TripPoint Flow/List
 -> MapProvider
 -> Polyline / Start / End
```

MapLibre 作为首选开源实现；tile/style provider 单独配置。

地图失败不影响 Trip 数据读取、统计和删除。

高德等中国地图可作为后续 adapter，不能把其 SDK 类型渗透到 Room/Repository。

---

## 11. Build Baseline

当前已具备 Gradle Wrapper。CI / Release 使用 `./gradlew`，Android SDK 与 `compileSdk` 保持一致。

当前验收优先级：

1. CI Green
2. Debug APK Artifact
3. 真机安装和业务走查
4. assembleRelease
5. signed production publish

---

## 12. 变更记录

### v1.4.0

- 对齐 commit `7204c56` 的完整 Record CRUD 与 UI 重构
- 记录 ChargingRecordRules / ChargingStatistics domain 抽取
- 增加删除确认、Snackbar、EmptyState、日期时间和充电类型
- 明确 Energy Hero 不是实时电池状态
- 前端阶段切换为 CI / APK / 真机验收

### v1.3.0

- 增加 DataStore 当前车辆上下文
- 定义车型目录本地 seed 方案
- 定义 LocationProvider / TripTrackingService
- 定义 MapLibre 可替换 MapProvider
- 明确 WGS84、foreground location 和统计口径
- 更新构建基线为使用已提交 Gradle Wrapper

### v1.2.0

- 对齐 Room CRUD 和真实 Dashboard / Records / Stats
