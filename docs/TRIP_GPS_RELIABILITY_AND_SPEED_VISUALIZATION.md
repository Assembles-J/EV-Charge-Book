# Trip GPS Reliability & Speed Visualization Plan

版本: v1.1.0
更新时间: 2026-08-27
状态: Partially Implemented / Physical Acceptance Pending

## 1. 背景

2026-08-27 的真实 Trip 数据连续暴露出两个核心方向：

1. 单一全程平均速度无法表达高速、城市道路、停车/低速等不同区段的驾驶特征。
2. `COMPLETED` 不能证明中间 GPS 连续；后台 callback、provider fallback、业务过滤与真实 GPS loss 必须可区分。

本文档最初基于 Trip #7 建立 P0 GPS reliability + P1 speed visualization 方案。第二轮真机 Trip 又确认：

- 切换其他 App 时仍可能出现移动中长时间轨迹空洞；
- 2-3 分钟红灯可能因 callback/accepted point 间隔被误解为长 gap；
- Network provider 的粗定位 speed 可以制造 122+ km/h 假峰值；
- 海拔、地址、起终点标记和速度颜色需要真正落到 Trip 详情。

当前多项代码已经进入 `main`，但后台连续性、最高速度实际一致性与彩色轨迹视觉仍需下一轮真机验收。CI 不能代替这些物理设备结论。

---

## 2. 真实数据证据

### 2.1 第一轮：Trip #7

Trip #7：

- distanceMeters: 42643.19 m
- elapsedSeconds: 4137 s
- movingSeconds: 4090 s
- stoppedSeconds: 20 s
- averageSpeedMps: 10.4262 m/s（约 37.5 km/h）
- maxSpeedMps: 25.92 m/s（约 93.3 km/h）
- status: COMPLETED

TripPoint 中存在多段长时间缺口，例如：

- Point 64 -> 65 约 749 秒（12 分 29 秒）
- Point 70 -> 71 约 917 秒（15 分 17 秒）

由此确认：

- `COMPLETED` 只能说明 Trip 最终正常收口，不代表中间 GPS 连续。
- 需要独立的 GPS health / continuity 指标。
- 长 gap 两端不能被画成可信连续路线。

### 2.2 第二轮：2026-08-27 Trip #2

第二轮实机导出进一步暴露：

- distanceMeters: 约 24.20 km
- elapsed: 约 83 分 45 秒
- moving: 约 51 分 25 秒
- stoppedSeconds: 仅 15 秒，明显不符合城市道路红灯实际
- maxSpeed: 约 122.4 km/h，但车辆实际返程没有该速度
- Session 已保存 start/end/min/max altitude，但 UI 当时未展示

典型 continuity 证据：

- Point 42 -> 43：约 643 秒（10 分 43 秒），坐标发生公里级变化，属于真实移动期间记录空洞。
- Point 109 -> 110：约 465 秒（7 分 45 秒），恢复时先出现 coarse network fallback。
- 一组红灯样本约 140 秒间隔，起始点 speed=0，符合“车辆静止但系统/业务没有保留足够 heartbeat”的误分类风险。

典型假速度证据：

- provider: `network`
- horizontal accuracy: 约 100m
- speed accuracy: null
- reported speed: 34.005 m/s（约 122.4 km/h）

另有类似 network point 报出约 142.6 km/h，说明不能把 Network `Location.speed` 与高质量 GPS 速度视为同等统计事实。

---

## 3. P0 - GPS 后台可靠性与可诊断性

### 3.1 目标

下一次出现断点时，必须能区分：

- Foreground Service 被系统杀/重启
- LocationManager 长时间没有 callback
- GPS provider 不可用
- network provider fallback
- Location 被业务过滤
- 权限或系统定位状态异常
- 车辆实际静止但 tracking callback 仍健康

### 3.2 运行时状态

当前运行时已经覆盖主要 health 信息：

- `lastLocationCallbackAt`
- `lastAcceptedPointAt`
- recent accepted provider
- rejected point count
- foreground service lifecycle diagnostic events

仍建议继续补充 Trip 级持久摘要：

- lastGpsPointAt / lastNetworkPointAt
- acceptedPointCount
- rejectedByAccuracyCount
- rejectedByJumpCount
- providerSwitchCount
- longestLocationGapSeconds
- cumulative gap summary

### 3.3 GPS Health 状态

UI / Notification 使用统一派生状态：

- GOOD：最近有效定位 <= 10s
- DEGRADED：10-30s
- LOST：> 30s
- LONG_GAP：> 120s

阈值仍属于可调 reliability 参数，必须通过真实设备继续验证。

### 3.4 Notification

