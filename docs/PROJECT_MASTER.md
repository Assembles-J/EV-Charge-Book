# EV Charge Book 项目总纲（PROJECT MASTER）

版本: v1.1.0

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

## 7. CI/CD 基线

所有主干代码必须具备可重复构建能力。

最低要求:

- PR / main push 执行 Android 构建
- 自动执行基础检查
- 自动生成 Debug APK Artifact
- 后续 Release Tag 自动生成 Release APK
- 部署脚本与 Action 逻辑保持一致，避免两套不可维护流程

---

## 8. 决策记录

### v1.1.0

- 仓库迁移至 Assembles-J 组织
- 确立 Single Source of Truth 文档体系
- 明确文档更新序号与版本规则
- 明确 v0.1 产品闭环和非目标
- 将 CI/CD 纳入项目权威设计基线
