# EV Charge Book Roadmap

版本: v1.8.0
更新时间: 2026-08-26

## 0. 路线原则

以 PROJECT_MASTER / PRODUCT / FEATURE_MATRIX 为准。发布逻辑遵循 Assembles-J 组织 production release 约定。

新增地图、行程、车型库、多车辆需求不进入 v0.1，避免 MVP 无法收口。

---

## v0.1 - Local Charging Book

状态: Released / Accepted

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
- [x] Android CI Green
- [x] Debug APK Artifact
- [x] 真机安装 / 启动
- [x] 新增 / 编辑 / 删除充电记录真机走查
- [x] 车辆编辑真机走查
- [x] Dashboard / Records / Stats 数据一致性走查
- [x] CI / Release workflow 基线
- [x] 服务器原子 APK 部署脚本
- [x] Release metadata 绑定真实 checkout SHA
- [x] latest 原子激活顺序修正
- [x] production signing secrets 验证
- [x] assembleRelease Green
- [x] apksigner verify
- [x] Signed Production APK Artifact
- [x] 服务器目录准备 / 上传 / 原子激活
- [x] v0.1 Release Accepted

### 首次正式发布

- Android Release Run #1: Green
- Run ID: `32939069000`
- Commit: `90d31072b6fecfecce7588968b6ddf1cc3c4ee08`
- Artifact: `ev-charge-book-0.1.1`
- Artifact digest: `sha256:c74a283ff4775ad62f7b74ff0d2c21cbb2d760c3e2a3ab8e9747199cbf84259a`

Production release 暂时保持手动触发；CI 继续自动运行。等 v0.2 稳定后再评估是否自动 production release。

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
v0.1 Release Accepted
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

### v1.8.0

- Android Release Run #1 Green
- Production signing / assembleRelease / apksigner / Actions Artifact 全部通过
- 服务器目录准备、上传与原子激活成功
- v0.1 正式标记 Released / Accepted
- Issue #7 release pipeline acceptance 关闭
- 当前主线切换到 v0.2 P0 `odometerKm`

### v1.7.0

- Android Build Run #45 Green
- Debug APK Artifact 验收完成
- 真机核心 CRUD / 启动 / 数据一致性验收通过，无阻塞 Bug
- v0.1 当前唯一主线切换为 Production Release
- 修正 release metadata SHA 来源，绑定实际 checkout commit
- 修正服务器发布顺序，latest 只在 APK 与 metadata 准备完成后激活
