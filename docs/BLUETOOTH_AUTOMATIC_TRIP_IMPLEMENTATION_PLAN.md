# Bluetooth-triggered Automatic Trip — Detailed Implementation Plan

版本: proposal v0.1  
更新时间: 2026-08-31  
Tracking: #235  
Design proposal: `BLUETOOTH_AUTOMATIC_TRIP_DESIGN.md`

> 本文是 #235 / #236 的执行层补充。它不把自动开始描述为已实现能力，也不改变 `LOCATION_TRIP.md`、#26、#77 的现有权威边界。

## 1. 推荐决策

建议先批准以下默认决策，再进入实现：

1. **Phase 1 只发布 `PROMPT_ONLY`**：蓝牙连接只创建候选 session 和通知，由用户确认开始。
2. **Phase 2 的 `VERIFIED_AUTO_START` 必须是显式 opt-in 实验能力**，不能因用户只是绑定了车辆蓝牙而自动启用。
3. **所有 Trip 创建必须收敛到一个 `TripStartCoordinator`**，UI、通知 action、未来自动化都不得直接写 Trip。
4. **蓝牙断开永远不直接结束 Trip**。首版只进入 `POSSIBLE_END` 并提示 completion。
5. **自动开始前先做 shadow verification**：先只计算“如果开启会不会 start”，不真正创建 Trip，用真实数据校准阈值。
6. **任何通知不可见、权限不足、系统不允许后台启动 FGS 的场景都 fail closed**：降级为 prompt / 下次打开 App 提示，而不是静默创建 Trip。

## 2. 产品体验时序

### 2.1 PROMPT_ONLY

```text
Vehicle Bluetooth connected
        |
        v
BluetoothConnectionReceiver
        |
        v
AutoTripDetectionCoordinator
  - resolve vehicle binding
  - active-trip guard
  - dedupe connection epoch
  - create candidate session
        |
        v
Notification: “已连接车辆，是否开始行程？”
    | START_NOW              | IGNORE_SESSION
    v                        v
TripStartCoordinator      session=IGNORED
    |
    v
Trip persisted -> existing TripTrackingService -> ongoing notification
```

关键 UX：

- 点击通知正文：进入 Trip start confirmation / 当前 active Trip；
- `立即开始`：不要求再次进入首页；
- `本次忽略`：本 connection epoch 内不再重复提醒；
- 蓝牙短时重连：更新同一 session，不刷第二条通知；
- 如果已有 active Trip：不显示“开始新行程”，可直接进入当前 Trip。

### 2.2 VERIFIED_AUTO_START

```text
Bluetooth connected
      |
      v
candidate session
      |
      v
preflight checks
(notification / location / active trip / vehicle / FGS eligibility)
      |
      +--> fail -> PROMPT_ONLY / BLOCKED
      |
      v
VERIFYING_DRIVE (bounded window)
      |
      +--> insufficient evidence -> EXPIRED
      |
      +--> user START_NOW -> TripStartCoordinator
      |
      v
policy = START
      |
      v
TripStartCoordinator
      |
      v
RECORDING + immediate visible ongoing notification
```

自动开始必须保证：

- 用户能从通知看出“为何自动开始”；
- 用户能快速进入 active Trip；
- 误启动处理不是在后台静默删除，而是走明确的 false-start 规则；
- 一旦进入 RECORDING，后续定位完全复用现有 TripTrackingService，不维护第二条 tracking pipeline。

### 2.3 POSSIBLE_END

```text
Bluetooth disconnected
      |
      v
connection epoch closes
      |
      +--> no active trip -> close detection session
      |
      v
active trip for same vehicle
      |
      v
POSSIBLE_END
      |
      +--> reconnect within grace -> RECORDING
      |
      +--> still moving / unreliable location -> remain RECORDING
      |
      v
stable disconnect + likely stationary
      |
      v
Action-required notification
      |
      v
existing completion form
```

禁止：

- `ACTION_ACL_DISCONNECTED` 一到就完成 Trip；
- 在后台猜测 end SOC / mileage；
- 因 GPS 暂失而把 Trip 当成停车；
- 把短时车机重连当成两段行程。

## 3. 模块接口建议

### 3.1 VehicleBluetoothBindingRepository

```kotlin
interface VehicleBluetoothBindingRepository {
    suspend fun findByDeviceAddress(address: String): VehicleBluetoothBinding?
    suspend fun getForVehicle(vehicleId: Long): VehicleBluetoothBinding?
    suspend fun save(binding: VehicleBluetoothBinding)
    suspend fun remove(vehicleId: Long)
}
```

