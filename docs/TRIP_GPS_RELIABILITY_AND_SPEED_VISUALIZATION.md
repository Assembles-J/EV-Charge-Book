# Trip GPS Reliability & Speed Visualization

版本: v1.4.0
更新时间: 2026-09-02
状态: Code Baseline Complete / Post-#281 Physical Revalidation Pending

## 1. 文档定位

本文档是 EV Charge Book 行程模块中 **GPS 可信度、轨迹连续性、速度可信度、后台可靠性与轨迹可视化** 的实现/验收说明。

历史 #80/#184 证据仍保留，但 current runtime authority 已继续演进到 #275/#278/#280/#281：

- Fused high-accuracy ~1Hz acquisition；
- 约 12s Fused silent watchdog -> platform GPS/network fallback；
- per-Trip 自包含 diagnostics；
- OEM/background recording guidance；
- Fused `LocationAvailability` / fallback reason / extended POWER_STATE；
- Room v17 epoch + `elapsedRealtimeNanos` dual-time TripPoint；
- monotonic time 用于新 Trip 的 interval/order/gap、callback gap、live GPS health 和所有相关显示派生。

当前 physical reliability owner 仍是 #77。CI 和 JVM tests 不能替代真机结论。

相关 authority：

- `docs/LOCATION_TRIP.md`
- `docs/TRIP_V0.6_APPROVED_UI_BASELINE.md`
- `docs/ROADMAP.md`
- #26 background/notification UX
- #77 Trip physical reliability
- #283 bounded source recovery / OEM matrix（evidence-driven）

设计原则：

1. GPS / Room 中保存的事实优先于视觉效果。
2. 不跨真实 GPS gap 或 capture-clock rebase 伪造路线、距离或速度。
3. Network/coarse provider 可以作为 fallback/diagnostic evidence，但不能污染可信最高速度或可信速度彩色轨迹。
4. UI 的速度颜色只表达本车可信 GPS 速度分布，不代表道路拥堵、限速或交通状态。
5. 地图 SDK、road snapping、云端轨迹处理都不是当前可靠性成立的前提。
6. **callback delivery 与 location capture 是两个不同的时间维度**：delivery liveness 用 monotonic delivery clock；新 Trip 的 capture interval 优先使用 persisted `Location.elapsedRealtimeNanos`。
7. civil/epoch timestamp 继续作为人类可读和可导出的事实，但不再是新 Trip 的首选 interval/order authority。

---

## 2. 历史真实数据证据

这些数据保留作为 trust rules 的来源，不代表 current main 仍存在相同缺陷。

### 2.1 Trip #7：十分钟级 GPS 空洞

历史 Trip #7：

- distanceMeters: 42643.19 m
- elapsedSeconds: 4137 s
- movingSeconds: 4090 s
- stoppedSeconds: 20 s
- averageSpeedMps: 10.4262 m/s（约 37.5 km/h）
- maxSpeedMps: 25.92 m/s（约 93.3 km/h）
- status: COMPLETED

曾出现：

- Point 64 -> 65：约 749 秒
- Point 70 -> 71：约 917 秒

由此确定：

- `COMPLETED` 只代表行程最终收口，不代表中间 GPS 连续；
- 大 gap 两端不得画成可信连续路线；
- gap 期间不得补造可信距离、移动时间或速度。

### 2.2 2026-08-27 Trip #2：后台空洞、停车误判和假最高速

历史实机证据：

- distanceMeters: 约 24.20 km
- elapsed: 约 83 分 45 秒
- moving: 约 51 分 25 秒
- stoppedSeconds: 仅 15 秒
- UI 曾出现约 122.4 km/h 的最高速度

典型 continuity 证据：

- Point 42 -> 43：约 643 秒移动空洞；
- Point 109 -> 110：约 465 秒，恢复时出现 coarse network fallback；
- 约 140 秒红灯样本暴露旧 `8m` LocationManager gate 与 15 秒 stationary heartbeat 的冲突。

