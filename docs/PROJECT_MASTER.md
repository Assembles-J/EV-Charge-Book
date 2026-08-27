# EV Charge Book 项目主文档

版本: v2.4.0
更新时间: 2026-08-27
状态: Current Authority

## 1. 项目定位

EV Charge Book 是一个面向新能源车主的个人能源、充电与行程管理应用。

核心问题：

> 电动车到底花了多少钱、充了多少电、跑了多少、当前状态如何，以及这些事实能否长期保存、恢复并形成可信分析。

当前产品原则：

1. Local First：无服务端时核心记账、车辆、Trip 仍可使用。
2. 数据可信度优先：不知道的数据保持 unknown，不为了 UI 完整伪造 SOC、位置、里程、路线或能耗。
3. 历史事实与当前状态分离：Charge / Trip 保存事件事实，VehicleState 保存当前 SOC / 里程投影。
4. 估算必须标识来源：SOC 差能耗、GPS 距离等不能冒充 BMS / 里程表实测。
5. UI 服务于快速录入、驾驶状态和复盘，不追求复杂架构或装饰性假数据。

## 2. 当前阶段

### 已完成发布基线

- v0.1：可用记账闭环，已发布/验收。
- 0.4 RC1：Trip reliability release gate 已完成，历史 Issue #41 已关闭，#54 已发布。

0.4 RC1 的完成不代表后续任何新真机问题都不存在。2026-08-27 第二轮实机测试发现的是新的 v0.5 可靠性/体验问题，不重开历史 #41。

### 当前 active milestone

**v0.5 Local Experience Hardening**

当前目标不是继续增加系统层，而是把已经具备的数据模型、Charge、Trip、VehicleState 与 Dark First UI 收口成真实可用的本地体验。

## 3. 当前主线能力

### Vehicle / VehicleState

已具备：

- 多车辆与当前车辆切换
- VehicleCatalog / editor
- 当前 SOC / 当前里程 `VehicleState`
- Charge / completed Trip 对 VehicleState 的事件顺序更新
- backdated edit/delete 后的 current-state 重建
- Backup / Restore 覆盖 VehicleState

相关实现：#79、#81、#87。

### ChargingRecord

已具备：

- create / edit / delete
- SOC、里程、电量、费用、单价、地点、充电类型
- 充电开始 SOC / 当前里程自动继承 VehicleState
- 同类型最近单价/偏好带入
- charger/meter energy 与 vehicle-received energy estimate 分离
- 充电损耗估算与异常提示
- 充电结束更新 VehicleState

### Trip

已具备：

- 手动开始 / 结束
- location foreground service
- GPS + Network raw point 保存
- elapsed / moving / stopped time
- trusted distance / average / max speed
- INTERRUPTED / resume
- GPS health / gap diagnostics
- 长 GPS gap 路线断开且不累计可信 gap 距离/时长
- callback liveness 与 stationary heartbeat 分层
- trusted GPS max-speed quality gate
- 起/终/最低/最高海拔
- 起终点 reverse geocoding + 坐标技术参数
- 起点圆形 / 终点方形语义标记
- trusted GPS speed-colored route；不可信区间灰色
- Trip start SOC / mileage snapshot
- Trip end SOC / mileage confirmation
- SOC-based estimated consumed kWh / kWh per 100km
- atomic Trip completion + VehicleState update
- Trip detail SOC / estimated energy summary

相关实现：#80、#82、#83、#85、#87、#88。

### Backup / Data Quality

已具备：

- Room migration
- JSON backup / restore
- CSV export
- orphan / invalid data guard
- unknown historical values remain unknown
- new Trip SOC/energy fields backward-readable

### CI / Release

- Android Build: JDK 17 / SDK 36 / Gradle Wrapper 9.5.0
- Android code PR 使用 `testDebugUnitTest + assembleDebug`
- Debug APK artifact
- docs-only PR 通过 path filter 可不跑 Android CI
- Production Release 独立于普通 CI
- release-only update manifest / signed APK 流程已建立

当前 GitHub `main` 仍未启用 branch protection / required checks；Issue #75 负责最小修复。在完成前，代码 PR 必须人工确认最新 head 已包含当前 main 且 Android Build Green 后再合并。

## 4. v0.5 Dark First UI

PR #71 已合入 Dark First 视觉基线：

- Dark default + persisted Light mode
- 统一 typography / spacing / shape / surface hierarchy
- Dashboard / Records / Stats / Trip / Vehicle 五个核心页面视觉重构
- 本地车型图资源，不在运行时加载 Base64/network Hero

当前 Hero 方向进一步明确：

> 车辆、极光/环境光、地面反射等复杂视觉直接作为最终图片资产生成；Compose 负责稳定展示，不继续用代码模拟昂贵视觉特效。

UI Authority：

- `UIUX.md`
- `EVChargeBook_UI_Design_Language_v0.5.md`
- `EVChargeBook_UI_Implementation_Plan_v0.5.md`
- `EVChargeBook_UI_Redesign_Task_Breakdown.md`

## 5. 数据真值规则

### 当前 VehicleState

VehicleState 是当前 SOC / 当前里程共享来源，不是历史事件替代品。

```text
Charge / completed Trip / manual state
  -> event-order-aware VehicleState projection
```

较早历史记录后补/编辑不能因为编辑时间较晚就覆盖更新的当前状态。

### Trip SOC 能耗

