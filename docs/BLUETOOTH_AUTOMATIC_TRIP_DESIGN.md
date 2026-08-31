# Bluetooth-triggered Automatic Trip Detection

版本: proposal v0.1  
更新时间: 2026-08-31  
状态: Design Proposal / Feasibility Required  
Tracking: #235  
Related authority: `LOCATION_TRIP.md`, `VEHICLE_STATE_ARCHITECTURE_v0.5.md`

## 1. 决策摘要

本方案将“连接指定车辆蓝牙”定义为 **行程候选信号**，而不是行程事实。

推荐路线：

1. Phase 0 先验证 Android 版本、进程状态和真实车机下的蓝牙事件可靠性。
2. Phase 1 将当前“连接后通知”升级为可解释、可去重的 detection session，但仍由用户确认开始。
3. Phase 2 仅在用户额外 opt-in 且“蓝牙 + 可信驾驶证据”达标时自动开始。
4. 自动开始必须复用现有 `TripTrackingService`、Trip 数据模型和 ongoing notification。
5. 蓝牙断开只进入“疑似停车”，最终结束仍进入现有 completion form，由用户确认 end SOC / mileage。
6. 不引入第二套定位服务、云端追踪、无障碍服务、悬浮窗或读取其他 App 通知。

Phase 1 是安全 MVP。Phase 2 是否发布由真机误启动率、漏启动率、延迟与耗电证据决定。

## 2. 当前实现基线

截至本文创建时，仓库已有：

- `BluetoothConnectionReceiver`
  - manifest 静态注册；
  - 监听 `BluetoothDevice.ACTION_ACL_CONNECTED`；
  - 检查 `BLUETOOTH_CONNECT`；
  - 读取 `BluetoothPromptPreferences`；
  - MAC 地址命中时发送固定 ID `2101` 的车辆连接通知；
  - 点击后通过 `MainActivity.EXTRA_OPEN_TRIP_CONFIRMATION` 进入确认流程。
- `BluetoothConnectionStateChecker`
  - 查询 A2DP / HEADSET profile；
  - 用于 App 内检查目标设备是否已连接。
- `BluetoothPromptPreferences`
  - DataStore 中保存一个全局 `enabled / deviceAddress / deviceName`；
  - 当前没有 `vehicleId`、策略、冷却时间和 detection session。
- `TripTrackingService`
  - 已是唯一 location foreground service；
  - 已有 ongoing notification、GPS health、可信累计距离和中断修复逻辑。
- Trip completion
  - 结束需要用户确认 end SOC / mileage；
  - Trip 完成与 VehicleState 更新遵循现有事务和事件顺序规则。

当前能力是“连接后提醒”，不是自动行程检测。已知缺口：

- 仅凭 ACL connect，不能证明用户正在驾驶；
- 设置未与具体车辆建立稳定业务绑定；
- 没有 connect/disconnect/reconnect session；
- 没有 active Trip 幂等门；
- 没有“本次忽略”、冷却和误启动撤销；
- 没有 Android 进程被杀、force-stop、重启、OEM 后台限制的支持矩阵；
- 通知被拒绝时只会静默 return，不适合自动开始模式。

## 3. 产品模式

### 3.1 OFF

默认模式。不得监听并生成用户可见的自动化行为。

### 3.2 PROMPT_ONLY

指定设备连接后创建候选 session，并发送一条通知：

- 立即开始
- 本次忽略
- 打开设置

这是 Phase 1 默认且推荐的模式。

### 3.3 VERIFIED_AUTO_START

用户单独开启。蓝牙连接后进入验证窗口；只有达到组合证据门槛才自动开始。

自动开始后必须立即显示 ongoing notification，并提供“误启动”入口。没有通知权限、精确定位权限或前台服务启动条件时，必须降级到 `PROMPT_ONLY` 或明确显示不可启用，不得静默记录。

### 3.4 CONNECT_IMMEDIATELY

