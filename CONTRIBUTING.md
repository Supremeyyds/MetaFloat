# 参与贡献

感谢你帮助改进 MetaFloat。

## 开发环境

- JDK 17
- Android SDK 35
- Android Studio 或 Gradle Wrapper

首次提交前请运行：

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleRelease
```

Windows 请使用对应的 `.\gradlew.bat` 命令。

涉及加密存储或 Android 系统行为时，还应在连接的设备或模拟器上执行：

```bash
./gradlew connectedDebugAndroidTest
```

## 代码要求

- 遵循项目 `.editorconfig` 和 Kotlin 官方代码风格。
- 不引入 Alpha、Experimental API 或无必要的构建插件。
- 不提交构建产物、本地 SDK 路径、keystore、密码或真实控制器配置。
- 修复问题时优先采用小范围、可回滚、保持现有行为的改动。
- 改动连接、URL、下载、解压、重连或位置计算逻辑时，应补充对应测试。

## 提交 Pull Request

- 清楚说明问题、改动行为和验证结果。
- UI 改动建议附截图，但必须隐藏主机、端口、secret 等私人信息。
- 确保 GitHub Actions 检查全部通过。
- 贡献代码将按 GPL-3.0 许可证发布。
