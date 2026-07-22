# MetaFloat

MetaFloat 是一款用于连接现有 mihomo 控制器的 Android 应用，可以通过悬浮窗查看实时流量，并在应用内打开 Zashboard 面板。

## 主要功能

- 连接 mihomo 控制器。
- 显示实时上传、下载和累计流量。
- 支持可拖动、可展开的悬浮窗。

## 系统要求

- Android 8.0（API 26）及以上。
- 使用悬浮窗功能时需要授予“显示在其他应用上层”权限。

## 使用方法

1. 在首页填写 mihomo 控制器地址、端口和密码。
2. 点击“连接”。
3. 连接成功后，可开启悬浮窗或打开 Zashboard。

## 本地构建

本项目使用 Gradle Wrapper 构建，建议使用 JDK 17，并准备好 Android SDK 及 API 35 构建工具。

Windows PowerShell：

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

macOS 或 Linux：

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleRelease
```

Debug APK 位于 `app/build/outputs/apk/debug/`，Release APK 位于 `app/build/outputs/apk/release/`。

## 许可证

MetaFloat 源代码采用 [GNU General Public License v3.0](LICENSE) 发布。第三方组件分别遵循各自许可证。