不进入首版。仅保留为开发/实验策略，不面向普通用户。单一蓝牙信号的误触发成本过高。

## 4. 用户流程

### 4.1 首次配置

车辆详情 -> 自动行程：

1. 选择当前车辆；
2. 选择已配对的车载蓝牙设备；
3. 开启“连接车辆后提醒”；
4. 查看权限与限制说明；
5. 完成一次测试连接；
6. 默认保存为 `PROMPT_ONLY`。

启用 `VERIFIED_AUTO_START` 时再单独说明：

- 可能漏记或误启动；
- 必须允许通知和定位；
- 不同厂商后台行为不同；
- 自动开始后可撤销；
- 强制停止 App 后不会保证自动检测。

不得在用户仅选择设备时顺带启用自动开始。

### 4.2 连接候选

通知文案：

> 已连接「零跑 C16」  
> 正在确认是否开始驾车  
> [立即开始] [本次忽略]

规则：

- 同一 connection epoch 只创建一个 session；
- 重复 ACL/profile 事件只更新同一 session；
- active Trip 存在时不生成开始通知；
- 目标设备不匹配时不生成 session；
- 用户“本次忽略”后，直到本次断开并进入新 epoch 前不再提示。

### 4.3 自动验证

`VERIFIED_AUTO_START` 下：

1. 收到目标蓝牙连接；
2. 建立 detection session；
3. 检查权限、active Trip 和服务启动条件；
4. 在有限窗口内收集驾驶证据；
5. 达到阈值则创建 Trip；
6. 立即切换为现有 tracking ongoing notification；
7. 未达到阈值则过期，不创建 Trip。

不得为了等待驾驶证据而无限保持后台定位。

### 4.4 疑似停车

蓝牙断开时：

- 若没有 active Trip：结束 detection session；
- 若存在该车辆 active Trip：进入 `POSSIBLE_END`；
- 短时重连：恢复 `RECORDING`，不提示结束；
- 持续断开且可信静止：通知“可能已停车”；
- 点击进入现有 completion form；
- 后台不得直接写 `COMPLETED`。

## 5. 状态机

```text
DISABLED
  -> IDLE
  -> BLUETOOTH_CANDIDATE
  -> VERIFYING_DRIVE
  -> READY_TO_START
  -> STARTING
  -> RECORDING
  -> POSSIBLE_END
  -> END_CONFIRMATION
  -> IDLE

任意非 RECORDING 状态
  -> IGNORED
  -> IDLE（新 connection epoch 后）

VERIFYING_DRIVE
  -> EXPIRED
  -> IDLE

STARTING
  -> START_FAILED
  -> PROMPT_ONLY fallback / IDLE
```

### 5.1 状态不变量

- 同一设备同一时刻最多一个 active detection session。
- 同一设备同一 connection epoch 最多一次开始动作。
- 全局最多一个 active Trip，沿用现有约束。
- `RECORDING` 必须对应一个已持久化 Trip ID。
- `POSSIBLE_END` 不改变 Trip 状态为 `COMPLETED`。
- 进程重建后从持久状态恢复，不从通知内容推断业务状态。
- 所有开始动作最终经过同一 `TripStartCoordinator` 幂等入口。

## 6. 建议数据模型

### 6.1 Vehicle automation settings

Phase 1 可以继续使用 DataStore，但设置必须按车辆绑定：

```kotlin
enum class AutoTripMode {
    OFF,
    PROMPT_ONLY,
    VERIFIED_AUTO_START
}

data class VehicleAutoTripSettings(
    val vehicleId: Long,
    val mode: AutoTripMode,
    val bluetoothAddress: String,
    val bluetoothName: String?,
    val activityRecognitionEnabled: Boolean,
    val updatedAtEpochMillis: Long
)
```

如果当前产品允许多车，应避免继续使用单一全局 `deviceAddress`。同一蓝牙设备默认只能绑定一辆未归档车辆；冲突必须让用户明确选择。

DataStore 适合保存偏好；不要把 detection session 塞进偏好字符串。

