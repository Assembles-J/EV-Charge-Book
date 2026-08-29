# EV Charge Book 前端设计

版本: v1.5.0
更新时间: 2026-08-29
状态: Authority Document

## 1. 技术方案

Android Native:

- Kotlin
- Jetpack Compose / Material 3
- ViewModel + StateFlow
- Room / SQLite
- Coroutines
- DataStore
- Android LocationManager / foreground service
- Repository Gradle Wrapper

当前包名 `com.evchargebook`。

架构继续保持单体、简单可维护，不为当前规模引入 Hilt/Koin、多 module Clean Architecture、第二套 Trip tracking service 或 WorkManager tracking。

---

## 2. 当前主数据流

```text
Compose UI
 -> MainViewModel
 -> ChargingRepository / domain rules
 -> Room DAO
 -> Flow / StateFlow
 -> UI
```

当前车辆上下文通过统一状态/DataStore 管理；Dashboard / Records / Stats / Trip / Add Record 不各自维护一份 selected vehicle。

Trip tracking 的持续定位不放进 Composable/ViewModel 循环，而由 foreground service 负责。

---

## 3. 当前一级 UI

```text
Dashboard
Records
Stats
Trip
Vehicle
```

Dark First 为默认视觉语言，Light mode 可切换。核心 UI token 由 `EVDesignTokens` / MaterialTheme 统一提供。

页面安全区由 root app/scaffold 统一拥有；嵌套 Trip Scaffold/TopAppBar 不重复申请系统顶部 inset。

---

## 4. Dashboard / Records / Stats

### Dashboard

- dynamic Vehicle Hero
- current VehicleState SOC / mileage
- latest valid completed Trip summary
- recent charging evidence
- generated Hero artwork slot

Hero 不伪造实时 SOC / remaining range，不在 Compose 里重建复杂极光/倒影。

### Records

- compact ledger summary
- chronological charging timeline
- row edit
- explicit delete + confirmation
- Add/Edit Charge 共用当前真实业务规则

### Stats

- current month summary
- previous-month comparison
- Trip SOC-derived energy estimate
- monthly trends / mix / places
- lifetime / interval evidence

UI 不自行重新计算已经由 domain/repository 拥有的业务统计。

---

## 5. Trip 数据与服务边界

### 5.1 Room facts

Trip 持久化核心仍是:

- `TripSession`
- `TripPoint`
- diagnostic events / runtime evidence

原始 GPS facts 与派生统计分离；raw point 不因为派生统计过滤而被静默重写。

### 5.2 `TripTrackingService`

当前 foreground service 负责:

- start/resume active Trip tracking
- LocationManager callback registration
- provider / permission health checks
- Location callback -> sampling/trust rules
- TripPoint persistence
- elapsed / distance / moving / stopped aggregates
- ongoing notification
- interrupted state / service diagnostic evidence

当前 callback baseline:

- request interval ~4s
- `SAMPLE_DISTANCE_METERS = 0f`
- callback liveness 保持 time-based
- stationary Room write 仍由 `TripSamplingRules` 约 15s 节流

不要重新在 LocationManager 层加 8m 之类的大位移门槛，否则会破坏 stationary heartbeat 与 callback health 诊断。

#77 仍只负责真实 Android/ROM 后台 callback 验收。

---

## 6. Trip UI 组件边界

Trip 当前已经从早期单页结构拆分为稳定的 v0.6 状态/阅读面。

### `TripReadyScreen`

拥有 no-active Trip 的两阶段入口:

1. Trip home/history
2. READY preparation

进入 Trip 默认显示 history；用户明确点击 `开始行程` 才进入 READY。

READY back 不创建空 Trip。

### `TripScreen`

拥有 active/interrupted cockpit:

- distance + trusted latest speed 主指标
- elapsed / moving average / max recorded speed / start SOC 辅助指标
- active truthful route/trends
- interrupted resume
- slide-to-end

active Trip 始终绑定启动时的 `vehicleId`，之后切换 selected vehicle 不重绑当前 Trip。

### completion surface

slide-to-end 进入唯一 Trip completion form:

- GPS distance + start SOC/mileage evidence
- end SOC required
- end mileage optional / validated
- SOC-derived energy estimate when trustworthy
- `继续行驶`
- `保存并结束`

