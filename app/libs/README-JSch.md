# JSch 2.28.8

- 文件：`jsch-2.28.8.jar`（随仓库分发，构建时不需要联网下载）
- 来源：http://www.jcraft.com/jsch/ ，上游维护分支 https://github.com/mwiede/jsch
- 用途：本项目唯一的第三方依赖，提供 SSH2 实现（密码 / 公钥 / `keyboard-interactive` 认证、PTY shell、SFTP、`direct-tcpip` 转发）。
- 许可：BSD 风格许可（JCraft 原始许可）。本仓库不修改该 jar；如需查看完整许可文本，请见上游 `LICENSE.txt`。
- 注意：不要把该 jar 换成 Maven 坐标而不改 `app/build.gradle.kts` —— 当前构建是 `implementation(files("libs/jsch-2.28.8.jar"))`，离线也能编。
