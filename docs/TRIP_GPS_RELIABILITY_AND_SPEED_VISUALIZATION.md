# Trip GPS Reliability & Speed Visualization

版本: v1.2.0
更新时间: 2026-08-29
状态: Code Baseline Complete / Physical Acceptance Pending

## 1. 文档定位

本文档是 EV Charge Book 行程模块中 **GPS 可信度、轨迹连续性、速度可信度与轨迹可视化** 的实现/验收说明。

当前阶段不再是“等待实现方案”。核心代码已经进入 `main`，剩余主要是 Android 真机上的后台定位、轨迹可读性与无障碍/窄屏验收。

相关 authority：

- `docs/TRIP_V0.6_APPROVED_UI_BASELINE.md`
- `docs/LOCATION_TRIP.md`
- `docs/ROADMAP.md`
- #145 Trip v0.6 总体验收
- #168 Trip v0.6 device-fidelity correction
- #77 后台 callback / stationary hold 真机验收
- #67 trajectory / speed-colored route 真机验收

设计原则保持不变：

1. GPS / Room 中保存的原始事实优先于视觉效果。
2. 不跨真实 GPS gap 伪造路线、距离或速度。
3. Network/coarse provider 可以作为诊断证据，但不能污染可信最高速度或彩色轨迹。
4. UI 的速度颜色只表达“本车可信 GPS 速度分布”，不代表道路拥堵、限速或交通状态。
5. 地图 SDK、road snapping、云端轨迹处理都不是当前可靠性成立的前提。

---

## 2. 历史真实数据证据

这些数据保留作为当前 trust rules 的来源，不代表当前代码仍存在相同缺陷。

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

- `COMPLETED` 只代表行程最终收口，不代表中间 GPS 连续。
- 大 gap 两端不得画成可信连续路线。
- gap 期间不得补造可信距离、移动时间或速度。

### 2.2 2026-08-27 Trip #2：后台空洞、停车误判和假最高速

历史实机证据：

- distanceMeters: 约 24.20 km
- elapsed: 约 83 分 45 秒
- moving: 约 51 分 25 秒
- stoppedSeconds: 仅 15 秒
- UI 曾出现约 122.4 km/h 的最高速度

典型 continuity 证据：

- Point 42 -> 43：约 643 秒移动空洞
- Point 109 -> 110：约 465 秒，恢复时出现 coarse network fallback
- 约 140 秒的红灯样本暴露旧 `8m` LocationManager gate 与 15 秒 stationary heartbeat 的冲突

典型假速度点：

- provider: `network`
- horizontal accuracy: 约 100m
- speed accuracy: null
- reported speed: 34.005 m/s（约 122.4 km/h）

上述证据推动了 PR #80、#82、#85 等后续修复。

---

## 3. 当前 GPS callback / stationary reliability baseline

### 3.1 PR #80：callback liveness 与停车 heartbeat

PR #80 `fix(android): keep Trip callbacks alive while stationary` 已进入 `main`。

当前实现：

- LocationManager `minDistance = 0m`，不再使用旧 8m displacement gate
- 保留约 4 秒 callback request interval
- callback 与数据库写入分层：系统回调可以频繁，Room 写入仍由 domain sampling rules 降频
- stationary heartbeat 约 15 秒持久化一次，而不是每个 callback 都写 TripPoint
- stationary heartbeat 增加 `stoppedSeconds`，不增加 `movingSeconds`
- `START_REDELIVER_INTENT` 保留
- provider / permission / service lifecycle 诊断保留

这解决了代码层“系统 8m gate 阻止业务 15s heartbeat”的冲突。

### 3.2 仍需 #77 真机验证

CI 无法证明不同 Android ROM 在后台时一定持续交付定位 callback。

因此 #77 保持 open，仅用于验证：

- [ ] 切换其他 App 并保持其前台 5–10 分钟时，可信距离仍持续增长
- [ ] 2–3 分钟真实停车会增加合理 `stoppedSeconds`
- [ ] 健康 stationary callback 不产生 false LONG_GAP
- [ ] 真正 provider/callback loss 仍产生 LOST/LONG_GAP
- [ ] stationary TripPoint 写入仍保持节流

没有新的真机证据前，不再重新实现第二套 tracking service / WorkManager / 云端 tracking。

---

## 4. GPS health 与诊断

当前运行时已经区分：