典型假速度点：

- provider: `network`
- horizontal accuracy: 约 100m
- speed accuracy: null
- reported speed: 34.005 m/s（约 122.4 km/h）

这些证据推动了 #80/#82/#85 等修复。

### 2.3 2026-08-29：PR #80 后仍有锁屏长缺口

真实设备行程在锁屏后完成，详情仍出现 2 个长缺口。

结论：

- 移除 8m displacement gate 还不足以覆盖全部 OEM/screen-off 行为；
- Android 可能已经采集真实 fix，但延迟派发；
- callback freshness 不能和 route continuity 共用同一个极短窗口。

这推动了 #184 的 delayed callback grace，而不是重新设计 tracking 架构。

### 2.4 2026-09-02 OnePlus A/B：Service 未死但后台 callback 被压制

同一 OnePlus PLU110 / Android API 36 / app 0.7.39：

**Trip 30，普通后台处理：**

- 278 个诊断事件；
- 2 个真实行驶中长缺口：约 445s / 6.89km 与 261s / 4.55km；
- 无 `SERVICE_DESTROY`；
- 无 `SERVICE_REDELIVERED`；
- 标准 Android snapshot 仍显示 `backgroundRestricted=false`。

**Trip 31，EV Charge Book 锁定在最近任务：**

- 40 个诊断事件；
- 没有同类行驶中长缺口；
- 唯一 >120s gap 出现在车辆已近乎静止、结束 Trip 前。

当前最强证据因此指向 **OEM/background location scheduling/callback starvation**，而不是前台 Service 被 Android 直接杀掉。

这组证据触发 #278/#280，不授权第二 Service、WorkManager 或 synthetic route。

---

## 3. 当前 acquisition / callback / stationary baseline

### 3.1 当前 source path

```text
location foreground service
  -> Google Fused high accuracy ~1Hz / minDistance=0
  -> Fused silent ~12s watchdog
  -> platform GPS + network fallback
  -> Trip continuity / quality / sampling
  -> Room
```

当前边界：

- non-GMS -> platform fallback；
- Fused registration failure -> platform fallback；
- Fused 注册成功但持续 silent -> platform fallback；
- platform 只使用真实 enabled GPS/network，不依赖 framework `fused`；
- `LocationAvailability` 只是诊断，不是 Trip truth/interruption authority；
- 当前不自动从 platform 高频来回切回 Fused；是否需要 bounded re-probe 由 #283 的真机证据决定。

### 3.2 callback 与 Room 写入分层

系统 acquisition/callback 可以约 1Hz；Room 不要求每个 callback 都写。

- minDistance=0；
- moving point 依据 trust/sampling 正常保留；
- stationary GNSS drift 不累计行驶距离；
- stationary heartbeat 约 15 秒级写入；
- moving -> stationary 的首个可信变化应及时保存。

这保持了 callback liveness 可诊断，同时控制耗电、数据量和漂移距离。

### 3.3 delayed callback freshness

#184 后 callback-delivery freshness tolerance 放宽到 10 分钟，但这不等于 10 分钟 route continuity。

当前 callback freshness 使用 `Location.elapsedRealtimeNanos` 对 `SystemClock.elapsedRealtimeNanos()` 判断，而不依赖 wall clock。

真实 `>=120s` capture gap 仍硬断开。

---

## 4. Dual-time capture authority — #281 / Room v17

### 4.1 为什么不能继续只用 `Location.time`

`Location.time`/epoch timestamp 是重要的 civil-time fact，但手机时钟可能被用户或系统校正。如果把它作为唯一 ordering/interval clock，会出现：

- wall clock 回拨 -> 假时间倒序；
- wall clock 前跳 -> 假长 gap；
- notification/GPS health 可能因校时误报 LOST/LONG_GAP；
- playback/trend X-axis 可能反向或被拉长。

所以 #281 将时间语义拆开。

### 4.2 TripPoint v17

