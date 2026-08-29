# EV Charge Book UI/UX 设计

版本: v1.3.0
更新时间: 2026-08-29
状态: Authority Document

## 1. 设计目标

关键词: 简洁、清晰、低录入成本、数据可信、长期可用。

当前 UI 已从早期 v0.1/v0.2 原型推进到 Dark First 的 v0.5/v0.6 产品形态。页面密度通过层级和间距提升，不通过缩小字体或堆叠装饰卡片实现。

全局规则:

- Dark First，Light mode 可切换并持久化
- 主强调色使用 `EVDesignTokens.Energy.green` (`#32F080`)
- 不使用蓝色作为主要动作色，不做霓虹 bloom / 大 halo / 广告式发光
- 原始事实、派生值、估算值必须明确区分
- 无数据时宁可显示不可用，也不伪造 SOC、路线、地址、速度、能耗或海拔
- 关键可点击区域尽量保持 >=48dp；320-360dp 与 fontScale 1.3 需要真机可用

详细 Trip v0.6 视觉/交互 authority 见 `TRIP_V0.6_APPROVED_UI_BASELINE.md`。

---

## 2. 当前一级信息架构

底部一级导航保持五个稳定入口:

```text
首页 Dashboard
记录 Records
统计 Stats
行程 Trip
车辆 Vehicle
```

不要再把 Trip 隐藏在 Records 二级入口中。Trip 已是一级产品能力。

当前车辆上下文会影响 Dashboard、Records、Stats、Trip 与新建记录目标；用户明确选择车辆后，不允许静默把不同车辆的数据混为单车数据。

---

## 3. Dashboard

Dashboard 当前重点是“车辆当前状态 + 最近发生了什么”，而不是展示静态规格表。

### Vehicle Hero

- 生成完成的车型 Hero 资产直接展示，不在 Compose 内重建极光/反射特效
- 当前 SOC 是主要动态事实；未知时显示 `--`
- 当前里程为次要动态事实
- 最近完成 Trip 的距离/可信平均能耗可作为辅助事实
- 不在 Hero 中重复电池容量、标称续航、`ACTIVE` 等静态或装饰信息

### Recent Trip

- 使用最新有效完成 Trip
- 时间/日期优先可见
- 起点、终点分行表达，保留语义图标/标签，不只靠颜色
- 距离、耗时、可信平均能耗/SOC/里程仅在有数据时显示
- 地址不可用时保留真实坐标或不可用状态，不编造地点

---

## 4. Records

Records 是充电账本，不是卡片画廊。

当前 v0.6 层级:

1. 紧凑累计账本摘要
2. 连续时间线式充电记录
3. 点击整行进入 Edit
4. 独立删除动作 + destructive confirmation

保留时间、SOC、电量、费用、类型、里程、地点、备注等真实记录事实。长地点/备注必须可预测截断；缺失地点明确显示未记录。

---

## 5. Stats

Stats 当前 v0.6 层级:

1. 本月支出 / 补能摘要
2. 上月比较
3. Trip SOC-derived 能耗估算
4. 月趋势 / charger mix / 常用地点
5. lifetime / interval evidence

Trip 能耗必须继续标记为估算/非 BMS；previous-month 为 0 时不制造百分比增长。

---

## 6. Trip v0.6

Trip 是当前最重要的驾驶数据页面之一。现有实现分为明确状态/阅读面，而不是一个超长页面。

### 6.1 Trip home / history

进入 Trip 且没有 active Trip 时，默认落到历史/概览，不直接暴露 READY 表单。

优先级:

1. 紧凑 `开始行程` 动作
2. 最新完成 Trip 摘要
3. 最近 Trip 历史列表
4. 日期/状态、起终点、距离/耗时、可信平均能耗

目标是普通手机可舒适浏览 5+ 条历史记录；长地址可预测截断。

### 6.2 READY / preparation

用户从 Trip home 明确进入 READY。

显示:

- 当前车辆
- 当前 SOC / 里程（已知时）
- 紧凑 GPS 实录说明
- restrained slide-to-start

规则:

- READY back 返回 Trip home，不创建空 Trip
- 未获得真实定位样本前不伪造 GPS accuracy/readiness
- slide progress 与 thumb 保持胶囊圆角
- partial drag 自然回弹
- accessibility 仍需存在等价语义动作

### 6.3 Active Trip cockpit

驾驶中只保留 glanceable、可信的信息:

主要事实:

- 已记录距离
- 当前/最近可信速度

辅助事实:

- 已记录时间
- 行驶均速
- 最高已记录可信速度
- 起始 SOC

支持面:

- 真实轨迹预览
- 可信 speed trend
- 有可信样本时 altitude trend
- interrupted 状态及显式恢复

