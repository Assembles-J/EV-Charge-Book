# EV Charge Book Roadmap

版本: v1.6.0
更新时间: 2026-08-26

## 0. 路线原则

以 PROJECT_MASTER / PRODUCT / FEATURE_MATRIX 为准。发布逻辑遵循 Assembles-J 组织 production release 约定。

新增地图、行程、车型库、多车辆需求不进入 v0.1，避免 MVP 无法收口。

---

## v0.1 - Local Charging Book

目标: 完成真正可安装使用的本地充电记账闭环。

### 已完成

- [x] Room / DAO / Repository / ViewModel
- [x] 车辆创建 / 编辑持久化
- [x] 充电记录新增 / 编辑 / 删除
- [x] 历史真实列表
- [x] 日期 / 时间选择
- [x] charger type / remark
- [x] 删除确认
- [x] 保存成功 / 错误反馈
- [x] 空状态
- [x] Dashboard / Stats 真实基础统计
- [x] 业务校验抽取到 ChargingRecordRules
- [x] 统计聚合抽取到 ChargingStatistics
- [x] 核心规则 / 统计单元测试
- [x] Material 3 主题与主要页面视觉重构
- [x] Gradle Wrapper / Android build scripts
- [x] CI / Release workflow 基线
- [x] 服务器原子 APK 部署脚本

### 当前验收阶段

P0 - Build / Installability

- [ ] Android CI Green
- [ ] Debug APK Artifact 验收
- [ ] 真机安装 / 启动
- [ ] 新增 / 编辑 / 删除充电记录真机走查
- [ ] 车辆编辑真机走查
- [ ] Dashboard / Records / Stats 数据一致性走查

P1 - Production Release

- [ ] assembleRelease Green
- [ ] production signing secrets 验证
- [ ] `/opt/ev-charge-book` 服务器目录权限验证
- [ ] 首次 signed APK production publish
- [ ] apksigner / SHA-256 / latest 原子切换验收

v0.1 不继续扩功能。CI / 真机验收发现的问题只做阻塞修复。

---

## v0.2 - Vehicle & Trip Foundation

目标: 从“充电记账”扩展成“车辆 + 充电 + 驾驶行程”的个人车辆数据中心。

### P0 数据闭环

- [ ] ChargingRecord 增加可选 `odometerKm`
- [ ] 里程输入与上一条记录做合理性提示
- [ ] 通过 vehicleId + 时间 + odometer 建立充电区间与行程分析基础
- [ ] 不建立错误的一充一行程硬外键

### P0.5 数据可恢复

- [ ] Local Backup / Restore
- [ ] 导出前后校验记录数和核心字段
- [ ] 恢复流程支持覆盖确认 / 防误操作

### P1 多车辆

- [ ] 多车辆创建
- [ ] 当前/默认车辆
- [ ] Dashboard / Records / Stats 按车辆隔离
- [ ] 车辆归档

### P1 车型目录

- [ ] 本地车型目录 seed
- [ ] 品牌 / 车系 / 年款 / 配置搜索
- [ ] 选择车型后参数确认/覆盖
- [ ] 自定义车型兜底

### P2 定位 / 地图

- [ ] 获取当前位置
- [ ] 充电记录绑定当前位置
- [ ] Android Location Provider
- [ ] MapLibre 轨迹地图
- [ ] 地图 provider 可替换

### P2 行程记录

- [ ] 用户手动开始 / 结束
- [ ] location foreground service
- [ ] 记录经纬度 / GPS 海拔 / 速度 / 精度 / 时间
- [ ] elapsed / moving / stopped time
- [ ] 距离 / 平均速度 / 最高速度
- [ ] Trip history / detail
- [ ] 每次 Trip 绑定 vehicleId
- [ ] INTERRUPTED 行程恢复
- [ ] 控制采样频率和数据库体积

### P2 数据可靠性

- [ ] 关键数据来源 DataSource 设计落地
- [ ] 规则型异常检测
- [ ] ChargingPlace 轻量地点复用

### 非目标

- 无提示后台持续追踪
- 导航 / 路径规划
- 高精度测绘级海拔
- 家庭账号 / 多驾驶员账号体系

---

## v0.3 - Analytics

- [ ] 月度费用 / 电量趋势
- [ ] 快慢充比例
- [ ] 月度对比
- [ ] 充电区间实际里程
- [ ] 百公里充电成本
- [ ] 估算百公里充入电量
- [ ] 行程与充电关联分析
- [ ] 家充 / 公司 / 公共充电分类统计
- [ ] 速度 / 海拔 / 能耗关系（仅在数据可靠时）

Analytics 必须区分原始事实、派生值和估算值。

---

## v0.4 - Cloud & Catalog Sync

- [ ] Spring Boot 单体服务
- [ ] PostgreSQL
- [ ] 用户账号
- [ ] 车辆 / 充电 / 行程同步
- [ ] 车型目录更新
- [ ] Docker Compose / CI-CD

云同步不得成为用户数据唯一恢复方式；Local Backup 保留。

---

## v0.5 - Smart Input

- [ ] OCR 充电订单/小票
- [ ] OCR 数据标注来源
- [ ] 识别确认
- [ ] 常用充电站 / 电价复用
- [ ] 智能补全

---

## v1.0 - AI EV Assistant

- [ ] AI 月度用车总结
- [ ] 成本优化
- [ ] 充电习惯
- [ ] 驾驶效率建议
- [ ] 异常数据解释

AI 必须区分事实、推算和建议，并利用 DataSource / accuracy 信息。

---

## 当前执行顺序

```text
v0.1 CI Green
  -> Debug APK Artifact
  -> Physical Device Acceptance
  -> assembleRelease Green
  -> Signed Production APK
  -> v0.1 Release Accepted
  -> v0.2 odometerKm
  -> Local Backup / Restore
  -> Multi Vehicle / Catalog
  -> Location + Manual Trip Tracking
  -> Trip Recovery / Stop Time
  -> Data Quality / ChargingPlace
  -> Map Route Display
  -> v0.3 Analytics
```

---

## 变更记录

### v1.6.0

- commit `7204c56` 完成充电记录完整编辑、日期时间、充电类型、删除确认与反馈
- v0.1 从“功能开发阶段”切换为“构建 / 真机 / 发布验收阶段”
- Android CI Green 与 Debug APK 提升为当前 P0
- v0.2 第一优先级固定为 odometerKm，其后为 Local Backup / Restore
- 地图 / 行程继续后置，避免阻塞数据闭环和可恢复性

### v1.5.0

- 将 odometerKm 提升为 v0.2 P0 数据闭环能力
- 增加充电与行程的非硬外键关联原则
- 增加 DataSource / 规则异常检测
- 增加 ChargingPlace
- 将 Local Backup / Restore 提前到云同步之前
- 增加 Trip 中断恢复、停车时间和采样体积控制

### v1.4.0

- 增加 v0.2 Vehicle & Trip Foundation
- 地图、定位、行程、车型库、多车辆正式进入路线
