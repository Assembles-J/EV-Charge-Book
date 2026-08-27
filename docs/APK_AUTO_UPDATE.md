# EV Charge Book APK 自动升级设计

版本: v1.0.0
更新时间: 2026-08-27
状态: Authority Subdocument

## 1. 目标

EV Charge Book 采用独立 APK 分发，不依赖应用商店时，需要提供可控、可验证、不会破坏 Local First 的升级路径。

核心目标：

- App 自动发现新的正式 APK
- 用户一键下载并交给 Android 系统安装器完成覆盖安装
- 下载后的 APK 必须先通过 SHA-256 校验
- 普通业务提交、Debug CI、文档提交均不升级正式版本号
- 只有真正执行 Production APK Release 时才生成新的正式版本
- App 只发现已经完整上传并原子激活的 release
- 更新服务不可用时不影响本地记账、Trip、统计等核心能力

Android 普通第三方应用不能绕过系统安装确认进行真正的静默升级。本项目不要求 root / Device Owner，因此“自动升级”定义为：自动检查 + 一键下载 + 校验 + 调起系统安装器。

---

## 2. 版本策略

### 开发 / CI

`android/app/build.gradle.kts` 的默认值保持：

```text
versionCode = 1
versionName = 0.1.0-dev
```

这些默认值只用于本地开发和普通 Android Build CI。

业务提交不得为了“代码有变化”而修改正式 APK 版本。

### Production Release

正式版本只在 `.github/workflows/android-release.yml` 被人工触发时生成：

```text
VERSION_CODE = Android Release workflow run number
VERSION_NAME = <release_series>.<release workflow run number>
```

示例：

```text
release_series = 0.4
Android Release Run #12

=> versionCode = 12
=> versionName = 0.4.12
=> APK = ev-charge-book-0.4.12.apk
```

规则：

1. 只有需要发布新的 Production APK 时才触发 Android Release。
2. 普通 push / PR / Android Build 不产生正式版本。
3. Release 失败可以消耗一个 workflow run number；未原子激活的版本不对客户端可见。
4. `versionCode` 是升级判断的唯一有序依据；`versionName` 主要用于用户展示。
5. `release_series` 只在阶段需要变化时调整，例如 `0.4 -> 0.5`，不要因为每个业务 commit 修改源码版本。

---

## 3. 服务端文件布局

```text
/opt/ev-charge-book/
├─ releases/
│  └─ ev-charge-book-0.4.12.apk
├─ latest/
│  └─ ev-charge-book-latest.apk -> ../releases/ev-charge-book-0.4.12.apk
├─ release-meta/
│  ├─ 0.4.12.env
│  ├─ 0.4.12.json
│  ├─ latest.env
│  └─ latest.json
└─ release-upload/
```

`releases/*` 与每版本 metadata 均为不可变文件。

客户端自动升级只读取：

```text
https://groupim.cn/ev-charge-book/release-meta/latest.json
```

默认地址通过 `BuildConfig.UPDATE_MANIFEST_URL` 注入，必要时可在 Release 构建环境使用 `APP_UPDATE_MANIFEST_URL` 覆盖。

---

## 4. latest.json

Schema v1：

```json
{
  "schemaVersion": 1,
  "versionCode": 12,
  "versionName": "0.4.12",
  "apkPath": "../releases/ev-charge-book-0.4.12.apk",
  "sha256": "...",
  "publishedAt": "2026-08-27T03:00:00Z",
  "mandatory": false
}
```

说明：

- `versionCode`: 客户端判断是否升级
- `versionName`: UI 展示
- `apkPath`: 相对 manifest URL 指向不可变 APK
- `sha256`: 客户端下载后校验
- `publishedAt`: 排障/展示辅助
- `mandatory`: 预留强制升级能力，默认必须为 false

首版不开放后台随意修改 `mandatory=true`。强制升级需要单独评审，因为升级服务失败不能阻塞 Local First 核心功能。

---

## 5. 原子发布流程

```text
人工触发 Android Release
  -> 输入 ref + release_series
  -> Checkout 指定 ref
  -> 解析真实 commit SHA
  -> 注入正式 versionCode / versionName
  -> assembleRelease
  -> apksigner verify
  -> 生成 APK SHA-256
  -> 上传 Actions Artifact
  -> SCP *.part 到服务器
  -> 服务端再次校验 SHA-256
  -> 移动到 immutable releases/
  -> 写 <version>.env
  -> 写 <version>.json
  -> 更新 latest.env
  -> 更新 latest APK symlink
  -> 最后原子替换 latest.json
```