### 6.2 Detection session

推荐新增 Room 表，以支持进程重建、审计和幂等：

```kotlin
@Entity(
    tableName = "auto_trip_detection_sessions",
    indices = [
        Index("state"),
        Index(value = ["deviceAddressHash", "connectionEpoch"], unique = true)
    ]
)
data class AutoTripDetectionSessionEntity(
    @PrimaryKey val id: String,
    val vehicleId: Long,
    val deviceAddressHash: String,
    val deviceNameSnapshot: String?,
    val connectionEpoch: Long,
    val mode: String,
    val state: String,
    val triggerSource: String,
    val connectedAt: Long,
    val verificationStartedAt: Long?,
    val expiresAt: Long?,
    val ignoredAt: Long?,
    val tripId: Long?,
    val lastEvidenceAt: Long?,
    val evidenceMask: Long,
    val failureReason: String?,
    val updatedAt: Long
)
```

隐私规则：

- 不需要在此表保存经纬度；
- MAC 如无业务查询必要，持久 session 只保存不可逆 hash；
- 原始地址仍仅保留在车辆自动化设置中；
- session 采用有限保留，例如 30 天后只保留聚合指标或清理；
- backup 是否包含设置与 session 必须分开决定：设置可以备份，短期诊断 session 默认不备份。

### 6.3 Evidence

```kotlin
data class DriveEvidence(
    val bluetoothConnected: Boolean,
    val inVehicleConfidence: Int?,
    val trustedDisplacementMeters: Double?,
    val trustedSpeedMps: Double?,
    val locationAccuracyMeters: Float?,
    val observedAt: Long
)
```

Evidence 只用于判断，GPS 原始事实仍由现有 Trip tracking 模型负责。Phase 0/1 不新增伪精确评分。

## 7. 组件设计

建议新增以下边界，名称可在实现时按现有包结构调整：

```text
bluetooth/
  BluetoothConnectionReceiver
  VehicleBluetoothBindingRepository
  BluetoothConnectionEpochTracker

autotrip/
  AutoTripDetectionCoordinator
  AutoTripDetectionRepository
  AutoTripEligibilityPolicy
  DriveEvidenceCollector
  AutoTripNotificationController
  AutoTripActionReceiver
  TripStartCoordinator
```

### 7.1 BluetoothConnectionReceiver

职责收窄为：

1. 校验 action 和权限；
2. 提取设备标识；
3. 使用 `goAsync()`；
4. 把事件交给 `AutoTripDetectionCoordinator`；
5. 不直接创建 Trip，不直接包含完整产品策略。

接收器必须有短执行上限。耗时定位验证不得长期运行在 broadcast 生命周期内。

### 7.2 AutoTripDetectionCoordinator

统一处理：

- connect/disconnect；
- connection epoch；
- settings 与 vehicle binding；
- active Trip guard；
- session 创建与幂等；
- prompt、ignore、expire；
- 启动验证或降级；
- 进程重建后的 session reconciliation。

### 7.3 AutoTripEligibilityPolicy

使用纯 Kotlin policy，便于 JVM test：

```kotlin
sealed interface AutoTripDecision {
    data object Ignore : AutoTripDecision
    data class Prompt(val reason: String) : AutoTripDecision
    data class Verify(val expiresAt: Long) : AutoTripDecision
    data class Start(val reason: String) : AutoTripDecision
    data class Block(val reason: String) : AutoTripDecision
}
```

输入必须显式包含：

- 用户模式；
- 通知/蓝牙/定位权限；
- active Trip；
- 设备是否绑定；
- 当前 session；
- Android/系统启动能力；
- 驾驶证据；
- 冷却和忽略状态。

### 7.4 TripStartCoordinator

手动开始、通知确认和未来自动开始必须收敛到同一入口：

```kotlin
suspend fun startTrip(request: TripStartRequest): TripStartResult
```

必须在一个幂等事务/临界区内：

