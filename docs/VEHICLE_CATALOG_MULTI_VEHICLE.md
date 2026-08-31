# EV Charge Book Vehicle Catalog & Multi-Vehicle Design

版本: v2.0.0
更新时间: 2026-08-31
状态: Authority Subdocument

## 1. 核心原则

Vehicle 模块采用 **后台车型主数据 + Android Offline First 缓存 + 用户车辆实例** 三层模型。

必须遵守：

1. **后台决定“这是什么车”**：支持品牌、车系、年款、配置、标准规格、标准续航、品牌 Logo、车型 Hero 全部由 Web Admin / managed catalog 维护。
2. **用户只决定“这是我的哪辆车”**：用户车辆只能编辑昵称等个人属性，不能修改标准车型事实。
3. **Android 不拥有支持车型名单**：新增品牌或车型不得要求修改 Kotlin 白名单、`when (brand)`、字符串匹配或重新发布 APK。
4. **App 永远以本地缓存运行**：Room 是 Android 运行时车型目录来源；网络仅用于后台刷新，刷新失败继续使用 last-known-good 数据。

---

## 2. 数据分层

### VehicleBrand

后台维护的品牌主数据：

- `brandId`（稳定 ID）
- `displayName`
- `englishName?`
- `logoKey?`
- `isActive`
- `sourceUpdatedAtEpochMillis`

品牌 Logo 作为版本化远程资产管理；Android 只消费 `logoKey` / manifest，不硬编码品牌 drawable 映射。

### VehicleCatalog

后台维护、Android 只读的标准车型数据：

- `catalogId`（稳定 ID）
- `brandId`
- `series`
- `modelName`
- `modelYear`
- `trimName`
- `powertrainType` (`BEV` / `PHEV` / `REEV`)
- `batteryCapacityKwh?`
- `rangeKm?`
- `rangeStandard?` (`CLTC` / `WLTC` / etc.)
- 其他标准规格字段
- `heroArtworkKey?`
- `isActive`
- `sourceUpdatedAtEpochMillis`

这些字段属于车型客观事实。Android 车辆详情可以展示，但不能提供用户编辑入口。

### UserVehicle

用户自己的车辆实例（现有 `VehicleEntity`）：

- `id`
- `catalogVehicleId`
- `nickname?`
- 标准车型不可编辑快照（仅用于离线/历史兼容）
- `isDefault`
- `isArchived`
- `createdAtEpochMillis`
- sync metadata

`nickname` 是主要用户可编辑文本。

展示名称规则：

```text
nickname 非空 -> nickname
nickname 为空 -> catalog/model compact display name
```

车型目录后续修正不能静默重写既有用户历史；保留快照是兼容策略，不代表这些字段对用户可编辑。

---

## 3. Web Admin 是唯一支持车型管理中心

Web 端负责：

```text
品牌管理
├─ 新增 / 编辑品牌
├─ Logo 上传 / 更新
├─ 上架 / 下架
│
车型管理
├─ 新增车系 / 年款 / 配置
├─ 标准电池 / 续航 / 续航标准
├─ 标准规格
├─ Hero 关联 / 发布
└─ 上架 / 下架
```

发布要求：

- 稳定 ID 不因文案修正改变；
- `catalogVersion` 单调递增；
- 元数据原子写入；
- 无效/重复数据拒绝发布；
- 下架使用 `isActive=false`，不物理删除；
- 已添加到用户本地的车辆和历史记录不因目录下架被删除。

新增第 100 个品牌/车型时，Android 代码改动应为 **0**。

---

## 4. Android Offline First 目录

运行时数据链路：

```text
APK bundled seed
      ↓ first run
Room vehicle catalog
      ↓
UI always reads local Room
      ↑
best-effort background refresh
      ↑
managed server catalog
```

### 刷新规则

远程目录只有在以下条件全部通过后才能写入 Room：

- HTTPS 请求成功；
- schemaVersion 支持；
- 文档非空；
- ID 合法且无重复；
- 必填文本完整；
- 枚举值合法；
- 数值范围合法。

任一失败：

```text
remote failure
     ↓
DO NOT clear Room
     ↓
continue last-known-good catalog
```

