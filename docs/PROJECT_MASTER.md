# EV Charge Book 项目总纲（PROJECT MASTER）

版本: v1.8.0
更新时间: 2026-08-26
状态: Authority Document / Single Source of Truth

## 1. 项目定位

EV Charge Book 是新能源车主的 Local First 车辆数据中心。

演进顺序：

1. 充电记账与成本
2. 多车辆与车型目录
3. 位置 / 驾驶行程数据
4. 充电与行程的数据闭环
5. 数据分析
6. 云同步与 AI

首个验证车型仍为零跑 C16，但产品、数据库和 UI 不绑定单一品牌。

---

## 2. 权威文档体系

- PRODUCT.md
- FEATURE_MATRIX.md
- UIUX.md
- FRONTEND.md
- BACKEND.md
- DATABASE.md
- CI_CD.md
- ROADMAP.md
- DEVELOPMENT.md
- LOCATION_TRIP.md
- VEHICLE_CATALOG_MULTI_VEHICLE.md
- DATA_QUALITY_BACKUP.md
- LOCAL_AGENT_HANDOFF.md
- NEXT_PHASE_DESIGN.md

实现与文档冲突时，先以当前代码与 CI 事实确认状态，再修正文档。

---

## 3. 产品原则

- 简单可维护
- Local First
- 真实数据来源
- 用户录入成本低
- 统计口径可解释
- 原始事实 / 派生值 / 估算值必须区分
- 不伪造实时 SOC / SOH / 续航
- 定位记录与地图 SDK 解耦
- 车型目录与用户车辆分离
- 外部供应商必须可替换
- Local First 必须具备可恢复路径

---

## 4. v0.1 状态: Released / Accepted

v0.1 已完成正式发布和验收：充电记录 CRUD、车辆编辑、Dashboard/Records/Stats、核心规则测试、Android CI、真机 CRUD、signed production APK 与原子发布。

---

## 5. v0.2 状态: Core Code Complete / Device Acceptance Parallel

v0.2 已不再处于主要功能开发阶段。

### 已完成核心

- odometer foundation + Room migration
- Local Backup / Restore + 真机恢复验收
- Multi Vehicle + 车型目录
- Bluetooth 指定设备连接提示 -> Trip 用户确认
- Android LocationManager 当前位置
- WGS84 lat/lng/accuracy
- AddressResolver + Android Geocoder
- TripSession / TripPoint
- foreground location service
- Trip start/stop/recovery/detail
- distance / elapsed / moving / stopped
- speed / bearing / GPS altitude / accuracy
- GPS bad-point / jump filtering
- stationary point throttling
- TripRouteGeometry + 无底图真实轨迹预览
- ChargingIntervalAnalytics
- odometer / Trip coverage evidence
- SOC estimate confidence hints

### 仍需实体设备验收

- 指定车载蓝牙连接事件
- 开始行程不闪退
- foreground 锁屏持续定位
- INTERRUPTED 恢复
- 实际驾驶距离 / 速度 / 海拔合理性
- 当前定位 / Geocoder 在目标设备上的表现

这些验收由用户实机并行完成，不阻塞本地 analytics 代码继续推进。

MapLibre 是可选展示层，不再作为 v0.3 前置条件。

---

## 6. 当前阶段: v0.3 Analytics

当前已进入本地数据分析阶段。

已实现：

- 充电区间里程差
- 费用 / 100km 账本估算
- 补入电量 / 100km 账本估算
- Trip 覆盖率辅助证据
- SOC 差异可信度提示
- 区间明细
- 最近 6 个月费用 / 电量趋势
- 家充 / 公共慢充 / 公共快充 / 其他 分类
- charger type 次数 / 电量 / 费用占比

下一优先级：

1. 最新累计 Android CI 恢复 Green
2. month-over-month 对比
3. charger type 成本 / 电量对比
4. 稀疏数据与估算文案完善
5. 根据真实使用决定是否增加筛选和更复杂图表

暂不引入重型 chart framework。

---

## 7. 数据可信度原则

允许来源包括 MANUAL / GPS / OCR / CATALOG / VEHICLE_API / OBD / DERIVED。

不为所有字段制造统一 confidence 分数。优先保存原始值、来源、可获得 accuracy 和计算口径。

AI 后续只能在可解释数据基础上做总结和建议。

---

## 8. 隐私与恢复

- 默认本地保存
- 持续定位记录必须可见
- 云同步轨迹需要明确同意
- 分享路线之前实现 Privacy Zone
- Local Backup / Restore 是云同步之前的正式恢复路径
- 恢复失败不得破坏当前数据

---

## 9. Android / 发布基线

- JDK 17
- Android SDK 36
- Build Tools 36.0.0
- repository Gradle Wrapper
- CI 与 Production Release 分离
- signed APK
- Actions Artifact
- server `.part` + SHA/apksigner + atomic activation

---

## 10. 架构约束

保持：

```text
Compose -> MainViewModel -> ChargingRepository -> Room DAO -> Room
```

不要为当前规模引入 Hilt/Koin、多 module Clean Architecture 或无明确收益的抽象。

每轮开发结束必须：

- Gradle test/build
- GitHub CI Green
- 更新对应 Issue
- 阶段变化时更新 ROADMAP / PROJECT_MASTER
- 不把“代码写完”等同于“真机验收完成”

---

## 11. 决策记录

### v1.8.0

- v0.2 core code 标记完成，实体设备验收并行继续
- MapLibre 降为非阻塞可视化工作
- Charging interval / Trip coverage / SOC confidence 已进入稳定数据闭环
- 正式切换当前主线到 v0.3 Analytics
- 6 个月趋势与 charger type analytics 已实现

### v1.7.0

- v0.1 Released / Accepted
- odometer、Backup/Restore、Multi Vehicle 完成基础验收
