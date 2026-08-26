# EV Charge Book Database Design

Version: v1.3.0
更新时间: 2026-08-26

## 1. Design Principle

Local First。Room/SQLite 保存用户真实业务数据；统计值优先动态聚合。车型目录属于参考数据，用户车辆属于业务数据，两者分离。

---

## 2. UserVehicle / Vehicle

当前 `vehicles` 在 v0.1 已存在:

- id
- brand
- model
- batteryCapacityKwh
- rangeKm

v0.2 演进字段:

- catalogVehicleId: String? nullable
- nickname: String?
- isDefault: Boolean
- isArchived: Boolean
- createdAt: Long

车型目录更新不得自动覆盖用户车辆参数。

---

## 3. VehicleCatalog

v0.2 新增本地参考表或 seed repository:

- catalogId: String PK
- source
- sourceModelCode?
- brand
- series
- modelName
- modelYear?
- trimName?
- powertrainType
- batteryCapacityKwh?
- rangeKm?
- rangeStandard?
- batteryChemistry?
- manufacturer?
- isActive
- sourceUpdatedAt?

VehicleCatalog 可重建/更新；UserVehicle 不可因目录刷新丢失。

---

## 4. ChargingRecord

现有核心字段保持:

- id
- vehicleId
- chargeTimeEpochMillis
- startSoc / endSoc
- energyKwh
- cost
- chargerType?
- location?
- remark?

v0.2 可选增加:

- latitude: Double?
- longitude: Double?
- locationAccuracyMeters: Float?

原始坐标统一保存 WGS84；地图 adapter 需要其他坐标系时只在显示层转换。

所有充电查询必须支持按 vehicleId 过滤。

---

## 5. TripSession

v0.2 新增:

- id: Long PK
- vehicleId: Long
- startedAtEpochMillis: Long
- endedAtEpochMillis: Long?
- distanceMeters: Double
- elapsedSeconds: Long
- averageSpeedMps: Double?
- maxSpeedMps: Double?
- startLatitude / startLongitude: Double?
- endLatitude / endLongitude: Double?
- startAltitudeMeters: Double?
- endAltitudeMeters: Double?
- minAltitudeMeters: Double?
- maxAltitudeMeters: Double?
- status: RECORDING / COMPLETED / INTERRUPTED

汇总字段只在行程结束时落盘，原始依据保存在 TripPoint。

---

## 6. TripPoint

- id: Long PK
- tripId: Long
- capturedAtEpochMillis: Long
- latitude: Double
- longitude: Double
- altitudeMeters: Double?
- speedMps: Float?
- bearingDegrees: Float?
- horizontalAccuracyMeters: Float?
- verticalAccuracyMeters: Float?
- speedAccuracyMps: Float?
- provider: String?

索引:

- tripId
- capturedAtEpochMillis

删除 TripSession 时应级联或由 Repository 显式删除 TripPoint。

GPS 海拔为原始传感/定位数据，爬升量等指标必须经过过滤/平滑后再计算。

---

## 7. Relationships

```text
VehicleCatalog 0..1 ---- 1 UserVehicle

UserVehicle 1 ---- N ChargingRecord
UserVehicle 1 ---- N TripSession
TripSession  1 ---- N TripPoint
```

---

## 8. Multi-Vehicle Query Rule

v0.2 起任何 Dashboard / Records / Stats / Trip 查询必须明确数据范围:

- selectedVehicleId
- 或显式 allVehicles

禁止默认把所有车辆数据混合后冒充当前车辆数据。

当前车辆 ID 建议保存在 DataStore，不作为业务表重复字段。

---

## 9. v0.1 Statistics

保持:

- monthCost
- monthEnergy
- chargingCount
- totalCost
- totalEnergy
- averagePrice

`pricePerKwh` 继续作为派生值，不持久化。

---

## 10. Migration Rule

数据库 schema 变化必须:

1. 更新 DATABASE.md
2. 提升 Room database version
3. 增加 Migration
4. 增加迁移测试
5. Release 禁止 destructive migration

Trip/VehicleCatalog 进入实现时必须先提供对应 Migration，而不是删除用户现有 v0.1 数据。

---

## 11. 变更记录

### v1.3.0

- 增加 VehicleCatalog / UserVehicle 分层
- 增加多车辆状态字段
- 增加 ChargingRecord 可选经纬度
- 定义 TripSession / TripPoint
- 明确 WGS84 原始坐标和多车辆查询范围

### v1.2.0

- 落地 v0.1 Room schema、统计口径和 Migration 规则