Foreground notification 已实现：

- 正在记录行程
- GPS health
- 最近有效定位时间
- recent accepted provider
- rejected point count
- 结束行程动作
- LOST / LONG_GAP 使用同一条 ongoing notification 更新，不刷屏

后续 #26 继续补：

- 已记录时长
- 已记录可信距离
- 点击通知进入当前 Trip
- provider/permission/background restriction 修复入口

### 3.5 Service 生命周期

当前服务继续使用 location foreground service + `START_REDELIVER_INTENT`。

已记录的关键诊断事件包含：

- service start
- START_REDELIVER_INTENT redelivery
- service destroy
- provider disabled
- permission missing
- location registration failed
- sampled rejected location event

这些事件用于解释断点，不用于长期堆积无界日志。

### 3.6 callback 与 stationary heartbeat

第二轮真机发现了一个明确冲突：

```text
LocationManager minDistance = 8m
        +
TripSamplingRules stationary heartbeat = 15s
```

车辆停在红灯时，如果系统层因为没有移动 8m 而不产生 callback，业务层就无法执行 15s stationary heartbeat。下一次 accepted point 若超过 120s，可能被当成 LONG_GAP。

PR #80 已进入 `main`：

- LocationManager `minDistance` 从 8m 调整为 0m；
- 保持约 4 秒 callback request interval；
- Room 写入仍由 `TripSamplingRules` 负责降频；
- stationary heartbeat 继续约 15 秒一条；
- JVM test 覆盖 stationary heartbeat 增加 `stoppedSeconds` 而不增加 `movingSeconds`。

该改动解决“代码层两套门槛互相打架”，但仍必须通过 #77 的真机流程验证：切到其他 App 后 callback/距离是否真的持续。

### 3.7 GPS / Network provider 融合

当前允许 GPS + Network provider 进入原始 TripPoint，但统计权限分层：

- GPS 高质量点优先作为主轨迹/速度事实。
- Network point 保留为 fallback / continuity / diagnostic evidence。
- 时间非常接近的 GPS / Network point 使用 continuity rule 去重/择优。
- Network 粗速度不能制造最高速度。
- Network 粗速度不能给速度彩色轨迹上色。
- 原始数据不因为统计过滤而被删除或改写。

---

## 4. P0 - 最高速度可信度

### 4.1 问题

第二轮实机的 122.4 km/h 最高速来自 coarse `network` point，而非可信 GPS 峰值。

因此“原始 Location.speed 存在”不等于“允许进入 maxSpeedMps”。

### 4.2 已实现质量门

PR #82 已进入 `main`，新增独立 max-speed trust gate。

最高速度 candidate 必须同时满足：

- existing aggregate continuity/distance corroboration；
- provider = `gps`；
- horizontal accuracy <= 25m；
- speedAccuracy 存在时 <= 3 m/s；
- speed 为 finite 且非负。

Network / coarse point 的原始速度仍保留在 TripPoint，只是不再污染派生最高速度。

JVM regression 已覆盖：

- 122.4 km/h network + 100m accuracy 被拒绝；
- 合理 GPS highway peak 可以通过；
- coarse GPS peak 被拒绝。

### 4.3 仍需真机确认

#78 继续保持 open，下一轮需要把 App 的“最高已记录速度”与车辆实际峰值进行对照。

产品文案仍应理解为“最高已记录可信 GNSS 速度”，不是车辆真实最高速度的绝对证明，因为短时峰值可能恰好没有被采到。

---

## 5. P1 - 分段速度模型

### 5.1 三种速度必须区分

Trip UI 至少区分：

1. 全程平均速度：总可信距离 / elapsed time
2. 行驶平均速度：总可信距离 / moving time
3. 分段/轨迹速度：可信 segment 的显示或分析派生
4. 最高已记录速度：经异常过滤的可信 GNSS `Location.speed` 峰值

当前 UI 已明确显示全程均速 / 行驶均速 / 最高速度 / 移动时间。

### 5.2 第一版 route speed visualization 已实现

PR #85 已实现轻量第一版，不新增 Room 表：

```text
TripPoint[]
  -> trusted measured-speed gate
  -> TripGeoPoint(speed?)
  -> TripRouteGeometryBuilder
  -> colored route renderer
```

规则：

- 只有相邻两端都拥有可信 GPS speed，该线段才有速度颜色；
- 任一端 speed 不可信/缺失，该线段保持中性灰色；
- 不能把未知速度当作 0 km/h；
- 长 GPS gap 在 geometry 层仍保持断开；
- downsample/normalize 后保留可信 speed metadata。

### 5.3 完整 TripSpeedSegment 仍是 future

