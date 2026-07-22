# MetaFloat

MetaFloat 是一款用于连接现有 [mihomo](https://github.com/MetaCubeX/mihomo) 控制器的 Android 应用，可以通过悬浮窗查看实时流量，并在应用内打开 Zashboard 面板。

## 主要功能

- 连接 mihomo 控制器。
- 显示实时上传、下载和累计流量。
- 支持可拖动、可展开的悬浮窗。
- 支持打开和重新下载 Zashboard。
- 支持多种下载源及自定义镜像。
- 支持浅色、深色和跟随系统主题。

## 系统要求

- Android 8.0（API 26）及以上。
- 已运行并允许访问的 mihomo 控制器。
- 使用悬浮窗功能时需要授予“显示在其他应用上层”权限。

## 使用方法

1. 在首页填写 mihomo 控制器地址、端口和密码。
2. 点击“连接”。
3. 连接成功后，可开启悬浮窗或打开 Zashboard。
4. 外观、下载源和重新下载入口位于设置页。

## 本地构建

需要 JDK 17 和 Android SDK 35，也可以直接使用 Android Studio 自带的 JBR。

Windows：

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

macOS / Linux：

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleRelease
```

## 安装正式版

从 GitHub Releases 下载 `MetaFloat-v1.0.0.apk` 和对应的 `.sha256` 文件，校验后安装。

此前安装过 Debug 或测试签名版本时，需要先卸载旧版再安装 v1.0.0。自 v1.0.0 起，后续正式版本将使用同一发布密钥，可正常覆盖升级。

## 参与开发

构建、测试和提交要求见 [CONTRIBUTING.md](CONTRIBUTING.md)，版本变化见 [CHANGELOG.md](CHANGELOG.md)。安全问题请参考 [SECURITY.md](SECURITY.md)。

## 许可证

MetaFloat 源代码采用 [GNU General Public License v3.0](LICENSE) 发布。第三方组件分别遵循各自许可证。
