# EV Charge Book Product Specification

Version: v1.2.0
更新时间: 2026-08-26

## Vision

打造新能源车主自己的车辆数据中心：从充电记账开始，逐步沉淀车辆、充电、行程、位置与能耗数据，并形成可信的长期分析。

## Core Principles

- Local First
- Simple / Reliable / Maintainable
- 数据必须有真实来源
- 不为了“全功能”阻塞第一版可用性
- 供应商能力可替换，避免地图/车型 API 锁定

## Core Modules

### 1. Vehicle Management

- 创建 / 编辑车辆
- 多车辆管理与当前车辆切换
- 车型目录搜索与选择
- 找不到车型时自定义添加
- 电池容量 / 标称续航
- 车辆归档，不轻易删除历史数据

### 2. Charging Management

- 新增 / 编辑 / 删除充电记录
- SOC、充入电量、费用、类型、时间、地点、备注
- 可使用当前位置辅助填写充电地点
- 按车辆隔离记录

### 3. Trip & Location

- 用户主动开始 / 结束行程
- 持续记录位置、GPS 海拔、速度、方向、精度与时间
- 自动形成距离、耗时、平均/最高速度等汇总
- 地图查看行驶轨迹
- 每次行程绑定具体车辆

### 4. Statistics

- 月度 / 累计充电费用
- 充电量、平均电价、次数
- 后续结合行程形成百公里成本、行驶效率、路线与海拔关联分析

### 5. Battery / Usage Insights

- 快慢充比例
- SOC 习惯
- 行程与充电使用画像
- 无真实 BMS/OBD 数据时不得声称准确 SOH

### 6. AI Assistant (Future)

- 用车总结
- 成本优化
- 充电建议
- 异常数据解释

## Version Boundary

### v0.1

只收口 Local Charging Book：车辆、充电 CRUD、真实统计、CI/APK。

### v0.2

进入 Vehicle & Trip Foundation：多车辆、基础车型目录、当前位置、手动开始/结束行程、轨迹和地图展示。

地图/定位/车型库详细设计分别以 `LOCATION_TRIP.md` 和 `VEHICLE_CATALOG_MULTI_VEHICLE.md` 为准。

## Change Log

### v1.2.0

- 增加定位、地图和驾驶行程模块
- 增加车型目录和多车辆产品能力
- 明确 v0.1 不扩 scope，新增需求进入 v0.2
- 强化真实数据来源与供应商可替换原则
