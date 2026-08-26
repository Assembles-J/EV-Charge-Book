# EV Charge Book 前端设计

版本: v1.3.0
更新时间: 2026-08-26

## 1. 技术方案

Android Native:

- Kotlin
- Jetpack Compose / Material 3
- ViewModel + StateFlow
- Room / SQLite
- DataStore（v0.2 selectedVehicleId）
- Coroutines
- Android Location API / foreground service（v0.2）
- MapLibre Native adapter（v0.2 地图展示）

当前包名 `com.evchargebook`。继续避免为早期功能引入不必要的 DI/微型 Clean Architecture 仪式。

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

Dashboard / Records / Stats 必须共享真实数据口径。

v0.1 剩余重点是充电记录完整编辑与 CI/APK 验收，新定位/地图需求不得阻塞 v0.1。

---

## 3. v0.2 模块扩展

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

## 4. 多车辆 App State

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

## 5. Vehicle Catalog

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

## 6. LocationProvider

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

## 7. TripTrackingService

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

## 8. Map Adapter

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

## 9. 充电表单

v0.1 字段保持时间、SOC、电量、费用、类型、地点、备注。

v0.2 增加:

- 当前车辆选择
- 使用当前位置

获取位置失败不得阻塞手工保存。

---

## 10. Trip 统计口径

- distance: 相邻有效坐标点累计
- elapsed time: endedAt - startedAt
- current/max speed: Location speed，过滤异常值
- average speed: 必须明确使用 elapsed time 或 moving time；第一版使用 elapsed time 更可解释
- altitude: 标记 GPS altitude；爬升量先平滑再计算

---

## 11. Build Baseline

当前已具备 Gradle Wrapper。CI / Release 应使用 `./gradlew`，Android SDK 与 `compileSdk` 保持一致。

---

## 12. 变更记录

### v1.3.0

- 增加 DataStore 当前车辆上下文
- 定义车型目录本地 seed 方案
- 定义 LocationProvider / TripTrackingService
- 定义 MapLibre 可替换 MapProvider
- 明确 WGS84、foreground location 和统计口径
- 更新构建基线为使用已提交 Gradle Wrapper

### v1.2.0

- 对齐 Room CRUD 和真实 Dashboard / Records / Stats
