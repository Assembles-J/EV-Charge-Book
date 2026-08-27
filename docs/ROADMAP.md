# EV Charge Book Roadmap

版本: v2.6.1
更新时间: 2026-08-27

## 0. 路线原则

以 PROJECT_MASTER / PRODUCT / FEATURE_MATRIX 为准。

继续坚持：简单可维护、Local First、真实数据、先验收后扩功能；原始事实、派生值与估算值必须区分。

---

## 当前阶段重点

Trip #15 核心功能已经验收关闭；真实长行程暴露出的后续可靠性统一由 #41 跟踪。

当前实现基线：

- PR #36：GPS health / notification / gap route segmentation，Build Run #184 Green。
- PR #38：速度来源、GPS reliability、OBD P3 文档收口。
- #41 第一批 P0：trusted distance / provider dedupe / startup false-speed guard 已实现到分支，等待 CI。

### P0 — Trip trusted facts

- [x] GPS health runtime visibility
- [x] `>=120s` gap 路线预览断开
- [x] `>=120s` gap 两端不补可信距离（实现待 CI）
- [x] gap 内 moving/stopped 时间不补造（实现待 CI）
- [x] 首点/恢复点不直接进入 max speed 聚合（实现待 CI）
- [x] 陈旧 Location callback freshness guard（实现待 CI）
- [x] reported speed 与可信位移交叉验证（实现待 CI）
- [x] GPS -> Network 8 秒窗口去重（实现待 CI）
- [x] Network -> GPS 切换重新建立距离基线（实现待 CI）
- [ ] Trip completeness / persistent diagnostics
- [ ] service lifecycle / re-delivery evidence
- [ ] long-drive lock-screen physical verification

### P1 — Segmented speed

- [x] 全程均速 / 行驶均速 / 最高已记录速度语义拆分
- [ ] `TripSpeedSegment`
- [ ] 连续深红 -> 红 -> 黄 -> 绿 -> 蓝速度轨迹
- [ ] 最快/最慢区段等派生统计仅在数据可信后加入

### P2 — Map

- [ ] MapLibre / basemap，继续低优先级

### P3 — Optional OBD-II

- [ ] 外接 Bluetooth/BLE/Wi-Fi OBD-II adapter PoC
- [ ] 查询标准 Vehicle Speed
- [ ] OBD speed 与 GNSS speed 对照

不做私有 CAN/BMS 逆向，不让 OBD 成为 Trip 必需依赖。

---

## v0.4 - Local First Sync

Vehicle + ChargingRecord stable identity foundation 已存在；同步主线 #27/#28 暂时排在 #41 本地 Trip 可靠性之后。

恢复顺序：

1. 当前 Trip P0 累计 CI Green
2. Trip completeness / persistent diagnostics
3. 长行程锁屏真机复验
4. P1 segmented speed + colored route
5. 回到 Vehicle / ChargingRecord sync DTO + conflict/apply rules
6. 最小 HTTPS sync client/server

旧 Run #177 属于 GitHub Actions stale queued 状态，不再作为同步主线 blocker。

---

## 当前执行顺序

```text
#41 trusted distance + startup speed guard
  -> cumulative Android CI
  -> Trip completeness / persistent diagnostics
  -> long-drive physical verification
  -> TripSpeedSegment + colored route
  -> resume #27/#28 v0.4 sync
  -> optional OBD-II PoC only when justified
```

---

## 变更记录

### v2.6.1

- 增加未起步时约 120 km/h 的真实设备异常案例
- 第一批 #41 P0 增加首点速度保护、location freshness、速度/位移交叉验证
- `>=120s` gap 实现可信距离/时间断开，避免假距离
- 增加 GPS/Network 短窗口去重和 provider 切换基线规则
- 保持 P1 彩色速度、P3 OBD 的优先级不前移

### v2.6.0

- Trip reliability 优先于继续 sync expansion
- 明确 GNSS / derived / future OBD speed 数据来源
- OBD-II 定为 P3 optional exploration
