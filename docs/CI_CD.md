# EV Charge Book CI/CD 与发布设计

版本: v2.0.0
更新时间: 2026-08-31
状态: CI / Production Release authority

## 1. 文档职责

本文件只负责 Android CI / Production Release 的稳定规则。

各 GitHub Actions workflow 的具体职责、trigger 与 deployment boundary 见 `WORKFLOW_OWNERSHIP.md`。

当前 PR/head/run 状态不写死在本文件，统一以 current GitHub state / `CURRENT_STATUS_AUTHORITY.md` 为准。

核心原则：

- Android CI 与 Production Release 分离；
- 正式 APK 版本只在明确触发 Production Release 时生成；
- 普通 commit / PR / Debug CI / docs change 不生成新的正式版本；
- CI Green 不等于真机验收；
- Production workflow 成功也不自动等于 old-production -> new-production 真机覆盖升级已验收。

---

## 2. Android Build Baseline

当前稳定构建参数：

- JDK 17；
- compileSdk 36；
- targetSdk 36；
- CI Android platform 36；
- Build Tools 36.0.0；
- Gradle Wrapper 9.5.0；
- wrapper 实际路径：`android/gradlew`；
- wrapper distribution：`android/gradle/wrapper/gradle-wrapper.properties`。

普通开发/CI 默认版本来自 `android/app/build.gradle.kts`：

```text
versionCode = 1
versionName = 0.1.0-dev
```

Debug build 额外使用：

```text
applicationIdSuffix = .debug
versionNameSuffix = -debug
```

因此 Debug 与 Production APK 可并存，不应再通过修改 production applicationId 来解决调试安装冲突。

---

## 3. Android CI

Workflow：`.github/workflows/android-build.yml`

Workflow name：`Android Build`

Job/check name：`Android CI`

### Trigger

PR / `main` push 仅在以下路径变化时运行：

- `android/**`
- `.github/workflows/android-build.yml`

同时支持 manual `workflow_dispatch`。

### Current build contract

```text
checkout
 -> validate Android repository baseline
 -> enforce packaged Hero WebP size budget
 -> JDK 17
 -> Android SDK 36 / Build Tools 36.0.0
 -> Gradle cache
 -> cd android
 -> ./gradlew --no-daemon testDebugUnitTest :app:assembleDebug
 -> upload Debug APK artifact
```

Debug APK artifact retention 当前为 7 天。

### CI authority

Android CI 可以证明：

- 当前 PR head 能完成配置的自动化测试；
- Debug APK 能构建；
- workflow 内显式 guardrail 通过。

Android CI **不能证明**：

- 真机后台/锁屏行为；
- GPS/provider/OEM 差异；
- 视觉/交互验收；
- Production signing；
- production server publish；
- old-production -> new-production 覆盖升级。

---

## 4. Current-head merge evidence

Repository governance 目标由 #75 跟踪。

对于会触发 Android Build 的 runtime PR：

1. 必须确认 PR 当前 base/stack 关系；
2. 必须检查当前 changed files / effective diff；
3. required evidence 应来自当前 PR head；
4. 如果 sync/rebase/merge `main` 改变了 head/effective code，旧 Green run 不能继续当最终 merge evidence；
5. 需要重新获得当前 head 的 `Android CI` 成功；
6. stacked child 不能复用 parent 的 CI 作为自己的最终 merge evidence。

Docs-only change 如果不命中 Android workflow path filter，可以没有 Android CI；不要为了“形式统一”强制无关 Android 构建。

---

## 5. Production Release

Workflow：`.github/workflows/android-release.yml`

Workflow name：`Android Release`

Trigger：**manual `workflow_dispatch` only**。

输入：

- `ref` — 要发布的 git ref；
- `release_series` — 产品阶段版本，例如 `0.4`。

Workflow 内部生成：

```text
VERSION_CODE = github.run_number
VERSION_NAME = <release_series>.<github.run_number>
APK_FILE = ev-charge-book-<VERSION_NAME>.apk
```

Release workflow 使用 `environment: production`。

### Production build contract

