# Changelog

## [2.1.0] - 2026-11-15

基于 Fork `888021/OneSSH-Native`（上游 `jinhuaitao/OneSSH-Native` main `6d8be58`，27 提交，v2.0.0）。

### 新增
- 🔀 多跳 ProxyJump：跳板机链最多 5 级，按链路顺序逐级转发；每一级先试 `direct-tcpip`，不通再试 `nc` / `ncat` / `socat` 命令隧道；任一级失败会明确报出是哪一级。`Host.jumpId` 单跳保持向后兼容，新增 `jumpIds`（JSON 数组）存多跳链。
- 🧪 本地单元测试：`AnsiParserTest`、`TerminalBufferTest`、`SshSessionTest`，`./gradlew testDebugUnitTest` 可跑，不需要真机或 SSH 服务器。

### 改进
- 📱 `targetSdk` 34 → 35（`compileSdk` 仍 36，`minSdk` 26 不变）。
- 🏗️ Release 开 R8（`isMinifyEnabled=true` + `isShrinkResources=true`，`proguard-rules.pro` 已保留 JSch / org.json / 应用类）；Lint 在 Release 构建时检查但不阻断（`abortOnError=false`）。
- 🔑 Release 签名改为环境变量配置：CI Secrets `ONESSH_KEYSTORE_BASE64` / `ONESSH_KEYSTORE_PASSWORD` / `ONESSH_KEY_ALIAS` / `ONESSH_KEY_PASSWORD` 配好即出签名包，未配则仍按 unsigned 构建，不让构建失败。

### 修复
- 📄 README 许可证一节与仓库实际不一致：仓库已有 GPL-3.0 `LICENSE`，README 却写“尚未指定许可证”，已改为 GPL-3.0，并补 `app/libs/README-JSch.md` 说明 JSch 2.28.8 的 BSD 许可来源。
- 🧾 `.gitattributes` 首行中文说明没加 `#`，Git 会报 `is not a valid attribute name`，已改为注释。

## [2.0.0] - 2026-11-15（上游）

- 纯原生 Android SSH 客户端：自绘终端、单跳 ProxyJump、SFTP、Keystore AES-256-GCM 加密落盘。
- 版本 `2` / `2.0.0`，`minSdk 26 / targetSdk 34 / compileSdk 36`，唯一第三方依赖 JSch 2.28.8。
