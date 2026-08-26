# EV Charge Book Data Quality / Backup Design

版本: v1.0.0
更新时间: 2026-08-26
状态: Authority Subdocument

## 1. 目标

建立长期可解释、可恢复的数据基础，避免未来 OCR、GPS、车型目录、OBD、车辆 API 和 AI 接入后出现“数据来源不明、精度不明、无法恢复”的问题。

原则:

1. 事实数据与推导数据分开。
2. 关键数据尽量记录来源。
3. Local First 必须提供可恢复路径。
4. 异常优先由确定性规则发现，AI 只负责解释和建议。
5. 不为 v0.1 增加不必要字段；按 Roadmap 进入对应版本时实施。

---

## 2. DataSource

后续关键数据可标记来源:

- MANUAL
- GPS
- OCR
- CATALOG
- VEHICLE_API
- OBD
- DERIVED

示例:

- ChargingRecord.energyKwh: OCR / MANUAL
- ChargingRecord.location: MANUAL / GPS / reverse geocode
- TripPoint.speed: GPS
- Vehicle.batteryCapacityKwh: CATALOG 后由用户确认

涉及精度的数据应保存 accuracy，而不是给所有字段设计统一虚假 confidence 分数。

---

## 3. 数据真实性规则

UI/统计必须区分:

- 原始事实: 用户录入、账单、GPS、设备读取
- 计算结果: pricePerKwh、distance、costPer100Km
- 估算: SOC × battery capacity 等

不得把估算结果包装成车机实时事实。

---

## 4. Rule-based Validation

优先实现简单规则:

- SOC 0..100，endSoc >= startSoc
- energyKwh > 0
- cost >= 0
- odometerKm 不应低于该车辆上一条可靠里程记录
- 极端 pricePerKwh 提示检查，而不是禁止保存
- GPS 异常跳点不参与里程聚合
- 轨迹速度异常值过滤

规则异常默认允许用户确认后继续保存，避免因边界场景阻塞真实记录。

---

## 5. ChargingPlace

在 location 字符串形成稳定复用需求后，引入轻量地点实体:

- id
- name
- type: HOME / WORK / PUBLIC / HIGHWAY / OTHER
- latitude?
- longitude?
- note?

用途:

- 快速复用地点
- 家充 / 公司 / 公共充电分类统计
- 后续常用电价建议

v0.1 不要求创建 ChargingPlace 表。

---

## 6. Local Backup / Restore

云同步之前优先提供本地备份。

备份必须覆盖:

- vehicles
- charging records
- trips / trip points（存在时）
- charging places（存在时）
- app settings / selected vehicle
- schemaVersion
- exportedAt
- appVersion

建议格式:

```text
ev-charge-book-backup-YYYY-MM-DD.zip
  manifest.json
  data.json 或 database export
```

原则:

- Restore 前必须校验 schemaVersion
- Restore 不得静默覆盖现有数据
- 第一阶段可以采用“空数据库恢复”降低冲突复杂度
- CSV 属于分析/交换格式，不作为完整恢复格式

---

## 7. Privacy

轨迹属于敏感数据。

后续导出/分享支持 Privacy Zone:

- 用户定义 HOME 等隐私区域
- 分享轨迹时裁剪区域内起终点
- 本地原始记录可保留

Privacy Zone 不阻塞 v0.2 第一阶段行程记录。

---

## 8. AI Contract

未来 AI 读取数据时应能够知道:

- source
- accuracy（有则提供）
- derived / raw
- 数据时间范围

AI 输出必须说明哪些是事实、哪些是推算、哪些是建议。

---

## 9. 变更记录

### v1.0.0

- 建立 DataSource 与事实/推导分层
- 建立规则异常检测原则
- 定义 ChargingPlace 演进
- 将 Local Backup / Restore 提前到云同步之前
- 预留轨迹 Privacy Zone
