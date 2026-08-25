# EV Charge Book Roadmap

版本: v1.2.0

更新时间: 2026-08-25

## 0. 路线原则

路线以 PROJECT_MASTER.md、PRODUCT.md、FEATURE_MATRIX.md 为准。

每个版本必须有:

- 明确范围
- 明确非目标
- 可执行验收标准
- 对应文档版本更新

发布逻辑遵循 Assembles-J 组织统一 production release 约定。

---

## v0.1 - Local Charging Book

目标: 做出真正可长期使用的本地充电记账 App，并具备可重复构建和正式 APK 发布能力。

### 功能范围

- [ ] Android 工程可稳定构建
- [ ] 车辆创建 / 编辑
- [ ] 充电记录新增 / 编辑 / 删除
- [ ] 历史记录列表与详情
- [ ] Room 本地数据库
- [ ] Dashboard 基础统计
- [ ] 空状态 / 错误状态

### 工程与发布范围

- [ ] Gradle Wrapper 完整
- [ ] PR / main Android CI
- [ ] Debug APK Artifact
- [ ] Release signing 配置
- [x] Production Release workflow 基线
- [x] 服务器原子 APK 部署脚本
- [ ] production secrets 配置验证
- [ ] 第一次 signed Release APK 发布验收
- [ ] Android build baseline 完成后启用 main 自动 production release

### 核心统计

- 本月费用
- 本月充电量
- 平均电价
- 充电次数
- 累计费用
- 累计充电量

### 非目标

- 登录
- 云同步
- AI
- OBD
- 复杂图表

### 验收标准

用户安装正式签名 APK 后，无网络情况下可以创建车辆、记录一次充电、编辑/删除记录，并在首页与历史页看到正确统计结果。

同时满足：

- CI 可重复生成 Debug APK
- production workflow 可生成签名 Release APK
- Release APK 通过 `apksigner verify`
- 服务器保留不可变版本 APK
- `latest/ev-charge-book-latest.apk` 仅在发布成功后切换

---

## v0.2 - Analytics

目标: 让记账数据产生可理解的趋势价值。

### 功能范围

- [ ] 月度费用趋势
- [ ] 月度充电量趋势
- [ ] 快充 / 慢充占比
- [ ] 月度对比
- [ ] 简单行驶里程录入
- [ ] 基础百公里成本 / 电耗分析

### 可选增强

- [ ] CSV 导出
- [ ] 充电记录筛选
- [ ] 更完整车辆统计

---

## v0.3 - Cloud Sync

目标: 支持账号、备份和多设备同步。

### 功能范围

- [ ] Spring Boot 单体服务
- [ ] PostgreSQL
- [ ] 用户账号
- [ ] 车辆同步
- [ ] 充电记录同步
- [ ] 冲突处理
- [ ] Docker Compose 部署
- [ ] 服务端 CI/CD
- [ ] 发布 workflow 引入 backend / android scope detection

### 非目标

- 微服务
- 重型消息队列
- 复杂实时系统

---

## v0.4 - Smart Input

目标: 降低人工录入成本。

### 功能范围

- [ ] OCR 识别充电订单 / 小票
- [ ] 识别结果人工确认
- [ ] 常用充电站 / 电价快速复用
- [ ] 智能补全

---

## v1.0 - AI EV Assistant

目标: 从“记录工具”升级为“个人新能源用车数据助手”。

### 功能范围

- [ ] AI 月度用车总结
- [ ] 充电成本优化建议
- [ ] 充电习惯分析
- [ ] 电池使用建议
- [ ] 异常数据解释

AI 输出必须基于用户真实记录，并明确区分事实、推算和建议。

---

## Long Term

仅在有真实数据价值时探索:

- OBD 数据接入
- IoT / 家充设备数据
- 车辆 API 接入
- 电池健康长期模型
- Web Dashboard

---

## 当前执行顺序

```text
Authority Docs v1.2
   ↓
Android Gradle / Build Baseline
   ↓
Debug CI Green
   ↓
Room CRUD
   ↓
Dashboard / History
   ↓
Signed Release Build
   ↓
Production Server Atomic Publish
   ↓
v0.1 Acceptance
   ↓
Enable main automatic release
```

---

## 变更记录

### v1.2.0

- 将组织统一 signed APK production release 纳入 v0.1
- 增加服务器原子部署和 latest 指针验收
- 明确自动 production release 必须在 Android build baseline 完成后启用
- v0.3 后端阶段预留 Third-Hand 同类 scope detection

### v1.1.0

- 版本路线改为 v0.1 / v0.2 / v0.3 / v0.4 / v1.0
- 明确每阶段非目标与验收标准
- 将 GitHub Actions APK 构建纳入 v0.1 必须项
- 将 OCR 从数据分析阶段拆为 Smart Input 独立阶段
