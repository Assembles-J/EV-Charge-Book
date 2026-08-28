# EV Charge Book Location / Map / Trip Tracking Design

版本: v1.4.0
更新时间: 2026-08-27
状态: Authority Subdocument

## 1. 目标

为 EV Charge Book 增加低成本、Local First 的定位与行程记录能力，记录真实驾驶轨迹并形成可解释的数据资产。

核心原则:

1. 定位记录与地图展示解耦。
2. 原始轨迹默认保存在本地 Room。
3. 不依赖单一地图厂商才能完成行程记录。
4. 用户必须明确启动记录；v0.2 不做无感后台自动追踪。
5. 位置信息属于敏感数据，权限、通知和数据保留必须透明。
6. 记录可靠性优先于采样频率，必须控制耗电和数据库体积。
7. 不能用两端 GPS 点直连来伪造缺失路线；GPS gap 必须显式表达。
8. 在没有道路类型/限速/交通数据前，速度颜色只能表达本车速度分布，不能宣称真实拥堵等级。
9. 速度值必须保留来源语义；GNSS、位置差分和未来 OBD 不得混成一个无来源数字。

详细可靠性与速度可视化方案见 `TRIP_GPS_RELIABILITY_AND_SPEED_VISUALIZATION.md`。

---

## 2. 技术决策

### 定位核心

优先使用 Android 系统 Location API / GNSS 能力，并封装 `LocationProvider` 接口。

记录能力不要求地图 SDK。Android Location 可提供:

- latitude / longitude
- altitude（设备支持时）
- speed
- bearing
- horizontal accuracy
- speed / altitude accuracy（系统支持时）
- timestamp

统一保存 WGS84 原始坐标；若未来接入国内地图厂商需要 GCJ-02，仅在地图/provider adapter 层转换，数据库不混存坐标系。

### 地图展示

首选开源 `MapLibre Native` 作为地图渲染层，并建立 `MapProvider` 接口。

MapLibre 本身开源免费，但瓦片/地图数据服务不是天然无限免费。正式公开分发前必须选择合规、可持续的 tile provider 或自托管方案。

高德作为可选中国地图 adapter，而不是核心记录依赖。

---

## 3. v0.2 行程流程

```text
用户选择车辆
  -> 点击“开始行程”
  -> 请求精确定位权限
  -> 启动 location foreground service
  -> 持续采样 Location
  -> 写入 Room TripPoint
  -> 实时更新耗时 / 距离 / 当前速度
  -> 用户点击“结束行程”
  -> 计算 TripSession 汇总
  -> 保存并展示轨迹
```

持续记录时显示常驻通知，并提供“结束记录”动作。

---

## 4. 采样策略

### 4.1 callback 与持久化必须分层

2026-08-27 真机数据证明，不能同时在 Android LocationManager 层和业务层使用较大的位移门槛，否则停车时可能没有 callback，业务层也就无法证明“定位仍健康但车辆静止”。

当前实现明确分为两层：

**系统 callback 层：**

- `TripTrackingService` 请求间隔约 4 秒。
- `SAMPLE_DISTANCE_METERS = 0f`，不再要求至少移动 8m 才允许 callback。
- 目标是保持时间维度的定位活性与可诊断性，不代表每 4 秒都必须写 Room。

**业务采样 / 写库层：**

- `TripSamplingRules` 继续负责 accuracy、跳点、移动/静止判断与写库节流。
- 移动判定当前以约 `1 m/s` 报告速度或约 `5m` 位移作为基础条件。
- 静止状态默认约 15 秒保留一个 heartbeat，避免 4 秒一次永久高频写库。
- accuracy 过差或不可信跳点不参与可信统计。

因此当前设计不是“0m 高频永久采样”，而是：

```text
Location callback 保活
  -> 业务质量过滤
  -> 移动点正常记录 / 静止 heartbeat 降频
  -> Room
```

目的:

- 让红灯/停车仍能证明 tracking callback 健康；
- 降低定位耗电和 Room 数据量；
- 降低 GPS 抖动造成的假距离；
- 为后台 callback starvation 提供可诊断证据。

