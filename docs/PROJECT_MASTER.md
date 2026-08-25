# EV Charge Book 项目总纲（PROJECT MASTER）

版本: v1.3.0

更新时间: 2026-08-25

状态: Authority Document / Single Source of Truth

## 1. 项目定位

EV Charge Book 是面向新能源车主的充电记账、成本分析、车辆数据沉淀与后续智能分析 App。

当前优先级:

1. 真实可用的充电记账
2. 低成本、低复杂度的数据统计
3. Local First
4. 可持续演进到云同步与 AI 分析

首个验证车型为零跑 C16，但数据模型与产品设计不得绑定单一品牌。

---

## 2. 产品原则

- 简单可维护
- 优先真实使用场景
- Local First
- 避免过度设计
- 数据长期价值优先
- 用户录入成本要低
- 统计口径必须可解释
- 后端和 AI 不得阻塞基础记账功能

---

## 3. 当前版本目标

当前阶段: MVP v0.1

核心闭环:

```text
车辆 -> 新增充电记录 -> 本地保存 -> 历史查看 -> 成本统计 -> Dashboard
```

v0.1 必须做到:

- App 可独立离线使用
- 至少支持一个车辆档案
- 新增、修改、删除充电记录
- 查看历史记录
- 查看累计充电量、累计费用、平均电价等基础指标
- GitHub Actions 自动构建 Debug APK

v0.1 不强制:

- 登录注册
- 云同步
- 后端服务
- AI 分析
- OBD / IoT
- 复杂电池健康预测
- 生产服务器 APK 分发

---

## 4. 当前执行状态

当前开发任务：Issue #4，第一个可运行 Android Dashboard。

当前实现目标:

- 完整 Android Application Gradle 配置
- Compose Material3
- 单 Activity + 最小 Navigation shell
- Dashboard 首屏
- 零跑 C16 作为首个示例车辆
- PR / main 自动生成 Debug APK Artifact

完成后按顺序推进：

```text
#4 Runnable Android
-> #3 Room database
-> ChargingRecord CRUD
-> Dashboard real statistics
-> Release readiness
```

---

## 5. 工程结构

```text
EV-Charge-Book/
├── docs/
├── android/
├── server/           后续真正需要云能力时再启用
├── scripts/          有重复操作需要脚本化时再增加
└── .github/workflows/
```

不为未来假设提前拆微服务，不提前建立 Redis、Docker、服务器发布链路。

---

## 6. 开发流程

项目采用最小治理：

```text
Issue -> feature/<issue>-name -> implementation -> PR -> CI -> squash merge -> main
```

详细规则见 `DEVELOPMENT.md`。

---

## 7. CI/CD 基线

当前只要求：

- PR / main 构建 Android
- `assembleDebug`
- Debug APK Artifact
- 公共构建步骤优先复用 `Assembles-J/.github`

当前没有 Gradle Wrapper，因此 bootstrap 阶段由组织 reusable workflow 安装固定 Gradle 版本。工程稳定后生成 wrapper，回归 wrapper-first 构建。

生产签名、GitHub Release、服务器分发等在产品闭环稳定后单独设计，不从 Third-Hand 提前复制。

---

## 8. Secrets 原则

当前 v0.1 Debug 构建不需要 Secret。

未来若出现真实服务器部署需求：

- 共享 SSH 使用组织 Secret：`COMMON_SERVER_HOST`、`COMMON_SERVER_USER`、`COMMON_SSH_PRIVATE_KEY`
- App 签名、第三方 API key 等项目专属凭据留在本仓库 Environment / Repository Secrets

---

## 9. 权威文档

- `PRODUCT.md` - 产品边界
- `FEATURE_MATRIX.md` - 功能版本
- `UIUX.md` - 页面与交互
- `FRONTEND.md` - Android 架构
- `BACKEND.md` - 后端边界
- `DATABASE.md` - 数据模型和统计口径
- `CI_CD.md` - 构建和发布
- `ROADMAP.md` - 路线和验收
- `DEVELOPMENT.md` - 开发流程

只在契约实际变化时更新相关文档，禁止为了流程机械修改所有文档。

---

## 10. 决策记录

### v1.3.0

- 把 v0.1 收敛为 Local First Android 产品闭环
- 删除过早的 production server / atomic APK release 强制要求
- 确立 Issue -> Branch -> PR -> CI -> Merge 最小开发路线
- 接入 Assembles-J 组织 reusable Android CI
- 当前开发正式进入 Issue #4 runnable Dashboard

### v1.2.0

- 仓库迁移至 Assembles-J
- 建立权威文档体系和 CI/CD 基线