```text
manual release decision
 -> checkout requested ref
 -> resolve real commit SHA
 -> validate release baseline / secrets
 -> verify current public update discovery route
 -> ensure Android SDK 36 / Build Tools 36.0.0
 -> restore production signing keystore
 -> cd android
 -> ./gradlew --no-daemon --build-cache :app:assembleRelease
 -> apksigner verify
 -> SHA-256
 -> prepare immutable/atomic upload
 -> publish release files / metadata
```

具体 server upload / atomic activation 以 current `android-release.yml` 为实现事实。

---

## 6. Version Rules

1. 普通 commit 不修改正式 `versionCode/versionName`。
2. Android Build 不生成正式 release version。
3. 只有明确触发 Android Release 时才生成正式版本。
4. `versionCode` 必须单调增加；当前使用 release workflow run number。
5. `versionName` 当前由 `release_series + run_number` 组成。
6. Release 失败允许跳号；未成功激活的版本不能被客户端当作可用最新版本。
7. `release_series` 只在产品阶段变化时调整，不随普通 feature PR 增长。

---

## 7. Production Signing

Production Release 依赖：

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

以及发布服务器相关 secret：

- `COMMON_SERVER_HOST`
- `COMMON_SERVER_USER`
- `COMMON_SSH_PRIVATE_KEY`

Production signing key 必须长期保持一致，否则旧正式 APK 无法被新版 APK 原地覆盖升级。

签名 secret 不应被写入仓库文档、Issue、PR body 或日志。

---

## 8. App Update Discovery

Production App 默认 update manifest：

```text
https://groupim.cn/ev-charge-book/release-meta/latest.json
```

BuildConfig 入口：`UPDATE_MANIFEST_URL`。

Release 环境可以通过 `APP_UPDATE_MANIFEST_URL` 覆盖。

客户端核心判断：

```text
latest.versionCode > BuildConfig.VERSION_CODE
 -> prompt
 -> DownloadManager
 -> SHA-256 verify
 -> Android system installer
```

更新服务不可用时必须 non-blocking，不能影响 Local First 核心功能。

Updater 的 runtime/physical owner 见 `APK_AUTO_UPDATE.md` 与 #102。

---

## 9. Production Release Gate

执行 Production Release 前至少确认：

- 目标 ref/commit 是明确的；
- applicable Android CI 已通过；
- production signing secrets 可用；
- public update discovery route 当前可验证；
- assembleRelease 成功；
- `apksigner verify` 成功；
- SHA-256 生成；
- server/staging 有足够权限和空间；
- atomic metadata activation 逻辑未被绕过。

如果目标 ref 在最后一次 CI 后发生改变，应重新验证 current effective commit；不要用旧 CI run 给新的 release ref 背书。

---

## 10. Production Acceptance

Production workflow success 证明“发布流程执行成功”，但 App 升级产品能力还需要真实设备验收：

- old production APK 发现 newer production；
- 下载 / restart recovery；
- SHA-256；
- unknown-source permission；
- same-signing-key in-place install；
- 用户数据保留；
- service failure non-blocking。

该验收由 #102 跟踪。

---

## 11. Other Repository Workflows

Hero / catalog / admin workflows 不属于 Android Production Release。

完整职责见 `WORKFLOW_OWNERSHIP.md`：

- `hero-admin.yml` — integrated resource-admin validation；
- `hero-admin-deploy.yml` — production admin deployment；
- `hero-assets-publish.yml` — Hero package validation；
- `admin-batch-image-upload.yml` — batch image browser-contract validation；
- `admin-prompt-library.yml` — prompt/copy-center validation；
- `vehicle-catalog-admin-tools.yml` — catalog admin tools validation。

不要因为多个 workflow 命中相同目录就自动判断它们重复；先看它们的 responsibility / deployment authority。

---

## 12. Repository Governance Relationship

- #75 — `main` protection + required current-head applicable CI；
- #265 — stale branch cleanup + merge-time branch deletion；
- `BRANCH_AND_PR_GOVERNANCE.md` — PR/branch/stack rules；
- `WORKFLOW_OWNERSHIP.md` — workflow responsibility；
- `CURRENT_STATUS_AUTHORITY.md` — 当前 runtime/PR/CI snapshot。

仓库保护未真正启用前，不得因为文档写了“必须 CI”就假装 GitHub 已经强制执行。