1. 查询 active Trip；
2. 验证 vehicle 未归档；
3. 快照 VehicleState 的 start SOC / mileage；
4. 创建唯一 TripSession；
5. 把 detection session 关联到 tripId；
6. 启动现有 `TripTrackingService`；
7. 若服务启动失败，留下可解释的 interrupted/start-failed 状态，不创建第二条 Trip。

不要让 `BroadcastReceiver`、通知 action 和 UI 各自复制 Trip 创建逻辑。

### 7.5 DriveEvidenceCollector

Phase 2 前才实现。优先低成本、有限窗口：

- 蓝牙连接必须持续成立；
- Activity Recognition 可选；
- 位置证据必须经过现有可信质量门；
- 验证窗口结束即停止；
- 不将 network 粗速度当作强证据；
- 不在没有权限时反复弹权限请求。

建议 Phase 0 从观测模式开始：只记录“如果启用策略会如何决策”，不自动开始 Trip，以校准阈值。

## 8. 触发与阈值策略

阈值必须通过真机数据校准，以下仅为 spike 初始假设，不是产品常量：

- verification window：约 90-180 秒；
- 防抖：同一设备短时重复连接合并为同一 epoch；
- reconnect grace：约 60-120 秒；
- verified start 至少满足：
  - 目标蓝牙仍连接；
  - 位置权限和 provider 可用；
  - 连续可信位移或可信速度达到门槛；
  - 可选 `IN_VEHICLE` 增强置信度；
  - 不存在 active Trip；
- 仅 `IN_VEHICLE` 或仅单点速度不得自动开始。

所有阈值通过 `AutoTripPolicyConfig` 集中定义，禁止散落在 Receiver、Service 和 UI。

## 9. Android 权限与平台边界

| 能力 | 相关权限/条件 | 无法满足时 |
|---|---|---|
| 识别指定蓝牙 | Android 12+ `BLUETOOTH_CONNECT` | 功能不可启用或降级 |
| 发送可见通知 | Android 13+ `POST_NOTIFICATIONS` | 禁止 VERIFIED_AUTO_START |
| 收集精确驾驶证据 | Fine Location + provider | 只允许 PROMPT_ONLY |
| 启动位置 FGS | 平台允许的启动上下文、FGS location 权限 | 显示 start failed / 等待用户打开 |
| Activity Recognition | 单独运行时权限（如引入） | 不作为必需信号 |

必须通过 Phase 0 验证而不是文档假定：

- 静态 receiver 在目标 Android 版本是否稳定收到 ACL 事件；
- App 被系统回收与用户 force-stop 的差异；
- 从后台蓝牙事件启动 location FGS 是否允许；
- 手机重启后的设置与状态恢复；
- 小米、华为、OPPO/vivo、三星等 OEM 的后台限制；
- Companion Device APIs 是否能带来足够可靠性，是否值得新增配对/关联 UX。

明确声明：用户在系统设置中“强行停止”后，Android 通常会抑制组件直到用户再次启动 App；产品不得宣传 force-stop 后仍可靠自动检测。

## 10. 通知设计

### 10.1 Channel

延续 #26 的分层：

- `vehicle_detection`：候选、验证、忽略；
- `trip_tracking`：现有 ongoing Trip；
- `trip_action_required`：疑似停车、结束确认；
- `trip_warning`：现有 repair/warning。

若迁移现有 `vehicle_connection` channel，需考虑用户已配置的 channel 偏好，不应无理由重复创建多个近似 channel。

### 10.2 Notification IDs

禁止所有车辆/session 永久共用一个固定通知 ID。建议：

- detection：由 session ID 稳定派生；
- tracking：由 active trip ID 稳定派生或沿用现有唯一 ongoing ID；
- action required：由 trip ID 派生；
- 同一 session update 使用同一 ID；
- session 完成/忽略时明确 cancel。

### 10.3 Actions

所有 action 必须：

- 使用 immutable `PendingIntent`；
- 包含 session ID / trip ID，不只传布尔 extra；
- Receiver 端重新读取数据库并校验当前状态；
- 幂等；
- 过期 action no-op 并清理陈旧通知。

