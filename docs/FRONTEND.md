# EV Charge Book 前端设计

版本: v1.2.0

更新时间: 2026-08-25

## 1. 技术方案

Android Native:

- Kotlin
- Jetpack Compose
- Material 3
- ViewModel + StateFlow
- Room / SQLite
- Coroutines

当前实际包名统一使用 `com.evchargebook`。v0.1 不引入 Hilt/Koin，不为未来模块提前增加复杂依赖注入。

---

## 2. v0.1 当前实现阶段

UI 骨架已具备:

- Dashboard
- Records
- Add Record
- Stats
- Vehicle
- Bottom Navigation

下一业务基线从“静态页面”切换到“真实本地数据闭环”:

```text
Add Record Form
  -> validation
  -> ViewModel
  -> ChargingRepository
  -> Room DAO
  -> Flow refresh
  -> Dashboard / Records / Stats
```

禁止继续使用静态示例金额、固定日期、固定充电站作为正式业务数据。

---

## 3. 包结构

```text
com.evchargebook
├── MainActivity.kt
├── data
│   ├── entity
│   ├── dao
│   ├── database
│   └── repository
├── ui
│   ├── dashboard
│   ├── records
│   ├── stats
│   └── vehicle
└── viewmodel
```

v0.1 仅在出现可复用复杂业务逻辑时增加 domain/usecase。

---

## 4. 核心数据流

读取:

```text
Room Flow
 -> Repository
 -> MainViewModel
 -> MainUiState
 -> Compose
```

写入:

```text
AddRecordScreen
 -> validate
 -> MainViewModel.addChargingRecord()
 -> Repository.insert()
 -> Room
```

UI 不直接持有 DAO 或 Database。

---

## 5. MainUiState

v0.1 采用单一轻量状态承载跨页面核心数据:

- vehicle
- chargingRecords
- monthCost
- monthEnergy
- averagePrice
- chargingCount
- totalCost
- totalEnergy
- isLoading
- errorMessage

Dashboard 与 Stats 的数字必须从同一数据源聚合，避免页面口径不一致。

---

## 6. 充电记录表单

必填:

- chargeTime
- startSoc
- endSoc
- energyKwh
- cost

可选:

- location
- chargerType
- remark

校验:

- SOC: 0..100
- endSoc >= startSoc
- energyKwh > 0
- cost >= 0

派生值:

```text
pricePerKwh = cost / energyKwh
```

页面保存成功后返回 Records/Dashboard；失败则保留用户已输入内容并展示错误。

---

## 7. Records

v0.1 必须支持:

- 按充电时间倒序
- 查看地点 / SOC / 电量 / 费用 / 单价
- 删除

编辑详情可在 CRUD 基线后补齐，但删除必须进入 v0.1。

---

## 8. Dashboard

v0.1 首屏只显示可由真实数据可靠计算的内容:

- 当前车辆
- 本月费用
- 本月充电量
- 平均电价
- 充电次数
- 最近 3 条记录

“实时 SOC / 剩余续航 / 电池健康度”在没有车机或人工录入数据源前不得伪装成实时数据。

---

## 9. Stats

v0.1 只做基础统计:

- 累计费用
- 累计充电量
- 平均电价
- 本月费用
- 本月充电量

趋势图进入 v0.2；v0.1 可以保留明确标记为“即将支持”的占位区域，但不得展示伪造曲线。

---

## 10. Vehicle

v0.1 至少支持一个车辆档案:

- brand
- model
- batteryCapacityKwh
- rangeKm

默认测试数据可以用于首次启动初始化，但用户修改后必须由 Room 持久化。

---

## 11. 构建基线

Android 工程必须具备:

- root `build.gradle.kts`
- `app/build.gradle.kts`
- AndroidManifest
- 可解析依赖
- Debug assemble green

CI 使用固定 Gradle 版本执行，Gradle Wrapper 后续补齐但不阻塞第一次远程构建验证。

---

## 12. 测试基线

至少覆盖:

- SOC 校验
- 单价计算
- 月度费用/电量聚合
- Repository insert/delete

---

## 13. 变更记录

### v1.2.0

- 对齐本地提交后的 Dashboard / Records / Stats / Vehicle UI
- 正式进入 Room CRUD + 动态统计业务阶段
- 统一实际包名 `com.evchargebook`
- 禁止正式页面继续展示伪造实时数据和伪造统计
- 明确 v0.1 MainUiState 与页面数据口径

### v1.1.0

- 明确 Android v0.1 技术栈、数据流、导航和表单规则
