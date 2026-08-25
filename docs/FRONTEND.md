# EV Charge Book 前端设计

版本: v1.1.0

更新时间: 2026-08-25

## 1. 技术方案

Android Native:

- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- ViewModel + StateFlow
- Room
- Coroutines

v0.1 暂不引入复杂依赖注入框架，优先保持工程简单可读；当模块数量和构造依赖明显增长时再评估 Hilt/Koin。

---

## 2. 前端职责

Android 端在 v0.1 同时承担:

- 页面与交互
- 表单校验
- 本地业务编排
- Room 数据读写
- 基础统计聚合

后端未来接入后，前端仍必须保持离线可用能力。

---

## 3. 推荐包结构

```text
com.assemblej.evchargebook
├── app
├── ui
│   ├── dashboard
│   ├── charging
│   ├── history
│   ├── vehicle
│   └── settings
├── data
│   ├── local
│   │   ├── entity
│   │   ├── dao
│   │   └── database
│   └── repository
├── domain
│   ├── model
│   └── usecase
└── common
```

v0.1 不强制每个功能都创建 UseCase；只有存在可复用业务逻辑时才提取。

---

## 4. 数据流

标准读取流:

```text
Compose UI
  -> ViewModel
  -> Repository
  -> Room DAO
  -> StateFlow
  -> Compose UI
```

标准写入流:

```text
用户表单
  -> UI 校验
  -> ViewModel
  -> Repository
  -> Room
  -> 数据流刷新
```

UI 不直接调用 DAO。

---

## 5. 页面状态

每个主要页面建议至少表达:

- Loading
- Content
- Empty
- Error

简单页面允许通过一个不可变 UiState data class 表达，而不是建立复杂状态机。

例如 DashboardUiState:

```text
vehicle
monthCost
monthEnergy
averagePrice
chargingCount
latestRecord
isLoading
errorMessage
```

---

## 6. Navigation

v0.1 一级导航:

- Dashboard
- Record
- Me

二级页面:

- AddChargingRecord
- ChargingDetail
- EditChargingRecord
- VehicleEditor
- History

Navigation 参数只传稳定标识符，例如 recordId / vehicleId，不直接传完整对象。

---

## 7. 表单规则

Charging Record 表单:

- SOC: Int 0..100
- energyKwh: Decimal >= 0
- cost: Decimal >= 0
- chargeTime: 必填
- endSoc >= startSoc 默认校验；特殊场景未来再放开

自动计算:

```text
pricePerKwh = cost / energyKwh
```

energyKwh 为 0 时不得执行除法。

---

## 8. 本地数据与统计

v0.1 统计优先通过 Room Query 或 Repository 内轻量聚合完成。

禁止为简单统计引入额外分析引擎。

基础统计:

- monthCost
- monthEnergy
- averagePrice
- chargingCount
- totalCost
- totalEnergy

---

## 9. 错误处理

- 数据库错误转换为用户可理解状态
- 表单校验错误不进入 Repository
- 不吞异常
- Debug 构建保留足够日志
- Release 不输出敏感信息

---

## 10. 测试基线

v0.1 至少覆盖:

- 充电记录字段校验
- 单价计算
- 月度费用聚合
- Repository 基础 CRUD

UI 自动化测试不是 v0.1 阻塞项，但关键表单应可稳定手工验收。

---

## 11. 性能原则

- 数据库查询避免主线程
- 列表使用 LazyColumn
- 页面只订阅所需状态
- 不进行无意义全表加载
- 本地记录规模较小时优先简单实现，不提前分页

---

## 12. 变更记录

### v1.1.0

- 明确 Android v0.1 技术栈和包结构
- 明确 StateFlow 单向数据流
- 明确导航参数、表单校验和统计边界
- 降低早期工程复杂度，不强制 DI / UseCase 形式化
