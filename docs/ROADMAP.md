# EV Charge Book Roadmap

版本: v3.0.0
更新时间: 2026-08-31
状态: Milestone / sequencing authority

## 0. 文档职责

本文件只负责 **里程碑、阶段顺序和产品优先级**。

它不再维护高频 PR/commit/CI/head-SHA 状态。当前实现、Open PR stack、真机验收 owner 和仓库治理快照统一以 `CURRENT_STATUS_AUTHORITY.md` 为准。

当本文件的日期晚于旧设计文档但早于 current-status snapshot 时：

- 产品阶段顺序仍以本文件为准；
- “现在代码做到哪”以 `CURRENT_STATUS_AUTHORITY.md` / current `main` 为准；
- Open Issue 不代表代码一定未实现；
- CI Green 不等于真机或 Production Release 验收。

路线原则：简单可维护、Local First、真实数据、先验收后扩功能；原始事实、派生值与估算值必须明确区分。

---

## v0.1 — Local Charging Book

状态: **Released / Accepted**

目标：建立离线可用的车辆与充电记账基础。

稳定能力：

- Vehicle / ChargingRecord 本地持久化；
- 充电记录新增、编辑、删除；
- Dashboard / Records / Stats 基础；
- 本地数据恢复基础；
- Android Debug / Production Release 基础链路。

本阶段不再作为活跃开发 owner。

---

## v0.2 — Vehicle, Location & Trip Foundation

状态: **Core Implemented / Physical Reliability Closeout**

目标：建立真实、可解释的驾驶行程事实层。

稳定方向：

- TripSession / TripPoint；
- foreground tracking；
- WGS84 坐标、速度、方向、海拔、accuracy；
- distance / elapsed / moving / stopped / average / max；
- provider / callback / accepted-point diagnostics；
- `LONG_GAP` 断点与不补造距离原则；
- Fused + non-GMS/provider fallback；
- truthful route / speed / altitude presentation；
- interruption / resume / repair notification。

当前关闭条件不是再做一套 tracking architecture，而是完成 current-main 真机可靠性：锁屏、另一 App 前台、静止、provider loss/recovery、delayed callback、距离可信度。

主要 owner：#77 / #26 / #14 / #215。

---

## v0.3 — Local Analytics & Data Reliability

状态: **Feature Complete / Incremental Follow-up Only**

目标：在不伪造 BMS/车辆遥测的前提下，把本地 Charge / Trip 事实转成可解释分析。

稳定方向：

- 月度充电费用 / 电量趋势；
- month-over-month；
- charger type mix；
- common place / interval evidence；
- cost / 100km 与 energy / 100km 估算；
- Trip validity-aware analytics；
- anomaly / confidence hints；
- CSV / JSON 本地导出与恢复。

只在真实需求或数据质量证据出现时增量扩展，不为“看起来完整”增加重型分析框架。

---

## v0.4 — Local First Sync & Catalog Platform

状态: **Identity / Catalog Runtime Baseline Implemented; Sync Expansion Deferred**

### Sync

已建立 Vehicle / ChargingRecord 稳定 sync identity、updatedAt / tombstone / backup compatibility 等基础。

后续 #27 的第一批目标保持最小：

- protocol/schema version；
- Vehicle / ChargingRecord envelope；
- push/pull cursor；
- idempotent upsert；
- explicit tombstone propagation；
- 简单冲突策略；
- smallest HTTPS + Spring Boot monolith + PostgreSQL slice。

明确不做：CRDT、微服务、MQ、让云端成为 App 运行前提、第一批同步 TripPoint。

### Catalog

Managed catalog runtime、Offline First refresh、后台维护、JSON/CSV import/export、Brand Logo/Hero metadata 已建立。

剩余 #20 聚焦：

- 可审计 source provenance；
- brand/series/year/trim normalization；
- duplicate/conflict/correction policy；
- broader real-model coverage；
- coverage quality metrics。

---

## v0.5 / v0.6 — Local Experience & Physical Closeout

状态: **Code Baseline Implemented / Physical Closeout**

目标：把 Dashboard / Records / Stats / Trip / Vehicle 收敛到成熟的 Dark First、信息密集、truthful UI，并完成真实设备验收。

代码方向已经从“重设计”转为“真机 closeout”：

- Dashboard dynamic Vehicle Hero + recent Trip；
- Records dense charging ledger；
- Stats compact analytics hierarchy；
- Trip home / READY / active / completion / completed detail 分层；
- route / trends / diagnostics progressive disclosure；
- narrow width / large font / IME / accessibility hardening；
- Trip playback / route interaction / trend inspection 等增量能力。

主要剩余 owner：#70 / #42 / #94 / #95 / #145 / #168 / #77 / #14 / #26 / #102 / #124。