每个新 TripPoint 保存：

- `capturedAtEpochMillis`：human-readable/export fact；
- `capturedAtElapsedRealtimeNanos`：同一 boot 内 monotonic interval/order authority。

v16 历史行与 portable backup restored point 无法诚实重建 boot-relative elapsed realtime，因此新列保持 `NULL` 并显式 epoch fallback。

### 4.3 `TripCaptureTimeRules`

相邻两点规则：

1. elapsed 都存在且 current > previous -> `ELAPSED_REALTIME`；
2. elapsed 相等 -> duplicate，拒绝；
3. elapsed 回退但 epoch 前进 -> `ELAPSED_REALTIME_REBASE`，视为 reboot/monotonic reset，当前点只建立新 baseline；
4. elapsed 与 epoch 都回退 -> out-of-order historical point，拒绝；
5. elapsed 缺失 -> `EPOCH_FALLBACK`；
6. legacy epoch fallback 不递增 -> 拒绝。

### 4.4 Rebase 不等于可拼接

`CAPTURE_TIME_REBASE` 是一个 hard disconnected baseline：

- 接受当前真实点；
- 不跨边界计 distance；
- 不跨边界计 moving/stopped duration；
- 不跨边界算 aggregate/max speed；
- route/playback/trend/elevation 均断开；
- 不生成 synthetic point。

`LONG_GAP_SECONDS = 120` 保持不变。

---

## 5. GPS health 与 callback gap

运行时区分：

- tracking start age；
- last callback age；
- last accepted point age；
- recent accepted provider；
- rejected evidence；
- source/provider transitions；
- service lifecycle；
- permission/provider repair state。

#281 后，live GPS health 使用 `SystemClock.elapsedRealtime()`：

- WAITING：启动后仍在等待首次 callback；
- GOOD：最近 callback/accepted point 新鲜；
- DEGRADED：约 15s 以上；
- LOST：约 30s 以上；
- LONG_GAP：约 120s 以上。

这些阈值代表 **delivery/accepted liveness**。它和两个 persisted capture fix 的 continuity interval 是不同维度，但两者现在都使用合适的 monotonic clock，不再依赖 civil clock 计算“多久”。

callback gap diagnostics 同样用 `SystemClock.elapsedRealtime()`。

---

## 6. 诊断能力 — #275/#280/#281

每条 selected Trip 可以导出自包含 CSV。

### 6.1 TripPoint evidence

每点包含：

- epoch capture timestamp；
- elapsed realtime nanos；
- adjacent delta；
- `timeAuthority`；
- `timeDecision`；
- lat/lon/altitude；
- speed/bearing；
- horizontal/vertical/speed accuracy；
- provider。

### 6.2 Runtime/source evidence

诊断事件包含：

- SERVICE_START / REDELIVERED / DESTROY；
- LOCATION_REGISTRATION_FAILED；
- LOCATION_SOURCE；
- LOCATION_AVAILABILITY；
- LOCATION_CALLBACK_GAP；
- CAPTURE_TIME_REBASE；
- GPS_HEALTH_TRANSITION；
- POWER_STATE；
- PROVIDER_DISABLED；
- PERMISSION_MISSING；
- LOCATION_REJECTED。

source diagnostics 可看：

- Fused registration；
- Google `LocationAvailability` transition；
- Fused silent fallback reason；
- platform registered providers。

POWER_STATE 包含：

- powerSave；
- deviceIdle；
- interactive；
- ignoringBatteryOptimizations；
- backgroundRestricted；
- appStandbyBucket；
- locationPowerSaveMode；
- processImportance。

目标是区分：

```text
service 被杀
vs
service 活着但 callback 不来
vs
Fused unavailable/silent
vs
source fallback
vs
callback 来了但 point 被 trust rule 拒绝
vs
真实 long gap
vs
civil clock 改变
vs
monotonic rebase/reboot
vs
Android 标准 power/background restriction
```

---

## 7. 后台记录保护 — #278/#280