- `lastLocationCallbackAt`
- `lastAcceptedPointAt`
- recent accepted provider
- rejected point evidence / sampled diagnostics
- foreground service start / destroy / redelivery
- provider disabled
- permission missing
- location registration failure

UI / notification 使用的核心 health 语义仍是：

- GOOD：最近有效定位约 <=10s
- DEGRADED：约 10–30s
- LOST：约 >30s
- LONG_GAP：约 >=120s 的真实 continuity break

这些阈值是产品 reliability 参数，可根据真机数据做小幅调整，但不能通过放宽阈值来隐藏真实 gap。

### 可选的未来摘要

以下属于 future / evidence-driven，不是当前 blocker：

- Trip 级 longest gap
- GPS / Network provider counters
- rejected reason 汇总
- provider switch count

只有当诊断成本明显影响真实问题定位时再增加，不为了“指标齐全”扩展 schema。

---

## 5. Notification / interruption 状态

早期文档曾把 #26 写成后续代码工作；该描述已经过期。

当前必要代码已经通过 PR #130 / #131 / #132 进入 `main`：

- ongoing notification 显示真实已记录时长
- ongoing notification 显示已持久化可信累计距离
- 点击 ongoing notification / `打开行程` 可回当前 Trip
- provider 不可用或 Location 权限丢失时进入 `INTERRUPTED`
- repair notification 可进入对应设置修复
- 修复后必须由用户明确恢复，不自动偷偷 resume
- Android 13+ notification permission 拒绝不会回滚或阻止 Trip
- 锁屏通知默认不暴露精确经纬度 / HOME / WORK 地址

#26 目前属于 **physical acceptance owner**，不是待实现代码清单。

---

## 6. 最高速度可信度

### 6.1 PR #82 / #78 已完成

历史 122.4 km/h 假峰值来自 coarse `network` point。

PR #82 已建立独立 max-speed trust gate，#78 已按 completed 关闭。

当前最高速度 candidate 必须满足：

- existing aggregate continuity / distance corroboration
- provider = `gps`
- horizontal accuracy <= 25m
- speedAccuracy 存在时 <= 3 m/s
- speed finite 且非负

Network/coarse point 的原始 `speedMps` 仍可保留在 TripPoint 作为诊断事实，但不能刷新可信 `maxSpeedMps`。

JVM regression 已覆盖：

- 122.4 km/h network + 100m accuracy 被拒绝
- 合理 GPS highway peak 可以通过
- coarse GPS peak 被拒绝

因此不要再把 #78 写成当前 open blocker。

产品语义仍应理解为“最高已记录可信 GNSS 速度”，不是车辆真实物理最高速度的绝对证明。

---

## 7. 可信速度彩色轨迹

### 7.1 PR #85 已实现

PR #85 `feat(android): color Trip route by trusted vehicle speed` 已合并为 `482b6e9`。

Android CI run `33086126816` 在 PR head `8337d81` 上成功。

数据路径：

```text
TripPoint[]
  -> trusted measured-speed gate
  -> TripGeoPoint(speed?)
  -> TripRouteGeometryBuilder
  -> colored route renderer
```

规则：

- 只有相邻两端都拥有可信 GPS speed，线段才使用速度颜色
- 任一端 speed 不可信/缺失，线段保持中性灰色
- 未知速度不能被当作 0 km/h
- downsample / normalize 后保留可信 speed metadata
- LONG_GAP segmentation 优先于颜色；gap 不会因为上色而重新连起来

### 7.2 当前颜色语义

连续映射：

- 0–5 km/h：深红
- 5–15 km/h：红
- 15–30 km/h：红 -> 黄
- 30–50 km/h：黄 -> 绿
- 50–70 km/h：绿
- 70–90 km/h：绿 -> 蓝
- 90+ km/h：蓝 / 深蓝
- 未知 / 不可信：灰色

颜色仅属于 UI 派生，不写回原始 TripPoint。

### 7.3 不允许的交通语义

没有道路类型、限速、map matching、实时交通数据时：

- 8 km/h 可能只是停车场
- 15 km/h 可能只是小区道路
- 40 km/h 在不同道路上意义完全不同

因此 UI 必须继续说明：颜色代表本车可信 GPS 速度分布，不代表道路拥堵等级。

---

## 8. GPS gap 与轨迹连续性

当前核心规则：

- `>=120s` 的真实 gap 拆成 disconnected segments
- gap 两端不累计可信直线距离
- gap duration 不进入可信 moving/stopped 统计
- gap 不允许刷新 aggregate / max speed
- speed-colored route 不跨 gap 画线