Point count / altitude sample count 属于诊断，不重复占据主指标区。

结束使用 restrained slide-to-end；滑动后直接进入唯一的 Trip completion form，不再叠加一个通用“是否结束”AlertDialog。

### 6.4 Completion

完成表单先展示证据，再收集输入:

1. GPS 距离
2. 起始 SOC / 起始里程
3. 结束 SOC（必填 0..100）
4. 结束里程（可选、非负、不得低于起始里程）
5. 可计算时显示 SOC-derived energy estimate，并明确非 BMS

`继续行驶` / dismiss 不结束 Trip；只有合法 `保存并结束` 才真正 stop。

### 6.5 Completed detail

完成/选中的 Trip detail 已分为三个支持的阅读 section:

- `概览`: summary + 紧凑起终点卡
- `轨迹`: 真实 route + speed/altitude trends
- `数据`: altitude/reliability summary + raw point progressive disclosure

默认打开 `概览`。切换 section 不修改 Trip 数据。

不加入当前产品没有能力支撑的 `充电`、`备注`、目的地选择、导航预览或假地图。

### 6.6 Route / endpoint semantics

- 起点: 紧凑绿色 start/play 语义
- completed 终点: 小型红旗，无大 ring/halo
- active latest point: 绿色 `当前点`
- interrupted/non-final latest point: `最后记录点`，不得伪装成 completed endpoint
- LONG_GAP 保持断开，不把缺失路线补成可信连续实线
- 速度颜色仅代表本车可信 GPS speed，不代表道路拥堵

### 6.7 Diagnostics

默认不展开 raw GPS log。

可见摘要可以包含:

- continuity/reliability
- point count
- accuracy context
- long-gap summary
- altitude start/end/min/max/ascent/descent
- speed / altitude trends

raw points 通过 `查看轨迹点` 显式展开。

---

## 7. Location / permission / background UX

坐标是事实，地址是派生展示。Geocoder 失败不能阻塞记录。

Trip 需要用户明确启动；持续记录必须有 foreground notification。

当前通知/中断 UX:

- ongoing notification 显示已记录时间 + 可信累计距离
- notification 可直接回 active Trip
- runtime Location permission/provider loss -> Trip `INTERRUPTED`
- repair notification 提供系统设置入口
- 修复后由用户明确恢复，不自动后台 resume
- Android 13+ 通知权限拒绝不能阻塞 Trip 本身
- 锁屏通知不展示精确坐标 / HOME / WORK 地址

后台 callback / stationary hold 的最终可靠性仍由 #77 真机验收，不能从 CI 推导。

---

## 8. Map 边界

地图/route renderer 只消费已有 TripPoint，不参与记录事实生成。

当前已经拥有无底图真实 WGS84 route preview；MapLibre 仍是可选未来 renderer，不是当前 Trip v0.6 完整性的前置条件。

不为了“像地图”而道路吸附、补线或制造假 destination。

---

## 9. Vehicle

车辆页当前采用 garage/list + switcher，而不是无限横向卡片。

支持:

- 当前车辆切换
- 编辑车辆
- catalog 选择/搜索
- custom vehicle fallback
- 归档
- Bluetooth vehicle prompt 配置
- Backup / CSV 等工具入口

当前车辆切换不改变已开始 Trip 的 `vehicleId`。

---

## 10. 当前验收边界

代码侧 Trip v0.6 已实现；以下仍必须真机完成:

- Trip home 5+ rows 信息密度
- READY slide 手感/误触
- active route / live telemetry update
- completion + IME
- completed `概览 / 轨迹 / 数据`
- LONG_GAP / endpoint semantics
- 320-360dp
- fontScale 1.3
- Dark/Light readability
- #77 background/stationary callback reliability

不得用 Android CI Green 直接关闭这些 physical acceptance owners。

---

## 11. 变更记录

### v1.3.0

- 对齐当前五一级导航与 Dark First v0.5/v0.6 页面层级
- 同步 Trip home / READY / active / completion / completed detail 三 section 信息架构
- 同步 slide-to-end 直接进入唯一 completion form 的当前交互
- 同步真实 route / LONG_GAP / endpoint / diagnostics 语义
- 明确 MapLibre 仍为可选 renderer，不是当前 Trip 完整性前置条件
- 明确 Trip v0.6 代码完成与真机验收的边界

### v1.2.0

- 增加当前车辆切换器与多车辆列表设计
- 增加车型选择 / 自定义兜底流程
- 增加开始行程、记录中、行程详情 UX
- 明确地图加载失败不影响原始行程数据
- 明确持续定位和权限透明原则

### v1.1.0

- 建立 v0.1 信息架构、表单、空状态和基础无障碍规则