约束：

- 同一蓝牙地址默认只允许绑定一个 active vehicle；
- 车辆被归档/删除后绑定必须失效；
- UI 展示设备名，但业务匹配不能只依赖设备名；
- 地址读取失败时不得 fallback 成“同名设备即命中”。

### 3.2 AutoTripDetectionRepository

```kotlin
interface AutoTripDetectionRepository {
    suspend fun createCandidate(...): CreateSessionResult
    suspend fun getActiveForDevice(deviceKey: String): AutoTripDetectionSession?
    suspend fun transition(sessionId: String, expected: State, next: State): Boolean
    suspend fun attachTrip(sessionId: String, tripId: Long): Boolean
    suspend fun expireBefore(now: Long): Int
    suspend fun reconcile(now: Long): ReconcileResult
}
```

必须使用 compare-and-set / transaction 思路，避免：

- 两个 Receiver 事件同时创建两个 session；
- 通知点击与自动判定同时 start；
- 进程重建后重复执行旧 action。

### 3.3 TripStartCoordinator

```kotlin
sealed interface TripStartSource {
    data object ManualUi : TripStartSource
    data class BluetoothPrompt(val sessionId: String) : TripStartSource
    data class VerifiedAuto(val sessionId: String) : TripStartSource
}

data class TripStartRequest(
    val vehicleId: Long,
    val source: TripStartSource,
    val requestedAt: Long
)

sealed interface TripStartResult {
    data class Started(val tripId: Long) : TripStartResult
    data class AlreadyActive(val tripId: Long) : TripStartResult
    data class Blocked(val reason: String) : TripStartResult
    data class Failed(val reason: String) : TripStartResult
}
```

Coordinator 的原子边界至少包含：

1. 再次读取 active Trip；
2. 校验 vehicle 当前仍有效；
3. 读取 VehicleState 作为 start snapshot；
4. 创建唯一 active Trip；
5. session -> trip 关联；
6. 请求启动现有 tracking service；
7. 对 service-start failure 留下明确可恢复状态。

**禁止通知 Receiver 先 insert Trip、再异步检查 active Trip。**

### 3.4 AutoTripEligibilityPolicy

Policy 本身保持纯函数，输入完整状态，输出解释性 reason code。

建议 reason code：

```text
OFF
DEVICE_NOT_BOUND
ACTIVE_TRIP_EXISTS
SESSION_IGNORED
SESSION_EXPIRED
NOTIFICATION_PERMISSION_MISSING
LOCATION_PERMISSION_MISSING
PROVIDER_UNAVAILABLE
FGS_START_NOT_ALLOWED
BLUETOOTH_ONLY_INSUFFICIENT
WAITING_FOR_EVIDENCE
VERIFIED_BY_MOVEMENT
VERIFIED_BY_MOVEMENT_AND_ACTIVITY
```

reason code 用于：

- debug 日志；
- 本地 audit；
- notification copy；
- shadow-mode 统计；
- 真机验收报告。

## 4. Connection epoch 设计

蓝牙广播不是业务 session。必须建立 connection epoch：

```text
DISCONNECTED
   |
   | target connect event
   v
CONNECTED(epoch=N)
   |
   | duplicate connect/profile change
   +------ stay epoch=N
   |
   | target disconnect
   v
DISCONNECT_GRACE(epoch=N)
   |
   +--> reconnect in grace -> CONNECTED(epoch=N)
   |
   +--> grace expires -> CLOSED(epoch=N)

next stable connect -> epoch=N+1
```

建议规则：

- epoch 使用本地单调序号或 UUID，不用设备时间戳作为唯一键；
- wall clock 只做审计；duration 使用 elapsed realtime；
- A2DP / HEADSET / ACL 的多事件归并到同一 epoch；
- 进程死亡时若无法证明连接仍成立，恢复为 safe state，而不是直接继续 STARTING。

## 5. 数据迁移方案

当前全局 `BluetoothPromptPreferences(enabled/deviceAddress/deviceName)` 不应直接删除。

推荐迁移：

### v1 -> per-vehicle binding

首次升级读取旧配置：

- 若只有一个 active vehicle：允许提示用户“一键迁移到该车辆”；
- 若多车：进入设置页要求用户明确选择车辆；
- 不可自动猜测 vehicleId；
- 迁移成功后写新结构，并保留一次 migration marker；
- 失败时继续保留旧 prompt 能力，不静默关闭已有体验。