禁止为了“拿最新车型”让 Dashboard、记录、行程、统计或车辆切换依赖在线服务。

---

## 5. 车辆新增 UX

正式流程：

```text
选择/搜索品牌或车型
 -> 选择车系
 -> 年款/配置
 -> 查看标准参数（只读）
 -> 确认添加
 -> 可选设置车辆昵称
```

标准参数不能修改。

不再把“自定义填写品牌、车型、电池、续航”作为正常支持车型的兜底方案。缺失车型应由 Web Admin 补录并通过远程目录发布。

如未来确实需要实验性自定义车辆，必须明确标记为非标准车型，并与 managed catalog 数据隔离；不得污染标准车型统计和规格事实。

---

## 6. 编辑车辆 UX

编辑入口只编辑用户车辆属性，例如：

```text
车辆名称
[ 小黑 ]
```

车辆详情中的标准信息：

```text
零跑 C16 2026款
580 智享版
纯电
74.9 kWh
CLTC 580 km
```

全部为只读展示，不出现编辑按钮或文本输入框。

---

## 7. 多车辆切换

切换器的主视觉：

```text
[品牌 Logo]  nickname / compact model name
```

例如：

```text
[零跑 Logo] 小黑
[小米 Logo] SU7
[BYD Logo] 通勤车
```

切换器不展示电池容量、续航等次要标准规格。

切换后 Dashboard、Records、Stats 默认只显示当前车辆数据。进行中的 Trip 仍固定绑定开始时的 `vehicleId`，不能因切换当前车辆而改变归属。

---

## 8. 品牌 Logo 与车型 Hero

品牌 Logo 与车型 Hero 都是 managed assets，不属于 Android 支持车型代码。

### Brand Logo

```text
brandId
  -> logoKey
  -> asset manifest
  -> disk/memory cache
  -> remote latest
```

### Vehicle Hero

```text
catalogId
  -> heroArtworkKey
  -> Hero manifest
  -> disk/memory cache
  -> remote latest
```

资源加载失败不得影响车型本身可用性；优先继续使用缓存，首次离线且无资源时使用通用占位。

禁止：

- `when (brand)` 选择 Logo/Hero；
- Kotlin 中维护支持车型列表；
- 通过 `brand.contains("小米")` 等字符串规则猜车型图片；
- 新增车型时要求发布新版 APK 才能显示。

---

## 9. 删除与归档

用户车辆默认采用 `isArchived`：

- 不再出现在默认切换器；
- 历史充电记录和行程继续保留；
- 不因远程目录下架自动归档用户车辆。

车型目录采用 `isActive`：

- `false` 后不再提供给新用户选择；
- 已存在的 UserVehicle 继续可用；
- 历史数据保持可读。

---

## 10. 验收目标

- [ ] Web Admin 可维护品牌与稳定 `brandId`
- [ ] Web Admin 可上传/更新品牌 Logo
- [x] Web Admin 可新增/编辑/上下架车型
- [x] Web Admin 发布后 `catalogVersion` 递增
- [x] Android 本地 Room Catalog + best-effort remote refresh
- [x] 远程失败保持 last-known-good Catalog
- [ ] Android 不再存在支持品牌/车型白名单
- [ ] Android 不再通过品牌/车型字符串猜 Hero
- [ ] 标准车型参数全部只读
- [ ] UserVehicle 支持 `nickname`
- [ ] 编辑车辆只允许编辑 nickname
- [ ] 新增车辆只能确认 managed catalog 标准信息
- [ ] 车辆切换器显示 `Logo + nickname/fallback`
- [ ] 新增后台品牌/车型无需 APK 更新即可在 App 出现
- [ ] 归档/下架不破坏历史记录

---

## 11. 变更记录

### v2.0.0

- 将 Web Admin 定义为支持品牌/车型唯一管理中心；
- 标准车型事实改为 App 只读；
- UserVehicle 只允许 nickname 等用户属性可编辑；
- 明确 Android 不允许维护支持车型白名单；
- 品牌 Logo / Hero 改为动态 managed assets；
- 保留 Room Offline First + last-known-good 缓存模型；
- 废弃“用户确认后修改标准车型参数”的旧产品规则。
