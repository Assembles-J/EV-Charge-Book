# EV Charge Book Location / Map / Trip Tracking Design

版本: v1.0.0
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

MapLibre 本身开源免费，但瓦片/地图数据服务不是天然无限免费。开发阶段可以使用符合使用政策的 OSM 数据/瓦片；正式公开分发前必须选择合规、可持续的 tile provider 或自托管方案。

高德作为可选中国地图 adapter，而不是核心记录依赖。高德需要 API Key，个人非商业研究存在免费配额，但公开/商业/组织使用需要按其最新许可与配额规则评估。

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

Android 10+ foreground service 必须声明 location service type；持续记录时显示常驻通知，并提供“结束记录”动作。

v0.2 基础方案只要求用户在 App 可见时主动启动行程，然后前台服务继续记录。这样避免第一版为了“自动启动”引入更复杂的后台启动限制和权限。

---

## 4. 采样策略

初始建议:

- 行驶中目标间隔: 2-5 秒
- 最小位移参考: 5-10 米
- 低速/停车时自适应降低频率
- 明显低质量点（例如 accuracy 过差）不参与距离聚合，但可按调试策略保留

距离不直接累加所有 GPS 抖动；应过滤异常跳点后计算相邻有效点距离。

### 速度

优先保存系统 Location speed（m/s），展示时转换 km/h。

汇总:

- avgSpeed = validDistance / movingOrElapsedTime（口径必须明确）
- maxSpeed = 有效采样最大值，需过滤异常点

### 海拔

GPS 海拔天然比平面位置更容易抖动，因此:

- 保存 raw altitude + accuracy（若系统提供）
- UI 标注为“GPS 海拔”
- 爬升/下降量必须先平滑后聚合
- 不把手机 GPS 海拔包装成高精度地形测量

---

## 5. v0.2 不做的自动化

不默认实现“检测开车后自动偷偷开始记录”。原因:

- Android 后台 foreground service 启动限制
- 后台位置权限与应用商店审核要求
- 电量消耗
- 用户隐私预期
- 容易误判公交/出租车/高铁为自驾

后续可选增强:

1. 连接指定车载蓝牙时提示“是否开始行程”
2. Activity Recognition 判断 in_vehicle 后提示
3. 用户配置自动记录并明确授权后再研究自动启动

优先做“自动提醒”，晚于“自动记录”。

---

## 6. 地点与充电记录

新增充电记录时可以提供:

- “使用当前位置”按钮
- 经纬度保存
- 地点名称仍允许人工编辑

逆地理编码属于独立 provider 能力，不成为保存充电记录的硬依赖。网络失败时坐标仍可保存。

---

## 7. 隐私与数据安全

- 首次启用时解释为什么需要定位
- 仅在用户主动记录时持续采样
- 行程记录状态必须在通知中可见
- 默认本地存储
- 后续云同步时轨迹数据必须单独获得用户同意
- 提供单条行程删除与全部轨迹清理

---

## 8. 验收目标

v0.2 第一阶段完成标准:

- [ ] 获取当前位置
- [ ] 充电记录可绑定当前位置
- [ ] 用户手动开始/结束行程
- [ ] 后台锁屏后由 location foreground service 持续记录
- [ ] 记录经纬度、GPS 海拔、速度、精度和时间
- [ ] 计算距离、耗时、平均/最高速度
- [ ] MapLibre 显示已记录路线
- [ ] 行程与具体 Vehicle 绑定
- [ ] 删除行程同步删除轨迹点

---

## 9. 变更记录

### v1.0.0

- 建立定位 / 地图 / 行程追踪权威设计
- 确定 Android Location + MapLibre 解耦架构
- 明确 v0.2 采用手动开始 + foreground service
- 明确坐标、速度、海拔的真实性和隐私边界
