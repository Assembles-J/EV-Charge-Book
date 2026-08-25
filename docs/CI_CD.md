# EV Charge Book CI/CD 与发布设计

版本: v1.2.0
更新时间: 2026-08-25
状态: Authority Subdocument

## 1. 目标

EV Charge Book 的构建、签名、发布和服务器分发逻辑遵循 Assembles-J 组织现有项目的统一发布思路，主要参考 Third-Hand production deployment：

- CI 与 Production Release 分离
- Release 使用 GitHub Actions `production` Environment
- Android Release APK 必须签名
- 版本号由 GitHub Actions run number 驱动
- APK 同时保留 GitHub Actions Artifact
- `/opt/<project>/releases` 保存不可变版本文件
- `.part` 临时上传 + 原子激活
- 稳定 latest 下载入口
- `workflow_dispatch` 手动生产发布门禁

---

## 2. Android Build Baseline

当前已具备:

- `android/build.gradle.kts`
- `android/settings.gradle.kts`
- `android/app/build.gradle.kts`
- AndroidManifest
- Room/KSP/Compose 依赖声明
- production signing 环境变量接口

CI 固定使用:

- JDK 17
- Android API 37
- Build Tools 36.0.0
- Gradle 9.5.0
- AGP 9.3.1

Gradle Wrapper 是推荐的本地一致性工具，但不再阻塞第一次远程 CI 验证。CI 由 `gradle/actions/setup-gradle` 安装固定 Gradle 版本，避免“缺 Wrapper 就静默跳过构建”。

---

## 3. CI 流程

PR / main Android 变更:

```text
Checkout
 -> Validate build files
 -> JDK 17
 -> Android SDK 37
 -> Gradle 9.5.0
 -> testDebugUnitTest
 -> assembleDebug
 -> Debug APK Artifact
```

CI 必须真实执行构建；禁止因为工程缺少非必要辅助文件而直接报告成功。

---

## 4. Production Release 流程

当前保持手动触发:

```text
workflow_dispatch
 -> Checkout selected ref
 -> VERSION_CODE / VERSION_NAME
 -> Restore production keystore
 -> Gradle 9.5.0 assembleRelease
 -> apksigner verify
 -> SHA-256
 -> Actions Artifact
 -> SCP *.part
 -> Remote verify
 -> Atomic mv into releases/
 -> Update latest symlink
 -> Write release metadata
```

完成 Debug CI Green 和首次 signed release 验收后，再评估 main Android 变更自动 production release。

---

## 5. 版本规则

MVP:

- `VERSION_CODE = github.run_number`
- `VERSION_NAME = 0.1.<github.run_number>`
- APK: `ev-charge-book-0.1.<run_number>.apk`

VERSION_CODE 不随产品 minor 版本重置。

---

## 6. production Environment / Secrets

Environment: `production`

Secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `SERVER_IP`
- `SERVER_USER`
- `SERVER_SSH_KEY`

不得提交 keystore、密码或 SSH 私钥。

---

## 7. Android Signing Contract

`app/build.gradle.kts` 读取:

- `ANDROID_KEYSTORE_FILE`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `APP_VERSION_CODE`
- `APP_VERSION_NAME`

Production workflow 负责恢复 keystore 并注入这些环境变量。

本地没有 production signing 环境变量时允许生成开发 release 包用于编译验证，但正式生产验收必须由 production workflow 完成并执行 `apksigner verify`。

---

## 8. 服务器目录与原子发布

```text
/opt/ev-charge-book/
  releases/
  latest/
  release-meta/
  release-upload/
```

上传使用 `<apk>.part`，校验完成后移动到不可变 `releases/`，最后才更新 `latest/ev-charge-book-latest.apk`。

失败时不得改变 latest。

---

## 9. Artifact

Debug:

- `ev-charge-book-debug-<run_number>`
- 7 days

Release:

- `ev-charge-book-<version>`
- 14 days

服务器 release 为长期分发源；Actions Artifact 用于构建审计和短期下载。

---

## 10. 自动 Production Release 门禁

开启 main 自动 production release 前必须满足:

- Debug CI Green
- Debug APK Artifact 可下载
- `assembleRelease` Green
- production signing Secrets 已验证
- `/opt/ev-charge-book` 可写
- 首次 signed APK 原子发布完成

Gradle Wrapper 不再作为 Production Release 的硬门禁，但仍建议后续由本地 Android Studio 生成并提交。

---

## 11. 与组织逻辑的关系

v0.1 没有后端，因此不复制 Third-Hand 的 Docker/backend 健康检查。

保持统一骨架:

- production Environment
- Secret 命名
- run-number version
- signed APK
- Actions Artifact
- `/opt/<project>/releases`
- `.part` atomic activation
- SSH/SCP

v0.3 引入后端后，再增加 backend/android scope detection。

---

## 12. 变更记录

### v1.2.0

- Android Gradle build baseline 已落地
- CI 固定 Gradle 9.5.0 并开始真实 build/test
- Gradle Wrapper 从硬门禁调整为推荐项
- production release 同步改用固定 Gradle 9.5.0
- 保持 production 手动发布门禁直到首次签名验收

### v1.1.0

- 对齐 Assembles-J / Third-Hand 发布逻辑
- 建立 signed APK、production Environment 和服务器原子发布规范
