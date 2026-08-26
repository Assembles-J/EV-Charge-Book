# EV Charge Book Database Design

Version: v1.4.0
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

高价值演进字段:

- odometerKm: Double? — 用于两次充电之间真实行驶里程和百公里成本/电量分析
- latitude: Double?
- longitude: Double?
- locationAccuracyMeters: Float?
- dataSource: String? — MANUAL / OCR / VEHICLE_API 等，在对应功能进入实现时启用

`odometerKm` 不强制进入 v0.1 发布，但应优先于复杂 Analytics 落地。

规则:

- 同一车辆新录入 odometerKm 低于上一条可靠里程时提示检查
- 不因异常提示直接丢弃用户记录
- 原始坐标统一保存 WGS84
- 所有充电查询必须支持按 vehicleId 过滤

---

## 5. ChargingPlace（后续）

当地点复用需求稳定后再增加:

- id
- name
- type: HOME / WORK / PUBLIC / HIGHWAY / OTHER
- latitude?
- longitude?
- note?

ChargingRecord 现阶段仍保留 location snapshot，未来即使关联 ChargingPlace 也不得依赖地点表才能展示历史记录。

---

## 6. TripSession

v0.2 新增:

- id: Long PK
- vehicleId: Long
- startedAtEpochMillis: Long
- endedAtEpochMillis: Long?
- distanceMeters: Double
- elapsedSeconds: Long
- movingSeconds: Long?
- stoppedSeconds: Long?
- averageSpeedMps: Double?
- maxSpeedMps: Double?
- startLatitude / startLongitude: Double?
- endLatitude / endLongitude: Double?
- startAltitudeMeters: Double?
- endAltitudeMeters: Double?
- minAltitudeMeters: Double?
- maxAltitudeMeters: Double?
- status: RECORDING / COMPLETED / INTERRUPTED

`elapsedSeconds`、`movingSeconds`、`stoppedSeconds` 必须明确口径，避免服务区停车导致平均速度误导。

App 启动时若存在 RECORDING / INTERRUPTED 行程，应进入恢复流程，而不是静默创建新 Trip。

---

## 7. TripPoint

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

GPS 海拔为原始定位数据，爬升量等指标必须经过过滤/平滑后再计算。

采样频率不得无限提高；优先 2-5 秒 + 位移阈值 + 静止降频，控制耗电和数据库体积。

---

## 8. Charging / Trip Data Link

不强制建立一对一外键。

分析层通过以下数据关联:

- vehicleId
- ChargingRecord.odometerKm
- chargeTimeEpochMillis
- TripSession 时间范围 / distanceMeters

这样允许“一次充电覆盖多次行程”或“多次补能覆盖一个使用区间”，避免错误的一对一建模。

---

## 9. Relationships

```text
VehicleCatalog 0..1 ---- 1 UserVehicle

UserVehicle 1 ---- N ChargingRecord
UserVehicle 1 ---- N TripSession
TripSession  1 ---- N TripPoint

ChargingPlace 0..1 ---- N ChargingRecord (future optional reference)
```

---

## 10. Multi-Vehicle Query Rule

v0.2 起任何 Dashboard / Records / Stats / Trip 查询必须明确数据范围:

- selectedVehicleId
- 或显式 allVehicles

禁止默认把所有车辆数据混合后冒充当前车辆数据。

当前车辆 ID 建议保存在 DataStore，不作为业务表重复字段。

---

## 11. Data Source / Quality

详细规则见 `DATA_QUALITY_BACKUP.md`。

数据库只在真实有需要时增加 source / accuracy 字段，不为所有字段设计统一 confidence 分数。

优先保留:

- 原始值
- 来源
- 系统提供的 accuracy
- 计算口径

---

## 12. v0.1 Statistics

保持:

- monthCost
- monthEnergy
- chargingCount
- totalCost
- totalEnergy
- averagePrice

`pricePerKwh` 继续作为派生值，不持久化。

---

## 13. Migration Rule

数据库 schema 变化必须:

1. 更新 DATABASE.md
2. 提升 Room database version
3. 增加 Migration
4. 增加迁移测试
5. Release 禁止 destructive migration

Trip/VehicleCatalog/odometer 进入实现时必须先提供对应 Migration，而不是删除用户现有数据。

---

## 14. 变更记录

### v1.4.0

- ChargingRecord 预留高价值 odometerKm
- 定义 Charging/Trip 非一对一关联策略
- Trip 增加 moving/stopped 时间口径和中断恢复
- 增加 ChargingPlace 演进模型
- 明确 DataSource/accuracy 设计原则
- 明确轨迹采样需控制耗电和数据库体积

### v1.3.0

- 增加 VehicleCatalog / UserVehicle 分层
- 增加多车辆状态字段
- 增加 ChargingRecord 可选经纬度
- 定义 TripSession / TripPoint
- 明确 WGS84 原始坐标和多车辆查询范围