原计划的更长窗口派生模型仍有价值：

```text
TripPoint[]
  -> TripSpeedSegmentBuilder
  -> TripSpeedSegment[]
  -> analytics / UI
```

推荐继续研究：

- 20-30 秒，或
- 200-300 米

每个 segment 可包含：

- start/end timestamp
- distance
- duration
- averageSpeedKmh
- min/max speed（可选）
- GPS quality / gap flag

注意：PR #85 的“相邻可信 GPS speed 颜色”不是完整的 `TripSpeedSegmentBuilder`。文档必须保持两者边界。

---

## 6. P1 - 速度颜色映射

### 6.1 当前视觉语义

在未接道路类型/限速/交通数据前，不使用“严重拥堵 / 畅通”等交通事实词。

当前使用本车速度语义：

- 深红：极低速
- 红：低速
- 黄：较慢
- 绿：正常/快速
- 蓝：高速
- 灰：速度数据不足 / 不可信

### 6.2 连续颜色

PR #85 已按以下连续映射实现：

- 0-5 km/h：深红
- 5-15 km/h：红
- 15-30 km/h：红 -> 黄
- 30-50 km/h：黄 -> 绿
- 50-70 km/h：绿
- 70-90 km/h：绿 -> 蓝
- 90+ km/h：蓝 / 深蓝

最终颜色只属于 UI 派生，不写回原始 TripPoint。

### 6.3 交通语义边界

在没有道路类型 / 限速 / map matching / 实时交通数据前：

- 8 km/h 可能只是停车场
- 15 km/h 可能只是小区道路
- 40 km/h 在城市道路可能畅通，在高速可能拥堵

因此彩色轨迹代表“本车可信 GPS 速度分布”，不是“道路拥堵等级”。

UI 已明确说明该边界。

---

## 7. P1 - Trip detail facts

第二轮真机同时暴露了三个展示缺口，PR #83 已进入 `main`：

### 7.1 海拔

当前 Trip detail 可展示已持久化的：

- 起点海拔
- 终点海拔
- 最低海拔
- 最高海拔

当前不计算累计爬升/下降；raw altitude 需要经过 vertical accuracy / smoothing 后再做高程分析。

### 7.2 起终点地址

- WGS84 坐标继续作为权威事实。
- 已完成/中断 Trip 使用现有 `AndroidGeocoderAddressResolver` 做展示层 reverse geocoding。
- 地址作为主阅读文本，坐标保留为技术参数。
- geocoder 失败显示“地址暂不可用”，不阻止 Trip 完成。
- RECORDING 状态不反复解析实时 endpoint。

### 7.3 起终点 marker

原先两端仅使用两个同形状、不同颜色的小点，真机不易辨认。

当前改为：

- 起点：圆形 + `起点 · 圆形`
- 终点：方形 + `终点 · 方形`
- Canvas accessibility semantics 明确两者含义

因此不再只依赖红/绿颜色区分。

---

## 8. GPS Gap 可视化与统计

禁止把长时间缺失的两端 GPS 点直接连成实线路线。

当前规则：

- `>=120s` gap 后 renderer 拆成 disconnected segment；
- gap 两端不累计可信直线距离；
- gap duration 不进入 moving/stopped 统计；
- gap 不允许刷新 aggregate/max speed；
- speed-colored route 同样不会跨 gap 画线。

```text
已知轨迹 ━━━━━     ━━━━━ 已知轨迹
             GPS 缺失
```

无 basemap 阶段继续使用断开表达；MapLibre 后续只负责更好的 renderer，不负责补造缺失事实。

---

## 9. 当前验收状态

### P0 GPS Reliability

- [x] 显示最近有效定位时间
- [x] 识别 >30s GPS gap，并提供 LOST / LONG_GAP health
- [x] 区分 GPS / Network provider 基线
- [x] rejected point 运行时计数 / sampled diagnostic
- [x] service start / destroy / redelivery 等生命周期诊断事件
- [x] GPS 长时间丢失时 ongoing notification 明确提示
- [x] `>=120s` 长 gap 路线断开且不累计可信距离/时长
- [x] LocationManager 8m callback gate 移除，stationary heartbeat 可在业务层执行
- [ ] longest gap / provider counters / rejected reason 的 Trip 级持久摘要
- [ ] 切换其他 App 5-10 分钟仍持续 callback / 距离更新真机复验（#77）
- [ ] 2-3 分钟红灯不产生 false LONG_GAP，且 stoppedSeconds 合理真机复验（#77）

### P0 Speed Trust

- [x] Network/coarse speed 不得刷新 maxSpeedMps
- [x] max-speed quality gate 有 JVM regression
- [ ] 下一次真实行程与车辆仪表峰值对照（#78）

