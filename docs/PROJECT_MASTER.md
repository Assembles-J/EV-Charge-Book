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

## 2. 权威文档体系

本文件为最高级设计文档。

权威子文档:

1. PRODUCT.md - 产品边界、用户价值、核心功能
2. FEATURE_MATRIX.md - 功能版本归属与优先级
3. UIUX.md - 页面、交互、状态与视觉原则
4. FRONTEND.md - Android 前端架构与实现边界
5. BACKEND.md - 后端职责、接口演进与同步原则
6. DATABASE.md - 核心实体、字段、关系与统计口径
7. CI_CD.md - 构建、APK、Release、部署与自动化规则
8. ROADMAP.md - 版本路线、阶段目标与验收标准
9. DEVELOPMENT.md - 日常开发和提交规范

实现与文档冲突时，以文档为准；若实现确需改变设计，必须同步升级相关权威文档版本。

---

## 3. 文档版本规则

- Patch: 表述修正，不改变行为
- Minor: 新增功能、字段、流程或实现约束
- Major: 产品定位、架构或核心数据模型发生不兼容变化

功能变更按影响范围检查:

1. PRODUCT
2. FEATURE_MATRIX
3. UIUX
4. FRONTEND
5. BACKEND
6. DATABASE
7. CI_CD
8. ROADMAP

不相关文档无需机械修改。

---

## 4. 产品原则

1. 简单可维护
2. 优先真实使用场景
3. Local First
4. 避免过度设计
5. 数据长期价值优先
6. 用户录入成本要低
7. 统计口径必须可解释
8. 后端和 AI 不得阻塞基础记账
9. 不向用户展示没有真实数据来源的“实时车辆数据”或伪造分析

---

## 5. 当前产品阶段

当前阶段: MVP v0.1 Local Charging Book

核心闭环已经进入真实数据实现:

```text
车辆
 -> 新增充电记录
 -> Room 本地保存
 -> 历史记录
 -> Dashboard / Stats 真实聚合
```

当前已落地:

- Room Vehicle / ChargingRecord
- DAO / AppDatabase / Repository
- MainViewModel + StateFlow
- 充电记录新增
- 充电记录删除
- 历史记录真实列表
- Dashboard 真实本月费用 / 电量 / 平均电价 / 次数
- Stats 累计费用 / 累计电量
- 空状态与基础错误提示

v0.1 仍必须完成:

- 车辆编辑持久化
- 充电记录编辑
- 日期时间编辑
- 快充 / 慢充录入
- Android CI Green + Debug APK
- 首次 production signed APK 发布验收

明确不在 v0.1 强制范围:

- 登录注册
- 云同步
- AI 分析
- OBD / IoT
- 复杂电池健康预测
- 复杂趋势图

---

## 6. Android 架构基线

当前实际数据流:

```text
Compose UI
 -> MainViewModel
 -> ChargingRepository
 -> Room DAO
 -> SQLite
 -> Flow / StateFlow
 -> Compose UI
```

v0.1 不引入复杂 DI 或微型 Clean Architecture 仪式化层次。

正式统计必须来自同一 Repository/State 数据源，避免 Dashboard、Records、Stats 口径不一致。

---

## 7. 工程结构原则

```text
EV-Charge-Book/
├── docs/
├── android/
├── server/            后续阶段
├── scripts/
└── .github/workflows/
```

当前 Android build baseline 已包含 root/app Gradle 和 Manifest。

CI 固定使用 Gradle 9.5.0 进行远程构建，因此 Gradle Wrapper 不再作为第一次 CI 的硬门禁；后续仍建议由本地 Android Studio 生成并提交用于本地一致性。

---

## 8. 组织统一发布基线

正式发布继续遵循 Assembles-J / Third-Hand 同类骨架:

- CI 与 Production Release 分离
- `production` GitHub Environment
- signed Android APK
- VERSION_CODE 使用 GitHub run number
- Actions Artifact
- 组织统一 signing / SSH Secret 命名
- `/opt/ev-charge-book/releases`
- `.part` 上传
- SHA / apksigner 校验
- 原子激活
- latest 仅在完整成功后更新
- 已发布版本不可覆盖

Production Release 当前仍保持 `workflow_dispatch` 手动门禁。

---

## 9. CI/CD 门禁

当前 CI 必须真实执行:

```text
JDK 17
 -> Android SDK 37
 -> Gradle 9.5.0
 -> testDebugUnitTest
 -> assembleDebug
 -> Debug APK Artifact
```

在以下条件完成前，不开启 main 自动 Production Release:

- Debug CI Green
- Debug APK Artifact 验收
- assembleRelease 成功
- production signing Secrets 已配置并验证
- `/opt/ev-charge-book` 目录权限通过
- 第一次 signed APK 原子发布成功

---

## 10. 当前业务推进顺序

```text
Room CRUD baseline ✅
 -> Real Dashboard / Records / Stats ✅
 -> Debug CI Green
 -> Vehicle edit
 -> Charging record edit
 -> Date/time + charger type
 -> Signed Release
 -> v0.1 Acceptance
```

---

## 11. 决策记录

### v1.3.0

- 接收本地 Android UI Skeleton 更新并继续业务实现
- Room / Repository / ViewModel 数据闭环落地
- Dashboard / Records / Stats 切换到真实数据源
- 禁止伪造实时 SOC、续航和分析数据
- Android CI 改为固定 Gradle 9.5.0 真实构建
- Gradle Wrapper 从硬门禁调整为推荐项

### v1.2.0

- 纳入 Assembles-J 统一发布逻辑
- 确立 signed APK + production Environment + server atomic release

### v1.1.0

- 仓库迁移至 Assembles-J 组织
- 确立 Single Source of Truth 文档体系