```text
estimated consumed kWh
  = battery capacity * positive SOC drop / 100

estimated kWh/100km
  = estimated consumed kWh / trusted GPS distance km * 100
```

这只是 SOC-based estimate，不是 BMS 实测。

以下情况保持 unavailable：

- start SOC unknown
- SOC unchanged/increases
- distance insufficient
- input insufficient

### GPS / Speed

- raw TripPoint 保留来源、accuracy、speed。
- Network/coarse speed 不能刷新 maxSpeedMps。
- route speed color 只使用可信 GPS speed。
- 长 gap 不连可信实线，不补造道路轨迹。
- 速度颜色表示本车速度分布，不代表道路拥堵。

## 6. 当前真机验收门

### Trip reliability

- #77：切到其他 App 后 callback / 距离连续；2-3 分钟红灯不 false LONG_GAP，stoppedSeconds 合理。
- #78：最高已记录速度与车辆实际峰值合理一致。
- #67：可信速度彩色 route、灰色未知段、GPS gap、Dark/Light 可读性。
- #66：Trip 地址 reverse geocoding 真机成功/失败路径。
- #69：海拔展示与后续 elevation analytics。
- #42：endpoint/accessibility/large-font/TalkBack/state-safety。

### Trip state / energy

真机需验证：

```text
READY
 -> snapshot current SOC/mileage
 -> LIVE GPS Trip
 -> completion dialog
 -> confirm end SOC/mileage
 -> estimated energy if meaningful
 -> VehicleState refreshed
 -> Trip detail shows SOC/energy summary
```

Foreground notification 当前只打开 Trip flow，不能直接写 `COMPLETED`，因为 completion 必须确认 end SOC。未来 #26 可以 deep-link 到 completion UI，但不能绕过确认。

## 7. 当前执行顺序

1. 完成 Trip 物理设备 reliability / speed / address / altitude / route-color 验收。
2. 完成 READY -> LIVE -> end SOC -> VehicleState 的完整真机闭环。
3. Home / Dashboard / Vehicle 统一消费 VehicleState，减少重复录入。
4. Stats / Energy 明确区分 Charge 事实与 Trip SOC estimate。
5. 五个一级页面 Dark/Light、large font、small screen、TalkBack 真机视觉收口。
6. 使用最终生成的 per-model Hero 图片资产完成视觉 closeout。
7. 修复 main branch protection / required checks (#75)。
8. 本地 v0.5 稳定后再恢复 #27/#28 sync expansion。

## 8. 暂缓

在上述本地体验未完成前，不优先：

- 云同步大规模扩展
- 微服务 / Kafka / MQ
- CRDT
- OBD 私有 CAN/BMS 逆向
- 复杂 MapLibre renderer
- speculative AI / social features
- Compose 代码重建 photorealistic Hero 光效

## 9. Authority Documents

- `PROJECT_MASTER.md`
- `PRODUCT.md`
- `FEATURE_MATRIX.md`
- `UIUX.md`
- `FRONTEND.md`
- `BACKEND.md`
- `DATABASE.md`
- `CI_CD.md`
- `ROADMAP.md`
- `DEVELOPMENT.md`
- `LOCATION_TRIP.md`
- `TRIP_GPS_RELIABILITY_AND_SPEED_VISUALIZATION.md`
- `VEHICLE_STATE_ARCHITECTURE_v0.5.md`
- `CHARGE_TRIP_UX_FLOW_v0.5.md`
- `V0.5_IMPLEMENTATION_ROADMAP.md`
- `V0.5_IMPLEMENTATION_STATUS.md`
- `VEHICLE_CATALOG_MULTI_VEHICLE.md`
- `DATA_QUALITY_BACKUP.md`
- `SYNC_PROTOCOL.md`
- `NEXT_PHASE_DESIGN.md`

维护规则：

1. 代码/CI/真机状态变化时先更新 owning Issue。
2. 执行顺序变化更新 ROADMAP。
3. 架构/阶段变化才更新 PROJECT_MASTER。
4. CI Green 不等于 physical acceptance。
5. 已完成历史 gate 不得继续写成 active blocker。
6. 新文档不得通过删减权威细节制造“更简洁但更不准确”的回归。

## 10. 当前完成定义

### v0.5 local experience closeout

需满足：

- Charge / Trip / VehicleState 核心状态闭环真机通过
- Trip background / stationary / max speed / route visualization 真机可信
- Dashboard / Records / Stats / Trip / Vehicle 五页完成 Dark/Light 视觉 pass
- 无 P0 state-safety / data-quality blocker
- Backup/Restore 覆盖当前核心事实
- 权威文档、Issue、main code 描述一致

之后再决定 v0.5 production release 与 sync expansion。

## 11. 变更记录

### v2.4.0

- 同步 Dark First UI #71 之后的完整 v0.5 主线。
- 同步 VehicleState #79、Smart Charge #81、Trip reliability #80/#82/#83/#85、Trip SOC/energy #87、Trip summary #88。
- 明确 Hero 使用最终生成图片资产，Compose 不继续模拟极光/反射。
- 将当前 blocker 从历史 #41 更正为 v0.5 physical acceptance #77/#78/#67/#66/#69/#42 与状态闭环真机验收。
- 同步 docs-only CI 与 branch protection #75 边界。

### v2.2.0

- 0.4 RC1 Trip reliability release gate 已完成。
- v0.5 local experience / state-safety 成为下一阶段。