Trip 准备页 evidence-driven guidance：

- 检查 standard background/battery state；
- real `backgroundRestricted=true` 始终可重新提示，不被旧 acknowledgement 永久隐藏；
- acknowledgement 按 manufacturer/brand/API 作用；
- OnePlus/OPPO/realme 提示允许后台活动并在最近任务锁定 App；
- approximate-only location 提示开启 precise location；
- 不申请 `ACCESS_BACKGROUND_LOCATION`；
- 不直接请求 battery optimization exemption；
- 不因为 OEM 名称堆大量私有 deep link。

其他 ROM 的专项 guidance 由 #283 OEM matrix 以真实设备证据逐步增加。

---

## 8. GPS gap 与轨迹连续性

核心规则：

- `>=120s` capture interval -> disconnected segment；
- `CAPTURE_TIME_REBASE` -> disconnected segment；
- gap/rebase 两端不累计可信直线距离；
- gap/rebase duration 不进入 moving/stopped；
- gap/rebase 不刷新 aggregate/max speed；
- speed-colored route 不跨断点；
- playback 在断点期间保持最后真实定位，不插值；
- trend chart 不跨断点连线；
- elevation cumulative ascent/descent 不跨断点拼高度差。

```text
已知轨迹 ━━━━━     ━━━━━ 已知轨迹
             gap / rebase
```

MapLibre 未来也不能改变这一事实边界。

---

## 9. 可信最高速度

历史 122.4 km/h 假峰值来自 coarse network point。

当前 max-speed candidate 必须满足：

- existing aggregate continuity/distance corroboration；
- provider = GPS；
- horizontal accuracy <= 25m；
- speedAccuracy 存在时 <= 3 m/s；
- speed finite 且非负。

Network/coarse raw `speedMps` 可保留作为诊断事实，但不能刷新可信 `maxSpeedMps`。

产品语义仍是“最高已记录可信 GNSS 速度”，不是车辆真实物理最高速度的绝对证明。

---

## 10. 可信速度彩色轨迹与回放

规则：

- 只有可信 measured GPS speed 才参与速度颜色；
- unknown/untrusted speed 保持 neutral；
- LONG_GAP/rebase segmentation 优先于颜色；
- 颜色只属于 UI 派生，不写回 TripPoint；
- playback 使用与 tracking 一致的 monotonic/legacy-fallback timeline，不自己再用 epoch 重新计算 progress。

当前颜色：

- 0–5 km/h：深红
- 5–15 km/h：红
- 15–30 km/h：红 -> 黄
- 30–50 km/h：黄 -> 绿
- 50–70 km/h：绿
- 70–90 km/h：绿 -> 蓝
- 90+ km/h：蓝 / 深蓝
- unknown/untrusted：灰色

不允许在没有道路 context 时显示“拥堵/畅通”等交通事实标签。

---

## 11. 海拔 / 趋势

trusted elevation analysis 已支持：

- start/end altitude；
- min/max；
- cumulative ascent/descent；
- vertical-accuracy filtering；
- jitter suppression；
- gap/rebase isolation。

#281 后，elevation gap 判断与趋势 X-axis 也使用 monotonic/legacy-fallback timing policy，因此 civil clock 回拨不应制造假 elevation gap 或反向 trend timeline。

v0.6 详情 ownership：

- `轨迹`：route + speed/altitude trends；
- `数据`：compact altitude summary + GPS diagnostics + playback/export actions。

---

## 12. Notification / interruption 状态

必要代码已经进入 `main`：

- ongoing notification 显示已记录时长 + 可信累计距离；
- deep link 回当前 Trip；
- provider 或 Location permission 丢失 -> `INTERRUPTED`；
- repair notification 进入对应设置；
- 修复后必须由用户明确恢复；
- Android 13+ notification permission 拒绝不回滚 Trip；
- 锁屏通知默认不暴露精确经纬度/HOME/WORK。

