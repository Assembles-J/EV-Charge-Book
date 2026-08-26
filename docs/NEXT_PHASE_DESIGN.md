# EV Charge Book 后续业务设计

更新时间: 2026-08-26
状态: Active Design

## 1. 当前边界

- v0.1、Local Backup / Restore、Multi Vehicle 已验收。
- Vehicle Catalog #16 已实现并通过 CI，等待真机验收。
- 当前少量本地车型 seed 仅验证目录链路；全量车型支持由 #20 的数据管道推进。

## 2. 下一阶段顺序

```text
Vehicle Catalog #16 device acceptance
 -> Bluetooth connection prompt #21 discovery
 -> Location foundation #14
 -> Manual Trip #15
 -> Data Reliability / ChargingPlace #19 remaining
 -> analytics
```

## 3. 蓝牙连接提示 #21

蓝牙只提供“可能开始驾驶”的提示，不是行程事实来源。

```text
用户选择已配对的车载设备并开启开关
 -> 系统报告指定设备连接
 -> 本地通知：开始行程 / 忽略
 -> 用户确认后才调用 Trip start
```

不扫描附近设备，不自动开始或结束行程，不读取 OBD/BLE 车辆协议，不保存扫描历史。设备断连仅供未来提示优化，不能作为自动结束依据。

## 4. 定位基础 #14

先实现 `LocationProvider` 与 Android runtime permission 状态；WGS84 原始坐标、时间和 accuracy 独立保存。充电记录的“使用当前位置”可先于地图出现，失败不阻塞手工地点录入。地图仅为渲染 adapter，不承担记录能力。

## 5. 手动 Trip #15

Trip 必须始终绑定 selected vehicle。首版流程：手动开始、foreground service、有限频率定位、结束汇总、异常中断恢复。距离聚合只采用 accuracy 合格且没有跳点的原始点；elapsed、moving、stopped 三种时间口径分别展示。

## 6. 数据质量与分析

每个数据字段按需标明 MANUAL / GPS / CATALOG / OBD / DERIVED 等来源。统计中明确事实、派生值与估算值。ChargingPlace、异常规则和 CSV 导出在定位/Trip 的真实使用数据出现后再收口。

## 7. 各阶段验收门槛

| 阶段 | 必须验证 |
| --- | --- |
| Catalog | 目录搜索、参数覆写、自定义兜底、离线可用、恢复后关联仍在 |
| Bluetooth | 仅指定已配对设备触发提示；拒绝权限和忽略均不记录行程 |
| Location | 权限/失败状态明确；坐标和 accuracy 可解释 |
| Trip | 锁屏持续记录、无重复会话、中断恢复、距离与时间口径正确 |