动作：

- `START_NOW`
- `IGNORE_SESSION`
- `OPEN_SETTINGS`
- `OPEN_ACTIVE_TRIP`
- `REPORT_FALSE_START`
- `OPEN_END_CONFIRMATION`

“误启动”不应直接删除一个已有可信点的行程。撤销规则见下一节。

## 11. 误启动撤销

推荐规则：

1. 自动开始后在短时间且可信移动不足时，允许“标记误启动并删除”；
2. 一旦已有明显可信行驶证据，动作改为“打开行程”，由用户决定结束/删除；
3. 删除必须复用现有 Trip 删除事务，清理 points/diagnostics；
4. 不更新 VehicleState；
5. detection session 标记 `FALSE_START`，用于本地评估；
6. 不把误启动 Trip 导出为正常历史记录。

具体时间/距离边界需要真机研究，不能仅以 elapsed time 判断。

## 12. 并发与故障处理

| 场景 | 预期 |
|---|---|
| connect 事件重复 | 同一 epoch 幂等 |
| A2DP 与 HEADSET 分别连上 | 合并为同一车辆 session |
| 自动开始与用户手动开始竞态 | `TripStartCoordinator` 只创建一条 Trip |
| 已有其他车辆 active Trip | block 并给出明确原因，不自动切车 |
| 车辆已归档/删除 | session 失效并清理通知 |
| 进程在 VERIFYING 时被杀 | 重建后过期或安全恢复，不直接推断 Start |
| FGS 启动失败 | Trip 标记可解释状态并提示修复，不循环重试 |
| 蓝牙短时断连 | grace 内不结束 Trip |
| 通知 action 延迟点击 | 重新校验 session，过期则 no-op |
| 系统时间变化 | duration 使用 elapsed realtime；持久审计保留 wall clock |
| backup/restore | active detection session 不恢复；设置需重新校验设备与权限 |

## 13. 数据与隐私

- 所有判断默认 Local First。
- 不上传 MAC、轨迹或驾驶行为。
- 锁屏通知不显示坐标、HOME/WORK 地址。
- audit 只保存触发原因、状态、时间和聚合 evidence mask。
- 原始定位仍只进入现有 TripPoint 模型；未创建 Trip 前原则上不持久化验证轨迹。
- 导出/备份必须明确区分用户设置与短期诊断数据。
- 用户关闭功能时应取消候选 session 和通知，并停止尚未完成的 evidence collection；不得影响已在记录的 Trip。

## 14. 测试方案

### 14.1 JVM unit tests

`AutoTripEligibilityPolicy`：

- OFF 永不提示；
- 未绑定设备忽略；
- active Trip block；
- PROMPT_ONLY 永不自动开始；
- 通知权限缺失不能 VERIFIED_AUTO_START；
- 蓝牙单信号不足；
- 组合证据达到阈值才 Start；
- ignored epoch 不再提示；
- session 过期；
- reconnect grace；
- start action 幂等。

### 14.2 Room / repository tests

- unique connection epoch；
- session 状态合法迁移；
- detection -> trip 关联原子性；
- 进程重建 reconciliation；
- false-start 删除不更新 VehicleState；
- 多车辆绑定冲突；
- 数据库 migration 与 backup codec 策略。

### 14.3 Instrumentation tests

- Receiver 非目标 action/设备 no-op；
- Android 12+ 蓝牙权限缺失；
- Android 13+ 通知权限缺失；
- notification action deep link；
- stale PendingIntent；
- concurrent manual/auto start；
- service start failure；
- notification update/cancel。

### 14.4 真机矩阵

至少覆盖：

- Android 11、12/12L、13、14、15+ 项目支持版本；
- App 前台、后台、进程被系统回收、重启后、force-stop 后；
- 锁屏；
- 通知允许/拒绝；
- 定位允许/拒绝/provider off；
- 真实车辆蓝牙与普通耳机；
- 目标设备重连抖动；
- 多设备同时连接；
- 城市道路红灯静止；
- 地库/隧道；
- 至少两个 OEM。