#26 仍是 physical acceptance owner，不是待实现一套新 notification architecture。

---

## 13. 当前验收状态

### P0 GPS Reliability — code

- [x] location FGS + ongoing notification
- [x] Fused ~1Hz + platform fallback
- [x] 12s Fused silent watchdog
- [x] old 8m callback gate removed
- [x] stationary write throttle / drift protection
- [x] callback freshness / delayed fix handling
- [x] 120s hard gap / no gap distance bridging
- [x] service lifecycle diagnostics
- [x] per-Trip full diagnostics
- [x] Fused LocationAvailability / fallback reason
- [x] extended Android background/power diagnostics
- [x] background/precise-location guidance
- [x] Room v17 epoch + elapsed realtime
- [x] monotonic callback gap / live GPS health
- [x] monotonic route/playback/trend/elevation/CSV timing
- [x] capture-clock rebase hard-disconnect semantics

### P0 GPS Reliability — physical #77

- [ ] existing v16 real-device DB opens/upgrades to v17, old Trip/Charging data intact
- [ ] lock screen while moving
- [ ] another app foreground 5–10 minutes while moving
- [ ] 2–3 minute stationary hold
- [ ] real provider/location interruption + manual resume
- [ ] no unexplained in-motion callback gap
- [ ] normal new intervals export `ELAPSED_REALTIME` authority
- [ ] civil clock correction does not manufacture LONG_GAP/LOST/reverse route
- [ ] reboot/rebase if exercised produces `CAPTURE_TIME_REBASE` and no cross-boundary distance
- [ ] true >=120s gap remains disconnected
- [ ] out-of-order delayed point remains rejected

### P0 Speed Trust

- [x] Network/coarse speed cannot refresh maxSpeedMps
- [x] GPS quality gate + JVM regression

### P1 Speed/route UI

- [x] trusted GPS speed colors
- [x] neutral unknown speed
- [x] gap/rebase not bridged
- [x] playback uses same timing authority
- [ ] Dark/Light route/legend/endpoint physical readability
- [ ] 320–360dp + fontScale 1.3 physical pass

### P1 Elevation/detail

- [x] trusted altitude summary/cumulative ascent/descent
- [x] gap/rebase isolation
- [x] data-tab compact diagnostics actions
- [ ] Location/Geocoder physical pass
- [ ] detail/trend/diagnostic narrow/large-font pass

---

## 14. 推荐 current-main 真机验收脚本

必须使用包含 #281 或更晚代码的 build。

### 升级前置

1. 在已有真实数据的 v16 App 上确认 Trip/Charging 可读；
2. 安装 current-main Debug / release candidate；
3. 启动 App，确认 Room 16->17 成功打开；
4. 随机检查历史 Trip 与 Charging 数据未丢失。

### 行程脚本

1. 前台开始 Trip，正常行驶 3–5 分钟；
2. 锁屏继续移动；
3. 解锁，切另一 App 前台 5–10 分钟继续移动；
4. 停车/红灯 2–3 分钟；
5. 安全方便时测试一次系统定位/provider 中断，再手工恢复；
6. 恢复行驶并回 EV Charge Book；
7. 正常结束 Trip；
8. 检查 `概览 / 轨迹 / 数据`；
9. 若出现行驶中异常 gap，立即 `导出诊断`。

必须检查：

- [ ] 后台期间可信距离/route 持续；
- [ ] stationary stoppedSeconds 合理且不虚增距离；
- [ ] true provider/callback loss 仍显示健康异常；
- [ ] real >=120s gap 不补距离/线；
- [ ] CSV points 有 elapsed realtime，新点 delta 显示 `ELAPSED_REALTIME`；
- [ ] source/availability/power evidence 足以解释 fallback/异常；
- [ ] route/playback/trend/altitude 对同一 gap 的断开语义一致；
- [ ] no synthetic points。

CI 不能替代上述物理设备结论。

---

## 15. Future / evidence-driven only