原则：没有具体真机 regression，就不重新开启 broad redesign。

---

## v0.7 — Charging Maturity + Current Product Closure

状态: **Active Delivery**

当前主线产品工作是 Charging v0.7，而不是重做既有 Vehicle/Trip 基础。

### Charging workflow

Parent: #251

产品模型：

- `开始充电` — 可选 lifecycle / preset bookkeeping；
- `充电记录维护` — 独立、完整的历史记录新增/补录/编辑入口。

手工维护不能被“开始充电”取代。

### Billing / derived metrics

Owners: #252 / #260

核心规则：

`总费用 > 单价 > 桩端/电表电量`

要求：

- centralized calculation/editor state；
- raw typing 稳定；
- 不出现 bidirectional recomposition loop；
- duration / average power / loss 只在输入充分时派生；
- 估算值不冒充 BMS/桩端遥测。

当前 PR stack 与实时 merge gate 不写死在本文件，见 `CURRENT_STATUS_AUTHORITY.md`。

### Optional active charging lifecycle

Owner: #253

目标：

- Local First active session；
- process-death recovery；
- truthful `充电中` 状态；
- completion 只生成/更新一条 completed record；
- preset 只保存可复用环境输入，不保存未来结果。

### Charging location

Owner: #254

先复用真实 coordinate + reverse geocode + manual address 基础；map-point picker 只在产品价值明确时增加。

---

## Vehicle maturity / managed assets — Parallel Closeout

Owners: #244 / #20

并行完成：

- managed read-only standard vehicle facts；
- user nickname；
- Brand Logo / Hero asset runtime；
- Offline First / last-known-good；
- catalog retire compatibility；
- provenance / normalization / coverage quality。

这不是新的产品主线架构项目，而是成熟度与数据质量收口。

---

## Repository governance — Parallel P0

Owners: #6 / #75 / #265

项目高频迭代要求仓库治理同步成熟：

- README / authority hierarchy / Issue/PR templates；
- stacked PR 明确依赖与 retarget 规则；
- `main` protection + current-head required CI；
- merged/closed stale remote branch cleanup；
- merge-time branch deletion；
- workflow ownership documentation。

仓库治理不应阻塞业务探索，但必须防止旧 Issue/branch/PR/CI 证据重新成为伪权威。

---

## Post-v0.7 / Optional Enhancements

### Map context

Owners: #192 / #199

只有 provider/style 在中国大陆真实 Trip 上证明道路/中文标签/可用性/许可价值后，才引入 basemap。现有 truthful no-basemap route 继续作为 fallback。

### Destination planning

Owner: #152

未来 capability；当前 Trip 不伪造 destination / ETA / navigation state。

### Higher-fidelity vehicle telemetry

Owner: #138 / future OBD research

优先级：公开/用户授权 API > 用户自有 OBD/telemetry > 用户提供事实 > 当前透明估算。

不逆向其他 App 私有数据，不让 OBD/云端成为核心 Trip 必需依赖。

### Cross-device sync

Owner: #27

在当前 Local Experience / Charging closure 之后恢复最小 Vehicle + ChargingRecord cloud sync。

---

## 当前执行顺序

产品主线：

```text
Charging v0.7 calculation/editor stack
  -> manual Add/Edit maturity
  -> optional active charging lifecycle
  -> charging location/detail workflow
  -> migration/backup/physical acceptance
```

并行 closeout：

```text
Trip / Vehicle / Dashboard / Records / Stats physical acceptance
  + background/location/notification reliability
  + production updater acceptance
  + catalog provenance/coverage quality
```

并行仓库治理：

```text
main protection / required current-head CI
  + stale branch cleanup
  + documentation / Issue / PR authority maintenance
```

之后再恢复：

```text
minimal Local First cloud sync
  -> optional map context / destination / telemetry research
```

---

## 阶段状态定义

文档必须使用下列不同状态，不可混用：

- **Implemented** — 代码已进入 `main`；
- **CI Green** — 自动化检查通过；
- **Physical Functional Accepted** — 真机功能链路通过；
- **Physical Visual Accepted** — 真机视觉/交互通过；
- **Production Accepted** — 正式发布/升级链路通过。

任何一个状态都不能自动推导另一个状态。

---

## 路线维护规则

1. 里程碑/执行顺序变化才更新本文件。
2. PR head、最新 CI run、branch behind/ahead、当天 Issue 状态写入 `CURRENT_STATUS_AUTHORITY.md` / owning Issue，而不是本文件。
3. 稳定产品/架构原则写入 `PROJECT_MASTER.md` / domain authority。
4. 历史实现证据保留在 merged PR / Git 历史，不在 Roadmap 长期堆叠 SHA 清单。
5. Open Issue 不等于未实现；开始新开发前必须检查 current `main`。
