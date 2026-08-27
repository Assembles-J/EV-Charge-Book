# EV Charge Book Location / Map / Trip Tracking Design

版本: v1.3.0
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

当前目标:

- 行驶中目标间隔: 2-5 秒
- 最小位移参考: 5-10 米
- 低速/停车时自适应降低频率
- accuracy 过差点不参与距离聚合

禁止默认 1 秒永久高频采样。

目的:

- 降低定位耗电
- 控制 Room 数据量
- 降低 GPS 抖动造成的假距离

结束行程后可增加轨迹简化用于地图展示；原始点是否长期保留由数据保留策略决定。

### GPS gap 不是普通降频

静止降频只能解释短间隔缺点，不能解释十分钟级位置空洞。

真实行程已经观察到 12 分钟和 15 分钟级 GPS gap，因此必须逐步补齐：

- lastLocationCallbackAt
- lastAcceptedPointAt
- lastGpsPointAt / lastNetworkPointAt
- accepted / rejected point count
- longest gap
- service lifecycle / re-delivery evidence

PR #36 第一阶段已经实现运行时 GPS health 与 ongoing notification，并在长 gap 时把轨迹拆成多个可信 segment。

---

## 5. 时间、距离与速度口径

Trip 至少区分:

- elapsed time: 开始到结束总耗时
- moving time: 有效移动时间
- stopped time: 停车/静止时间

首版停车识别可以采用简单规则:

- speed 低于约 1-2 km/h
- 持续达到阈值后计为 stopped

阈值需要真机数据调优，不写死成产品事实。

### 5.1 当前距离如何计算

当前 `distanceMeters` 来自连续 accepted GPS point 之间的地理直线距离累计。

因此它不是道路里程，也不是车辆轮速里程；在以下场景会存在偏差：

- 道路连续弯曲但采样点较稀
- GPS 漂移
- GPS / Network provider 切换
- 长时间 GPS gap 后直接把 gap 两端连接

产品规则：长时间 gap 两端不能继续作为“可信连续距离”直接累计。该规则属于 P0 reliability follow-up；在落地前，平均速度必须被视为基于已记录 GPS 距离的派生值，而不是车辆仪表级事实。

### 5.2 当前速度到底来自哪里

当前存在两个完全不同的概念：

1. `Location.speed`：Android Location 提供的定位时刻速度，当前用于 TripPoint `speedMps`，也是最高速度的主要来源。
2. `point-to-point distance / time`：可作为轨迹区段速度的派生/校验证据，但不应该冒充瞬时速度。

因此：

- 当前“最高速度”不是简单用两点直线距离除时间得到的。
- 当前“全程均速 / 行驶均速”依赖累计 `distanceMeters`，所以会受到 GPS 距离质量影响。
- 未来“分段速度”不能只用一个单点 `Location.speed`，也不能只用两点直线距离；应结合连续可信点、speed accuracy、provider 与 gap 状态。

短时速度峰值存在漏采可能。例如真实峰值只持续约 4 秒，而采样目标同样是 2-5 秒，则可能在峰值时没有产生有效 Location fix。产品上应区分“最高已记录 GNSS 速度”和“车辆真实最高速度”，不能把未采到的峰值推算出来。

### 5.3 速度 UI

必须区分：

1. 全程平均速度：total recorded distance / elapsed time
2. 行驶平均速度：total recorded distance / moving time
3. 分段平均速度：可信 segment 内的综合速度
4. 最高已记录速度：经异常过滤后的 Location.speed 峰值

PR #36 已把详情页原先模糊的“平均速度”拆成全程均速 / 行驶均速 / 最高速度 / 移动时间。

后续新增 `TripSpeedSegment` 派生模型，不新增 Room 表即可完成首版。

分段速度颜色采用连续渐变，语义为本车速度分布：

- 深红：极低速
- 红：低速
- 黄：较慢
- 绿：正常/快速
- 蓝：高速

在未接道路类型 / 限速 / map matching / 实时交通数据前，不展示“严重拥堵 / 拥堵 / 畅通”等交通事实标签。

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

当前允许 GPS + Network provider fallback。

后续原则：

- GPS 高质量点优先作为主轨迹事实
- Network point 作为 fallback / continuity evidence
- 不默认将 GPS 与 Network 视为同等质量
- provider switch 必须可观察
- 时间非常接近的 GPS / Network point 需要去重/择优，避免重复计距离
- network 漂移点不能制造假距离或假速度

GPS 海拔:

- 保存 raw altitude + vertical accuracy（有则保存）
- UI 标注“GPS 海拔”
- 累计爬升/下降必须经过平滑后计算
- 不宣称测绘级精度

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

后续继续补：

- service restart / re-delivery evidence
- provider counters
- longest gap / cumulative gap summary

该方向与 Issue #26 的后台活动可见性方案保持一致。

---

## 10. GPS Gap 可视化

禁止将长时间缺失的两个 GPS 点直接画成可信实线。

PR #36 已按 `>=120s` gap 将路线几何拆成多个可信 segment；预览只绘制 segment 内部连续轨迹。

```text
已知轨迹 ━━━━━     ━━━━━ 已知轨迹
             GPS 缺失
```

无 basemap 阶段即可做断开表达；MapLibre 后续只负责更好的 renderer，不负责补造缺失事实。

下一步必须让距离聚合与可视化语义一致：长 gap 两端不能继续直接累计直线距离。

---

## 11. 自动化演进

v0.2 不默认实现“检测开车后自动偷偷开始记录”。

后续顺序:

1. 连接指定车载蓝牙时提示开始
2. Activity Recognition / in_vehicle 作为辅助提示
3. 用户主动开启“连接车辆后自动记录”后再实现

自动开始必须有清晰开关和可见通知。

---

## 12. 地点与充电记录

新增充电记录支持:

- 使用当前位置
- 经纬度保存
- 地点名称人工编辑

逆地理编码属于独立 provider，不成为保存记录的硬依赖。

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
- [x] elapsed / moving / stopped time
- [x] 距离 / 平均速度 / 最高速度
- [x] Trip 绑定具体 Vehicle
- [x] 中断行程恢复
- [x] 删除 Trip 同步处理 TripPoint

P0 reliability follow-up：

- [x] 运行时 GPS health / accepted point heartbeat
- [x] GPS LOST / LONG_GAP ongoing notification
- [x] long gap 路线断开，不画可信实线
- [ ] 长 gap 两端不参与可信距离累计
- [ ] GPS/Network 去重与择优
- [ ] longest gap / provider counters / rejected reason 持久摘要
- [ ] service restart / re-delivery evidence
- [ ] 锁屏长行程真机复验

P1 speed visualization：

- [x] 全程平均 / 行驶平均明确区分
- [ ] TripSpeedSegment 派生模型
- [ ] 连续速度颜色映射
- [ ] 短时峰值与 segment speed 分开展示

P3 optional vehicle data：

- [ ] OBD-II Vehicle Speed 最小 PoC
- [ ] GNSS vs OBD 对照验证
- [ ] 证明稳定价值后再评估更多车辆数据

Optional：

- [ ] MapLibre 显示真实 basemap 路线

---

## 15. 变更记录

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
