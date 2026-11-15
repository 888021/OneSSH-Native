# Changelog

## [2.2.0] - 2026-11-15

P1 全做（2.1.0 详细分析后 jin 选了「P1 全做」）：

### 新增
- 🔀 跳板机链编辑器：主机表单里按顺序列出每一跳，点一跳可上移 / 下移 / 移除，底部可追加，最多 5 跳；纯函数 `data/JumpChain.java`（解析 / 追加 / 移动 / 剪掉已删主机）+ `JumpChainTest`（7 个测试）。
- 💾 备份 / 导入：设置 → 数据 → 导出 `onessh-backup.json` / 导入并整体替换，导入前弹窗确认；纯函数 `data/Backup.java`（带格式标记，兼容直接拿原 `onessh.json` 当备份）+ `BackupTest`（4 个测试）。密码 / 私钥仍是本机 Keystore 密文，换设备后要重填。
- 🔁 自动重连 + 前台服务：意外断开自动重试最多 3 次（3s / 6s / 12s 退避，`ssh/ReconnectPolicy.java` + 测试）；连上后跑 `SshForegroundService`（`dataSync` 类型 + 低优先级通知），降低切后台后被回收的概率。会话仍归终端页所有，页面销毁后会话照常关闭。
- 🔒 应用锁：设置 → 安全 → 开关，打开 App 时用系统锁屏（密码 / 图案 / 指纹）验证一次；没设锁屏直接放行，验证失败或取消直接退出；进程内只验证一次。

### 改进
- 🧪 CI 先跑测试：`build.yml` 在编译 APK 之前跑 `./gradlew testDebugUnitTest`，失败直接停住，并上传测试报告（30 天）。
- 🧪 测试 23 → 36 个：新增 `JumpChainTest`（7）、`BackupTest`（4）、`ReconnectPolicyTest`（2），加上原有的 AnsiParser / TerminalBuffer / SshSession 测试。

### 限制（说清楚）
- 前台服务只保进程优先级，不替终端页保会话；杀进程、页面销毁后会话还是会断。
- 应用锁只拦 App 入口（MainActivity），不重新加密已存数据；Keystore 密钥本身没改。
- 备份换设备后密码 / 私钥要重填，见 `data/Backup.java` 的说明。

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