结束行程后可增加轨迹简化用于地图展示；原始点是否长期保留由数据保留策略决定。

### 4.2 GPS gap 不是普通降频

静止降频只能解释正常 heartbeat 间隔，不能解释十分钟级位置空洞。

真实行程已经观察到 12 分钟、15 分钟，以及 2026-08-27 第二轮实机中的约 10 分 43 秒 / 7 分 45 秒移动空洞。因此必须继续区分：

- lastLocationCallbackAt
- lastAcceptedPointAt
- lastGpsPointAt / lastNetworkPointAt
- accepted / rejected point count
- longest gap
- service lifecycle / re-delivery evidence

PR #36 已实现运行时 GPS health 与 ongoing notification，并在长 gap 时把轨迹拆成多个可信 segment。

PR #80 进一步移除 LocationManager 的 8m callback 位移门槛，让现有 15s stationary heartbeat 有机会在停车状态真正执行。该代码已进入 `main`；“切换其他 App 后是否持续回调”仍属于 #77 真机验收项，不能由 CI 代替。

---

## 5. 时间、距离与速度口径

Trip 至少区分:

- elapsed time: 开始到结束总耗时
- moving time: 有效移动时间
- stopped time: 停车/静止时间

首版停车识别采用简单可信规则，不把阈值写成不可变产品事实。当前静止 heartbeat 会把可信静止区间累积到 `stoppedSeconds`；后续仍需用城市道路真机数据继续校准。

### 5.1 当前距离如何计算

当前 `distanceMeters` 来自连续 accepted point 之间的可信地理直线距离累计。

因此它不是道路里程，也不是车辆轮速里程；在以下场景仍会存在偏差：

- 道路连续弯曲但采样点较稀
- GPS 漂移
- GPS / Network provider 切换
- 长时间 GPS callback/usable-location 缺失

`TripContinuityRules` 当前已对 `>=120s` 的长 gap 建立 baseline：恢复后的点可以作为新的轨迹起点，但 gap 两端 **不计可信距离、不计该段 moving/stopped duration，也不允许该 gap 直接贡献速度统计**。

因此“长 gap 两端不得直接累计直线距离”的 P0 规则已经落地，不再是 future follow-up。

平均速度仍必须被理解为基于“已记录可信 GPS 距离”的派生值，而不是车辆仪表级道路里程事实。

### 5.2 当前速度到底来自哪里

当前存在不同层次的速度概念：

1. `Location.speed`：Android Location 提供的定位时刻速度，原始值保存在 TripPoint `speedMps`。
2. point-to-point distance / time：用于连续性、距离和速度可信度校验证据，不冒充车辆瞬时速度。
3. route visualization speed：首版使用相邻两端都通过可信 GPS 质量门的 `Location.speed` 做显示派生。

PR #82 后，“最高已记录速度”不再允许任意 provider 的原始 `Location.speed` 直接刷新。当前 max-speed candidate 必须满足：

- provider = GPS；
- horizontal accuracy <= 25m；
- Android 提供 speed accuracy 时，speed accuracy <= 3 m/s；
- 同时通过现有连续距离/时间 corroboration。

Network fallback 原始速度仍保存在 TripPoint 便于审计，但不能制造新的 `maxSpeedMps`。这正是 2026-08-27 真机中约 122.4 km/h / 142.6 km/h network 粗定位峰值后的修正。

短时速度峰值仍存在漏采可能。例如真实峰值只持续约 4 秒，而定位 callback/accepted point 没有覆盖峰值，则产品不能自行推算。UI 应理解为“最高已记录可信 GNSS 速度”，不是车辆真实最高速度的绝对证明。

### 5.3 速度 UI

必须区分：

1. 全程平均速度：total recorded distance / elapsed time
2. 行驶平均速度：total recorded distance / moving time
3. 分段/轨迹速度：可信 route segment 的显示派生
4. 最高已记录速度：经异常过滤后的可信 GPS `Location.speed` 峰值

PR #36 已把详情页原先模糊的“平均速度”拆成全程均速 / 行驶均速 / 最高速度 / 移动时间。

PR #85 已实现第一版彩色 route preview，不新增 Room 表：

