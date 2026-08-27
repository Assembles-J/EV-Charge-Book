# Trip GPS Reliability & Speed Visualization Plan

版本: v1.0.0
更新时间: 2026-08-27
状态: Design / Future Implementation

## 1. 背景

2026-08-27 的真实 Trip #7 数据暴露出两个需要优先处理的问题：

1. 单一全程平均速度无法表达高速、城市道路、拥堵/低速等不同区段的驾驶特征。
2. 行程中出现多段明显 GPS 空洞，最长可达十分钟级，不能继续只靠最终 COMPLETED 状态判断记录是否可靠。

该文档只定义后续实现方向，不修改现有原始 Trip 数据口径。

---

## 2. 真实数据证据

Trip #7：

- distanceMeters: 42643.19 m
- elapsedSeconds: 4137 s
- movingSeconds: 4090 s
- stoppedSeconds: 20 s
- averageSpeedMps: 10.4262 m/s（约 37.5 km/h）
- maxSpeedMps: 25.92 m/s（约 93.3 km/h）
- status: COMPLETED

但 TripPoint 中存在多段长时间缺口，例如：

- Point 64 -> 65 约 749 秒（12 分 29 秒）
- Point 70 -> 71 约 917 秒（15 分 17 秒）

因此：

- `COMPLETED` 只能说明 Trip 最终正常收口，不代表中间 GPS 连续。
- 需要独立的 GPS health / continuity 指标。

---

## 3. P0 - GPS 后台可靠性与可诊断性

### 3.1 目标

下一次出现断点时，必须能区分：

- Foreground Service 被系统杀/重启
- LocationManager 长时间没有回调
- GPS provider 不可用
- network provider fallback
- Location 被业务过滤
- 权限或系统定位状态异常

### 3.2 需要记录的运行时状态

建议先以轻量内存状态 + Trip 级派生摘要实现，是否落库后续再决定。

至少包括：

- lastLocationCallbackAt
- lastAcceptedPointAt
- lastGpsPointAt
- lastNetworkPointAt
- acceptedPointCount
- rejectedPointCount
- rejectedByAccuracyCount
- rejectedByJumpCount
- providerSwitchCount
- serviceStartCount / serviceRestartCount（若能可靠识别）
- longestLocationGapSeconds

### 3.3 GPS Health 状态

UI / Notification 使用统一派生状态：

- GOOD：最近有效定位 <= 10s
- DEGRADED：10-30s
- LOST：> 30s
- LONG_GAP：> 120s

阈值为初始建议，必须通过真机数据调优，不能作为固定业务事实。

### 3.4 Notification

Foreground notification 后续至少展示：

- 正在记录行程
- 已记录时长
- 已记录距离
- GPS 状态
- 最近有效定位时间
- 结束行程动作

当 GPS LOST / LONG_GAP 时更新同一条 ongoing notification，不重复刷通知。

### 3.5 Service 生命周期

当前服务已使用 location foreground service + `START_REDELIVER_INTENT`。

后续需要明确记录：

- onCreate / onStartCommand / onDestroy
- start reason / restored intent
- 是否发生 service re-delivery

目的不是永久日志堆积，而是能解释真实行程中的 GPS 空洞。

### 3.6 GPS / Network provider 融合

当前允许 GPS + Network provider 同时进入 TripPoint。

后续原则：

- GPS 高质量点优先作为主轨迹事实
- Network point 作为 fallback / continuity evidence
- 不默认把 GPS 与 Network 视为同等质量
- provider 切换需可观察
- 避免 network 漂移点制造假距离或假速度

---

## 4. P1 - 分段速度模型

### 4.1 三种速度必须区分

Trip UI 至少区分：

1. 全程平均速度：总距离 / elapsed time
2. 行驶平均速度：总距离 / moving time
3. 分段平均速度：某一区段内的有效距离 / 时间

现有 `averageSpeedMps` 当前更接近 moving-average 口径，后续 UI 必须明确命名，避免用户把它理解成全程平均。

### 4.2 TripSpeedSegment

先做派生模型，不新增 Room 表：

```text
TripPoint[]
  -> TripSpeedSegmentBuilder
  -> TripSpeedSegment[]
  -> UI renderer
```

推荐区段切分优先按时间/距离混合：

- 20-30 秒，或
- 200-300 米

具体阈值通过真实数据调优。

每个 segment 至少包含：

- start/end timestamp
- distance
- duration
- averageSpeedKmh
- min/max speed（可选）
- GPS quality / gap flag

---

## 5. P1 - 速度颜色映射

### 5.1 初始视觉语义

在未接道路类型/限速/交通数据前，不使用“严重拥堵 / 畅通”等交通事实词。

先使用本车速度语义：

- 深红：极低速
- 红：低速
- 黄：较慢
- 绿：正常/快速
- 蓝：高速

### 5.2 连续颜色而不是四档硬切

建议采用连续渐变：

- 0-5 km/h：深红
- 5-15 km/h：红
- 15-30 km/h：红 -> 黄
- 30-50 km/h：黄 -> 绿
- 50-70 km/h：绿
- 70-90 km/h：绿 -> 蓝
- 90+ km/h：蓝 / 深蓝

最终颜色阈值属于 UI 配置，不写入原始 TripPoint。

### 5.3 交通语义边界

在没有道路类型 / 限速 / map matching / 实时交通数据前：

- 8 km/h 可能只是停车场
- 15 km/h 可能只是小区道路
- 40 km/h 在城市道路可能畅通，在高速可能拥堵

因此彩色轨迹代表“本车实际速度分布”，不是“道路拥堵等级”。

---

## 6. GPS Gap 可视化

禁止把长时间缺失的两端 GPS 点直接连成实线路线。

建议：

```text
已知轨迹 ━━━━━ · · · · · ━━━━━ 已知轨迹
               GPS 缺失
```

规则：

- gap 超过阈值后，renderer 标记为 disconnected segment
- 无 basemap 阶段可用虚线/断开提示
- MapLibre 阶段使用独立 dashed polyline
- 不做假 map matching，不补造路径

---

## 7. 验收标准

### P0 GPS Reliability

- [ ] 能显示最近有效定位时间
- [ ] 能识别 >30s GPS gap
- [ ] 能统计最长 GPS gap
- [ ] 能区分 accepted / rejected point
- [ ] 能区分 GPS / Network provider
- [ ] 能判断 service 是否发生 restart / re-delivery（若 Android 生命周期允许可靠识别）
- [ ] GPS 长时间丢失时 ongoing notification 明确提示
- [ ] 真实锁屏行程完成后能解释轨迹断点原因

### P1 Speed Segmentation

- [ ] UI 区分全程平均 / 行驶平均 / 最高速度
- [ ] TripSpeedSegment 为派生模型，不改原始点
- [ ] 分段平均速度有 JVM tests
- [ ] 轨迹支持连续颜色映射
- [ ] 长 GPS gap 不画成可信实线
- [ ] 未接交通数据前不宣称“真实拥堵等级”

---

## 8. 实施顺序

```text
P0: GPS callback / service lifecycle telemetry
  -> GPS health state
  -> notification health display
  -> provider quality / gap diagnostics

P1: TripSpeedSegmentBuilder
  -> 全程/行驶平均速度口径
  -> SpeedColorScale
  -> 彩色 route preview
  -> GPS gap dashed/disconnected rendering

P2: MapLibre renderer
  -> true basemap
  -> colored polyline
  -> dashed GPS gaps
```

MapLibre 仍为低优先级，不应阻塞 P0 GPS reliability。
