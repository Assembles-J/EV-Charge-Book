# EV Charge Book Location / Map / Trip Tracking Design

版本: v1.5.0
更新时间: 2026-09-02
状态: Authority Subdocument / Current Main + Physical Acceptance Pending

## 1. 目标

为 EV Charge Book 提供低成本、Local First、可诊断的定位与行程记录能力，记录真实驾驶轨迹并形成可解释的数据资产。

核心原则：

1. 定位记录与地图展示解耦。
2. 原始轨迹默认保存在本地 Room。
3. 不依赖单一地图厂商才能完成行程记录。
4. 用户必须明确启动记录；自动化只能在显式 opt-in 和 Android 后台限制允许的范围内演进。
5. 位置信息属于敏感数据，权限、通知和数据保留必须透明。
6. 记录可靠性优先于视觉完整度；真实缺失必须暴露，不能补造。
7. 不能用两端 GPS 点直连来伪造缺失路线；GPS gap / capture-clock rebase 必须显式断开。
8. 在没有道路类型、限速、map matching 或交通数据前，速度颜色只能表达本车可信速度分布，不能宣称真实拥堵等级。
9. GNSS、点间派生速度和未来 OBD 数据必须保留来源语义，不混成一个无来源数字。
10. Android civil time 与 monotonic time 分工明确：epoch 用于人类可读时间，elapsed realtime 用于新 Trip 的区间/顺序/健康判断。

详细可靠性与速度可视化方案见 `TRIP_GPS_RELIABILITY_AND_SPEED_VISUALIZATION.md`。当前物理可靠性 owner 为 #77。

---

## 2. 技术决策

### 2.1 定位核心

当前 production acquisition path：

```text
TripTrackingService (location foreground service)
  -> FusedLocationProviderClient / HIGH_ACCURACY / ~1Hz / minDistance=0
  -> 约 12s primary silent watchdog
  -> Platform GPS + Network fallback
  -> Trip continuity / quality / sampling rules
  -> Room TripPoint
```

关键边界：

- Google Fused 是 production 首选来源；
- non-GMS 或 Fused 注册失败/持续 silent 时回退 Android platform GPS/network；
- platform fallback 明确不信任某些 ROM 暴露但不回调的 framework `fused` provider；
- `LocationAvailability` 只作为诊断信号，不单独触发 Trip 中断、不伪造位置；
- provider fallback 只改变获取来源，不改变已经持久化的位置事实；
- 当前 source recovery 仍以 `Fused -> platform` 单向 failover 为基线；是否需要 bounded re-probe 由 #283 的真机证据决定。

Android Location 可提供：

- latitude / longitude
- altitude（设备支持时）
- speed
- bearing
- horizontal accuracy
- speed / altitude accuracy（系统支持时）
- epoch timestamp
- elapsed realtime timestamp

统一保存 WGS84 原始坐标；未来接入国内地图厂商需要 GCJ-02 时，仅在地图/provider adapter 层转换，数据库不混存坐标系。

### 2.2 TripPoint 双时间

Room v17 起，TripPoint 同时保存：

- `capturedAtEpochMillis`：来自 `Location.time`，用于日期、时钟展示、导出以及 legacy fallback；
- `capturedAtElapsedRealtimeNanos`：来自 `Location.elapsedRealtimeNanos`，在同一 boot 内用于新 Trip 的区间、顺序、长 gap 与显示派生时间轴。

原因：epoch/civil time 可能因用户校时、NTP/网络校时等前跳或后跳；它不适合成为新数据的首选 interval clock。Android elapsed realtime 在单次 boot 内单调递增，并包含 deep sleep 时间，更适合判断“两个 fix 实际相隔多久”。

v16 历史行与可移植备份恢复点没有可信的 boot-relative monotonic time，因此 v17 migration **不反推、不伪造** elapsed realtime；该字段保持 `NULL`，显式走 epoch fallback。

### 2.3 地图展示

地图 SDK 不是可靠记录成立的前提。MapLibre 仍可作为未来 basemap renderer；高德可作为可选中国地图 adapter，而不是核心记录依赖。

正式公开分发前，tile/provider 合规、成本和长期可用性必须单独评估。

---

## 3. 行程流程

