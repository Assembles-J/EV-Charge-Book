# EV Charge Book Local Agent Handoff

更新时间: 2026-08-26
状态: Active Handoff

## 1. 当前基线

仓库: `Assembles-J/EV-Charge-Book`
默认分支: `main`

v0.1 已正式 Released / Accepted。

当前开发阶段: `v0.2 Vehicle & Trip Foundation`

当前代码基线（交接时）:

- latest Android feature commit: `f8e18498a47970b1c4cfc417e3bc46608df5a304`
- Android Build Run #64: Failed
- Run ID: `32940521828`

## 2. 本地 Agent 第一任务

不要继续扩业务功能，先恢复 `main` CI Green。

当前唯一已确认编译阻塞:

```text
android/app/src/main/java/com/evchargebook/MainActivity.kt:154:52
Unresolved reference 'BuildConfig'
```

处理原则:

1. 用最小改动修复 BuildConfig/versionName 获取问题。
2. 不为此引入 DI、配置框架或额外架构层。
3. 本地执行与 CI 一致的命令:

```bash
cd android
./gradlew --no-daemon testDebugUnitTest :app:assembleDebug
```

4. push 后必须确认 GitHub Android Build Green 和 Debug APK Artifact。
5. CI 未 Green 前不要开始 Multi Vehicle / Catalog / Location / Trip。

## 3. 已完成: v0.1

- Room Vehicle / ChargingRecord
- Repository / MainViewModel / StateFlow
- Charging Record add / edit / delete
- date / time / charger type / remark
- Vehicle edit
- Dashboard / Records / Stats
- Material 3 UI
- Android CI / Debug APK
- physical-device CRUD acceptance
- signed production APK
- atomic server release

正式发布信息见 `docs/ROADMAP.md`。

## 4. 已完成: v0.2 odometer foundation

Issue: #18

- `ChargingRecord.odometerKm: Double?`
- Room v1 -> v2 migration
- Add/Edit 支持可选总里程
- Records 展示里程
- 同 vehicleId + 时间选择上一条可靠里程
- 里程下降只提示、不阻塞
- odometer rules / previous-reading unit tests
- Android Build Run #56 Green
- Debug APK Artifact verified

仍保留给后续的工作:

- migration test
- 正式 charging interval distance domain calculation
- v0.3 cost/100km / charged kWh/100km 统计口径
- TripSession 存在后的交叉校验

这些不是当前第一优先级。

## 5. 已实现但未验收完成: Local Backup / Restore

Issue: #19

当前代码已经包含:

- JSON backup payload
- schemaVersion / exportedAt / appVersion
- Vehicle + ChargingRecord（包含 odometer）
- Android SAF CreateDocument / OpenDocument
- 不申请额外存储权限
- restore overwrite confirmation
- schemaVersion validation
- Room transaction restore
- export decode/count validation
- restore 后 vehicle / charging record count validation
- record -> vehicle 引用检查
- Vehicle 页面备份/恢复入口

当前未验收原因不是业务逻辑失败，而是 Run #64 在 `MainActivity.kt` 编译时找不到 `BuildConfig`。

CI 修好后的验收顺序:

1. Debug APK Artifact
2. 真机导出备份
3. 记录导出前 vehicle / charging record 数量
4. 修改或删除本地数据
5. 从备份恢复
6. 确认覆盖弹窗
7. 验证 vehicle / charging records / odometer / remark 等核心字段恢复
8. 用坏 JSON / 错 schemaVersion 验证不会覆盖当前数据
9. 完成后更新 Issue #19 和 ROADMAP

## 6. 后续优先级

严格按以下顺序推进:

```text
P0  Fix Run #64 / CI Green
 -> P0.5 Backup / Restore device acceptance
 -> P1 Multi Vehicle (#17)
 -> P1 Vehicle Catalog (#16)
 -> P2 Location foundation (#14)
 -> P2 Manual Trip Tracking (#15)
 -> Data Reliability / ChargingPlace (#19 remaining)
 -> Map display
 -> v0.3 Analytics
```

多车辆和车型目录可以同一阶段设计，但实现时优先 selectedVehicleId / per-vehicle data scope，再接 catalog，避免 catalog 反向绑死 UserVehicle。

## 7. 架构约束

保持当前简单架构:

```text
Compose
 -> MainViewModel
 -> ChargingRepository
 -> Room DAO
 -> Room
```

除非出现明确复杂度，不引入:

- Hilt / Koin
- 多 module Clean Architecture
- UseCase ceremony
- 云账号作为 Local First 前置条件

业务规则优先放 domain object；数据库写入经 Repository；UI 不直接操作 DAO。

## 8. 数据真实性约束

不得伪造:

- 实时 SOC
- 实时续航
- SOH
- VIN / BMS 状态
- 自动推断成事实的 odometer / GPS / Trip

事实、派生值、估算值必须区分。

## 9. 权威文档

开始开发前至少阅读:

1. `docs/PROJECT_MASTER.md`
2. `docs/ROADMAP.md`
3. `docs/LOCAL_AGENT_HANDOFF.md`
4. 对应功能子文档

功能对应:

- odometer / DB: `DATABASE.md`
- backup: `DATA_QUALITY_BACKUP.md`
- multi vehicle/catalog: `VEHICLE_CATALOG_MULTI_VEHICLE.md`
- location/trip: `LOCATION_TRIP.md`

代码和文档冲突时，以当前代码事实为依据先修正文档，不要靠旧文档猜实现。

## 10. GitHub 状态

当前没有 Open PR。

活跃 v0.2 Issues:

- #18 odometer / charging interval foundation
- #19 backup + data reliability
- #17 multi vehicle
- #16 vehicle catalog
- #14 location
- #15 trip tracking

历史 #1 / #2 已在交接时关闭。

## 11. 每轮 Agent 完成标准

每个任务结束前至少做到:

- 实现最小可维护改动
- 相关 unit test / migration test（适用时）
- 本地 Gradle build/test
- GitHub CI Green
- 更新对应 Issue checkbox/status
- 更新 ROADMAP / PROJECT_MASTER（阶段状态变化时）
- 不把“代码已写”当成“功能已验收”
