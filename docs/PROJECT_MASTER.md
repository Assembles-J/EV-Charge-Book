# EV Charge Book 项目总纲（PROJECT MASTER）

版本: v3.0.0
更新时间: 2026-08-31
状态: Stable product / architecture authority

## 1. 文档职责

`PROJECT_MASTER.md` 只维护 **稳定产品定位、数据真值、架构边界和长期工程原则**。

它不再承担“今天代码做到哪”“哪个 PR Green”“当前 head SHA 是什么”的高频状态职责。

状态职责分工：

- `CURRENT_STATUS_AUTHORITY.md` — 当前实现 / PR / Issue / CI / acceptance 快照；
- `ROADMAP.md` — 产品里程碑与执行顺序；
- domain authority — 具体模块设计；
- merged PR / current `main` — 实现事实。

当本文与 current `main` / `CURRENT_STATUS_AUTHORITY.md` 在“当前实现状态”上冲突时，以后者为准；本文继续约束产品/架构原则。

---

## 2. 项目定位

EV Charge Book 是新能源车主的 **Local First 车辆数据与用车事实中心**。

核心演进方向：

1. 充电记账与真实成本；
2. 多车辆与可维护车型目录；
3. 位置 / 驾驶行程事实；
4. Charge / Trip / VehicleState 数据闭环；
5. 本地分析与数据可靠性；
6. 可恢复、可解释的跨设备同步；
7. 在真实数据基础上的可选 Web / AI 增值。

首个重点验证车型可以是具体车型，但产品、数据库和 UI 不能绑定单一品牌或单一车型。

---

## 3. 权威文档体系

### 当前状态

- `CURRENT_STATUS_AUTHORITY.md`
- `ROADMAP.md`

### 产品 / 架构

- `PRODUCT.md`
- `FEATURE_MATRIX.md`
- `UIUX.md`
- `FRONTEND.md`
- `BACKEND.md`
- `DATABASE.md`
- `DEVELOPMENT.md`
- `LOCATION_TRIP.md`
- `DATA_QUALITY_BACKUP.md`
- `SYNC_PROTOCOL.md`
- `VEHICLE_CATALOG_MULTI_VEHICLE.md`

### CI / 发布 / 仓库治理

- `CI_CD.md`
- `APK_AUTO_UPDATE.md`
- `BRANCH_AND_PR_GOVERNANCE.md`
- `WORKFLOW_OWNERSHIP.md`

### 当前 domain authority

- `CHARGING_V0.7_DESIGN_AND_IMPLEMENTATION_PLAN.md`
- `TRIP_V0.7_SOC_WHEEL_EDIT_DESIGN.md`
- 版本化 UI baseline 文档仅对其明确版本/视觉范围负责。

实现与文档冲突时：先检查 current `main` / merged PR / CI / 真机证据，再修正文档；不要为了维护旧文档而倒退实现。

---

## 4. 核心产品原则

- **Local First / Offline First**：核心记账、车辆和 Trip 不能依赖网络才能工作。
- **真实数据来源**：不伪造 live SOC / SOH / BMS / charger telemetry / GPS route。
- **事实分层**：原始事实、用户确认值、派生值、估算值必须可区分。
- **可解释**：成本、能耗、距离、速度、海拔、SOC 推导必须能解释输入与限制。
- **可恢复**：本地备份长期保留；云端不能成为唯一恢复路径。
- **低录入成本**：在不牺牲真值的情况下复用当前车辆、最近设置、真实位置等已有事实。
- **渐进复杂度**：没有产品/数据证据时不引入重型框架、第二套状态系统或新服务。
- **真机独立验收**：CI Green 不替代 physical functional / visual / Production acceptance。

---

## 5. 事实模型

### 5.1 Vehicle / Catalog

区分两类事实：

**标准车型参考事实**

- brand / series / model year / trim；
- battery/range 等公开规格；
- Brand Logo / Hero artwork metadata；
- 由 managed catalog 维护，用户侧只读。

**用户车辆事实**

- nickname；
- 当前 VehicleState；
- 用户历史 Charge / Trip；
- 已保存 snapshot。

Catalog 后续更新不能静默改写用户历史事实。Retire 车型可阻止未来选择，但不能删除已有用户车辆或历史。

Catalog reference data 不等于 VIN/BMS/live telemetry。

### 5.2 VehicleState

VehicleState 代表当前车辆的本地事实状态，例如：

- 当前已知 SOC；
- 当前已知里程；
- 事实来源与时间顺序。

Trip / Charge / manual update 应遵守明确的事件时间 authority。旧事件不得覆盖更新的真实事实。

### 5.3 ChargingRecord

ChargingRecord 表示已完成或历史补录的充电事实。

核心原则：

- 用户可以独立维护历史记录；
- `开始充电` 不能成为维护历史记录的强制入口；
- cost / unit price / meter energy 的联动必须集中在 domain/editor state；
- duration / average power / loss 只有输入充分且语义兼容时才派生；
- SOC-derived vehicle energy 必须保持 estimate 语义。

未来 active charging session 可以是独立生命周期，但不能伪造 charger/BMS 实时遥测。

### 5.4 Location

- WGS84 coordinates 是位置事实；
- address 是 reverse-geocode 派生展示；
- Geocoder 失败不能阻止坐标保存；
- 用户手工地址不应在无显式动作时偷偷改写底层 coordinates；
- map renderer 只是展示层，不能改写持久化位置事实。

### 5.5 Trip

Trip 记录真实 accepted location evidence。

必须保持：

- 原始 capture timestamp authority；
- accuracy / provider / speed trust；
- `LONG_GAP` 硬连续性边界；
- 不跨真实 gap 补造距离、速度或路线；
- stationary GNSS drift 不得制造明显行驶距离；
- raw TripPoint 可保留，派生统计决定哪些证据进入汇总；
- route / trend / playback 不得创建新的持久化 Trip 事实。