```text
用户选择车辆
  -> Trip 准备页
  -> 检查定位权限 / 后台记录保护提示
  -> 用户明确开始
  -> 启动 location foreground service
  -> Fused / platform acquisition
  -> callback diagnostics + power/background diagnostics
  -> quality / continuity / stationary sampling
  -> 写入 Room TripPoint
  -> 实时更新可信距离 / 速度 / GPS health
  -> 用户结束
  -> completion flow
  -> 保存并展示概览 / 轨迹 / 数据
```

Android 13+ notification permission 拒绝不会阻止已经启动的 Trip；但用户会被告知锁屏时可能看不到 ongoing 状态。

仅获得 approximate/coarse location 时，Trip 准备页会提示开启精确定位。当前提示不强制阻塞用户显式开始，但汽车轨迹产品应把 precise location 视为推荐状态。

---

## 4. 采样与 callback 策略

### 4.1 callback 与持久化分层

历史真机数据证明，不能同时在系统 Location 层和业务层使用较大的位移门槛，否则停车时可能没有 callback，业务层也无法区分“车辆静止”与“定位回调死亡”。

当前系统 acquisition 层：

- Fused high-accuracy 目标约 1 Hz；
- min update distance = 0m；
- max update delay = 0；
- Fused 首次/后续持续约 12 秒 silent 时切 platform fallback；
- platform 直接注册 enabled GPS/network provider；
- callback 活性与写库频率解耦。

业务采样/写库层：

- `TripSamplingRules` 负责 accuracy、移动/静止、异常点和写库节流；
- 移动判定优先使用可信 reported speed，并结合距离证据；
- stationary GNSS drift 不应累计成行驶距离；
- 静止阶段约 15 秒级 heartbeat 保留必要事实，不要求每次 ~1Hz callback 都写 Room；
- moving -> stationary 的首个可信变化应及时落地，避免 UI 长时间残留旧速度。

所以当前不是“1Hz 永久写库”，而是：

```text
~1Hz acquisition/callback
  -> quality + continuity
  -> moving points / stationary heartbeat throttle
  -> Room
```

### 4.2 callback delivery 与 capture continuity 是不同维度

OEM/锁屏可能延迟派发真实 fix。因此 callback 到达晚不等于 capture gap。

当前：

- callback freshness 允许有限延迟，使用 `Location.elapsedRealtimeNanos` 与当前 `SystemClock.elapsedRealtimeNanos()` 判断年龄；
- callback delivery gap 使用 `SystemClock.elapsedRealtime()`；
- accepted TripPoint 之间的 continuity 使用 persisted capture elapsed realtime；
- 真正 `>=120s` 的 capture interval 继续硬断开；
- delayed historical point 若在 monotonic/epoch 证据上都落后于已经 accepted 的新点，继续拒绝。

---

## 5. 时间、连续性、距离与速度口径

### 5.1 Capture-time authority

相邻点的时间判断由 `TripCaptureTimeRules` 统一管理：

1. 两点都有有效 elapsed realtime 且 current > previous：使用 elapsed realtime delta，哪怕 civil clock 发生回拨；
2. elapsed 相等：视为 duplicate capture，拒绝；
3. elapsed 回退、但 epoch 前进：视为 reboot/monotonic-clock rebase，接受当前点作为新 baseline，但跨边界强制断段；
4. elapsed 与 epoch 都回退：视为 out-of-order historical point，拒绝；
5. 任一点缺少 elapsed（v16 历史/备份恢复）：显式回退 epoch interval；
6. legacy epoch fallback 若不递增，同样拒绝。

`LONG_GAP_SECONDS = 120` 不变。

capture-clock rebase 与真实 long gap 的共同原则：

- 当前点可以建立新的真实 baseline；
- 不跨边界累计距离；
- 不跨边界累计 moving/stopped duration；
- 不跨边界产生 aggregate/max speed；
- route / playback / trend / elevation accumulation 均保持断开；
- 不生成 synthetic point。

### 5.2 GPS health time

运行中的 GOOD / DEGRADED / LOST / LONG_GAP 是“多久没有 callback / accepted point”的状态，应使用 monotonic delivery clock，而不是 wall clock。

