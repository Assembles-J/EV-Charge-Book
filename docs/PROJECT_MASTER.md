# EV Charge Book 项目总纲（PROJECT MASTER）

版本: v1.2.0

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

## 2. 权威文档体系

本文件为最高级设计文档。

以下文档共同构成项目权威设计基线:

1. PRODUCT.md - 产品边界、用户价值、核心功能
2. FEATURE_MATRIX.md - 功能版本归属与优先级
3. UIUX.md - 页面、交互、状态与视觉原则
4. FRONTEND.md - Android 前端架构与实现边界
5. BACKEND.md - 后端职责、接口演进与同步原则
6. DATABASE.md - 核心实体、字段、关系与统计口径
7. CI_CD.md - 构建、APK、Release、部署与自动化规则
8. ROADMAP.md - 版本路线、阶段目标与验收标准
9. DEVELOPMENT.md - 日常开发和提交规范

任何实现若与文档冲突，以文档为准；若实现确有必要改变设计，必须先更新对应文档版本。

---

## 3. 文档版本与变更规则

所有权威文档必须包含版本号。

版本规则:

- Patch: 表述修正，不改变行为
- Minor: 新增功能、字段、流程或实现约束
- Major: 产品定位、架构或核心数据模型发生不兼容变化

任何功能变更必须检查并按需更新以下序号链路:

1. PRODUCT
2. FEATURE_MATRIX
3. UIUX
4. FRONTEND
5. BACKEND
6. DATABASE
7. CI_CD
8. ROADMAP

不相关文档无需机械修改，但必须确认过影响范围。

---

## 4. 产品原则

1. 简单可维护
2. 优先真实使用场景
3. Local First
4. 避免过度设计
5. 数据长期价值优先
6. 用户录入成本要低
7. 统计口径必须可解释
8. 后端和 AI 能力不得阻塞基础记账功能

---

## 5. 当前版本目标

当前产品阶段: MVP v0.1

核心闭环:

车辆 -> 新增充电记录 -> 本地保存 -> 历史查看 -> 成本统计 -> Dashboard 展示

v0.1 必须做到:

- App 可独立离线使用
- 至少支持一个车辆档案
- 可以新增、修改、删除充电记录
- 可以查看历史记录
- 可以查看累计充电量、累计费用、平均电价等基础指标
- GitHub Actions 自动构建 Debug APK
- 具备签名 Release APK 的 production 发布链路

明确不在 v0.1 强制范围:

- 登录注册
- 云同步
- AI 分析
- OBD / IoT
- 复杂电池健康预测

---

## 6. 工程结构原则

仓库长期结构:

```text
EV-Charge-Book/
├── docs/             权威设计文档
├── android/          Android 客户端
├── server/           后端（后续阶段启用）
├── scripts/          构建、发布、部署脚本
└── .github/workflows GitHub Actions
```

不为未来假设提前拆微服务，不创建无实际用途的抽象层。

---

## 7. 组织统一发布基线

EV Charge Book 的正式发布遵循 Assembles-J 组织现有项目发布逻辑，并以 Third-Hand production deploy 为主要参考。

必须保持:

- CI 与 Production Release 分离
- `production` GitHub Environment
- Android APK 必须签名
- VERSION_CODE 使用 GitHub run number
- 正式 APK 同时保留 Actions Artifact
- SSH/SCP 使用组织统一 Secret 命名
- 服务器发布目录为 `/opt/ev-charge-book/releases`
- 上传先使用 `.part` 临时文件
- 校验成功后原子激活
- latest 下载入口只在完整发布成功后切换
- 正式版本文件发布后不可覆盖

当前 Android Skeleton 尚未具备 Gradle Wrapper，因此 production release 暂时只允许 `workflow_dispatch` 手动触发。完成 Android build baseline 后，才允许打开 main Android 变更自动发布。

后续若启用服务端，则沿用 Third-Hand 的 scope detection 思路：仅部署发生变化的后端，Android 变更时才发布新 APK。

---

## 8. CI/CD 最低要求

### CI

- PR / main Android 变更执行基础构建检查
- Unit Test
- assembleDebug
- Debug APK Artifact

### Production Release

- 完整 release signing 配置
- assembleRelease
- apksigner verify
- SHA-256
- Actions Artifact
- SCP 到 production server
- `/opt/ev-charge-book/releases` 原子发布
- latest 指针更新
- release metadata 更新

发布脚本与 Action 必须复用同一套目录和原子激活规则，避免形成两套部署逻辑。

---

## 9. 当前发布门禁

在以下项目完成前，不开启 main push 自动 Production Release:

- `android/gradlew`
- `android/gradle/wrapper/**`
- `android/app/build.gradle.kts`
- `assembleDebug` CI 成功
- `assembleRelease` CI 成功
- production Android signing Secrets 已配置
- `/opt/ev-charge-book` 部署目录可写

满足后，由 CI_CD.md 版本更新解除门禁。

---

## 10. 决策记录

### v1.2.0

- 正式纳入 Assembles-J 组织统一发布逻辑
- 对齐 Third-Hand production deploy 骨架
- 确立 signed APK + production Environment + server atomic release
- 规定 release 自动化启用前的构建与密钥门禁

### v1.1.0

- 仓库迁移至 Assembles-J 组织
- 确立 Single Source of Truth 文档体系
- 明确文档更新序号与版本规则
- 明确 v0.1 产品闭环和非目标
- 将 CI/CD 纳入项目权威设计基线