### P1 Speed Visualization

- [x] UI 区分全程平均 / 行驶平均 / 最高速度
- [x] 可信 GPS route 支持连续颜色映射
- [x] 不可信/未知 speed segment 使用中性灰色
- [x] 长 GPS gap 不画成可信实线
- [x] 未接交通数据前不宣称“真实拥堵等级”
- [x] trusted speed metadata 经过 geometry normalize/gap segmentation 有 JVM test
- [ ] `TripSpeedSegmentBuilder` 长窗口派生模型
- [ ] 分段平均速度综合模型 JVM tests
- [ ] 彩色路线 dark/light 真机视觉复验（#67）

### P1 Trip detail

- [x] 起/终/最低/最高海拔展示
- [x] 起终点地址展示，坐标保持技术参数
- [x] 起点圆形 / 终点方形 + 文本语义
- [ ] 地址 Geocoder 真实设备成功/失败路径复验（#66）
- [ ] 海拔与 marker dark/light 真机视觉复验（#69/#42）

---

## 10. 下一轮真机验收脚本

一次 20-30 分钟测试即可覆盖当前主要 blocker：

1. 前台开始 Trip，正常行驶 3-5 分钟。
2. 锁屏一段时间，确认 foreground notification 仍存在。
3. 解锁后切换到抖音/其他 App，保持该 App 前台 5-10 分钟并继续行驶。
4. 找一次约 2-3 分钟停车/红灯场景。
5. 恢复行驶，再返回 EV Charge Book 结束 Trip。
6. 对照车辆仪表记住本次大致最高速度。

必须检查：

- [ ] 切其他 App 时累计距离继续增长，没有新的公里级移动空洞
- [ ] 红灯期间产生合理 stoppedSeconds，不被误判成 LONG_GAP
- [ ] 真正 GPS/callback 丢失仍会显示 LOST/LONG_GAP
- [ ] 最高已记录速度与车辆实际峰值合理接近，不再出现 network 122+ km/h 假峰值
- [ ] 起/终/最低/最高海拔可见
- [ ] 起终点优先展示地址，地址失败时仍显示坐标且不虚构地点
- [ ] 起点圆形 / 终点方形一眼可分
- [ ] route 能看到低/中/高速度的红黄绿蓝变化
- [ ] 灰色只用于不可信/缺失速度区间
- [ ] 长 GPS gap 仍保持断开
- [ ] Dark / Light 下轨迹和图例都可读

只有真机通过后，才可以关闭 #77/#78/#67 对应 acceptance；不能用 Android CI 直接替代。

---

## 11. 实施顺序

当前已完成代码主线：

```text
PR #36 GPS health / diagnostics / gap route split
  -> PR #80 callback liveness + stationary heartbeat enablement
  -> PR #82 trusted max-speed gate
  -> PR #83 altitude / address / endpoint semantics
  -> PR #85 trusted speed-colored route preview
```

下一步：

```text
Physical acceptance #77/#78/#67/#66/#69/#42
  -> longest gap/provider summary（必要时）
  -> TripSpeedSegmentBuilder 长窗口分析（P1）
  -> MapLibre renderer（P2，可选，不阻塞 reliability）
```

MapLibre 仍为低优先级，不应阻塞 P0 GPS reliability 和当前本地 Trip 体验收口。

---

## 12. 变更记录

### v1.1.0

- 保留 Trip #7 第一轮十分钟级 GPS gap 历史证据。
- 新增 2026-08-27 Trip #2 第二轮真机证据：后台切 App 空洞、红灯 stationary gap 风险、122.4 km/h network 假峰值、海拔/地址/marker/速度色轨反馈。
- 同步 PR #80 callback-liveness 修复，并保留 #77 真机验收门。
- 同步 PR #82 max-speed trusted GPS quality gate，并保留 #78 仪表对照验收门。
- 同步 PR #83 Trip altitude/address/endpoint marker 实现。
- 同步 PR #85 trusted GPS speed-colored route；两端都可信才上色，未知段灰色。
- 明确完整 `TripSpeedSegmentBuilder` 尚未实现，不能把第一版 route visualization 写成完整 segment analytics。
- 状态从 `Design / Future Implementation` 更新为 `Partially Implemented / Physical Acceptance Pending`。

### v1.0.0

- 基于真实 Trip #7 建立 P0 GPS reliability 与 P1 speed visualization 方案。
- 明确 COMPLETED 不等于 GPS continuity。
- 定义 GPS health、provider quality、速度颜色与交通语义边界。
- 明确长 GPS gap 不得以实线伪造。