```text
已知轨迹 ━━━━━     ━━━━━ 已知轨迹
             GPS 缺失
```

无 basemap 阶段使用断开表达已经足够真实。

MapLibre 如果未来接入，只负责更好的 renderer，不负责补造道路或缺失路线。

---

## 9. 海拔 / 高程分析

早期版本写着“当前不计算累计爬升/下降”，该描述已经过期。

PR #127 `feat(android): add trusted Trip elevation analysis` 已实现 #69 并进入 `main`：

- start / end altitude
- min / max altitude
- cumulative ascent / descent
- vertical-accuracy quality filtering
- jitter suppression
- LONG_GAP isolation
- Trip detail UI
- JVM regression coverage

#69 已完成关闭。

v0.6 详情中：

- `轨迹`：速度 / 海拔趋势作为 route supporting evidence
- `数据`：海拔摘要与 reliability / raw-point diagnostics
- 无可信海拔时显示 truthful unavailable state，不生成合成高程

---

## 10. 地址与 endpoint 语义

### 10.1 Coordinate-first

#66 的 coordinate-first / geocode cache-retry code 已完成关闭。

当前原则：

- WGS84 latitude / longitude 是原始权威事实
- 地址只是展示层派生数据
- geocoder 失败不阻止 Trip 保存/完成
- 无法解析时保留坐标或明确 unavailable，不虚构地点

真实设备 Location / Geocoder 行为继续由 #14 验收，而不是重新打开 #66。

### 10.2 v0.6 endpoint semantics

当前 v0.6 authority 已替代早期“圆形起点 / 方形终点”视觉：

- start：compact primary-green play/start icon
- completed end：small red flag，无 ring / halo
- active latest point：green `当前点`
- interrupted/non-final latest point：`最后记录点`，不能冒充 completed endpoint

同时保留 accessibility semantics，不能只靠红/绿颜色区分状态。

---

## 11. v0.6 详情信息架构

PR #179 已将 completed Trip detail 从一个长页面拆成：

- `概览`：summary + start/end card
- `轨迹`：真实 route + trusted speed/altitude trends
- `数据`：altitude/reliability summary + raw-point progressive disclosure

因此本文件描述的 reliability / route / speed visualization 必须服从 `TRIP_V0.6_APPROVED_UI_BASELINE.md` 的当前信息架构，不再把所有诊断内容堆在默认详情页。

Raw GPS point list 默认折叠，只在用户明确点击 `查看轨迹点` 时展开。

---

## 12. 当前验收状态

### P0 GPS Reliability

- [x] 最近有效定位 / provider / rejected evidence
- [x] service lifecycle diagnostics
- [x] LOST / LONG_GAP health
- [x] `>=120s` route gap 断开，不累计伪造可信距离
- [x] old LocationManager 8m callback gate 已移除
- [x] stationary heartbeat 可以在 domain layer 执行并保持写入节流
- [ ] 另一 App 前台 5–10 分钟仍持续 callback / 距离更新（#77）
- [ ] 2–3 分钟停车不产生 false LONG_GAP，stoppedSeconds 合理（#77）
- [ ] 真正 provider/callback loss 仍可复现 LOST/LONG_GAP（#77）

### P0 Speed Trust

- [x] Network/coarse speed 不刷新 maxSpeedMps
- [x] GPS quality gate + JVM regression
- [x] #78 completed；不再作为当前 open blocker

### P1 Speed Visualization

- [x] 可信 GPS route 连续颜色映射
- [x] unknown/untrusted speed 使用 neutral color
- [x] LONG_GAP 不跨 gap 上色/连线
- [x] 不宣称真实拥堵等级
- [x] geometry metadata JVM coverage
- [ ] real-device route / legend Dark-Light 可读性（#67/#145/#168）
- [ ] 320–360dp + fontScale 1.3（#67/#145/#42）

### P1 Elevation / detail

- [x] start/end/min/max altitude
- [x] cumulative ascent/descent
- [x] quality/jitter/gap filtering
- [x] v0.6 `轨迹` / `数据` section ownership
- [ ] Location/Geocoder real-device pass（#14）
- [ ] detail/trend/diagnostic Dark-Light + narrow-screen pass（#145/#168/#178/#42）

---

## 13. 推荐一次性真机验收脚本

一次 20–30 分钟 session 可覆盖主要剩余 blocker：