- 只有相邻两点的速度都通过可信 measured-speed 质量门时，该线段才按速度上色；
- 任一端速度缺失或不可信时保持中性灰色，不把未知速度当成 0 km/h；
- 长 GPS gap 继续保持断开；
- 颜色连续映射为深红 -> 红 -> 黄 -> 绿 -> 蓝。

当前颜色语义只代表本车可信 GPS 速度分布：

- 0-5 km/h：深红
- 5-15 km/h：红
- 15-30 km/h：红 -> 黄
- 30-50 km/h：黄 -> 绿
- 50-70 km/h：绿
- 70-90 km/h：绿 -> 蓝
- 90+ km/h：蓝 / 深蓝

在未接道路类型 / 限速 / map matching / 实时交通数据前，不展示“严重拥堵 / 拥堵 / 畅通”等交通事实标签。

更长窗口的 `TripSpeedSegmentBuilder`（例如 20-30 秒 / 200-300m 综合 segment）仍属于后续分析能力，不能把 #85 的相邻可信点颜色渲染误写成已经完成完整分段统计模型。

---

## 6. 速度数据源演进

保持简单的可替换接口思想，不提前做复杂基础设施：

```text
VehicleSpeedSource
├── GnssSpeedSource       当前默认，来自 Location.speed
├── DerivedSpeedSource    GPS 点距离/时间，只做分段派生与校验
└── ObdSpeedSource        P3 可选，外接 OBD-II 设备
```

数据来源原则：

- GNSS speed 与 derived speed 必须可区分。
- future OBD speed 必须保留 `source=OBD`，不得覆盖原始 GNSS 事实。
- 多来源可以互相校验，但首版不做“智能融合成唯一真值”。

### OBD-II 边界

OBD-II 作为 P3 可选增强方向，优先只验证标准 Vehicle Speed 数据是否可读。

可行的最小实验：

```text
车辆 OBD-II 口
  -> Bluetooth/BLE/Wi-Fi adapter
  -> 查询标准支持能力
  -> 读取 Vehicle Speed
  -> 与 GNSS speed 对照
```

当前不进入产品主线的内容：

- 厂商私有 CAN ID 逆向
- 私有 BMS PID 逆向
- 为单一车型维护复杂协议表
- 以 OBD 作为 Trip 必需依赖

如果未来 OBD 验证出稳定价值，再逐步考虑 SOC / 电压 / 电流 / 电池温度等车辆事实；这些不应阻塞当前 GPS Trip 产品完善。

---

## 7. 中断与恢复

真实设备必须考虑:

- App 进程被杀
- 定位短暂丢失
- 手机重启
- 地库/隧道无 GPS
- foreground service 异常退出
- foreground service 仍存活但 LocationManager 长时间无 callback
- foreground service 存活但切换其他 App 后 ROM / 系统限制导致 callback starvation

TripSession 使用 `RECORDING / INTERRUPTED / COMPLETED` 状态。

App 启动时发现未正常结束的 Trip:

```text
检测到未结束行程
[继续记录] [结束并保存] [删除]
```

禁止静默创建第二条并发 Trip。

同一时刻同一设备默认只允许一个活动 Trip。

注意：`COMPLETED` 只表示最终正常收口，不代表中间 GPS 连续。因此 GPS continuity 必须独立统计。

---

## 8. GPS / Network Provider 质量

当前允许 GPS + Network provider fallback，但不同 provider 不具备同等统计权限。

当前原则与已实现基线：

- GPS 高质量点优先作为主轨迹事实。
- Network point 可作为 fallback / continuity evidence。
- 时间非常接近的 GPS / Network point 通过 continuity rule 做去重/择优基线，避免重复计距离。
- Network / coarse speed 不允许刷新最高速度。
- PR #85 后，Network / coarse speed 也不允许给 route speed segment 上色。
- provider switch 与 rejected point 继续保留诊断价值。
- 原始 TripPoint 不因派生统计过滤而被重写。

GPS 海拔:

- 保存 raw altitude + vertical accuracy（有则保存）。
- PR #83 已在 Trip 详情展示起点 / 终点 / 最低 / 最高海拔。
- 累计爬升/下降仍必须经过平滑后计算，当前未实现。
- 不宣称测绘级精度。

