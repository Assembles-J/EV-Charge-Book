# CI/CD Design

Version: v1.0.0

## Goal

自动完成 Android 构建、测试、APK产物生成。

## Pipeline

Code Push

-> GitHub Actions

-> Gradle Build

-> Unit Test

-> Assemble Debug APK

-> Upload Artifact

## Future

- Release APK
- GitHub Release
- Server deployment