### detection session schema

新增表时：

- 不迁移任何历史连接事件；
- 默认空表；
- backup restore 不恢复 active / verifying session；
- restore 后只恢复用户设置，并重新校验权限和设备绑定。

## 6. Notification 状态表

| Detection state | 用户可见内容 | Actions | 是否 ongoing |
|---|---|---|---|
| `BLUETOOTH_CANDIDATE` | 已连接车辆，准备确认 | START_NOW / IGNORE | 否 |
| `VERIFYING_DRIVE` | 正在确认是否驾车 | START_NOW / IGNORE | 否 |
| `STARTING` | 正在开始行程 | OPEN_APP | 短暂 |
| `RECORDING` | 复用现有行程通知 | OPEN_ACTIVE_TRIP / existing actions | 是 |
| `POSSIBLE_END` | 可能已停车 | OPEN_END_CONFIRMATION | 否/高优先 action |
| `BLOCKED` | 无法自动开始 + 原因 | FIX / OPEN_SETTINGS | 否 |
| `EXPIRED/IGNORED` | 不保留陈旧通知 | none | 否 |

通知要求：

- 同一 session 使用稳定 notification ID；
- notification action 每次执行后重新查询 DB；
- action 过期时 no-op 并 cancel；
- 不在锁屏展示地址/坐标；
- 通知权限被拒绝时 `VERIFIED_AUTO_START` 不可进入可用状态。

## 7. Shadow verification 设计

Phase 2 之前必须先运行 shadow mode。

Shadow mode 做什么：

- 真实创建 candidate / verifying session；
- 收集与正式策略相同的有限 evidence；
- 运行 `AutoTripEligibilityPolicy`；
- 记录 hypothetical decision；
- **绝不调用 TripStartCoordinator 自动开始**。

建议每个 session 只保存：

```text
sessionId
vehicleId
epoch
candidateAt
firstTrustedMovementAt
hypotheticalStartAt
evidenceMask
finalDecisionReason
manualStartObservedAt?  // 用于粗略判断漏启动
ignoredByUser
```

不需要保存：

- verification 阶段的完整 GPS 轨迹；
- 精确家庭/公司位置；
- 可上传的原始 MAC。

### 初始研究指标

- candidate -> manual start 比例；
- hypothetical start -> manual start 的吻合度；
- manual start 发生但 hypothetical start 未发生的比例；
- hypothetical start 延迟 median / p95；
- duplicate candidate rate；
- reconnect churn；
- 每日 evidence window 总运行时间；
- FGS/preflight block 原因分布。

## 8. Phase 0 真机可行性记录模板

每台设备/每轮场景都记录：

```text
Phone / OEM:
Android version:
App build:
Vehicle / BT device:
Battery optimization state:
Notification permission:
Bluetooth permission:
Location permission/provider:

Scenario:
[ ] app foreground
[ ] app background
[ ] screen locked
[ ] process reclaimed
[ ] phone rebooted
[ ] user force-stopped

Observed:
- connect event received: Y/N
- time from BT connection to receiver: ___ ms
- session created once: Y/N
- notification visible: Y/N
- action works from lockscreen: Y/N
- FGS start allowed: Y/N/NA
- duplicate events: ___
- reconnect behavior:
- OEM-specific behavior:
- unexpected battery impact:
```

至少要有：

- 一台接近原生 Android / Samsung 类设备；
- 一台国产强后台限制 OEM；
- 一个真实车辆蓝牙；
- 一个普通耳机作为负例；
- 锁屏 + 后台 + 重连 + 地库弱定位场景。

## 9. 实施 PR 依赖图

```text
A model/policy spike
        |
        +----> B sessionized PROMPT_ONLY
        |             |
        |             +----> C unified TripStartCoordinator
        |                         |
        +----> D device feasibility ADR
                      |
                      +----> E shadow verification
                                  |
                                  +----> F experimental VERIFIED_AUTO_START
                                               |
                                               +----> G POSSIBLE_END assistant
```

合并约束：

- B 不依赖自动开始；
- C 可以在 Phase 2 之前独立完成，并降低手动/通知竞态风险；
- D 必须先于 F；
- E 必须先于 F；
- G 不作为 F 的 merge blocker，可独立后置。

## 10. 每个 PR 的 Definition of Done

### PR A — model / policy

- [ ] 新 mode / reason code / state model
- [ ] policy 纯 Kotlin 单测
- [ ] 无生产自动开始行为
- [ ] migration 方案评审

