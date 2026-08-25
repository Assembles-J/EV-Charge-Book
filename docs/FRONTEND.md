# EV Charge Book 前端设计

版本: v1.0.0

## 技术方案

Android Native

- Kotlin
- Jetpack Compose
- MVVM
- Room

## 页面规划

### Dashboard

展示:

- 当前车辆
- 本月费用
- 行驶里程
- 百公里成本

### Charging Record

功能:

- 新增充电记录
- 修改记录
- 查看历史

### Vehicle

功能:

- 添加车辆
- 修改车辆参数

## 数据流

UI
 -> ViewModel
 -> Repository
 -> Room Database

## 原则

避免复杂状态管理，保持清晰数据流。