### 15.1 #283 OEM matrix

至少覆盖：

- ColorOS family；
- HyperOS/MIUI；
- OriginOS/vivo/iQOO；
- Pixel/Samsung 或接近 AOSP 的主流设备。

### 15.2 bounded source recovery

只有 current-main 诊断证明需要时才研究：

```text
FUSED
  -> PLATFORM
  -> bounded platform re-register
  -> optional FUSED re-probe
```

必须有 backoff、minimum dwell/hysteresis 和 transition diagnostics，禁止 provider 抖动。

### 15.3 Explicit non-goals

- 第二 tracking Service；
- WorkManager/Alarm 心跳保活；
- 长期 WakeLock；
- direct battery-optimization exemption；
- synthetic GPS points；
- road geometry fabrication；
- 无证据的大量 OEM 私有 deep link；
- 用阈值放宽掩盖 callback starvation。

其他可选：

- persistent longest-gap/provider/rejected summary；
- longer-window `TripSpeedSegmentBuilder`；
- MapLibre basemap renderer；
- OBD-II Vehicle Speed PoC。

---

## 16. 实施历史

主要可靠性链：

```text
#36 GPS health / diagnostics / gap route split
  -> #80 callback liveness + stationary heartbeat enablement
  -> #82 trusted max-speed gate
  -> #85 trusted speed-colored route
  -> #127 trusted elevation analysis
  -> #184 delayed callback grace
  -> #217 Fused production source
  -> #220 platform fallback
  -> #226 ~1Hz active acquisition
  -> #231 stationary drift hardening
  -> #234 12s silent-provider fallback
  -> #275 self-contained diagnostic export
  -> #278 background recording guidance
  -> #280 LocationAvailability + background/source observability
  -> #281 monotonic capture/delivery timing + Room v17
```

notification/repair：

```text
#130 elapsed + trusted distance + Trip deep link
  -> #131 provider/permission interruption repair
  -> #132 Android 13+ non-blocking notification permission
  -> #278/#280 evidence-driven background readiness guidance
```

---

## 17. 变更记录

### v1.4.0

- 同步 2026-09-02 OnePlus A/B：Service 未销毁但普通后台存在 445s/261s 行驶中 callback gap，最近任务锁定后没有同类问题。
- 同步 #275 per-Trip diagnostic export。
- 同步 #278 background recording guidance。
- 同步 #280 `LocationAvailability`、Fused/platform fallback reason、extended POWER_STATE、approximate-location guidance。
- 同步 #281 / Room v17：epoch + elapsed realtime dual-time authority；callback gap、live GPS health、route/playback/trend/elevation/CSV timing 使用 monotonic time。
- 明确 legacy/restored point epoch fallback，不伪造 elapsed realtime。
- 明确 `CAPTURE_TIME_REBASE` hard-disconnect boundary。
- 保留 `LONG_GAP_SECONDS = 120`、no synthetic points 和 fail-closed distance policy。
- current physical revalidation 从 post-#184 更新为 post-#281；#77 继续 open。

### v1.3.0

- 记录 2026-08-29 PR #80 后锁屏仍出现 LONG_GAP 的证据。
- 同步 #184 callback-delivery grace，保留 120s continuity trust。
- 明确 delivery age 与 route continuity 是不同维度。

### v1.2.0

- 更新为 Code Baseline Complete / Physical Acceptance Pending。
- 同步 #77/#80、max-speed trust、notification repair、trusted elevation 与 v0.6 detail architecture。

### v1.1.0

- 保留 Trip #7 与 2026-08-27 Trip #2 历史可靠性证据。
- 加入 callback/stationary、max-speed、altitude/address、speed-colored route 第一轮实现状态。

### v1.0.0

- 基于真实 Trip #7 建立 P0 GPS reliability 与 P1 speed visualization。
- 明确 COMPLETED 不等于 GPS continuity。
- 定义 GPS health、provider quality、速度颜色与长 gap 不得伪造的边界。
