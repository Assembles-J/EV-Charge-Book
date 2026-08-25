# EV Charge Book CI/CD 与发布设计

版本: v1.1.0
更新时间: 2026-08-25
状态: Authority Subdocument

## 1. 目标

EV Charge Book 的构建、签名、发布和服务器分发逻辑遵循 Assembles-J 组织现有项目的统一发布思路，优先参考 Third-Hand 的 production deployment 模式：

- CI 与 Production Release 分离
- Release 使用 GitHub Actions `production` Environment
- Android Release APK 必须签名
- 版本号由 GitHub Actions run number 驱动
- APK 同时保留 GitHub Actions Artifact
- 服务器使用 `/opt/<project>/releases` 保存不可变版本文件
- 上传使用 `.part` 临时文件，校验后原子切换
- 发布后保留稳定的 latest 下载入口
- 支持 `workflow_dispatch` 手动指定 ref / 强制发布

## 2. 当前阶段

当前 Android 工程仍处于 v0.1 Skeleton 阶段，尚未具备完整 Gradle Wrapper 和 Release signing build 配置。

因此发布策略分两阶段：

### Stage A - Release Pipeline Prepared

当前执行：

- 保留 Android CI 定义
- 建立 signed APK release workflow
- 建立服务器原子部署脚本
- Release workflow 仅允许手动触发
- 不允许未完成构建基线时因 main push 自动产生错误发布

### Stage B - Automatic Production Release

满足以下条件后开启 main 自动发布：

- `android/gradlew` 存在
- `android/gradle/wrapper/**` 完整
- `android/app/build.gradle.kts` 完整
- `assembleDebug` 通过
- `assembleRelease` 通过
- Android signing secrets 已配置
- 服务器 `/opt/ev-charge-book/releases` 可写

## 3. CI 流程

Pull Request / Android source change

```text
Checkout
  -> JDK 17
  -> Android SDK
  -> Gradle cache
  -> Unit Test
  -> assembleDebug
  -> Upload Debug APK Artifact
```

CI 不负责正式发布。

## 4. Production Release 流程

```text
workflow_dispatch / future main Android change
  -> Checkout selected ref
  -> Resolve VERSION_CODE / VERSION_NAME
  -> Restore signing keystore
  -> assembleRelease
  -> apksigner verify
  -> SHA-256
  -> Upload Actions Artifact
  -> SCP as *.part
  -> Remote verify
  -> Atomic mv into releases/
  -> Update latest symlink
  -> Write release metadata
```

## 5. 版本规则

MVP 期间：

- `VERSION_CODE = github.run_number`
- `VERSION_NAME = 0.1.<github.run_number>`
- APK: `ev-charge-book-0.1.<run_number>.apk`

后续进入 v0.2 / v0.3 时，只调整产品 minor 基线，不重置 `VERSION_CODE`。

## 6. GitHub Environment 与 Secrets

Environment:

- `production`

沿用组织项目已有命名：

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `SERVER_IP`
- `SERVER_USER`
- `SERVER_SSH_KEY`

原则：不在仓库中保存 keystore、密码或 SSH 私钥。

## 7. 服务器目录

正式目录：

```text
/opt/ev-charge-book/
  releases/
    ev-charge-book-0.1.12.apk
    ev-charge-book-0.1.13.apk
  latest/
    ev-charge-book-latest.apk -> ../releases/ev-charge-book-0.1.13.apk
  release-meta/
    latest.env
```

`releases/` 中版本文件发布后视为不可变产物。

## 8. 原子发布

上传文件必须先使用：

```text
<apk>.part
```

服务器完成非空检查和 SHA-256 后：

```text
mv <apk>.part releases/<apk>
ln -sfn ../releases/<apk> latest/ev-charge-book-latest.apk
```

避免客户端在上传过程中下载到半个 APK。

## 9. Artifact 保留

正式 APK 同时上传 GitHub Actions Artifact：

- 名称：`ev-charge-book-<version>`
- retention：14 days

服务器 release 是长期分发源；Actions Artifact 用于构建审计和短期下载。

## 10. 发布失败策略

任何以下情况必须终止发布：

- signing secret 缺失
- APK 不存在或为空
- `apksigner verify` 失败
- SCP 失败
- 远端目录不可写
- 原子激活脚本失败

失败时不得改变 `latest` 指针。

## 11. 与组织发布逻辑的关系

EV Charge Book 不复制 Third-Hand 的后端部署和业务健康检查，因为 v0.1 没有服务端业务。

但保持相同的发布骨架：

- production Environment
- 相同 Secret 命名
- GitHub run-number 版本策略
- signed APK
- Actions Artifact
- `/opt/<project>/releases`
- `.part` + atomic activation
- SSH/SCP 到统一服务器

新增后端后，再扩展为“后端变更检测 + Android 可选发布”的同类 scope detection 模式。

## 12. 变更记录

### v1.1.0 - 2026-08-25

- 对齐 Assembles-J / Third-Hand 发布逻辑
- 明确 signed APK 和 production Environment
- 定义服务器 release 目录、latest 指针与原子上传
- 因 Android Skeleton 尚不可构建，Release 暂以 workflow_dispatch 门禁启用
