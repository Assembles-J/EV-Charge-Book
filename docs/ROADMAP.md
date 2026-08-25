# EV Charge Book Roadmap

版本: v1.3.0

更新时间: 2026-08-25

## 0. 路线原则

路线以 PROJECT_MASTER.md、PRODUCT.md、FEATURE_MATRIX.md 为准。

发布逻辑遵循 Assembles-J 组织统一 production release 约定。

---

## v0.1 - Local Charging Book

目标: 做出真正可长期使用的本地充电记账 App，并具备可重复构建和正式 APK 发布能力。

### 功能范围

- [ ] Android 工程 CI 稳定 Green
- [ ] 车辆创建 / 编辑
- [ ] 充电记录编辑
- [x] 充电记录新增 / 删除
- [x] 历史记录真实数据列表
- [x] Room 本地数据库
- [x] Dashboard 基础真实统计
- [x] Stats 基础真实统计
- [x] 空状态 / 基础错误状态
- [ ] 记录日期时间编辑
- [ ] 快充 / 慢充类型录入

### 工程与发布范围

- [x] root/app Gradle 构建脚本
- [x] AndroidManifest
- [x] PR / main Android CI workflow
- [x] 固定 Gradle 9.5 CI 构建策略
- [ ] Debug CI Green 验收
- [ ] Debug APK Artifact 验收
- [x] Release signing 环境变量接口
- [x] Production Release workflow 基线
- [x] 服务器原子 APK 部署脚本
- [ ] production secrets 配置验证
- [ ] 第一次 signed Release APK 发布验收
- [ ] Android build baseline 完成后启用 main 自动 production release

### 已完成业务数据流

```text
Add Record
  -> UI validation
  -> MainViewModel
  -> ChargingRepository
  -> Room
  -> Flow
  -> Dashboard / Records / Stats
```

### 核心统计

- [x] 本月费用
- [x] 本月充电量
- [x] 平均电价
- [x] 充电次数
- [x] 累计费用
- [x] 累计充电量

### 非目标

- 登录
- 云同步
- AI
- OBD
- 复杂图表

### v0.1 剩余业务顺序

1. CI 编译问题清零
2. 车辆编辑真正持久化
3. 充电记录编辑
4. 日期时间选择
5. 充电类型
6. 删除确认 / 表单保存反馈
7. Signed APK 首次发布验收

### 验收标准

用户安装正式签名 APK 后，无网络情况下可以创建/编辑车辆，新增/编辑/删除充电记录，并在首页、历史页、统计页看到一致的真实统计结果。

同时满足：

- CI 可重复生成 Debug APK
- production workflow 可生成签名 Release APK
- Release APK 通过 `apksigner verify`
- 服务器保留不可变版本 APK
- `latest/ev-charge-book-latest.apk` 仅在发布成功后切换

---

## v0.2 - Analytics

目标: 让记账数据产生可理解的趋势价值。

- [ ] 月度费用趋势
- [ ] 月度充电量趋势
- [ ] 快充 / 慢充占比
- [ ] 月度对比
- [ ] 简单行驶里程录入
- [ ] 基础百公里成本 / 电耗分析
- [ ] CSV 导出（可选）
- [ ] 充电记录筛选（可选）

---

## v0.3 - Cloud Sync

目标: 支持账号、备份和多设备同步。

- [ ] Spring Boot 单体服务
- [ ] PostgreSQL
- [ ] 用户账号
- [ ] 车辆同步
- [ ] 充电记录同步
- [ ] 冲突处理
- [ ] Docker Compose 部署
- [ ] 服务端 CI/CD
- [ ] 发布 workflow 引入 backend / android scope detection

非目标: 微服务、重型消息队列、复杂实时系统。

---

## v0.4 - Smart Input

- [ ] OCR 识别充电订单 / 小票
- [ ] 识别结果人工确认
- [ ] 常用充电站 / 电价快速复用
- [ ] 智能补全

---

## v1.0 - AI EV Assistant

- [ ] AI 月度用车总结
- [ ] 充电成本优化建议
- [ ] 充电习惯分析
- [ ] 电池使用建议
- [ ] 异常数据解释

AI 输出必须基于用户真实记录，并明确区分事实、推算和建议。

---

## Long Term

仅在有真实数据价值时探索 OBD、IoT/家充设备、车辆 API、电池健康长期模型和 Web Dashboard。

---

## 当前执行顺序

```text
Room CRUD baseline ✅
   ↓
Real Dashboard / Records / Stats ✅
   ↓
Debug CI Green
   ↓
Vehicle edit + Record edit
   ↓
Date/time + charger type
   ↓
Signed Release Build
   ↓
Production Atomic Publish
   ↓
v0.1 Acceptance
```

---

## 变更记录

### v1.3.0

- 记录 2026-08-25 本地 UI 推送后的业务推进
- Room / Repository / ViewModel / 真实统计正式落地
- CI 改用固定 Gradle 9.5，不再因缺 Wrapper 静默跳过
- 明确 v0.1 剩余业务优先级

### v1.2.0

- 将组织统一 signed APK production release 纳入 v0.1
- 增加服务器原子部署和 latest 指针验收