---

## 9. 后台与通知

当前前台定位服务持续显示 ongoing notification。

PR #36 已增加：

- GPS health：WAITING / GOOD / DEGRADED / LOST / LONG_GAP
- 最近有效定位时间
- 最近 accepted provider
- rejected point count
- 同一条通知周期刷新，不刷屏
- 不显示经纬度或精确地址

PR #80 已增加 callback-liveness 修正：

- LocationManager `minDistance` 从 8m 调整为 0m；
- 系统 callback 与业务写库节流解耦；
- 业务层继续使用 stationary heartbeat 控制 Room 写入。

诊断事件已经覆盖 service start / START_REDELIVER_INTENT redelivery / destroy / provider disabled / permission missing / location registration failure 等核心生命周期证据。

仍需继续补或真机确认：

- provider counters
- longest gap / cumulative gap summary
- 切换其他 App 5-10 分钟后的真实 callback continuity
- Android 厂商后台限制/电池优化导致 starvation 时的修复引导

该方向由 #77 与 #26 分别承担数据可靠性和后台修复 UX。

---

## 10. GPS Gap 可视化

禁止将长时间缺失的两个 GPS 点直接画成可信实线。

当前按 `>=120s` gap 将路线几何拆成多个可信 segment；预览只绘制 segment 内部连续轨迹。

```text
已知轨迹 ━━━━━     ━━━━━ 已知轨迹
             GPS 缺失
```

同时，距离统计已与该语义对齐：长 gap 两端不再直接累计可信直线距离，也不计算该 gap 的移动/静止时长和 aggregate speed。

无 basemap 阶段即可做断开表达；MapLibre 后续只负责更好的 renderer，不负责补造缺失事实。

---

## 11. 自动化演进

v0.2 不默认实现“检测开车后自动偷偷开始记录”。

后续顺序:

1. 连接指定车载蓝牙时提示开始
2. Activity Recognition / in_vehicle 作为辅助提示
3. 用户主动开启“连接车辆后自动记录”后再实现

自动开始必须有清晰开关和可见通知。

---

## 12. 地点与地址展示

新增充电记录支持:

- 使用当前位置
- 经纬度保存
- 地点名称人工编辑

逆地理编码属于独立 provider，不成为保存记录的硬依赖。

PR #83 已把相同的 coordinate-first 原则应用到 Trip 详情：

- WGS84 起终点坐标仍是权威事实；
- 已完成/中断 Trip 可通过现有 `AndroidGeocoderAddressResolver` 解析起终点地址用于展示；
- 地址作为主阅读文本，坐标作为技术参数保留；
- geocoder 失败显示“地址暂不可用”，不得阻止 Trip 完成或虚构地点；
- RECORDING 中不反复解析实时 endpoint 地址。

后续 ChargingPlace 设计见 `DATA_QUALITY_BACKUP.md`。

---

## 13. 隐私

- 首次启用解释定位用途
- 默认本地保存
- 持续记录必须有通知
- 云同步轨迹需单独同意
- 支持单条/全部轨迹清理

后续分享轨迹支持 Privacy Zone:

- HOME 等隐私区域
- 导出/分享时裁掉隐私区域内精确起终点
- 本地原始数据可保留

---

## 14. 验收目标

Core 已完成：

- [x] 获取当前位置
- [x] 充电记录可绑定当前位置
- [x] 用户手动开始/结束行程
- [x] 经纬度 / GPS 海拔 / 速度 / 精度 / 时间
- [x] elapsed / moving / stopped time 数据结构与基础累计
- [x] 距离 / 平均速度 / 最高速度
- [x] Trip 绑定具体 Vehicle
- [x] 中断行程恢复
- [x] 删除 Trip 同步处理 TripPoint
- [x] Trip 起终点地址展示 + 坐标技术参数
- [x] Trip 起点圆形 / 终点方形语义标记
- [x] Trip 起点 / 终点 / 最低 / 最高海拔展示

P0 reliability：