1. 前台开始 Trip，正常行驶 3–5 分钟。
2. 锁屏一段时间，确认 foreground notification 存在并刷新真实时长/距离。
3. 解锁后切换到其他 App，保持其前台 5–10 分钟并继续行驶。
4. 找一次约 2–3 分钟停车/红灯场景。
5. 如果安全且方便，可测试一次系统定位/provider 中断，再明确手工恢复。
6. 恢复行驶并返回 EV Charge Book。
7. 通过 slide-to-end 打开 compact completion form，填写 end SOC / mileage 并结束。
8. 检查 completed `概览` / `轨迹` / `数据`。

必须检查：

- [ ] 后台/另一 App 前台期间可信距离继续增长
- [ ] 停车期间 stoppedSeconds 合理且无 false LONG_GAP
- [ ] 真正 GPS/callback loss 仍显示 LOST/LONG_GAP
- [ ] route 能看到可信低/中/高速度差异，不可信段保持 neutral
- [ ] LONG_GAP 清晰断开
- [ ] completed end 只在真实完成后显示 red flag
- [ ] speed/altitude trends 有可读 X/Y context
- [ ] altitude / ascent / descent truthfully available or unavailable
- [ ] raw diagnostics 默认折叠
- [ ] Dark / Light、320–360dp、fontScale 1.3 可用

CI 不能替代上述物理设备结论。

---

## 14. Future / evidence-driven only

以下不属于当前 Trip v0.6 closeout blocker：

### TripSpeedSegmentBuilder

如果以后需要更稳定的区间分析，可研究 20–30 秒或 200–300 米窗口的 segment model：

- start/end timestamp
- distance
- duration
- averageSpeedKmh
- optional min/max speed
- quality / gap flag

PR #85 的“相邻可信 GPS speed 上色”不是完整 segment analytics；两者边界必须继续保持。

### Persistent reliability summary

只有在真实故障分析证明需要时再考虑：

- longest gap
- provider switch count
- rejected reason counters

### MapLibre

MapLibre 继续是可选 renderer：

- 不阻塞当前 reliability
- 不负责 road snapping
- 不允许跨缺失数据补造路线

---

## 15. 实施历史

当前主要代码链：

```text
PR #36  GPS health / diagnostics / gap route split
  -> PR #80 callback liveness + stationary heartbeat enablement
  -> PR #82 trusted max-speed gate
  -> PR #83 altitude/address/endpoint foundation
  -> PR #85 trusted speed-colored route preview
  -> PR #127 trusted elevation analysis
  -> PR #158 / #170 v0.6 route/endpoint fidelity
  -> PR #179 completed detail `概览` / `轨迹` / `数据`
```

后台通知 / repair flow：

```text
PR #130 elapsed + trusted distance + Trip deep link
  -> PR #131 provider/permission interruption repair
  -> PR #132 Android 13+ non-blocking notification permission
```

---

## 16. 变更记录

### v1.2.0

- 将文档状态从 `Partially Implemented` 更新为 `Code Baseline Complete / Physical Acceptance Pending`。
- 同步 #77 / PR #80：旧 8m callback gate 已不存在，剩余 ROM/background 真机验证。
- 修正 #78 状态：max-speed trust gate 已完成，#78 已关闭，不再是 open acceptance owner。
- 同步 #26：必要 notification / interruption / permission code 已由 #130/#131/#132 完成，剩余真机验收。
- 同步 #66：coordinate-first / geocode cache-retry 已完成关闭，Location/Geocoder 真机验收归 #14。
- 同步 #69 / PR #127：trusted cumulative ascent/descent 已实现，不再写成 future。
- 同步 #67 / PR #85 Android CI `33086126816` Green，明确代码侧已完成、仅余 physical route fidelity。
- 同步 Trip v0.6 `概览` / `轨迹` / `数据` 详情信息架构和 endpoint semantics。

### v1.1.0

- 保留 Trip #7 与 2026-08-27 Trip #2 的历史可靠性证据。
- 加入 callback/stationary、max-speed gate、altitude/address/marker、speed-colored route 第一轮实现状态。

### v1.0.0

- 基于真实 Trip #7 建立 P0 GPS reliability 与 P1 speed visualization 方案。
- 明确 COMPLETED 不等于 GPS continuity。
- 定义 GPS health、provider quality、速度颜色与交通语义边界。
- 明确长 GPS gap 不得以实线伪造。