速度来源必须有语义：GNSS reported speed、derived evidence、future OBD 不混成无来源“真值”。

---

## 6. Android 架构边界

首选保持简单单体和清晰事实流：

```text
Compose UI
  -> ViewModel / UI state
  -> Repository / domain rules
  -> Room DAO / Room

Trip foreground service
  -> location source abstraction
  -> trust / sampling / continuity rules
  -> Room
```

允许 provider adapter，例如 Google Fused / platform GPS-network fallback，但 provider abstraction 不能改变 persisted fact semantics。

默认不引入：

- 为了“架构完整”拆多 module Clean Architecture；
- 无真实收益的 Hilt/Koin 重构；
- 第二套 Trip tracking service；
- 为 foreground Trip 再叠一套 WorkManager 状态机；
- 为 UI 动画增加高频数据库写入。

---

## 7. Backend / Cloud 边界

第一版云能力应保持：

```text
Android Room
  <-> HTTPS Sync API
Spring Boot monolith
  -> PostgreSQL
```

原则：

- 云端是第二份恢复/多设备数据，不是 App 运行前提；
- 第一批同步优先 Vehicle + ChargingRecord；
- stable sync identity + idempotent upsert；
- explicit tombstone；
- 简单可解释冲突规则；
- 服务端失败不破坏当前 Room 事实。

默认不做：

- 微服务；
- MQ/Kafka；
- CRDT；
- realtime websocket sync；
- 第一批 TripPoint cloud sync；
- AI 自动改写账本事实。

---

## 8. UI / UX 原则

- Dark First；Light mode 需要保持可读。
- 信息密度靠 hierarchy / spacing，不靠极小字体。
- 关键状态不能只靠颜色。
- 320–360dp / large font / IME / TalkBack 是真实 closeout 场景。
- finished Hero artwork 作为完整视觉资产使用；不要在 Compose 重建复杂极光/倒影/glow。
- route / trends / playback 可以增强解释性，但不能为了“好看”伪造数据。
- 驾驶状态 UI 优先显示少量可信事实，不做导航 App 式高噪音信息堆叠。

---

## 9. Notification / Background 原则

Notification 是后台状态可见性，不是 Trip 数据事实本身。

要求：

- ongoing Trip 使用 foreground notification；
- permission/provider loss 可进入显式 repair flow；
- 修复后由用户明确恢复，不偷偷修改历史事实；
- Android 13+ notification denial 不能回滚已合法开始的 Trip；
- 锁屏通知默认不泄露精确坐标 / HOME / WORK；
- 蓝牙连接只是候选触发信号，不自动等同“正在驾驶”；
- 蓝牙断开不能直接静默完成 Trip。

---

## 10. Backup / Privacy

- Local JSON Backup 长期保留；
- schema 变化必须考虑 migration / backward compatibility；
- restore 失败不得破坏当前有效数据；
- active Trip / active charging session 的 restore 语义必须显式；
- 坐标、家庭/公司地点等敏感位置不应被无必要上传或暴露；
- future route share/export 需要 Privacy Zone / 明确隐私设计。

---

## 11. CI / Release 原则

阶段必须严格区分：

- Implemented；
- CI Green；
- Physical Functional Accepted；
- Physical Visual Accepted；
- Production Accepted。

Android 普通 CI：

- build/test Debug；
- 不生成正式版本号；
- 不发布 production metadata。

Production Release：

- 显式人工触发；
- 使用 production signing；
- versionCode 单调增加；
- verify signed APK；
- atomic publish update metadata；
- old-production -> new-production 覆盖升级仍需独立真机验收。

详细流程见 `CI_CD.md` / `APK_AUTO_UPDATE.md` / `WORKFLOW_OWNERSHIP.md`。

---

## 12. Repository Governance 原则

高频开发不能让旧分支、Draft PR、历史 Issue 变成伪权威。

要求：

- runtime code 通过 PR + current-head applicable CI；
- `main` 目标状态为 protected；
- merged/closed stale branch 定期清理；
- stacked PR 显式 parent/base/merge order；
- parent merge 后 child retarget + re-diff + current-head CI；
- merged branch 在无 child dependency 后删除；
- PR/Issue 模板明确 owning Issue、authority、evidence、physical acceptance、supersession。

详细规则见 `BRANCH_AND_PR_GOVERNANCE.md`。当前仓库设置事实见 #75 / #265 / `CURRENT_STATUS_AUTHORITY.md`。

---

## 13. 可选未来能力边界

### Map context

只有 provider/style 在目标地区真实设备上证明价值后才引入 production basemap。现有 truthful no-basemap renderer 始终可作为 fallback。

不做 road snapping 来“修饰”GPS 事实。

### Destination

没有真实 product capability 前，不显示 fake destination / ETA / navigation state。

### OBD / higher-fidelity telemetry

优先用户授权、稳定、可解释的数据源。

不做：

- 其他 App 私有数据绕过；
- root/sandbox bypass；
- 未验证的通用 CAN 逆向平台；
- 让 OBD 成为 Trip 核心前置条件。

### AI

AI 可以解释、总结、提示，但不能无证据改写账本/Trip 原始事实。

---

## 14. 当前执行入口

不要在本文件寻找今天的 PR/CI 状态。

使用：

- `CURRENT_STATUS_AUTHORITY.md` — 当前项目执行快照；
- `ROADMAP.md` — 阶段与顺序；
- owning Issue — 剩余验收或 future work；
- current `main` / merged PR — 实现事实。

本文件只有在稳定产品原则、事实语义或架构边界变化时才更新。
