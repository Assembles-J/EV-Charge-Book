# EV Charge Book 项目总纲（PROJECT MASTER）

版本: v2.0.1
更新时间: 2026-08-26
状态: Authority Document / Single Source of Truth

## 1. 项目定位

EV Charge Book 是新能源车主的 Local First 车辆数据中心。

演进顺序：

1. 充电记账与成本
2. 多车辆与车型目录
3. 位置 / 驾驶行程数据
4. 充电与行程的数据闭环
5. 本地分析与可靠性
6. 跨设备同步 / 云恢复
7. Web / AI 增值能力

首个验证车型为零跑 C16，但产品、数据库和 UI 不绑定单一品牌。

---

## 2. 权威文档

- PRODUCT.md
- FEATURE_MATRIX.md
- UIUX.md
- FRONTEND.md
- BACKEND.md
- DATABASE.md
- CI_CD.md
- ROADMAP.md
- DEVELOPMENT.md
- LOCATION_TRIP.md
- VEHICLE_CATALOG_MULTI_VEHICLE.md
- DATA_QUALITY_BACKUP.md
- SYNC_PROTOCOL.md
- LOCAL_AGENT_HANDOFF.md
- NEXT_PHASE_DESIGN.md

实现与文档冲突时，先以当前代码、CI 与真机事实为准，再修正文档。

---

## 3. 产品原则

- 简单可维护
- Local First
- 真实数据来源
- 用户录入成本低
- 统计口径可解释
- 原始事实 / 派生值 / 估算值明确区分
- 不伪造实时 SOC / SOH / 续航
- 定位记录与地图 SDK 解耦
- 车型目录与用户车辆分离
- 外部供应商可替换
- 云同步不能成为唯一恢复路径
- 无网络和云端故障不能阻塞本地记账 / Trip

---

## 4. v0.1 状态: Released / Accepted

已完成充电记录 CRUD、车辆编辑、Dashboard/Records/Stats、核心规则测试、Android CI、真机 CRUD、signed production APK 与原子发布。

---

## 5. v0.2 状态: Core Accepted

已完成并经过当前真机功能验证：

- odometer + migration
- Local Backup / Restore
- Multi Vehicle / Vehicle Catalog
- Bluetooth 指定设备连接 / 已连接状态 -> Trip 用户确认
- Android LocationManager + WGS84 + accuracy
- AddressResolver + Android Geocoder
- TripSession / TripPoint
- foreground tracking / notification
- Trip start / stop / interrupted resume / detail
- distance / elapsed / moving / stopped
- speed / bearing / GPS altitude / accuracy
- GPS bad-point / jump filtering
- stationary throttling
- TripRouteGeometry + 无底图真实轨迹预览

Trip #15 与 Bluetooth #21 已按最新真机功能反馈关闭。

MapLibre 继续作为可选展示层，不是业务阶段门槛。

---

## 6. v0.3 状态: Feature Complete Candidate

已实现：

- 充电区间里程
- 费用 / 100km 账本估算
- 补入电量 / 100km 账本估算
- Trip 覆盖辅助证据
- SOC 可信度提示
- 区间明细
- 6 个月费用 / 电量趋势
- 本月 vs 上月对比与 zero-baseline 降级
- charger type 次数 / 电量 / 费用结构
- ChargingPlace 文本派生聚合
- Top 常用地点录入复用
- 当前车辆 CSV 分析导出
- 非阻塞异常输入提示

已确认基线：Android Build Run #169 Green，覆盖常用地点 + CSV。异常提示已经进入 main，等待后续累计 Android CI 一并验收。

v0.3 不再扩重型 chart、独立 ChargingPlace 表或无真实需求的期间筛选。

---

## 7. 当前阶段: v0.4 Local First Sync Foundation

### 7.1 当前原则

先解决“同一条数据跨设备是谁”，再接 HTTP / Spring Boot。

```text
Room local id
    = 当前设备内部关系键

syncId
    = 跨设备稳定业务身份
```

不重写现有 Room id / vehicleId / tripId 关系。

### 7.2 Phase A 当前代码

Vehicle + ChargingRecord 已进入同步身份实现：

- stable `syncId`
- `updatedAtEpochMillis`
- ChargingRecord `isDeleted` tombstone
- Room v6 -> v7 explicit migration
- 旧数据自动补 sync identity
- 正常充电查询 / analytics 排除 tombstone
- 用户删除 ChargingRecord 改为 tombstone
- Backup schema v6 保存 sync metadata / tombstone
- 旧 Backup 缺 syncId 时生成新稳定 ID
- sync identity JVM tests