不要重新加入通用 intermediate `AlertDialog` 作为第二次“是否结束”确认。

### `TripDetailScreenV06`

完成/选中 Trip detail 当前分为:

- `概览`: summary + endpoint card
- `轨迹`: route + speed/altitude trends
- `数据`: altitude/reliability + raw-point disclosure

默认 `概览`。section state 是 presentation state，不写回 Trip 数据。

不增加当前产品不支持的 `充电`、`备注`、destination planning 或 fake map tab。

---

## 7. Trip route / trend rendering

当前 route renderer 直接消费真实 TripPoint 派生 geometry，不要求外部地图 SDK。

规则:

- WGS84 原始坐标为事实
- LONG_GAP segment 断开
- gap 两端不计可信连续距离
- trusted speed 可用于 route color
- untrusted/unknown speed 使用中性语义
- completed endpoint 使用 red flag
- active latest point 保持 green current point
- trend >2min gaps 不插值
- speed / altitude trend 提供稀疏 X/Y reference

MapLibre 仍是未来可选 renderer。当前代码不应假装 MapLibre 已是 Trip 主依赖，也不应为了地图外观提前加入道路吸附。

---

## 8. Location / address frontend boundary

`LocationProvider` 与 `AddressResolver` 分离。

前端语义:

- coordinates 先作为事实保存
- reverse geocode 是 optional enhancement
- Geocoder 失败仍允许保存/继续业务
- 地址不可用时显示真实 fallback，不编造 place name

Location permission/provider 丢失时 active Trip 进入 `INTERRUPTED`，修复后由用户显式 resume。

---

## 9. Notification / deep link

Trip ongoing notification 当前承担状态可见性:

- elapsed time
- trusted persisted distance
- active Trip deep link

repair notification 用于 permission/provider interruption。

notification permission 被拒绝不能成为 Trip tracking 的业务阻塞；notification 也不能绕过 completion form 直接结束 Trip。

---

## 10. Responsive / accessibility baseline

当前 UI code 需要遵守:

- >=48dp interaction target where applicable
- icon-only action 提供 contentDescription/语义
- 320-360dp 不崩溃、不裁切关键操作
- fontScale 1.3 可用
- 关键状态不能只靠颜色表达
- Dark/Light 均需实际设备对比

active cockpit 的 metric grid 会在窄屏/大字体下降低列数，而不是缩小字体逃避布局问题。

---

## 11. Build / acceptance baseline

开发顺序:

1. current-head Android test/build
2. Android CI Green
3. Debug APK
4. owning Issue/PR 状态同步
5. physical function/visual acceptance
6. Production Release（需要下发 APK 时）

CI Green 不等于 Trip 真机验收。

当前 Trip UI code-side baseline 已完成；physical owners 主要是 #145 / #168 / #178 / #77 / #42 / #22。

---

## 12. Future map / sync boundary

### Map

可选未来方向:

- `MapProvider`
- MapLibre renderer
- China map adapter

只有当真实地图体验有明确价值时推进。地图 provider 类型不得渗透到 Room/Trip domain facts。

### Sync

第一批 cloud sync 仍只考虑 Vehicle + ChargingRecord。TripSession / TripPoint 不进入最小 v0.4 cloud slice。

---

## 13. 变更记录

### v1.5.0

- 对齐当前五一级页面与 Dark First v0.5/v0.6 UI
- 记录真实 `TripTrackingService` time-based callback + app-level stationary throttle baseline
- 对齐 `TripReadyScreen` / active `TripScreen` / completion / `TripDetailScreenV06` 职责
- 记录 completed detail `概览 / 轨迹 / 数据` 三 section
- 移除“MapLibre 已是当前 Trip 实现”的过时表述，恢复 optional renderer 边界
- 明确 CI 与 physical Trip acceptance 的区别

### v1.4.0

- 对齐早期完整 Record CRUD 与 UI 重构
- 记录 ChargingRecordRules / ChargingStatistics domain 抽取
- 增加删除确认、Snackbar、EmptyState、日期时间和充电类型
- 明确 Energy Hero 不是实时电池状态

### v1.3.0

- 增加 DataStore 当前车辆上下文
- 定义车型目录本地 seed 方案
- 定义 LocationProvider / TripTrackingService
- 明确 WGS84、foreground location 和统计口径
