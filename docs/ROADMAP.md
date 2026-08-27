# EV Charge Book Roadmap

版本: v2.6.0
更新时间: 2026-08-27

## 0. 路线原则

以 PROJECT_MASTER / PRODUCT / FEATURE_MATRIX 为准。

继续坚持：简单可维护、Local First、真实数据、先验收后扩功能；原始事实、派生值与估算值必须区分。

---

## v0.1 - Local Charging Book

状态: Released / Accepted

- [x] Room / DAO / Repository / ViewModel
- [x] 车辆创建 / 编辑持久化
- [x] 充电记录新增 / 编辑 / 删除
- [x] Dashboard / Records / Stats
- [x] Android CI / Debug APK
- [x] 真机核心 CRUD 验收
- [x] signed production APK / atomic server release

---

## v0.2 - Vehicle, Location & Trip Foundation

状态: **Core Accepted / Reliability Hardening**

### Trip / Location reliability

- [x] TripSession / TripPoint + migration
- [x] manual start/stop
- [x] foreground location service + persistent notification
- [x] WGS84 / accuracy / speed / bearing / altitude
- [x] distance / elapsed / moving / stopped / average / max speed
- [x] interrupted resume same TripSession
- [x] GPS quality / jump filtering + stationary throttling
- [x] GPS/Network provider fallback
- [x] runtime GPS health / accepted-point heartbeat
- [x] GPS LOST / LONG_GAP ongoing notification
- [x] long GPS gap 在 route preview 中断开，不画假实线
- [x] 全程均速 / 行驶均速 / 最高速度口径明确区分
- [ ] 长 gap 两端不参与可信距离累计
- [ ] GPS / Network 去重与择优
- [ ] longest gap / provider counters / rejected reason 持久摘要
- [ ] service lifecycle / restart / re-delivery evidence
- [ ] 锁屏长行程真机复验
- [ ] TripSpeedSegment 派生模型
- [ ] 连续速度颜色映射
- [ ] 短时峰值与 segment speed 分开展示

PR #36 第一阶段代码已通过 Android Build Run #184：Build/Test + Debug APK Green。

详细设计：`docs/TRIP_GPS_RELIABILITY_AND_SPEED_VISUALIZATION.md` 与 `docs/LOCATION_TRIP.md`。

---

## v0.3 - Local Analytics & Reliability

状态: **Feature Complete Candidate / Reliability Follow-up**

已实现：

- charging interval odometer distance
- cost/100km estimate
- charged kWh/100km estimate
- Trip + odometer coverage evidence
- SOC confidence hints
- six-month cost / energy trend
- month-over-month comparison
- charger type mix
- ChargingPlace aggregation + common-place reuse
- selected-vehicle charging CSV analysis export
- non-blocking anomaly hints

真实 Trip #7 观察到 12-15 分钟级 GPS gap，因此 reliability 优先级高于继续扩统计展示。

下一阶段：

1. long-gap distance aggregation correction
2. GPS / Network provider fusion / dedupe
3. Trip completeness summary / persistent diagnostics
4. lock-screen physical verification
5. segmented speed + continuous color route

---

## v0.4 - Cloud & Catalog Sync

状态: Foundation started; feature expansion deferred until Trip P0 reliability is observable

已存在 Local First Sync Foundation 不回退：stable identity / tombstone / protocol 文档继续保留。

Cloud sync must not become the only recovery path. Existing local JSON backup remains supported。

---

## P3 - Optional Vehicle Data Source / OBD-II

状态: Future Exploration / Not Product Blocker

首个最小 PoC：

- [ ] 定义轻量 `VehicleSpeedSource` 边界
- [ ] 外接 OBD-II Bluetooth/BLE/Wi-Fi adapter
- [ ] 查询标准 Vehicle Speed 支持能力
- [ ] 读取 OBD Vehicle Speed
- [ ] 与 GNSS `Location.speed` 对照

明确不进入当前主线：

- 厂商私有 CAN ID 逆向
- 私有 BMS PID 逆向
- 单车型复杂协议维护
- OBD 成为 Trip 必需依赖

只有 Vehicle Speed PoC 证明稳定价值后，才评估 SOC / 电压 / 电流 / 电池温度等更多车辆事实。

---

## 当前执行顺序

```text
Trip P0 reliability
  -> long-gap distance correction
  -> GPS / Network dedupe + quality policy
  -> Trip completeness summary / diagnostics
  -> physical lock-screen long-drive verification
  -> Trip P1 segmented speed + colored route
  -> v0.3 reliability closeout
  -> resume v0.4 sync expansion
  -> P3 OBD-II optional PoC when product value justifies it
```

MapLibre 继续保持低优先级；没有必要为了彩色速度轨迹先引入地图 SDK。

---

## 变更记录

### v2.6.0

- PR #36 GPS health / notification / route-gap / speed semantics passed Android Build Run #184
- clarified current peak speed comes primarily from `Location.speed`, not point-to-point straight-line division
- recorded that average speed still inherits GPS distance-quality limitations
- promoted long-gap distance correction and GPS/Network dedupe ahead of colored route work
- added OBD-II as P3 optional VehicleSpeedSource PoC, explicitly excluding private CAN/BMS reverse engineering from current product scope

### v2.5.0

- based on real Trip #7, promoted 12-15 minute GPS gaps to P0 reliability work
- added segmented speed / continuous color visualization as P1
- clarified speed colors describe this vehicle's speed, not real traffic congestion
- clarified long GPS gaps must not be rendered as trustworthy solid routes
- deferred v0.4 execution until Trip P0 reliability becomes observable
