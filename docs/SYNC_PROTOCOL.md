# EV Charge Book Sync Protocol

Version: v0.1.0
Updated: 2026-08-26
Status: Authority Subdocument / v0.4

## 1. Goal

定义 EV Charge Book 第一版跨设备同步协议，使 Android 在保持 Local First 的前提下，可以安全地把 Vehicle 与 ChargingRecord 同步到未来 Spring Boot + PostgreSQL 服务端。

该协议优先解决：

- 同一实体跨设备稳定识别
- 重复请求幂等
- 删除不会被旧设备复活
- 离线编辑可补同步
- 服务端故障不破坏 Room 本地事实

第一版不追求实时协同编辑。

---

## 2. Entity identity

### 2.1 Local relational id

`id: Long`

仅用于当前设备 Room 内部关系。

不得作为跨设备 API 主键。

### 2.2 Stable sync identity

`syncId: String`

- 客户端创建实体时生成
- 生成后不可因编辑改变
- 服务端以 syncId 做幂等 upsert identity
- 从旧数据库 / 旧 Backup 升级时生成

推荐当前格式：UUID string；迁移历史行可使用等价唯一随机字符串，不要求与新建数据格式完全一致，只要求稳定且唯一。

---

## 3. Protocol version

所有 sync request / response 都包含：

```text
protocolVersion: 1
```

服务端遇到不支持的新版本必须明确返回版本不兼容错误，不得静默丢字段。

---

## 4. Vehicle change payload

第一版 Vehicle 云端 payload：

```text
VehicleChange
- syncId: String
- updatedAtEpochMillis: Long
- catalogVehicleId: String?
- brand: String
- model: String
- batteryCapacityKwh: Double
- rangeKm: Int
- isArchived: Boolean
- createdAtEpochMillis: Long
```

明确不上传：

- local `id`
- `isDefault`
- selectedVehicleId

原因：当前/默认车辆属于设备 UX 状态，不应该因为在手机 A 切换车辆而覆盖手机 B 的当前页面状态。

第一版 Vehicle 不提供物理 delete；归档仍是业务字段 `isArchived`。

---

## 5. ChargingRecord change payload

```text
ChargingRecordChange
- syncId: String
- vehicleSyncId: String
- updatedAtEpochMillis: Long
- isDeleted: Boolean
- chargeTimeEpochMillis: Long
- energyKwh: Double
- cost: Double
- startSoc: Int
- endSoc: Int
- chargerType: String?
- location: String?
- remark: String?
- odometerKm: Double?
- latitude: Double?
- longitude: Double?
- locationAccuracyMeters: Double?
```

明确不上传本地 `vehicleId`，而是上传 `vehicleSyncId`。

当 `isDeleted = true` 时：

- syncId / vehicleSyncId / updatedAt / isDeleted 仍必须存在
- 服务端保留 tombstone
- 其他业务字段可以保留最后版本，第一版无需为了节省空间清空

---

## 6. Batch request

第一版建议单一批量端点，不为每个实体建立复杂 RPC：

```text
SyncPushRequest
- protocolVersion
- deviceId
- vehicles: List<VehicleChange>
- chargingRecords: List<ChargingRecordChange>
```

响应：

```text
SyncPushResponse
- protocolVersion
- serverRevision
- acceptedVehicleSyncIds
- acceptedChargingRecordSyncIds
- conflicts: List<SyncConflict>
```

`deviceId` 仅用于诊断/同步状态，不作为业务实体身份。

---

## 7. Pull request

```text
SyncPullRequest
- protocolVersion
- afterRevision: Long?
```

响应：

```text
SyncPullResponse
- protocolVersion
- serverRevision
- vehicles: List<VehicleChange>
- chargingRecords: List<ChargingRecordChange>
- hasMore: Boolean
```

第一版优先使用服务端单调 `serverRevision` / cursor 做增量拉取，而不是依赖客户端时间窗口。

客户端 `updatedAtEpochMillis` 用于同实体冲突判断；serverRevision 用于“有哪些变化需要拉”。

---

## 8. Idempotency

同一 `syncId + updatedAtEpochMillis + payload` 可以被重复 push 任意次，结果必须一致。

服务端必须以 syncId upsert，而不是每次 insert 新行。

