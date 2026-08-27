# EV Charge Book v0.5 Vehicle State Architecture

版本: v1.1.0
更新时间: 2026-08-27
状态: Implemented Foundation / Physical Acceptance Pending

## Goal

EV Charge Book 从“各页面重复录入状态”演进为以车辆当前状态驱动的本地 EV 管理应用。

核心原则：

`VehicleState` 是当前动态车辆信息的共享来源，但历史 `ChargingRecord` / `TripSession` 仍是事件事实，不能被当前状态反向改写。

## Core Model

```text
Vehicle
  |
VehicleState
  |
+-------------------+-------------------+
|                                       |
ChargingRecord                       TripSession
|                                       |
charge end SOC / mileage             trip end SOC / mileage
```

## VehicleState

当前维护：

- current SOC
- current mileage
- last update time
- update source

典型来源：

- CHARGE_RECORD
- TRIP_END
- MANUAL_UPDATE
- IMPORT / RESTORE

未知值保持 `null`，不能为了 UI 完整度制造历史 SOC、里程或能耗。

## State Update Rules

### Charging completion

```text
VehicleState.currentSoc = ChargingRecord.endSoc
VehicleState.currentMileage = known charge odometer / latest valid known mileage
```

### Trip start

开始行程时快照当前状态：

```text
TripSession.startSoc = VehicleState.currentSoc
TripSession.startMileageKm = VehicleState.currentMileage
```

快照之后即属于本次 Trip 的历史事实；后续 VehicleState 变化不能反向修改已经开始的 Trip。

### Trip completion

用户显式确认结束 SOC；结束里程可由“开始里程 + 可信 GPS 距离”预填并允许修正。

```text
TripSession.endSoc = user confirmed end SOC
TripSession.endMileageKm = confirmed / derived mileage
VehicleState.currentSoc = TripSession.endSoc
VehicleState.currentMileage = latest known valid mileage
```

Trip completion 与 VehicleState 更新必须处于同一数据库事务中，避免 Trip 已完成但车辆当前状态没有同步更新。

## Event Ordering Rule

VehicleState 表示“当前”状态，因此不能因为补录或编辑较早的历史事件而倒退。

当前实现会从有效 Charge / completed Trip 事件重建状态，并尊重时间顺序；较新的手动 VehicleState 不应被更早的历史事件覆盖。

删除或编辑历史 Charge / Trip 后，也必须重新根据剩余事件计算当前状态，而不是简单使用被删除事件的前一个值。

## Trip Energy Boundary

首版行程能耗来自 SOC 差估算：

```text
estimated consumed kWh
  = battery capacity kWh * (start SOC - end SOC) / 100

estimated average kWh/100km
  = estimated consumed kWh / trusted GPS distance km * 100
```

这是估算，不是 BMS 实测能耗。

以下情况不强行计算：

- start SOC 未知
- SOC 未下降或出现回升
- 距离不足
- 输入不足以形成有意义的估算

SOC 取整、回收制动、途中补能等都可能让短行程 SOC 差不适合作为能耗事实；此时保持 unavailable 比输出伪精确值更重要。

## UI Consumption Rule

后续页面应优先消费 VehicleState，减少重复录入：

- Charge form：开始 SOC / 当前里程从 VehicleState 带入
- Trip READY：展示当前 SOC / 当前里程
- Trip start：自动快照当前状态
- Trip end：只要求用户确认真正变化且无法自动确定的结束状态
- Dashboard / Vehicle：逐步统一展示当前状态

Trip READY 在真正开始定位前不得绘制虚拟当前位置、虚拟道路或假轨迹。

## Notification Boundary

Trip 结束需要用户确认结束 SOC，因此 foreground notification 不能直接把 Trip 写成 `COMPLETED`。

当前通知动作只打开 App。旧的 direct-stop intent 不得绕过 completion form；未来若做 deep link，应直接进入当前 Trip 的结束确认流程，而不是恢复后台静默完成。

## Implementation Status

已进入 `main`：

- PR #79 VehicleState foundation
- PR #81 Smart Charging defaults / state updates
- PR #87 Trip SOC / mileage / estimated energy / VehicleState completion

PR #84 已由 #87 取代并关闭。

仍需：

- Home / Vehicle 对 VehicleState 的统一展示收口
- Charge / Trip / Dashboard 真机交互验收
- Trip SOC-based energy 与真实使用场景的合理性复核

## Design Principle

简单、可解释、Local First：

1. 历史事件保存事实。
2. VehicleState 保存当前状态。
3. 已知值自动带入，未知值保持未知。
4. 派生估算必须明确标识，不能冒充实测。
5. 不为状态同步引入不必要的事件总线、云端依赖或复杂 CQRS。
