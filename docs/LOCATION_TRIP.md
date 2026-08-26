# EV Charge Book Location / Map / Trip Tracking Design

版本: v1.1.0
更新时间: 2026-08-26
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
  -> 批量写入 Room TripPoint
  -> 实时更新耗时 / 距离 / 当前速度
  -> 用户点击“结束行程”
  -> 计算 TripSession 汇总
  -> 保存并展示轨迹地图
```

持续记录时显示常驻通知，并提供“结束记录”动作。

---

## 4. 采样策略

初始建议:

- 行驶中目标间隔: 2-5 秒
- 最小位移参考: 5-10 米
- 低速/停车时自适应降低频率
- accuracy 过差点不参与距离聚合

禁止默认 1 秒永久高频采样。

目的:

- 降低定位耗电
- 控制 Room 数据量
- 降低 GPS 抖动造成的假距离

结束行程后可在后续版本增加轨迹简化用于地图展示；原始点是否长期保留由数据保留策略决定。

---

## 5. 时间与停车口径

Trip 至少区分:

- elapsed time: 开始到结束总耗时
- moving time: 有效移动时间
- stopped time: 停车/静止时间

首版停车识别可以采用简单规则:

- speed 低于约 1-2 km/h
- 持续达到阈值后计为 stopped

阈值需要真机数据调优，不写死成产品事实。

平均速度 UI 必须标注使用“全程平均”还是“行驶平均”。

---

## 6. 中断与恢复

真实设备必须考虑:

- App 进程被杀
- 定位短暂丢失
- 手机重启
- 地库/隧道无 GPS
- foreground service 异常退出

TripSession 使用 `RECORDING / INTERRUPTED / COMPLETED` 状态。

App 启动时发现未正常结束的 Trip:

```text
检测到未结束行程
[继续记录] [结束并保存] [删除]
```

禁止静默创建第二条并发 Trip。

同一时刻同一设备默认只允许一个活动 Trip。

---

## 7. 速度与海拔

速度优先保存系统 Location speed（m/s），展示转换为 km/h。

max speed 必须过滤明显异常点。

GPS 海拔:

- 保存 raw altitude + vertical accuracy（有则保存）
- UI 标注“GPS 海拔”
- 累计爬升/下降必须经过平滑后计算
- 不宣称测绘级精度

---

## 8. 自动化演进

v0.2 不默认实现“检测开车后自动偷偷开始记录”。

后续顺序:

1. 连接指定车载蓝牙时提示开始
2. Activity Recognition / in_vehicle 作为辅助提示
3. 用户主动开启“连接车辆后自动记录”后再实现

自动开始必须有清晰开关和可见通知。

---

## 9. 地点与充电记录

新增充电记录支持:

- 使用当前位置
- 经纬度保存
- 地点名称人工编辑

逆地理编码属于独立 provider，不成为保存记录的硬依赖。

后续 ChargingPlace 设计见 `DATA_QUALITY_BACKUP.md`。

---

## 10. 隐私

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

## 11. 验收目标

- [ ] 获取当前位置
- [ ] 充电记录可绑定当前位置
- [ ] 用户手动开始/结束行程
- [ ] 锁屏后 foreground service 持续记录
- [ ] 经纬度 / GPS 海拔 / 速度 / 精度 / 时间
- [ ] elapsed / moving / stopped time
- [ ] 距离 / 平均速度 / 最高速度
- [ ] MapLibre 显示路线
- [ ] Trip 绑定具体 Vehicle
- [ ] 中断行程恢复
- [ ] 删除 Trip 同步处理 TripPoint

---

## 12. 变更记录

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