每次记录：

- connect event 是否到达；
- session 是否唯一；
- 通知延迟；
- 开始延迟；
- 是否误启动/漏启动；
- FGS 是否成功；
- callback continuity；
- 额外耗电；
- disconnect/end prompt 行为。

## 15. 观测与发布门

Phase 2 前应先提供仅本地 debug/实验统计：

- candidate count；
- prompt accepted/ignored；
- hypothetical verified start；
- actual start；
- false start；
- missed start（需要用户标记或与手动开始对照）；
- median / p95 start delay；
- duplicate event count；
- FGS start failure count；
- 每小时验证窗口耗电近似。

建议发布门：

- Phase 1 无重复 Trip、无通知刷屏、无 active Trip 冲突；
- Phase 2 在目标真机样本内没有已知数据损坏；
- 误启动率和漏启动率达到团队在 #235 明确批准的阈值；
- 自动开始额外耗电可接受；
- 所有失败都可解释、可恢复；
- 用户可在设置中关闭并立即生效。

本文不预设百分比目标，避免在没有样本前制造精确指标。

## 16. 实施拆分建议

### PR A — model and policy spike

- 新增 settings model；
- 新增 detection state / policy；
- JVM tests；
- 不改生产触发行为；
- 输出 Android capability matrix 初稿。

### PR B — Phase 1 sessionized prompt

- Receiver 委托 coordinator；
- vehicle binding；
- detection session；
- notification actions；
- ignore / dedupe / expiry；
- 保持用户确认开始。

### PR C — unified TripStartCoordinator

- 手动与通知开始收敛；
- active Trip 原子 guard；
- VehicleState snapshot；
- FGS failure path；
- concurrency tests。

### PR D — Phase 0 device evidence

- 真机执行矩阵；
- ADR 选择 receiver / Companion Device / fallback；
- 确定最低支持与 OEM 声明；
- 不实现自动开始。

### PR E — shadow verification

- 有限时间 evidence collector；
- 只记录 hypothetical decision；
- 不自动创建 Trip；
- 校准阈值与耗电。

### PR F — experimental verified auto-start

- opt-in feature flag；
- 自动开始；
- immediate notification；
- false-start flow；
- 小范围真机 acceptance。

### PR G — possible-end assistant

- disconnect epoch / reconnect grace；
- POSSIBLE_END；
- deep link completion；
- 不后台完成 Trip。

每个 PR 必须小而可回滚。不得在一个 PR 同时改蓝牙绑定、Trip 数据事务、定位采样、通知视觉和停车判断。

## 17. ADR 待决策

Phase 0 结束时，在 #235 或独立 ADR 中批准：

1. 生产监听机制：ACL receiver、profile state、Companion Device 或组合；
2. 最低 Android 版本与支持矩阵；
3. 是否引入 Activity Recognition；
4. settings 使用 DataStore 还是 Room；
5. detection session 保留时间；
6. verified-start evidence 组成与阈值；
7. false-start 安全删除边界；
8. reconnect grace；
9. notification channel 迁移；
10. Phase 2 发布指标；
11. 不支持 force-stop 的产品说明；
12. backup/restore 对设置和 session 的处理。

## 18. Authority boundary

在 #235 完成研讨并批准 ADR 前：

- 本文是方案，不覆盖 `LOCATION_TRIP.md` 当前“用户明确开始”的权威基线；
- 不将自动开始写成已实现；
- 不改变 #26、#77 的既有实现/真机验收职责；
- 不将高德地图的红绿灯或 OEM 级实时活动能力列为本项目承诺。

批准并实现某个阶段后，再以小型文档更新同步 `LOCATION_TRIP.md`、`FEATURE_MATRIX.md` 与 `ROADMAP.md`，避免提前把 proposal 写成 shipped fact。
