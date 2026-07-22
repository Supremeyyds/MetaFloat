# 安全策略

## 支持范围

安全修复优先应用于最新正式版本。旧版本用户应先升级到最新 GitHub Release。

## 报告安全问题

请优先使用 GitHub 仓库的 Private Vulnerability Reporting 或 Security Advisory 私下报告安全问题。

不要在公开 Issue、截图或日志中包含：

- mihomo API secret
- 真实控制器公网地址或内网拓扑
- Release keystore、密码或 Base64 内容
- GitHub Token 或 Actions Secrets

报告中请提供受影响版本、复现步骤、影响范围和建议修复方式。普通功能问题可以使用公开 Issue。

## 安全边界

MetaFloat 支持连接本地和局域网 mihomo 控制器，并为兼容常见部署允许明文 HTTP。用户负责保护控制器端口、使用强 secret，并避免将未加密控制接口直接暴露到公网。

Zashboard 不打包在 APK 内，仅在用户请求时从配置的下载源获取。自定义镜像内容由用户自行信任和管理。
