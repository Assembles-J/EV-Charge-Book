# EV Charge Book CI/CD 与发布设计

版本: v1.4.1
更新时间: 2026-08-27
状态: Authority Subdocument

## 1. 目标

遵循 Assembles-J 组织发布思路：CI 与 Production Release 分离、signed APK、Actions Artifact、production Environment、服务器不可变 release 与原子激活。

新增原则：**正式 APK 版本只在真正执行 Production Release 时生成。普通业务提交、PR、Debug CI 和文档提交不升级正式版本。**

APK 自动升级详细设计见 `APK_AUTO_UPDATE.md`。

---

## 2. Android Build Baseline

当前统一构建参数：

- JDK 17
- compile/target SDK 36
- CI Android platform 36
- Build Tools 36.0.0
- Gradle Wrapper 9.5.0

CI 与 Release 必须使用仓库 `./gradlew`。

开发/普通 CI 使用 `build.gradle.kts` 默认 dev 版本：

```text
versionCode = 1
versionName = 0.1.0-dev
```

这些值不是正式发布版本，不应随业务 commit 修改。

Debug build 使用独立 application ID suffix，可与 release APK 并存安装，避免签名/包名冲突。

---

## 3. CI 流程

```text
push / pull request Android change
 -> Checkout
 -> Validate build + wrapper
 -> JDK 17
 -> Android SDK 36 / Build Tools 36
 -> Gradle cache
 -> ./gradlew testDebugUnitTest :app:assembleDebug
 -> Debug APK Artifact
```

Android Build 只验证代码，不产生正式版本号，不更新线上 `latest.json`。

文档-only PR 可以通过 workflow path 规则不触发 Android Build；不能为了“所有 PR 都有绿勾”而浪费 Android 构建资源。

---

## 4. Main Branch Merge Guard

当前仓库实际状态：`main` 尚未启用 branch protection，也没有 required status checks。

这意味着当前流程仍依赖维护者主动等待 CI 后再合并；技术上 GitHub 不会阻止未验证代码进入 `main`。

Issue #75 跟踪最小修复：

- protect `main`
- 普通代码变更通过 PR 进入
- Android 代码 PR 在 workflow 适用时必须要求当前 head Android Build 通过
- checked head 应包含最新 `main`，避免旧基线绿 CI 被合并
- docs-only PR 保持轻量，不强制无关 Android CI
- 不引入强制多人 review、复杂 merge queue 或 CODEOWNERS bureaucracy

在 #75 完成前，人工合并规则：

> Code PR 只在最新 head 已包含当前 main 且对应 Android Build Green 后合并。

---

## 5. Production Release

Production Release 继续保持 `workflow_dispatch` 手动门禁。

输入：

- `ref`: 要发布的 commit / branch / tag
- `release_series`: 产品阶段版本，例如 `0.4` / `0.5`

Release workflow 内部才生成：

```text
VERSION_CODE = github.run_number
VERSION_NAME = <release_series>.<github.run_number>
APK_FILE = ev-charge-book-<VERSION_NAME>.apk
```

示例：Android Release Run #12 + release_series `0.4` => `0.4.12`。

正式流程：

```text
人工确认需要下发 APK
 -> Run Android Release
 -> Checkout ref
 -> resolve real commit SHA
 -> inject production version
 -> restore production signing key
 -> assembleRelease
 -> apksigner verify
 -> SHA-256
 -> Actions Artifact
 -> SCP *.part
 -> server SHA verification
 -> immutable releases/<version>.apk
 -> per-version env/json metadata
 -> update latest.env
 -> update latest APK symlink
 -> atomic replace release-meta/latest.json LAST
```

只有 `latest.json` 最终替换成功后，App 才能发现新版本。

---

## 6. APK 自动升级发现

生产 App 默认读取：

```text
https://groupim.cn/ev-charge-book/release-meta/latest.json
```

该地址通过 `BuildConfig.UPDATE_MANIFEST_URL` 注入；Release 环境可用 `APP_UPDATE_MANIFEST_URL` 覆盖。

App 仅在 `release` build 检查更新：

```text
latest.versionCode > BuildConfig.VERSION_CODE
 -> 提示用户升级
 -> DownloadManager 下载 immutable APK
 -> SHA-256 校验
 -> Android 系统安装器确认覆盖安装
```

更新检查失败必须静默降级，不影响 Local First 核心功能。

---

## 7. 版本规则

1. 普通 commit 不修改正式 `versionName/versionCode`。
2. Android Build 不发布正式版本。
3. 只有明确需要给用户下发新 APK 时才触发 Android Release。
4. `versionCode` 是 Android 升级顺序依据，必须单调增加；Release workflow run number 满足该要求。
5. `versionName` 用于用户展示，由 `release_series + release run number` 组成。
6. Release 失败允许跳号；未成功原子激活的版本不对客户端可见。
7. product series 只在产品阶段变化时修改，例如 `0.4 -> 0.5`。

---

## 8. Secrets

Production Environment / organization secrets：

- ANDROID_KEYSTORE_BASE64
- ANDROID_KEYSTORE_PASSWORD
- ANDROID_KEY_ALIAS
- ANDROID_KEY_PASSWORD
- COMMON_SERVER_HOST
- COMMON_SERVER_USER
- COMMON_SSH_PRIVATE_KEY

生产 signing key 必须长期保持一致，否则 Android 不允许旧版 App 被新版 APK 覆盖安装。

---

## 9. 发布门禁

Production Release 前至少满足：

- 目标 commit 的 Android CI Green
- Debug APK 可用
- release signing secrets 可用
- assembleRelease Green
- apksigner verify Green
- server directory 可写

升级功能额外验收：

- `release-meta/latest.json` 公网可访问
- JSON 指向 immutable APK
- SHA-256 与服务器 APK 一致
- 旧正式 APK 能发现更高 versionCode
- 下载后校验成功才进入安装器
- 签名一致，可完成覆盖安装

v0.5 正式下发前还需要产品 owning issues 的真实设备验收，不因为 Debug CI Green 自动触发 Production Release。

---

## 10. 历史故障

2026-08-26 Android Build Run #41 曾因 workflow 请求不存在的 `platforms;android-37` 在 SDK 安装阶段失败。现已统一到 SDK 36；该故障与业务代码无关。

---

## 11. 变更记录

### v1.4.1

- recorded current `main` branch protection / required-check gap
- added Issue #75 as the minimal repository-safety owner
- documented the manual pre-merge rule until GitHub protection is enabled
- clarified docs-only PRs may intentionally skip Android CI
- recorded debug/release package coexistence baseline

### v1.4.0

- 正式版本改为仅在 Production Release 生成
- Release 增加 `release_series`
- server publish `latest.json` update manifest
- latest.json 作为客户端最后原子发现指针
- 增加 App release-only 自动检查 / 下载 / SHA 校验 / 系统安装流程
- 新增 `APK_AUTO_UPDATE.md`

### v1.3.0

- Gradle Wrapper 已实际提交
- SDK 统一到项目 SDK 36
- CI / Release 统一使用仓库 wrapper