网络超时后客户端可以安全重试，不需要判断上一次请求是否真的到达服务器。

---

## 9. Conflict rule v1

第一版不引入 CRDT 和字段级 merge。

对于同一 syncId：

1. incoming.updatedAt > stored.updatedAt -> incoming wins
2. incoming.updatedAt < stored.updatedAt -> stored wins，并作为 conflict/current version 返回客户端
3. updatedAt 相同且 payload 相同 -> idempotent success
4. updatedAt 相同但 payload 不同 -> deterministic conflict，不静默任选一边

相同时间戳不同内容时，客户端必须收到明确 conflict；第一版不自动合并 SOC、odometer、GPS 等事实字段。

未来如果设备时钟偏差成为真实问题，再引入 server-side version/revision 参与写冲突，不在第一版预先复杂化。

---

## 10. Tombstone rule

ChargingRecord 删除：

```text
isDeleted = true
updatedAtEpochMillis = delete time
```

服务端保留 tombstone。

另一设备拉到 tombstone 后：

- 本地对应记录标记 isDeleted
- 普通 UI / Stats / CSV 不显示
- Backup / Sync 数据仍保留 tombstone

旧设备如果随后 push 更旧的非删除记录，因为 updatedAt 更早，不能复活该实体。

第一版不做 tombstone 自动 GC。只有在明确具备所有设备同步水位 / retention 规则后才允许清理。

---

## 11. Apply remote changes transaction

Android pull 应：

1. 完整解析 response
2. 校验 protocolVersion / syncId / vehicle references
3. 在 Room transaction 中应用一个 batch
4. batch 失败则整体回滚
5. transaction 成功后再推进 lastServerRevision

禁止先更新 cursor 再写实体。

---

## 12. Dependency ordering

应用远端变更顺序：

```text
Vehicle
 -> ChargingRecord
```

ChargingRecord.vehicleSyncId 必须能解析到本地 Vehicle.id。

若远端 ChargingRecord 引用未知 vehicleSyncId：

- 该 batch 不静默丢记录
- 返回/记录明确 sync error
- 不伪造车辆

---

## 13. Local change selection

未来 DAO 应提供：

- 按 updatedAt / sync state 查待 push Vehicle
- 按 updatedAt / sync state 查待 push ChargingRecord，包括 tombstone

第一版可以使用简单 `lastSuccessfulPushAt` 或 dirty marker；在真正实现 HTTP 前再二选一。

原则：不能因为 App 崩溃或请求超时永久漏掉本地变化。

---

## 14. Authentication boundary

同步协议与登录方式解耦。

HTTP auth 可以后续采用账号 token；payload 内不复制敏感认证信息。

服务端必须从认证上下文确定 owner，不信任客户端提交任意 userId 来越权写数据。

---

## 15. Local First failure behavior

以下情况都不得影响本地 CRUD：

- 无网络
- DNS 失败
- HTTP 5xx
- token 过期
- protocol mismatch
- sync conflict
- pull payload validation failure

本地先保存成功，再异步/显式同步。

UI 后续只需要可解释状态：

```text
本地已保存
待同步
已同步
同步失败
```

---

## 16. Phase ordering

```text
Phase A
stable sync identity / updatedAt / tombstone

Phase B
pure DTO + conflict/apply rules

Phase C
Android HTTP client + Spring Boot monolith + PostgreSQL
Vehicle + ChargingRecord only

Phase D
TripSession / TripPoint sync
```

不要在 Vehicle + ChargingRecord 闭环未验证前引入大量 GPS point 同步。

---

## 17. Explicit non-goals v1

- realtime collaborative editing
- CRDT
- Kafka / MQ
- WebSocket sync
- microservices
- cloud-only storage
- server-side AI correction of user facts
- route sharing

---

## 18. Acceptance

协议进入实现必须满足：

1. 同一 syncId 重复 push 不产生重复记录。
2. 旧版本更新不能覆盖更新版本。
3. tombstone 能阻止旧设备复活 ChargingRecord。
4. pull batch 应用是事务性的。
5. Vehicle 的 `isDefault` / selectedVehicle 不参与跨设备冲突。
6. 服务端不可用时本地 CRUD 正常工作。
7. JSON Backup 仍是独立恢复路径。
