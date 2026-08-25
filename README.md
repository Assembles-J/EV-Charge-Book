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

## MVP 功能

### 车辆管理

- [ ] 添加车辆品牌/车型
- [ ] 电池容量记录
- [ ] 续航信息记录

### 充电记录

- [ ] 记录充电时间
- [ ] 记录 SOC 变化
- [ ] 记录充电电量
- [ ] 记录充电费用
- [ ] 自动计算电价

### 数据分析

- [ ] 月度充电费用
- [ ] 百公里电费
- [ ] 平均电耗趋势
- [ ] 快充/慢充比例

## 技术路线

### Android

- Kotlin
- Jetpack Compose
- Room Database
- Retrofit
- MVVM

### Backend

- Spring Boot
- PostgreSQL
- Redis
- Docker

## Repository Structure

```text
EV-Charge-Book
├── android
├── server
├── docs
│   ├── PRODUCT.md
│   ├── DATABASE.md
│   └── ROADMAP.md
└── README.md
```

## Roadmap

### Phase 1 - MVP

完成基础充电记录和成本统计。

### Phase 2 - 数据分析

增加趋势图表和车辆画像。

### Phase 3 - Intelligent EV Assistant

增加 OCR、AI 分析、电池健康预测。

## License

MIT
