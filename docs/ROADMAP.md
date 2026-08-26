# EV Charge Book Roadmap

版本: v1.4.0
更新时间: 2026-08-26

## 0. 路线原则

以 PROJECT_MASTER / PRODUCT / FEATURE_MATRIX 为准。发布逻辑遵循 Assembles-J 组织 production release 约定。

新增地图、行程、车型库、多车辆需求**不进入 v0.1**，避免 MVP 无法收口。

---

## v0.1 - Local Charging Book

目标: 完成真正可安装使用的本地充电记账闭环。

### 已完成

- [x] Room / DAO / Repository / ViewModel
- [x] 车辆创建 / 编辑持久化
- [x] 充电记录新增 / 删除
- [x] 历史真实列表
- [x] Dashboard / Stats 真实基础统计
- [x] Gradle Wrapper / Android build scripts
- [x] CI / Release workflow 基线
- [x] 服务器原子 APK 部署脚本

### 剩余

- [ ] 充电记录编辑接入 Repository/ViewModel
- [ ] 日期 / 时间选择
- [ ] charger type / remark
- [ ] 删除确认 / 保存成功反馈
- [ ] Android CI Green
- [ ] Debug APK Artifact 验收
- [ ] assembleRelease Green
- [ ] production signing secrets 验证
- [ ] 首次 signed APK production publish

### 当前 CI 问题

2026-08-26 最新已定位旧 workflow 安装 `platforms;android-37` 与项目 compileSdk 36 不一致。CI / Release 已更新为 SDK 36 + 已提交 Gradle Wrapper，等待新的 Actions 结果验收。

---

## v0.2 - Vehicle & Trip Foundation

目标: 从“充电记账”扩展成“车辆 + 充电 + 驾驶行程”的个人车辆数据中心。

### 多车辆

- [ ] 多车辆创建
- [ ] 当前/默认车辆
- [ ] Dashboard / Records / Stats 按车辆隔离
- [ ] 车辆归档

### 车型目录

- [ ] 本地车型目录 seed
- [ ] 品牌 / 车系 / 年款 / 配置搜索
- [ ] 选择车型后参数确认/覆盖
- [ ] 自定义车型兜底

### 定位 / 地图

- [ ] 获取当前位置
- [ ] 充电记录绑定当前位置
- [ ] Android Location Provider
- [ ] MapLibre 轨迹地图
- [ ] 地图 provider 可替换

### 行程记录

- [ ] 用户手动开始 / 结束
- [ ] location foreground service
- [ ] 记录经纬度 / GPS 海拔 / 速度 / 精度 / 时间
- [ ] 距离 / 耗时 / 平均速度 / 最高速度
- [ ] Trip history / detail
- [ ] 每次 Trip 绑定 vehicleId

### 非目标

- 无提示后台持续追踪
- 自动驾驶识别后强制启动记录
- 导航 / 路径规划
- 高精度测绘级海拔

---

## v0.3 - Analytics

- [ ] 月度费用 / 电量趋势
- [ ] 快慢充比例
- [ ] 月度对比
- [ ] 百公里充电成本
- [ ] 行程与充电关联分析
- [ ] 速度 / 海拔 / 能耗关系（仅在数据可靠时）

---

## v0.4 - Cloud & Catalog Sync

- [ ] Spring Boot 单体服务
- [ ] PostgreSQL
- [ ] 用户账号
- [ ] 车辆 / 充电 / 行程同步
- [ ] 车型目录更新
- [ ] Docker Compose / CI-CD

---

## v0.5 - Smart Input

- [ ] OCR 充电订单/小票
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

AI 必须区分事实、推算和建议。

---

## 当前执行顺序

```text
v0.1 Charging Edit
  -> CI Green + Debug APK
  -> Signed Production APK
  -> v0.1 Acceptance
  -> v0.2 Multi Vehicle / Catalog
  -> Location + Manual Trip Tracking
  -> Map Route Display
```

---

## 变更记录

### v1.4.0

- 对账车辆编辑已经完成
- 增加 v0.2 Vehicle & Trip Foundation
- 地图、定位、行程、车型库、多车辆正式进入路线
- 原 v0.2 Analytics 后移到 v0.3，Cloud -> v0.4，Smart Input -> v0.5
- 记录 Android CI SDK 37/36 配置不一致及修复

### v1.3.0

- Room / Repository / ViewModel / 真实统计落地