### PR B — PROMPT_ONLY sessionization

- [ ] per-vehicle binding
- [ ] connection epoch
- [ ] candidate session persistence
- [ ] START_NOW / IGNORE_SESSION
- [ ] stale action no-op
- [ ] duplicate connect 不刷通知
- [ ] active Trip 不创建第二条 Trip
- [ ] 进程重建后不会重复 start

### PR C — TripStartCoordinator

- [ ] 手动 UI 与通知统一入口
- [ ] active Trip 原子 guard
- [ ] VehicleState snapshot 不重复
- [ ] service-start failure 有状态和测试
- [ ] 并发测试证明最多一条 active Trip

### PR D — capability ADR

- [ ] Android/OEM 真机矩阵完成
- [ ] receiver / profile / Companion Device 方案明确
- [ ] force-stop / reboot / process-kill 支持声明明确
- [ ] 最低 Android 行为边界明确

### PR E — shadow verification

- [ ] bounded evidence window
- [ ] hypothetical decision only
- [ ] 本地 audit 可导出 debug summary
- [ ] 无 verification 原始轨迹长期持久化
- [ ] 耗电与延迟样本完成

### PR F — experimental auto-start

- [ ] explicit opt-in + 实验说明
- [ ] preflight fail closed
- [ ] multi-signal start
- [ ] immediate visible ongoing notification
- [ ] false-start path
- [ ] feature flag 可远离主路径关闭（本地开关即可，不要求云控）
- [ ] 无重复 Trip / 数据损坏

### PR G — possible-end

- [ ] disconnect grace
- [ ] reconnect 不分裂 Trip
- [ ] static/parking evidence
- [ ] completion deep link
- [ ] 不自动写 end SOC / mileage

## 11. Phase 2 发布门槛

进入 experimental rollout 前必须满足：

- Phase 1 在真机上无重复 Trip；
- notification 不刷屏；
- 并发手动 start / notification start 不会创建两条 Trip；
- FGS 被拒绝时不会留下“看似记录中但实际没采集”的假 active 状态；
- shadow mode 已有足够样本支持阈值决策；
- force-stop、通知拒绝、定位拒绝、provider off 都有明确降级行为；
- false-start 能安全处理且不污染 VehicleState；
- 用户可随时关闭 auto-start，关闭立即生效。

不建议在没有真实样本时预设“误启动率必须 < X%”。先采样，再在 #235 里把数字写成批准后的发布指标。

## 12. Rollback 方案

任何阶段出现问题时：

### Phase 1 rollback

- 设置 mode 强制回退 `OFF`；
- cancel 未完成 detection notifications；
- expire candidate/verifying sessions；
- 不影响已经 active 的正常 Trip。

### Phase 2 rollback

- 禁用 `VERIFIED_AUTO_START`；
- 已在 RECORDING 的 Trip 继续由现有 tracking service 正常记录；
- 不因 feature rollback 自动删除 Trip；
- 将未关联 Trip 的 verifying session 过期；
- 保留有限 audit 用于复盘。

### Schema rollback 原则

Room migration 一旦进入 production，不依赖 destructive downgrade。功能关闭只停用读取/写入路径，不尝试回滚数据库版本。

## 13. 建议先批准的 ADR 结论

为了避免 #235 长期停留在“研讨”，建议现在先批准 3 项：

1. **MVP = `PROMPT_ONLY`**。
2. **`TripStartCoordinator` 是所有开始入口的唯一 authority**。
3. **`VERIFIED_AUTO_START` 必须经过 capability matrix + shadow verification 后才能进入实验发布**。

其余具体阈值、Activity Recognition 是否引入、Companion Device 是否采用、reconnect grace 数值，等 Phase 0 样本后再批准。

## 14. 与高德地图体验参考的边界

可借鉴的是交互原则：

- App 不在前台时仍能在恰当时机给出低打扰信息；
- 通过持续、可更新的通知让驾驶状态可见；
- 用户不需要先打开首页再逐步导航到功能入口。

不应承诺或模仿：

- 红绿灯实时数据；
- OEM 私有“灵动岛/实况窗”能力；
- 读取其他 App 导航状态；
- 通过 Accessibility / 悬浮窗规避 Android 后台限制。

EV Charge Book 的核心目标是：**在标准 Android 能力边界内，把“连接车辆 -> 开始记录 -> 结束确认”做成可解释、可撤销、数据安全的自动化链路。**
