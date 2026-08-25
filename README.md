# EV Charge Book 🚗⚡

新能源车充电成本记录与电池健康分析 App。

## 项目定位

EV Charge Book 是一个面向新能源车主的充电管理工具，目标是记录真实用车成本，并通过数据分析帮助车主管理车辆。

核心方向：

- 充电记账
- 电耗分析
- 每公里成本计算
- 电池健康管理
- AI 用车分析

## MVP 功能进度

### 车辆管理
- [x] UI 设计：车辆信息卡片与电池健康度展示
- [ ] 功能实现：添加车辆品牌/车型
- [ ] 功能实现：电池容量记录
- [ ] 功能实现：续航信息记录

### 充电记录
- [x] UI 设计：时间线列表与流式添加表单
- [ ] 功能实现：记录充电时间
- [ ] 功能实现：记录 SOC 变化
- [ ] 功能实现：记录充电电量
- [ ] 功能实现：记录充电费用
- [ ] 功能实现：自动计算电价

### 数据分析
- [x] UI 设计：月度费用卡片与电耗趋势图表
- [ ] 功能实现：月度充电费用统计
- [ ] 功能实现：百公里电费计算
- [ ] 功能实现：平均电耗趋势分析
- [ ] 功能实现：快充/慢充比例统计

## 技术路线

### Android
- Kotlin
- Jetpack Compose (UI 框架)
- Room Database (待实现)
- Retrofit (待实现)
- MVVM (架构)

### Backend
- Spring Boot
- PostgreSQL
- Redis
- Docker

## Repository Structure

```text
EV-Charge-Book
├── android
│   └── app/src/main/java/com/evchargebook/ui  <-- 已完成核心 UI 实现
├── server
├── docs
│   ├── PRODUCT.md
│   ├── DATABASE.md
│   └── ROADMAP.md
└── README.md
```

## Roadmap

### Phase 1 - MVP
- [x] 核心 UI/UX 骨架搭建
- [ ] 本地数据库 (Room) 集成
- [ ] 基础充电记录功能联调

### Phase 2 - 数据分析
增加动态趋势图表和车辆画像。

### Phase 3 - Intelligent EV Assistant
增加 OCR、AI 分析、电池健康预测。

## License
MIT