PR #281 后，live GPS health 使用 `SystemClock.elapsedRealtime()` 计算 callback/accepted age，因此手机校时不应制造假 LOST/LONG_GAP。

### 5.3 当前距离如何计算

`distanceMeters` 来自连续 accepted point 之间的可信地理直线距离累计。

它不是道路里程、轮速里程或 map-matched distance。在以下场景仍有误差：

- 弯曲道路采样离散；
- GPS 漂移；
- provider 切换；
- 长时间 callback/usable-location 缺失；
- coarse/approximate location。

`>=120s` real gap 或 capture-clock rebase 两端不计可信距离。

### 5.4 时间口径

Trip 区分：

- elapsed time：Trip 开始到结束的产品总耗时；
- moving time：可信连续区间内有效移动时间；
- stopped time：可信连续区间内静止时间。

moving/stopped 的 interval evidence 使用 monotonic capture delta；真实 gap/rebase 不被伪装成 moving/stopped。

### 5.5 当前速度来源

1. `Location.speed`：原始定位速度，保存在 TripPoint `speedMps`；
2. point-to-point distance/time：作为 continuity / quality corroboration，不冒充车辆瞬时速度；
3. route/trend：只使用通过 measured-speed trust gate 的 GPS 速度派生显示。

可信最高速度 candidate：

- provider = GPS；
- horizontal accuracy <= 25m；
- speed accuracy 存在时 <= 3 m/s；
- 同时通过连续距离/时间 corroboration；
- finite 且非负。

Network/coarse 原始速度可保留用于诊断，但不能刷新可信 max speed 或彩色速度 route。

---

## 6. GPS health 与诊断

PR #275/#280/#281 后，selected Trip 的自包含 CSV 能覆盖：

- Trip summary；
- 全部 persisted TripPoint；
- epoch + elapsed realtime capture time；
- 相邻点 delta / `timeAuthority` / `timeDecision`；
- long gaps；
- callback gaps；
- accepted/rejected/provider/source changes；
- Fused `LocationAvailability`；
- Fused registration / silent fallback reason；
- GPS health transitions；
- service start / redelivery / destroy；
- permission/provider failure；
- POWER_STATE：power save、device idle、interactive、battery optimization allowlist、background restricted、app standby bucket、location power-save mode、process importance；
- app/Android/OEM/device metadata。

诊断目标是回答：

```text
Service 死了吗？
callback 根本没来？
Fused 是否认为 location unavailable？
是否切到 platform？为什么？
callback 来了但 point 被拒绝？为什么？
是 civil clock 变化、真实 long gap，还是 monotonic rebase？
当时 Android 标准 power/background state 是什么？
```

诊断数据不授权产品补造缺失轨迹。

---

## 7. 中断、恢复与 source failover

真实设备必须考虑：

- App/Service 被系统销毁；
- foreground service 仍在但 callback starvation；
- Fused 注册成功但 silent；
- provider/location 权限被用户关闭；
- 地库/隧道无定位；
- 手机重启；
- OEM 后台管理导致定位调度变化。

TripSession 使用 `RECORDING / INTERRUPTED / COMPLETED`。

权限或无可用 provider 时：

```text
RECORDING
  -> INTERRUPTED
  -> repair notification / settings
  -> 用户明确恢复
```

不自动偷偷 resume，不静默创建第二条 Trip。

当前 source acquisition recovery：

```text
FUSED
  -> registration failure / ~12s silence / no GMS
  -> PLATFORM GPS + NETWORK
```

是否需要 `PLATFORM -> re-register -> optional FUSED re-probe` 的 bounded recovery 由 #283 管理。没有 current-main 真机失败证据前，不主动增加双向 provider complexity。

---

## 8. 后台与 OEM 保护

2026-09-02 同一 OnePlus PLU110 / Android API 36 / app 0.7.39 的 A/B：

- 普通后台 Trip：出现约 445s / 6.89km 与 261s / 4.55km 两个行驶中真实断流；
- 没有 `SERVICE_DESTROY` / `SERVICE_REDELIVERED`；
- standard Android snapshot 仍为 `backgroundRestricted=false`；
- 把 EV Charge Book 锁定在最近任务后，下一次长行程没有同类行驶中 gap。

