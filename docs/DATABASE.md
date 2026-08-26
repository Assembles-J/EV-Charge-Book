# EV Charge Book Database Design

Version: v1.5.0
更新时间: 2026-08-26

## 1. Design Principle

Local First。Room/SQLite 保存用户真实业务数据；统计值优先动态聚合。车型目录属于参考数据，用户车辆属于业务数据，两者分离。

Room numeric `id` 是本地关系键；从 v0.4 起，跨设备同步不得直接把本地自增 id 当成全局身份。

---

## 2. UserVehicle / Vehicle

当前核心字段:

- id: Long PK / local relational key
- catalogVehicleId: String?
- brand
- model
- batteryCapacityKwh
- rangeKm
- isDefault
- isArchived
- createdAtEpochMillis

v0.4 Sync Phase A 计划新增:

- syncId: String — 跨设备稳定身份，生成后不可因编辑改变
- updatedAtEpochMillis: Long — 用户/业务字段最后一次本地修改时间

Vehicle archive 是现有业务状态，不等同于云端 delete/tombstone。第一批不为了同步新增“删除车辆”能力。

车型目录更新不得自动覆盖用户车辆参数。

---

## 3. VehicleCatalog

本地参考数据:

- catalogId: String PK
- source
- brand
- series
- modelName
- modelYear?
- trimName?
- powertrainType
- batteryCapacityKwh?
- rangeKm?
- sourceUpdatedAt?

VehicleCatalog 可重建/更新；UserVehicle 不可因目录刷新丢失。Catalog 的远端更新协议与用户 Vehicle 同步分离。

---

## 4. ChargingRecord

核心字段:

- id: Long PK / local relational key
- vehicleId: Long
- chargeTimeEpochMillis
- startSoc / endSoc
- energyKwh
- cost
- chargerType?
- location?
- remark?
- odometerKm?
- latitude?
- longitude?
- locationAccuracyMeters?

v0.4 Sync Phase A 计划新增:

- syncId: String — 跨设备稳定身份
- updatedAtEpochMillis: Long — 最后一次业务修改时间
- isDeleted: Boolean — 同步 tombstone，本地普通列表/统计必须排除

规则:

- 用户删除 ChargingRecord 后，未来同步模式下保留 tombstone，避免旧设备重新上传“复活”数据。
- tombstone 不参与 Dashboard / Records / Stats / CSV 正常分析。
- 完整 JSON Backup 在进入 sync metadata 版本后应保留 tombstone，以保证跨设备删除语义可恢复。
- 本地 `vehicleId` 继续作为 Room 关系键；远端协议通过 Vehicle.syncId 关联，不要求本阶段重写所有外键。
- 同一车辆新录入 odometerKm 低于上一条可靠里程时提示检查，不静默修改。
- 原始坐标统一保存 WGS84。

---

## 5. ChargingPlace

当前不新增独立 Room 表。

已有 location snapshot 文本已经支持：

- common-place aggregation
- count / energy / cost / average price
- Top common-place quick reuse

只有真实使用证明文本聚合不够时，才考虑结构化：

- id
- name
- type: HOME / WORK / PUBLIC / HIGHWAY / OTHER
- latitude?
- longitude?
- note?

历史 ChargingRecord 永远保留 location snapshot，不依赖地点表才能展示。

---

## 6. TripSession

当前字段:

- id: Long PK
- vehicleId: Long
- startedAtEpochMillis: Long
- endedAtEpochMillis: Long?
- distanceMeters: Double
- elapsedSeconds: Long
- movingSeconds / stoppedSeconds
- averageSpeedMps / maxSpeedMps
- start/end coordinates
- start/end/min/max altitude
- status: RECORDING / COMPLETED / INTERRUPTED

Trip sync 不进入 v0.4 第一批。先验证 Vehicle + ChargingRecord 的跨设备闭环，再增加 TripSession.syncId/updatedAt。

---

## 7. TripPoint

TripPoint 保持当前 append-heavy GPS 数据模型。

TripPoint 同步最后接入，因为它数据量最大。未来应采用 stable identity + append/idempotent upsert，不做点级 CRDT 或“智能路线修正”。

GPS 海拔、坐标、accuracy 都是原始事实；服务端不得为了路线好看静默改写。

---

## 8. Charging / Trip Data Link

不强制 ChargingRecord <-> TripSession 一对一外键。

分析通过：

- vehicleId
- odometerKm
- chargeTimeEpochMillis
- Trip 时间范围 / distanceMeters

这样支持一次充电覆盖多次行程或多次补能覆盖一个使用区间。

---

## 9. Sync Identity & Conflict Model

### 9.1 Local id vs syncId

```text
Room id
  = 当前设备内部关系键

syncId
  = 跨设备/服务端稳定业务身份
```

禁止用 Room 自增 id 直接做远端全局主键。

### 9.2 First-version conflict rule

不引入 CRDT：

- 同一 syncId 的显式编辑按 updatedAtEpochMillis 决定最新版本
- 不做字段级自动拼接
- 删除用 tombstone 传播
- odometer / SOC / GPS 等原始事实不自动“纠错”
- 重复 push/pull 必须幂等

如果未来发现设备时钟偏差会影响冲突判断，再引入 server revision/cursor；第一批不提前增加复杂度。

### 9.3 Write rule

任何会改变同步业务内容的本地写操作都必须更新 `updatedAtEpochMillis`：

- create
- edit
- archive/unarchive（Vehicle）
- delete/tombstone（ChargingRecord）

只切换当前 selected/default vehicle 是否算业务修改，需要在实现时明确；默认不应因为 UI 选择行为制造无意义云端冲突。

---

## 10. Backup & Sync

Local JSON Backup 与 Cloud Sync 是两个独立恢复路径。

进入 sync metadata schema 后，Backup 应保存：

- syncId
- updatedAtEpochMillis
- tombstone state

恢复旧备份时必须为缺失 syncId 的实体生成新稳定 ID，而不是拒绝历史备份。

云端失败不得影响本地 Backup / Restore。

---

## 11. Multi-Vehicle Query Rule

任何 Dashboard / Records / Stats / Trip 查询必须明确：

- selectedVehicleId
- 或显式 allVehicles

ChargingRecord tombstone 加入后，普通业务查询还必须显式排除 `isDeleted = true`。

---

## 12. Data Source / Quality

数据库只在真实需要时增加 source / accuracy 字段，不制造统一 confidence 分数。

优先保留：

- 原始值
- 来源
- 系统 accuracy
- 计算口径
- 同步变更元数据（仅同步实体）

---

## 13. Migration Rule

数据库 schema 变化必须：

1. 更新 DATABASE.md
2. 提升 Room database version
3. 增加显式 Migration
4. 验证旧数据保留
5. 增加迁移/规则测试
6. Release 禁止 destructive migration

v6 -> v7 的 Sync Phase A 必须保证：

- 旧 Vehicle / ChargingRecord local id 不变
- 旧关系不变
- 每个旧实体获得唯一 syncId
- updatedAt 有确定初始值
- 旧数据默认不是 tombstone

---

## 14. 变更记录

### v1.5.0

- 定义 v0.4 Local First sync identity
- 明确 Room id 与 syncId 分工
- Phase A 只覆盖 Vehicle + ChargingRecord
- 定义 ChargingRecord tombstone 语义
- 定义 first-version updatedAt conflict rule
- 明确旧 Backup / migration 兼容要求
- Trip / TripPoint sync 后置

### v1.4.0

- odometer / Charging-Trip 非一对一关联
- Trip moving/stopped 与中断恢复
- ChargingPlace 演进模型
- DataSource / accuracy 原则
