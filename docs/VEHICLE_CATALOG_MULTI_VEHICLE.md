# EV Charge Book Vehicle Catalog & Multi-Vehicle Design

版本: v1.0.0
更新时间: 2026-08-26
状态: Authority Subdocument

## 1. 目标

让用户不必手工填写全部车辆参数，同时支持一个用户管理多辆新能源车，并保证车型库更新不会破坏用户已有记录。

---

## 2. “所有电车”需求评审

产品目标可以是“覆盖主流及持续扩充的在售/历史新能源车型”，但不能承诺通过一个免费 API 永久、完整、实时覆盖市场上所有车型。

原因:

- 车型、年款、配置版持续变化
- 同一营销车型存在不同电池/续航配置
- 官方监管产品型号与消费者看到的品牌/车系/配置名并非一一对应
- 工信部公开车辆产品公告是权威数据来源之一，但不是面向 App 的稳定车型查询 API

因此采用“车型目录 + 用户自定义兜底”。

---

## 3. 数据分层

### VehicleCatalog

只读参考数据，可随版本或后续网络目录更新:

- catalogId
- source
- sourceModelCode
- brand
- series
- modelName
- modelYear
- trimName
- powertrainType (BEV / PHEV / REEV)
- batteryCapacityKwh?
- rangeKm?
- rangeStandard? (CLTC/WLTC/etc)
- batteryChemistry?
- manufacturer?
- isActive
- sourceUpdatedAt

### UserVehicle

用户真实车辆档案（现有 `VehicleEntity` 的演进方向）:

- id
- catalogVehicleId? nullable
- nickname?
- brandSnapshot
- modelSnapshot
- batteryCapacityKwh
- rangeKm
- isDefault
- isArchived
- createdAt

用户从车型库选择后，将关键参数快照到 UserVehicle。车型目录以后更新，不自动改写用户历史车辆参数。

始终提供“找不到车型 / 自定义车辆”。

---

## 4. 车型目录来源策略

### v0.2

优先使用仓库内版本化 JSON/Room seed 数据:

```text
assets/vehicle_catalog.json
 -> first-run import
 -> local search
```

先覆盖常见新能源品牌和车型，保证离线可用、没有 API 成本、结果可测试。

### 后续

建立目录更新工具，参考:

- 工信部道路机动车辆生产企业及产品公告
- 新能源车型公开目录/官方品牌资料

监管数据需要做消费者车型名归一化，不直接把原始公告当 UI 车型列表。

云端目录更新应晚于 v0.2 本地目录验证。

---

## 5. 车辆选择 UX

新增车辆:

```text
选择品牌
 -> 选择车系
 -> 年款/配置
 -> 展示可用参数
 -> 用户确认/修改
 -> 保存为 UserVehicle
```

搜索支持品牌/车系/车型关键字。

底部始终提供:

`没有找到？自定义添加车辆`

不要求用户提供 VIN 才能使用 App。

---

## 6. 多车辆设计

### Dashboard

顶部当前车辆改为可切换 Vehicle Switcher:

```text
[ C16 ▼ ]
```

切换后 Dashboard、Records、Stats 默认只显示该车数据。

可提供“全部车辆”汇总视图，但必须明确标记，禁止把多个车辆的数据无提示混在一起。

### Vehicle 页面

```text
我的车辆

[默认] 零跑 C16
        67.7 kWh

       小米 SU7
        73.6 kWh

[ + 添加车辆 ]
```

操作:

- 设为当前/默认车辆
- 编辑车辆
- 归档车辆
- 新增车辆

### Charging Record

新增充电记录自动关联当前车辆；表单顶部允许切换目标车辆。

### Trip

每次 TripSession 必须绑定 `vehicleId`，开始行程前显示当前车辆。

---

## 7. 删除与归档

有历史充电或行程数据的车辆默认不提供直接物理删除。

优先使用 `isArchived`:

- 不再出现在默认切换器
- 历史数据仍可查询
- 可恢复

真正删除时需要明确处理关联 ChargingRecord / TripSession。

---

## 8. 当前车辆状态

当前选择的 `vehicleId` 不放在每个页面单独维护。

v0.2 建议使用 DataStore 保存 `selectedVehicleId`，并由统一 VehicleRepository / app state 提供当前车辆。

所有查询必须显式按 vehicleId 过滤。

---

## 9. 验收目标

- [ ] 车型目录本地搜索
- [ ] 品牌 -> 车系 -> 年款/配置选择
- [ ] 自定义车辆兜底
- [ ] 添加至少两辆车辆
- [ ] 当前车辆切换
- [ ] Dashboard/Records/Stats 按车辆隔离
- [ ] Add Charging Record 可指定车辆
- [ ] 默认车辆持久化
- [ ] 车辆归档不丢历史记录
- [ ] TripSession 与车辆绑定

---

## 10. 变更记录

### v1.0.0

- 建立车型目录与多车辆权威设计
- 采用 Catalog + UserVehicle snapshot 模型
- 明确不承诺一个免费 API 覆盖所有车型
- 明确本地目录、自定义兜底、车辆切换与归档规则