- [x] 运行时 GPS health / accepted point heartbeat
- [x] GPS LOST / LONG_GAP ongoing notification
- [x] long gap 路线断开，不画可信实线
- [x] 长 gap 两端不参与可信距离/时长/aggregate speed
- [x] GPS/Network 时间邻近去重/择优基线
- [x] service start / destroy / re-delivery 等诊断事件
- [x] Network / coarse point 不得制造最高速度峰值
- [ ] longest gap / provider counters / rejected reason 持久摘要
- [ ] 切换其他 App 后后台 callback / 距离连续性真机复验（#77）
- [ ] 2-3 分钟红灯 stationary heartbeat / stoppedSeconds 真机复验（#77）
- [ ] 最高已记录速度与车辆实际峰值真机复验（#78）

P1 speed visualization：

- [x] 全程平均 / 行驶平均 / 最高速度明确区分
- [x] 可信 GPS 速度连续颜色 route preview
- [x] 不可信/未知速度 segment 保持中性灰色
- [x] 长 GPS gap 不跨缺口上色或画可信实线
- [x] UI 明确“本车速度分布”，不宣称真实道路拥堵
- [ ] `TripSpeedSegmentBuilder` 长窗口派生模型
- [ ] 20-30s / 200-300m 等综合 segment 平均速度与 JVM tests
- [ ] 短时峰值与综合 segment speed 的进一步分析展示
- [ ] 彩色 route dark/light 真机视觉验收（#67）

P3 optional vehicle data：

- [ ] OBD-II Vehicle Speed 最小 PoC
- [ ] GNSS vs OBD 对照验证
- [ ] 证明稳定价值后再评估更多车辆数据

Optional：

- [ ] MapLibre 显示真实 basemap 路线

---

## 15. 变更记录

### v1.4.0

- 同步 2026-08-27 第二轮真机 Trip 证据与 #77/#78 新 P0 reliability ownership。
- 同步 PR #80：LocationManager 取消 8m callback 位移门槛，系统 callback 与 15s stationary heartbeat 写库节流解耦。
- 修正长 gap 状态：`>=120s` 已不计可信距离、duration 与 aggregate speed，不再是 future follow-up。
- 同步 PR #82：最高已记录速度只允许通过可信 GPS 质量门的 candidate，Network 粗速度保留原始事实但不更新 max。
- 同步 PR #83：Trip 详情展示起/终/最低/最高海拔，起终点地址作为派生展示、坐标保持权威技术参数，起点圆形/终点方形避免只靠颜色区分。
- 同步 PR #85：相邻两端都可信时按本车 GPS 速度连续着色；未知/不可信段保持灰色；交通拥堵语义继续禁止。
- 明确上述 CI/代码完成不等于后台连续性、最高速或彩色轨迹的最终真机验收。

### v1.3.0

- 明确 Location.speed 是当前瞬时/最高速度主要来源，不等同于点间直线速度
- 明确平均速度仍受 GPS 累计距离质量影响
- 将短时速度峰值漏采作为真实采样限制记录
- 固定 GNSS / Derived / OBD 三类速度来源语义
- OBD-II 放入 P3 可选增强，只先验证标准 Vehicle Speed，不进入私有 CAN/BMS 逆向
- 同步 PR #36 GPS health、通知、gap 断线及速度 UI 已实现范围

### v1.2.0

- 基于真实 Trip #7 将十分钟级 GPS gap 提升为 P0 reliability 问题
- 增加 GPS callback / provider / service lifecycle 可诊断设计
- 明确 COMPLETED 不等于 GPS continuity 完整
- 增加全程平均 / 行驶平均 / 分段平均速度三种口径
- 增加连续速度颜色方案及交通语义边界
- 明确 GPS gap 不得以实线伪造

### v1.1.0

- 增加 elapsed/moving/stopped 时间口径
- 增加中断行程恢复设计
- 限制采样频率以控制耗电和数据库体积
- 增加 Privacy Zone 后续设计
- 明确自动记录演进顺序

### v1.0.0

- 建立定位 / 地图 / 行程追踪权威设计
- 确定 Android Location + MapLibre 解耦架构
- 明确手动开始 + foreground service
