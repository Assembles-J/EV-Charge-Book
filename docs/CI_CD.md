# EV Charge Book CI/CD

版本: v1.2.0
更新时间: 2026-08-25
状态: Authority Subdocument

## 1. 当前原则

v0.1 只解决一件事：**每个 PR 和 main 都能稳定构建出 Debug APK。**

当前不建立服务器 APK 分发、自动生产部署、后端部署或复杂 release pipeline。产品闭环尚未完成前，不从 Third-Hand 复制生产发布复杂度。

## 2. 当前 CI

```text
Pull Request / main push
  -> checkout
  -> JDK 17
  -> Android SDK
  -> Gradle build
  -> assembleDebug
  -> Upload Debug APK Artifact
```

项目调用组织公共 workflow：

```text
Assembles-J/.github/.github/workflows/android-build.yml@main
```

当前仓库还没有 Gradle Wrapper，因此 bootstrap 阶段由公共 workflow 安装固定 Gradle 8.10.2。Android 工程稳定后应生成并提交 wrapper，再把 `use-wrapper` 切回 `true`。

## 3. Debug Artifact

Artifact 名称：

```text
ev-charge-book-debug-apk
```

目标产物：

```text
android/app/build/outputs/apk/debug/*.apk
```

验收标准：PR CI 成功且 Artifact 可下载、可安装。

## 4. Release 什么时候做

只有同时满足以下条件才进入正式 Release：

- 充电记录 CRUD 已完成
- Room 数据可持续保存
- Dashboard 使用真实本地数据
- Debug APK 已稳定通过 CI
- 实机完成一轮日常使用验证

到那时再增加：

- Gradle Wrapper
- Android signing
- signed Release APK
- GitHub Release

服务器分发不是默认要求；只有出现真实的自建下载/更新需求时才增加。

## 5. Secrets

v0.1 Debug 构建不需要任何 Secret。

未来如果需要统一服务器部署，只使用组织共享 SSH Secret：

```text
COMMON_SERVER_HOST
COMMON_SERVER_USER
COMMON_SSH_PRIVATE_KEY
```

Android keystore 等项目专属凭据继续放在本仓库 `production` Environment，不提升为 Organization Secret。

## 6. 不做的事情

v0.1 明确不做：

- Docker 部署
- PostgreSQL / Redis CI
- 服务器 release 目录
- 自动 APK latest 指针
- AI API secrets
- 为未来假设建立复杂发布框架

## 7. 变更记录

### v1.2.0 - 2026-08-25

- 删除 v0.1 阶段过早的生产服务器发布设计
- 接入 Assembles-J reusable Android workflow
- 明确 bootstrap 阶段可无 wrapper 构建
- 明确未来统一使用 `COMMON_*` SSH Secret