因此当前 evidence 更符合 OEM/background location scheduling，而不是 Service 被直接杀死。

#278/#280 已实现：

- Trip 准备页 `后台记录保护`；
- 标准 Android background/battery state 检查；
- OnePlus/OPPO/realme 提示“允许后台活动 + 最近任务锁定”；
- 用户 acknowledgement 不会掩盖后来真正出现的 `backgroundRestricted=true`；
- acknowledgement 按 manufacturer/brand/API 作用，系统环境改变后可重新提示；
- 不申请 `ACCESS_BACKGROUND_LOCATION`；
- 不直接请求 battery-optimization exemption；
- 不使用大量 OEM 私有 deep link。

其他 OEM 的专项文案按 #283 真机矩阵逐步增加，不凭想象堆兼容代码。

---

## 9. GPS / Network Provider 质量

当前原则：

- GPS 高质量点优先作为主轨迹事实；
- Network point 可作为 fallback / continuity evidence；
- 近期 GPS/network 重复点由 continuity rule 去重/择优；
- Network/coarse speed 不刷新可信最高速度；
- Network/coarse speed 不给可信速度 route 上色；
- source/provider switch 保留诊断；
- 原始 TripPoint 不因派生统计过滤而被重写。

Fused 的 provider name 不等于“来源永远健康”；因此当前 watchdog 检测的是 callback silence，而不是“注册成功”这一布尔事实。

---

## 10. 海拔与轨迹可视化

海拔：

- 保存 raw altitude + vertical accuracy；
- 展示 start/end/min/max；
- cumulative ascent/descent 使用 vertical-accuracy filtering、jitter suppression；
- real long gap / capture-time rebase 两侧不拼接累计爬升/下降；
- 不宣称测绘级精度。

轨迹：

- `>=120s` real gap / rebase 拆成 disconnected segments；
- 无 basemap 时也必须断开；
- MapLibre 未来只负责 renderer，不负责 road snapping 或补造缺失路线；
- playback、trend X-axis 与 route segmentation 使用与 tracking 相同的 monotonic/legacy-fallback timing policy。

速度颜色只表示本车可信 GPS speed：深红 -> 红 -> 黄 -> 绿 -> 蓝；未知/不可信保持中性。

---

## 11. 自动化演进

当前已存在“指定车辆蓝牙连接 -> detection/prompt / 用户 opt-in auto-start”基础，但蓝牙连接不是“车辆正在移动”的事实。

原则：

- 手动/自动启动统一通过 Trip start coordinator；
- active Trip 存在时禁止创建第二条；
- Android 后台 FGS 限制必须真实验证；
- 蓝牙断开不直接静默结束 Trip；
- Activity Recognition / 可信连续位移只能作为未来第二证据；
- 不把普通 App 描述成拥有高德/OEM 系统级白名单能力。

详见 #235。

---

## 12. 地点、地址与隐私

- WGS84 坐标是原始位置事实；
- reverse geocoder 是展示派生，不是保存硬依赖；
- geocoder 失败显示 unavailable，不虚构地址；
- RECORDING 中不高频反查实时 endpoint；
- ongoing notification 默认不显示精确经纬度、HOME/WORK 地址；
- 云同步/分享精确轨迹必须另行获得用户同意；
- Privacy Zone 仍是后续分享能力。

---

## 13. 当前验收目标

### Code baseline 已完成

- [x] location foreground service
- [x] Fused high-accuracy + platform GPS/network fallback
- [x] ~1Hz acquisition baseline + stationary write throttle
- [x] user-started Trip / interrupted resume / completion
- [x] TripPoint raw coordinate/speed/accuracy/altitude/provider
- [x] Room v17 epoch + nullable elapsed realtime
- [x] 120s hard continuity boundary
- [x] no synthetic GPS points / no gap distance bridging
- [x] stationary drift protection
- [x] trusted max-speed gate
- [x] speed-colored route / playback / trend
- [x] trusted elevation analysis
- [x] ongoing GPS health + repair notification
- [x] per-Trip diagnostic CSV
- [x] Fused LocationAvailability / fallback diagnostics
- [x] extended Android power/background diagnostics
- [x] background recording guidance / approximate-location guidance

