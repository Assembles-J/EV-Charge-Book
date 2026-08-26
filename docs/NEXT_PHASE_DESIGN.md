# EV Charge Book 后续业务设计

更新时间: 2026-08-26
状态: Active Design

## 1. 当前边界

本地核心闭环已经具备：

- v0.1 充电记账 Released / Accepted
- Multi Vehicle / Vehicle Catalog foundation
- Local Backup / Restore
- Location + reverse geocoding
- Trip foreground tracking + Bluetooth confirmation
- charging interval / cost per 100km / charged kWh per 100km analytics
- monthly trend / month-over-month / charger-type mix
- common-place aggregation + quick reuse
- selected-vehicle CSV analysis export
- non-blocking anomaly warnings

Trip #15 与 Bluetooth #21 已在最新真机功能验证后关闭。MapLibre 不再作为阶段门槛。

## 2. 当前收尾

v0.3 只剩累计 CI 与文档收口。以下事项明确后置，不为了“形式完整”提前实现：

- Privacy Zone：等真正出现路线导出/分享时再做
- structured ChargingPlace table/type：等地点文本聚合不够用时再做
- DataSource metadata：只在来源会改变解释时引入
- heavy charts：当前数据密度不足，不引入额外图表框架

## 3. v0.4 推荐目标：最小可用云同步

云端的价值不是替换 Local First，而是解决：

1. 换机恢复
2. 多设备数据一致
3. 未来 Web / AI 分析的数据入口
4. 车型目录可持续更新

第一版保持单体：

```text
Android Local Room
   <-> Sync API
Spring Boot Monolith
   -> PostgreSQL
```

不拆微服务，不上消息队列，不为了未来规模提前复杂化。

## 4. v0.4 实现顺序

```text
sync identity + protocol
 -> Vehicle sync
 -> ChargingRecord sync
 -> TripSession / TripPoint sync
 -> conflict rules / delete semantics
 -> manual sync status UI
 -> optional account UX
 -> catalog update pipeline (#20)
```

### 4.1 Sync identity

需要稳定本地对象 ID / remote ID 或等价同步标识，避免使用 Room 自增 ID 作为跨设备事实身份。

第一版应优先定义：

- entity stable sync id
- updatedAt
- deleted/tombstone semantics
- schema / protocol version
- last successful sync cursor/time

### 4.2 Conflict rule

首版不做复杂 CRDT。

推荐：

- 单字段不自动拼接
- 同一记录冲突以 latest explicit edit 为主
- 删除使用 tombstone，避免另一设备把删除的数据重新上传回来
- 原始 GPS TripPoint 基本只追加，不做点级人工合并
- 任何冲突处理不得悄悄修改 odometer / SOC / GPS 原始事实

### 4.3 Sync UX

Local First 仍是默认体验：

- 无网络可完整记账和记录 Trip
- 云端失败不阻塞保存
- UI 只展示明确的同步状态：本地已保存 / 待同步 / 已同步 / 同步失败
- JSON Backup 继续保留，云同步不是唯一恢复路径

## 5. Catalog #20

车型目录与用户数据同步分离。

推荐后续建立可重复的数据 pipeline：

```text
公开/官方来源
 -> normalize
 -> validate
 -> versioned catalog dataset
 -> server publish
 -> app incremental download
```

UserVehicle 仍保存 snapshot/override，不因目录更新自动改写用户既有车辆资料。

## 6. 明确非目标

v0.4 第一批不做：

- 微服务
- Kafka / MQ
- 实时 websocket 同步
- 社区/排行榜
- AI 自动修改账本
- 路线公开分享
- 自动后台无限同步 GPS 点

## 7. 阶段验收

v0.4 第一阶段达到以下结果即可认为有业务价值：

1. Android 离线新增车辆/充电记录后仍可正常使用。
2. 有网络时可手动/安全触发同步。
3. 重装或第二设备能恢复同一用户的 Vehicle + ChargingRecord。
4. 重复同步不产生重复记录。
5. 删除不会被旧设备重新“复活”。
6. 云端不可用时本地数据不受影响。
7. JSON Backup 仍可独立导出和恢复。

Trip sync 在 Vehicle + ChargingRecord 同步稳定后再接入，避免第一批同时处理大量轨迹点。