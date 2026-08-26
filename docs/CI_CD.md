# EV Charge Book CI/CD 与发布设计

版本: v1.3.0
更新时间: 2026-08-26
状态: Authority Subdocument

## 1. 目标

遵循 Assembles-J 组织发布思路：CI 与 Production Release 分离、signed APK、Actions Artifact、production Environment、服务器不可变 release 与原子激活。

---

## 2. Android Build Baseline

当前已具备:

- android/build.gradle.kts
- android/settings.gradle.kts
- android/app/build.gradle.kts
- AndroidManifest
- `android/gradlew` / `gradlew.bat`
- `android/gradle/wrapper/**`

当前统一构建参数:

- JDK 17
- compile/target SDK 36
- CI 安装 Android platform 36
- Build Tools 36.0.0
- Gradle Wrapper 9.5.0

CI 与 Release 必须优先使用仓库的 `./gradlew`，避免本地、CI 两套 Gradle 来源。

---

## 3. 2026-08-26 CI 故障复盘

Android Build run #41 在 `Install Android SDK packages` 失败，业务代码尚未进入编译阶段。

根因:

```text
project compileSdk = 36
workflow requested platforms;android-37
sdkmanager: Failed to find package 'platforms;android-37'
```

已修复:

- android-build.yml -> platform 36
- android-release.yml -> platform 36
- CI / Release -> `./gradlew`
- wrapper 文件加入 baseline validation

因此在新的 Action 成功前，不得声称 Debug CI Green。

---

## 4. CI 流程

```text
Checkout
 -> Validate build + wrapper
 -> JDK 17
 -> Android SDK 36 / Build Tools 36
 -> Gradle cache
 -> ./gradlew testDebugUnitTest :app:assembleDebug
 -> Debug APK Artifact
```

---

## 5. Production Release

当前仍 `workflow_dispatch` 手动门禁:

```text
Checkout ref
 -> validate signing + wrapper
 -> SDK 36
 -> restore keystore
 -> ./gradlew :app:assembleRelease
 -> apksigner verify
 -> SHA-256
 -> Actions Artifact
 -> SCP *.part
 -> atomic activate under /opt/ev-charge-book/releases
 -> update latest
```

---

## 6. 版本 / Secrets

- VERSION_CODE = github.run_number
- VERSION_NAME = 0.1.<run_number>（v0.1）

Secrets:

- ANDROID_KEYSTORE_BASE64
- ANDROID_KEYSTORE_PASSWORD
- ANDROID_KEY_ALIAS
- ANDROID_KEY_PASSWORD
- SERVER_IP
- SERVER_USER
- SERVER_SSH_KEY

---

## 7. 自动 Production Release 门禁

必须满足:

- Debug CI Green
- Debug APK 可下载
- assembleRelease Green
- production signing secrets 验证
- server directory permission 验证
- 首次 signed APK 原子发布完成

再启用 main Android 变更自动 production release。

---

## 8. 变更记录

### v1.3.0

- Gradle Wrapper 已实际提交，恢复为 CI/Release 标准入口
- SDK 从 stale 37 配置统一到项目 SDK 36
- 记录 run #41 失败根因，明确失败发生在 SDK 安装而非业务编译

### v1.2.0

- Android build baseline 和 production workflow 落地