当前累计 Android code commit：`56830c03275a68a518f873cfcbfecb094a362758`。

Android Build Run #177 已创建但仍在 GitHub runner 队列。Phase A 在 Build/Test + Debug APK Green 前不得标记 Accepted。

### 7.3 同步协议

`SYNC_PROTOCOL.md` 已固定第一版协议边界：

- protocolVersion
- Vehicle / ChargingRecord change payload
- Vehicle 不同步 `isDefault` / selectedVehicleId
- ChargingRecord 使用 `vehicleSyncId`，不发送本地 vehicleId
- serverRevision/cursor 用于增量 pull
- stable syncId + updatedAt 用于实体冲突
- tombstone 防止旧设备复活已删除记录
- batch apply 必须 Room transaction，成功后才能推进 cursor
- 第一版无 CRDT / 微服务 / MQ / WebSocket

### 7.4 下一步

Phase A CI Green 后：

1. 实现 Vehicle / ChargingRecord sync DTO / envelope
2. 实现纯 Kotlin conflict/apply rules
3. 固定 pull cursor / push acknowledgement
4. 再实现最小 HTTPS sync client/server
5. 第一批服务端只同步 Vehicle + ChargingRecord
6. TripSession / TripPoint 后接

---

## 8. 同步冲突原则

第一版保持可解释：

- stable syncId 确定同一实体
- explicit edit 通过 updatedAt 决定新旧
- 不做字段级自动拼接
- 删除使用 tombstone，避免旧设备复活记录
- odometer / SOC / GPS 等事实不做“智能纠错”
- 重复 push / pull 必须幂等
- selected/default vehicle 属于设备 UX 状态，不应因为切换当前车辆制造无意义云冲突

如果实际测试证明设备时钟偏差成为问题，再引入 server revision/cursor 参与写冲突；当前不提前复杂化。

---

## 9. 恢复与隐私

- Local JSON Backup 长期保留
- Cloud Sync 不是唯一恢复路径
- restore 失败不得破坏当前数据
- 持续定位必须可见
- 路线公开分享/导出之前实现 Privacy Zone
- 云同步轨迹需要明确产品同意与隐私说明

---

## 10. 发布 / CI 基线

- JDK 17
- Android SDK 36
- Build Tools 36.0.0
- repository Gradle Wrapper
- Android CI 与 Production Release 分离
- signed APK
- Actions Artifact
- server `.part` + SHA/apksigner + atomic activation

最近已确认：Build Run #169 Green；同步 Phase A 等待累计 Run #177。

---

## 11. 架构约束

Android 保持：

```text
Compose -> MainViewModel -> ChargingRepository -> Room DAO -> Room
```

v0.4 云端第一版保持：

```text
Android Room
   <-> HTTPS Sync API
Spring Boot Monolith
   -> PostgreSQL
```

不要引入 Hilt/Koin、多 module Clean Architecture、微服务、MQ 或其他当前没有收益的基础设施。

---

## 12. 开发验收规则

每轮业务代码必须：

- Android Gradle test/build
- GitHub CI Green
- 更新 owning Issue
- 阶段变化同步 ROADMAP / PROJECT_MASTER
- schema 改动必须显式 Migration
- 不把“代码已写”标成“CI Accepted”
- 不把 CI 通过标成“真机已验收”

---

## 13. 当前 Issues

- #19 Data Reliability：只保留增量可靠性尾项，不阻塞 v0.4
- #22 UI polish：主要视觉工作已合并，真机视觉复核非阻塞
- #27 v0.4 Sync 主线
- #28 Sync Phase A 实现与验收
- #20 Catalog Coverage：后续可持续车型目录管道

#29/#30/#31/#32/#33 为工具误创建或 duplicate，已关闭，不作为任何业务入口。

---

## 14. 决策记录

### v2.0.1

- `SYNC_PROTOCOL.md` 纳入权威文档体系
- 固定第一版 payload / idempotency / tombstone / conflict / cursor 边界

### v2.0.0

- Trip / Bluetooth 最新真机功能验证通过并关闭 owning Issues
- v0.3 补齐常用地点复用、CSV 分析导出、异常提示
- Build Run #169 Green
- 正式切换主线到 v0.4 Local First Sync Foundation
- Vehicle + ChargingRecord Phase A sync identity 已实现，等待累计 Run #177
- 云同步必须先定 stable identity / tombstone / conflict protocol，再接服务端
