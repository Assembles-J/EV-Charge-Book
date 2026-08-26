# EV Charge Book Roadmap

版本: v2.0.0
更新时间: 2026-08-26

## 0. 路线原则

以 PROJECT_MASTER / PRODUCT / FEATURE_MATRIX 为准。

当前阶段继续坚持：简单可维护、Local First、真实数据、先验收后扩功能。

---

## v0.1 - Local Charging Book

状态: Released / Accepted

已完成：

- [x] Room / DAO / Repository / ViewModel
- [x] 车辆创建 / 编辑持久化
- [x] 充电记录新增 / 编辑 / 删除
- [x] 历史真实列表
- [x] 日期 / 时间选择
- [x] charger type / remark
- [x] 删除确认
- [x] 保存成功 / 错误反馈
- [x] 空状态
- [x] Dashboard / Stats 基础统计
- [x] ChargingRecordRules / ChargingStatistics
- [x] 核心单元测试
- [x] Material 3 UI
- [x] Gradle Wrapper / Android scripts
- [x] Android CI / Debug APK
- [x] 真机核心 CRUD 验收
- [x] assembleRelease / signing / apksigner
- [x] Signed Production APK
- [x] server atomic release
- [x] v0.1 Release Accepted

首次正式发布：

- Android Release Run #1: Green
- Run ID: `32939069000`
- Commit: `90d31072b6fecfecce7588968b6ddf1cc3c4ee08`
- Artifact: `ev-charge-book-0.1.1`

---

## v0.2 - Vehicle & Trip Foundation

### P0 odometer foundation

状态: Implemented / CI Accepted

- [x] ChargingRecord nullable `odometerKm`
- [x] Room v1 -> v2 migration
- [x] Add/Edit 支持可选总里程
- [x] Records 展示里程
- [x] 同车上一条可靠里程选择
- [x] 里程下降只提示、不阻塞
- [x] odometer validation / previous-reading tests
- [x] Android Build Run #56 Green
- [x] Debug APK Artifact

Issue #18 后续尾项：

- [ ] Room migration test
- [ ] charging interval distance domain calculation
- [ ] cost/100km / charged kWh/100km 正式口径与测试
- [ ] TripSession 存在后的交叉校验

这些尾项当前不是第一优先级。

### P0.5 Local Backup / Restore

状态: Implemented / CI Accepted / Device Accepted

已实现：

- [x] JSON backup payload
- [x] schemaVersion / appVersion / exportedAt
- [x] Vehicle + ChargingRecord + odometer
- [x] SAF CreateDocument / OpenDocument
- [x] 无额外存储权限
- [x] Restore schemaVersion validation
- [x] 覆盖确认
- [x] Room transaction restore
- [x] export decode/count validation
- [x] restore 后 vehicle / charging record count validation
- [x] record -> vehicle 引用校验
- [x] Vehicle 页面备份 / 恢复入口

CI 验收：

- [x] 最小改动启用 BuildConfig/versionName 获取
- [x] Android Build Run #65 Green（Run ID: `32942654435`）
- [x] Debug APK Artifact（`ev-charge-book-debug-65`）

接下来必须按顺序完成：

- [x] 真机导出备份
- [x] 真机覆盖确认
- [x] 真机恢复 Vehicle / ChargingRecord / odometer / remark
- [x] 坏 JSON / 错 schemaVersion 不破坏当前数据
- [x] 更新 #19 并标记 Backup / Restore accepted

Backup / Restore 已验收。

### P1 Multi Vehicle (#17)

状态: Released / Accepted

- [x] selectedVehicleId persisted
- [x] vehicle list / current vehicle switcher
- [x] Dashboard / Records / Stats 按车辆隔离
- [x] Add Record 绑定 selected vehicle
- [x] 车辆归档
- [x] 至少两辆车的真机切换与数据隔离验收
- [x] 归档车辆后历史记录保留的真机验收

### P1 Vehicle Catalog (#16)

- [x] 本地 versioned catalog seed
- [x] brand / series / year / trim 搜索
- [x] 用户确认 / 覆盖 catalog 参数
- [x] 自定义车型兜底

当前主线。实现时保持 Catalog reference data 与 UserVehicle snapshot 分离。

### P1.5 Bluetooth connection prompt (#21)

状态: Implemented / CI Accepted / Device Acceptance Pending

- [x] 指定已配对设备选择与持久化
- [x] Android 12+ Nearby Devices permission
- [x] Android 13+ notification permission
- [x] 指定设备连接通知
- [ ] 真机连接提示验收
- [ ] 拒绝权限 / 未配置设备 / 功能关闭不产生提示验收

### P2 Location (#14)

- [ ] Android LocationProvider
- [ ] 当前位置
- [ ] ChargingRecord optional lat/lng/accuracy
- [ ] Map provider adapter
- [ ] MapLibre prototype

### P2 Trip (#15)

- [ ] manual start / stop
- [ ] foreground service
- [ ] TripSession / TripPoint
- [ ] distance / elapsed / moving / stopped
- [ ] speed / altitude / accuracy
- [ ] INTERRUPTED recovery

### P2 Data Reliability (#19 remaining)

- [ ] DataSource contract
- [ ] 关键 source / accuracy
- [ ] 极端单价 / GPS jump 等规则
- [ ] ChargingPlace
- [ ] CSV analysis export
- [ ] Privacy Zone later

---

## v0.3 - Analytics

- [ ] 月度费用 / 电量趋势
- [ ] 快慢充比例
- [ ] 月度对比
- [ ] 充电区间实际里程
- [ ] 百公里充电成本
- [ ] 估算百公里充入电量
- [ ] 行程与充电关联分析

Analytics 必须区分原始事实、派生值和估算值。

---

## v0.4 - Cloud & Catalog Sync

- [ ] Spring Boot 单体服务
- [ ] PostgreSQL
- [ ] 用户账号
- [ ] 车辆 / 充电 / 行程同步
- [ ] 车型目录更新

云同步不得成为用户数据唯一恢复方式。

---

## 当前执行顺序

```text
Bluetooth connection prompt discovery (#21)
  -> Location foundation (#14)
  -> Manual Trip Tracking (#15)
  -> Data Reliability / ChargingPlace (#19 remaining)
  -> Map Display
  -> v0.3 Analytics
```

---

## 本地 Agent 入口

开始工作前阅读：

```text
docs/PROJECT_MASTER.md
docs/ROADMAP.md
docs/LOCAL_AGENT_HANDOFF.md
```

本地 Agent 第一任务不是新增功能，而是修复 Run #64 并恢复 main CI Green。

---

## 变更记录

### v2.0.0

- odometer foundation 确认由 Android Build Run #56 验收通过
- Local Backup / Restore 已通过 Android Build Run #65 和真机验收
- Multi Vehicle 已通过 Android Build Run #66 与真机验收，#17 已关闭
- Bluetooth 仅提升“连接指定车载蓝牙后提示开始”的可行性验证；不提前实现自动开始记录或 OBD 遥测
- 新增 LOCAL_AGENT_HANDOFF.md
- 清理历史 Issue #1 / #2
- 更新 #18 / #19 为真实当前状态
- 明确本地 Agent 接手顺序与完成标准