### #77 physical acceptance 仍未完成

- [ ] 在已有 v16 真机数据库上升级到 v17 并正常打开，历史 Trip/Charging 数据不丢失；
- [ ] 锁屏后继续行驶；
- [ ] 另一 App 前台 5–10 分钟继续行驶；
- [ ] 2–3 分钟停车/红灯；
- [ ] 一次真实 provider/location interruption + 手工恢复；
- [ ] no unexplained in-motion callback gap；
- [ ] 真正 >=120s gap 保持断开；
- [ ] new Trip CSV 常规 interval 主要显示 `ELAPSED_REALTIME` authority；
- [ ] civil clock 校时/回拨不制造假 LONG_GAP / LOST / route reversal；
- [ ] reboot/rebase 若发生则记录 `CAPTURE_TIME_REBASE` 且跨边界不造距离；
- [ ] precise/background guidance 真机可理解且不阻塞正常 Trip。

CI/JVM tests/Debug APK 不能替代这些结论。

---

## 14. Future / evidence-driven only

以下不作为当前代码 blocker：

- #283 多 OEM 真机矩阵；
- 只有证据证明需要时才做 bounded platform re-register / Fused re-probe；
- persistent longest-gap/provider/rejected summary；
- MapLibre basemap；
- 更长窗口的 TripSpeedSegmentBuilder；
- OBD-II Vehicle Speed 最小 PoC；
- Activity Recognition 作为自动 Trip 第二证据。

明确不做：

- 第二 tracking Service；
- WorkManager/Alarm 心跳保活；
- 长期 WakeLock；
- 跨 gap synthetic points；
- 无证据的 OEM 私有 deep-link 大全；
- 用放宽 trust threshold 掩盖 callback loss。

---

## 15. 变更记录

### v1.5.0

- 同步 2026-09-02 current `main`：Fused high-accuracy ~1Hz + 12s silent platform fallback。
- 同步 #275：per-Trip 自包含 GPS diagnostics。
- 同步 OnePlus A/B 证据与 #278：后台记录保护 / ColorOS-family 指引。
- 同步 #280：Fused `LocationAvailability`、source/fallback reason、扩展 POWER_STATE、approximate-location warning。
- 同步 #281 / Room v17：TripPoint 保存 epoch + elapsed realtime；新 Trip 的 interval/order/gap、callback gap、live GPS health、route/playback/trend/elevation/CSV timing 优先使用 monotonic time。
- 明确 v16/backup restored legacy points 使用 epoch fallback，不伪造 elapsed realtime。
- 明确 reboot/monotonic reset 使用 `CAPTURE_TIME_REBASE` 断开 baseline。
- `LONG_GAP_SECONDS = 120`、no synthetic points、no gap distance bridging 均未改变。
- current physical acceptance owner 仍为 #77；bounded source recovery / OEM matrix 由 #283 evidence-driven 管理。

### v1.4.0

- 同步 2026-08-27 第二轮真机 Trip 证据与 #77/#78 P0 reliability ownership。
- 同步 PR #80：取消旧 8m callback 位移门槛，系统 callback 与 stationary heartbeat 写库节流解耦。
- 同步 PR #82：可信 max-speed gate。
- 同步 PR #83/#85：海拔/address/endpoint 与 trusted speed-colored route。
- 明确 CI/代码完成不等于最终后台真机验收。

### v1.3.0

- 明确 Location.speed / derived / OBD 速度来源语义。
- 记录短时峰值漏采与 GPS 累计距离限制。
- 同步早期 GPS health、notification、gap route split。

### v1.2.0

- 基于真实 Trip #7 将十分钟级 GPS gap 提升为 P0 reliability。
- 建立 callback/provider/service lifecycle 可诊断设计。
- 明确 COMPLETED 不等于 GPS continuity 完整。
- 明确 GPS gap 不得以实线伪造。

### v1.1.0

- 增加 elapsed/moving/stopped 时间口径。
- 增加中断行程恢复、Privacy Zone 与自动记录演进设计。

### v1.0.0

- 建立定位 / 地图 / 行程追踪权威设计。
- 确定 Android Location + MapLibre 解耦架构。
- 明确手动开始 + foreground service。
