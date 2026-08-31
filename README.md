# EV Charge Book 🚗⚡

EV Charge Book 是一个面向新能源车主的 **Local First 车辆数据与充电/行程记录 App**。

项目优先记录可解释、可恢复的真实用车事实：车辆、充电记录、SOC/里程快照、GPS 行程、成本与本地分析。缺失数据保持缺失；估算值必须与实测/用户确认事实区分。

> 本 README 只提供稳定入口，不维护高频实现 checklist。当前代码/Issue/PR 的实时状态以 [`docs/CURRENT_STATUS_AUTHORITY.md`](docs/CURRENT_STATUS_AUTHORITY.md) 为准。

## 当前稳定能力

### Android / Local First

- Kotlin + Jetpack Compose
- Room 本地数据库与显式 migration
- 多车辆与当前车辆上下文
- managed vehicle catalog + Offline First 缓存
- 用户车辆 nickname；标准车型事实只读
- Charging Record 新增 / 编辑 / 删除 / 统计
- VehicleState 当前 SOC / 里程事实层
- Trip 前台记录、真实 WGS84 TripPoint、距离/时间/速度/海拔分析
- Trip LONG_GAP / provider / reliability 诊断与 truthful route presentation
- Local JSON Backup / Restore
- release-only APK 更新发现、下载、SHA-256 校验与 Android installer handoff

### Managed catalog / assets

- 后台维护车型目录、品牌元数据与 Hero artwork key
- Brand Logo Light/Dark 资产发布与 Android 缓存展示
- Catalog JSON/CSV import/export 与批量资源维护工具

### 当前正在推进

- Charging v0.7：成熟的费用/单价/桩端电量联动编辑、可选 `开始充电` 生命周期与 truthful derived metrics
- Trip / Vehicle / Records / Stats 的真实设备 closeout
- 后台定位、通知、Geocoder、production updater 的 physical acceptance
- 车型目录 provenance / normalization / coverage quality

“代码已实现”“CI Green”“真机功能验收”“真机视觉验收”“Production Release 验收”是不同状态，不互相替代。

## 数据真值原则

- Local First：服务端/网络不可成为核心记账与 Trip 记录的前置条件。
- coordinates 是位置事实，address 是派生展示。
- 不生成 synthetic GPS point，不跨真实 LONG_GAP 补造路线或距离。
- 不把手工 SOC、SOC-derived energy、估算值描述为 BMS 实测。
- Catalog reference data 不等于 VIN / BMS / live vehicle telemetry。
- CI Green 不等于真机验收。

## 权威文档入口

### 当前执行状态

- [`docs/CURRENT_STATUS_AUTHORITY.md`](docs/CURRENT_STATUS_AUTHORITY.md) — 高频变化的当前实现/验收状态
- [`docs/ROADMAP.md`](docs/ROADMAP.md) — 路线与阶段顺序

### 稳定产品与架构

- [`docs/PROJECT_MASTER.md`](docs/PROJECT_MASTER.md) — 项目总纲
- [`docs/PRODUCT.md`](docs/PRODUCT.md) — 产品边界
- [`docs/FEATURE_MATRIX.md`](docs/FEATURE_MATRIX.md) — 功能矩阵
- [`docs/DATABASE.md`](docs/DATABASE.md) — 数据模型
- [`docs/LOCATION_TRIP.md`](docs/LOCATION_TRIP.md) — Location / Trip
- [`docs/CI_CD.md`](docs/CI_CD.md) — CI / Production Release
- [`docs/APK_AUTO_UPDATE.md`](docs/APK_AUTO_UPDATE.md) — App updater
- [`docs/VEHICLE_CATALOG_MULTI_VEHICLE.md`](docs/VEHICLE_CATALOG_MULTI_VEHICLE.md) — Vehicle / Catalog
- [`docs/BRANCH_AND_PR_GOVERNANCE.md`](docs/BRANCH_AND_PR_GOVERNANCE.md) — Branch / PR / stacked-PR 治理

当文档冲突时，使用以下优先级：

1. 当前 `main` 的实现事实 / schema / runtime contract
2. merged PR + CI evidence
3. `CURRENT_STATUS_AUTHORITY.md`
4. owning Open Issue 的剩余验收 / future work
5. 旧 Roadmap、版本化 UI baseline 与历史设计文档

## Repository Structure

```text
EV-Charge-Book/
├── android/          # Android / Compose / Room
├── server/           # managed backend/admin related code
├── docs/             # product, architecture, UX and status authority
├── .github/          # CI/CD workflows and contribution templates
└── README.md
```

## 开发与发布

开发、构建与验收规则见 [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) 与 [`docs/CI_CD.md`](docs/CI_CD.md)。

Production Release 与普通 Android CI 分离。正式 APK 版本只在明确触发 Production Release 时生成；普通业务提交、PR、Debug CI 和文档变更不生成新的正式版本。

## Issue / PR 规则

- Open Issue 不代表代码一定未实现；先核对 current `main` 与 merged PR。
- Draft / unmerged PR 不是 runtime authority。
- 实现完成但仍需真机验证时，Issue 必须明确改写为 physical acceptance owner。
- Stacked PR 必须明确 parent/base，并按依赖顺序合并和 retarget。
- 已关闭/合并工作的远端 head branch 应按治理规则清理。

持续文档治理由 Issue #6 跟踪；仓库保护与 required CI 由 Issue #75 跟踪。

## License

MIT License. See [`LICENSE`](LICENSE).
