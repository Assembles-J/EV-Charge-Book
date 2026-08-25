# EV Charge Book Database Design

Version: v1.2.0

更新时间: 2026-08-25

## 1. Design Principle

Local First。

v0.1 Android Room/SQLite 是唯一事实数据源；统计值优先动态聚合，不提前持久化冗余汇总表。

---

## 2. Vehicle

表名: `vehicles`

字段:

- `id: Long` PK autoGenerate
- `brand: String`
- `model: String`
- `batteryCapacityKwh: Double`
- `rangeKm: Int`

约束:

- brand/model 非空
- batteryCapacityKwh > 0
- rangeKm > 0

v0.1 支持单车优先，但模型保持一对多扩展能力。

---

## 3. ChargingRecord

表名: `charging_records`

字段:

- `id: Long` PK autoGenerate
- `vehicleId: Long`
- `chargeTimeEpochMillis: Long`
- `startSoc: Int`
- `endSoc: Int`
- `energyKwh: Double`
- `cost: Double`
- `chargerType: String?`
- `location: String?`
- `remark: String?`

派生值 `pricePerKwh` 不持久化，读取时按 `cost / energyKwh` 计算，避免修改费用或电量后出现数据不一致。

约束:

- startSoc/endSoc: 0..100
- endSoc >= startSoc
- energyKwh > 0
- cost >= 0

索引:

- `vehicleId`
- `chargeTimeEpochMillis`

默认查询按 `chargeTimeEpochMillis DESC`。

---

## 4. v0.1 Statistics

不创建 `CostSummary` 实体。

由 DAO/Repository 动态聚合:

- monthCost = SUM(cost)
- monthEnergy = SUM(energyKwh)
- chargingCount = COUNT(*)
- totalCost = SUM(cost)
- totalEnergy = SUM(energyKwh)
- averagePrice = totalCost / totalEnergy

月度边界由应用层计算 `[monthStart, nextMonthStart)` 后传入 DAO。

---

## 5. Relationship

```text
Vehicle 1
   |
   | N
ChargingRecord
```

v0.1 可不启用 SQLite 外键级联，删除车辆前必须由业务层处理关联记录；云同步阶段再评估严格外键策略。

---

## 6. Future Entities

以下实体不进入 v0.1 Room 基线:

- DrivingRecord
- BatteryHealth
- CostSummary persisted table
- User / Sync metadata

进入对应版本前必须先更新本文件。

---

## 7. Migration Rule

数据库版本从 `1` 开始。

任何字段删除、重命名、类型修改必须:

1. 更新 DATABASE.md 版本
2. 增加 Room Migration
3. 增加迁移测试
4. 禁止 Release 使用 destructive migration

开发早期 Debug 可临时重装应用，但不能把 destructive migration 当正式迁移方案。

---

## 8. 变更记录

### v1.2.0

- 将设计落到 v0.1 可实现 Room schema
- ChargingRecord 增加 vehicleId / chargeTime / chargerType / remark
- pricePerKwh 改为派生值
- 明确 v0.1 不持久化 CostSummary
- 明确统计口径、索引和 Migration 规则

### v1.1.0

- 建立 Vehicle / ChargingRecord / DrivingRecord / BatteryHealth / CostSummary 初始模型