`latest.json` 必须最后写入。

原因：客户端只要能看到新的 `latest.json`，就必须保证对应不可变 APK、SHA 和版本 metadata 已全部存在。

失败发生在 `latest.json` 替换之前时，旧客户端仍继续看到上一个完整版本。

---

## 6. App 自动升级流程

仅 Production `release` 包自动检查；Debug/dev 包不检查。

```text
App 启动
  -> GET latest.json
  -> 网络/服务失败：静默忽略，不影响 App
  -> 校验 schemaVersion
  -> latest.versionCode <= BuildConfig.VERSION_CODE：无更新
  -> latest.versionCode > BuildConfig.VERSION_CODE：显示升级对话框
  -> 用户点击“升级”
  -> 检查“允许安装未知来源应用”权限
  -> Android DownloadManager 下载 immutable APK
  -> 本地计算 SHA-256
  -> 与 latest.json.sha256 比较
  -> 不一致：停止，不打开安装器
  -> 一致：ACTION_VIEW 调起 Android 系统安装器
  -> 用户确认覆盖安装
```

系统安装器还会校验 APK 签名。后续正式 APK 必须持续使用同一 production signing key，否则 Android 不允许覆盖安装。

---

## 7. 用户体验规则

首版：

- 每次 App 进程启动最多自动检查一次
- 无更新不打扰用户
- 检查失败不提示错误
- 有更新才弹窗
- 下载过程显示状态
- 支持“稍后”
- 不允许静默下载后直接安装
- 不因更新服务不可用阻断 App

后续如真实使用发现检查频率过高，再增加 DataStore 的 `lastUpdateCheckAt`（例如 6 小时/24 小时），首版不提前增加状态复杂度。

---

## 8. 安全边界

必须保持：

1. HTTPS update manifest。
2. APK 只使用 Release workflow 产出的 production signed APK。
3. Release workflow 先执行 `apksigner verify`。
4. 上传前/服务端激活前校验 SHA-256。
5. App 下载后再次校验 SHA-256。
6. 最终安装仍由 Android package installer 校验签名和用户确认。
7. 客户端使用 immutable APK URL，不依赖 latest symlink 进行内容身份判断。

SHA-256 用于传输完整性，production APK signing key 才是覆盖安装身份的核心信任根。

---

## 9. 回滚策略

如果新版本存在严重问题：

- 不覆盖旧 `releases/*` 文件
- 在 Production Release/服务器侧重新把 `latest.json` 指向一个安全版本时，必须注意 Android 默认不允许 `versionCode` 降级覆盖
- 已安装更高 versionCode 的用户不能通过普通安装自动降级

因此真正回滚应优先：

```text
修复代码
 -> 发布一个 versionCode 更高的 hotfix APK
 -> latest.json 指向 hotfix
```

服务端指针回退只对尚未升级的用户有意义，不应作为已升级设备的主要回滚机制。

---

## 10. 当前实现状态

已实现：

- Release 时生成正式 versionCode/versionName
- `release_series` 手动选择阶段版本
- deploy script 生成 per-version JSON + latest.json
- latest.json 最后原子激活
- App release-only update check
- DownloadManager 下载
- SHA-256 二次校验
- Android unknown-source permission 跳转
- 系统安装器覆盖安装

仍需验收：

- Android CI compile/test Green
- 下一次 Production Release 生成首个 latest.json
- 公网 URL 能正确返回 `application/json` 或可解析 JSON
- 正式旧 APK -> 新 APK 真机升级
- production signing key 覆盖安装验证
- 下载中断/服务端 404/SHA 不匹配不会影响核心业务

---

## 11. 发布操作规则

以后当业务代码完成但**不需要给用户下发 APK**时：

```text
只跑 Android Build
不要触发 Android Release
不要修改正式版本号
```

当需要给用户下发新 APK 时：

```text
确认目标 commit CI Green
 -> 手动 Run Android Release
 -> 选择 ref
 -> 选择 release_series
 -> Release 自动生成版本
 -> signed APK + metadata + latest.json 原子发布
 -> App 自动发现新 versionCode
```

这就是本项目唯一正式的 APK 版本升级入口。
