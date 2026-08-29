# Trip GPS Replay Testing

## Goal

验证 Trip 后台定位链路时，不要求每次都驾驶车辆。

## Modes

- Real GPS: 最终真机验收
- Replay GPS: 开发阶段重复验证

## Replay scenarios

1. Start Trip
2. Enable replay route
3. Lock screen
4. Open another foreground app
5. Wait for replay completion
6. Check Trip points, distance, gap diagnostics

## Boundary

Replay is a development/testing source only.

Do not:

- generate production GPS points
- hide real LONG_GAP events
- replace final physical acceptance
