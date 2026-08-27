# EV Charge Book v0.5 Charge and Trip UX Flow

版本: v1.1.0
更新时间: 2026-08-27
状态: Implemented Core / Physical Acceptance Pending

## 1. UX Goal

用户只确认真正发生变化、系统无法可靠推断的状态；已知车辆状态尽量自动带入，同时保持所有估算可解释、可修改、不伪造。

---

## 2. Charge Flow

### Before charge

默认值优先来自当前 VehicleState 与最近有效记录：

- start SOC -> `VehicleState.currentSoc`
- current mileage -> `VehicleState.currentMileage`
- end SOC -> 最近充电 end SOC，无历史时可默认 100
- price -> 最近同类型充电单价
- location -> 当前定位可用时优先，仍允许常用地点/手动选择

### During input

区分：

- charger/meter energy：计费桩表电量
- vehicle received energy：按电池容量与 SOC 变化估算的车辆获得电量
- charging loss：两者差值的估算

总费用可以自动计算，同时允许用户按真实账单修正。

### After charge

保存 ChargingRecord 后，在同一业务流程中更新当前 VehicleState：

```text
ChargingRecord.endSoc
  -> VehicleState.currentSoc

known odometer
  -> VehicleState.currentMileage
```

补录较早的 Charge、编辑或删除历史 Charge 时，当前状态按剩余有效事件重新计算，不能简单把 VehicleState 倒退到旧记录。

---

## 3. Trip Ready Flow

Trip 未开始前只展示真实已知状态：

- 当前车辆
- current SOC
- current mileage
- GPS 轨迹待开始
- 最近已完成行程

未真正启动定位前：

- 不绘制虚拟当前位置
- 不绘制虚拟道路/路线
- 不显示伪造 GPS READY 点位

开始按钮触发真实 location foreground service 后，才进入 LIVE Trip。

如果 current SOC 未知：

- 仍允许记录真实 GPS 行程
- start SOC 保存为 unknown
- 结束时仍可录入 end SOC，并更新车辆当前 SOC
- 因 start SOC 缺失，本次不虚构能耗

---

## 4. Trip Start

开始时自动快照：

```text
TripSession.startSoc = VehicleState.currentSoc
TripSession.startMileageKm = VehicleState.currentMileage
```

然后启动真实 GPS tracking。

Trip 可靠性继续遵循：

- callback liveness 与 Room 写库节流分层
- GPS gap 不伪造连续路线
- coarse/network speed 不制造最高速或彩色高速段

详细规则见 `LOCATION_TRIP.md` 与 `TRIP_GPS_RELIABILITY_AND_SPEED_VISUALIZATION.md`。

---

## 5. Trip End

结束行程必须经过显式 completion flow。

用户确认：

- end SOC：必填，0~100
- end mileage：可修改；若 start mileage 已知，默认按 start mileage + trusted GPS distance 预填

系统预览：

- SOC change
- estimated consumed kWh（有意义时）
- estimated average kWh/100km（有意义时）

估算规则：

```text
consumed energy
  = battery capacity * positive SOC drop

average consumption
  = consumed energy / recorded trusted GPS distance * 100
```

如果：

- start SOC unknown
- SOC unchanged / increases
- distance insufficient

则能耗保持 unavailable，不输出假 0 或假正数。

保存后：

```text
TripSession -> COMPLETED
TripSession.endSoc / endMileage / estimates persisted
VehicleState.currentSoc -> endSoc
VehicleState.currentMileage -> latest valid end mileage
```

Trip 与 VehicleState 更新必须保持原子性。

---

## 6. Notification Flow

因为结束 Trip 需要 end SOC，ongoing notification 不能直接完成 Trip。

当前行为：

```text
notification: 打开行程
  -> App
  -> active Trip
  -> completion dialog
  -> 用户确认 end SOC / mileage
  -> save and complete
```

旧的 direct-stop notification intent 不允许绕过这个流程。

后续 #26 可优化为直接 deep-link 到 active Trip completion dialog，但数据确认仍然必须保留。

---

## 7. Completed Trip Detail

当前 Trip detail 可以展示：

- trusted GPS distance / duration / speed metrics
- start/end SOC
- estimated consumed kWh
- estimated average kWh/100km
- start/end/min/max altitude
- resolved start/end address + coordinate technical facts
- start circle / end square markers
- trusted GPS speed-colored route

所有 SOC-derived energy 都应明确理解为 estimate，不等于 BMS 实测。

---

## 8. UX Principle

1. 已知状态自动带入。
2. 用户只确认真实变化。
3. 自动推断允许修正。
4. 未知值不伪造。
5. 未开始 GPS 不画假地图/假路线。
6. 结束 SOC 是 VehicleState 连续性的关键事实，不能被通知快捷动作绕过。
7. 数据可信度优先于“看起来完整”。

---

## 9. Implementation

已进入 `main`：

- PR #79 VehicleState foundation
- PR #81 Smart Charging Flow
- PR #87 Trip SOC / energy / VehicleState completion

PR #84 已由 #87 取代并关闭。

仍需真机确认：

- Charge defaults / location / cost flow
- Trip READY -> LIVE -> completion interaction
- end SOC 后 VehicleState 是否在各页面一致刷新
- SOC-based estimated consumption 在实际长短行程中的可理解性
- notification -> App -> completion UX
