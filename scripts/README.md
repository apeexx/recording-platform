# scripts

本目录用于后续放置本地开发、数据处理、部署或运维辅助脚本。

## start-dev.ps1

`start-dev.ps1` 用于在 Windows PowerShell 中一键启动录音任务平台的本地开发服务。

推荐在仓库根目录运行：

```powershell
.\scripts\start-dev.cmd
```

也可以直接运行 PowerShell 脚本：

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-dev.ps1
```

查看帮助：

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-dev.ps1 -Help
```

脚本启动前会先执行两项脱敏前置检查：

- 从当前进程环境或根目录 `.env` 读取 `MONGODB_URI`，默认检查 `localhost:27017` 的 TCP 可达性。
- 从同样位置读取 `RECORDING_STORAGE_DIR`，相对值按仓库根目录解析，并使用临时探针文件检查录音目录是否可写；后端录音、导入和就绪检查使用相同的仓库根目录语义。

检查失败时脚本在结束旧 Web/后端进程和启动新窗口之前退出。脚本不打印 MongoDB URI、用户名或密码，不安装/启动/停止 MongoDB，也不会结束 `27017` 端口上的任何进程。TCP 检查只能证明端点可达，账号权限和数据库命令仍由后端就绪接口校验。

前置通过后，脚本会检查固定开发端口：

- `8080`：Spring Boot 后端。
- `5173`：Vite Web 前端。

如果端口已被占用，脚本会直接结束监听该端口的进程，并打印端口、PID 和进程名，然后继续启动服务。请在运行前确认这两个端口上没有需要保留的其他程序。

脚本会打开两个可见的 `pwsh` 窗口，分别展示后端和前端实时日志：

```powershell
backend\mvnw.cmd spring-boot:run
npm run dev -- --host localhost --port 5173
```

两个窗口标题分别为 `Recording Backend` 和 `Recording Frontend`。如果命令退出，窗口会提示按 Enter 关闭，便于查看错误信息。

启动成功后可访问：

```text
http://localhost:5173/admin/voice-generation/workbench
```

脚本只启动后端和前端，不创建 `.env`，也不会写入或打印任何 API Key。当前后端身份、会话和语音生成记录依赖 MongoDB；真实语音生成联调还需要在根目录 `.env` 中填写 `MINIMAX_API_KEY`。

脚本不再创建或写入根目录 `logs/`；实时输出直接显示在两个 `pwsh` 窗口中。

## 生产一键部署

`deploy-production.sh` 用于 Ubuntu 生产服务器从 `origin/main` 更新、验证、构建并
启动录音任务平台。脚本应在仓库根目录运行：

```bash
./scripts/deploy-production.sh
```

首次使用前需要确认：

- 当前分支为 `main`，Git 工作区干净。
- 根目录 `.env` 已由运维人员配置，权限不允许组用户或其他用户读取（建议
  `chmod 600 .env`）。
- `.env` 已配置 MongoDB、录音/头像/语音生成存储、正式微信 AppID/AppSecret、
  HTTPS Cookie、关闭路径迁移以及外部集成 Key 摘要；首管理员完成首次改密后
  可以删除 `INITIAL_ADMIN_USERNAME`、`INITIAL_ADMIN_PASSWORD`。
- 服务器已安装 Java 17、NVM 与 Node.js 22、npm、PM2、Git 和 curl。
- `/var/log/recording-platform` 已存在且当前部署用户可写。
- MongoDB 和生产存储目录已经由运维人员单独配置并验证。

脚本先执行干净工作区与生产配置脱敏门禁，再使用：

```text
git fetch origin main
git pull --ff-only origin main
```

更新完成后会重新执行仓库中的最新脚本版本，并依次完成：

1. Web `npm ci`、Node/Vitest 测试和 Vite 生产构建。
2. 后端 Maven 完整测试与 Spring Boot JAR 打包。
3. 微信小程序依赖安装和 Node 自动测试。
4. 使用 `scripts/pm2-production.config.cjs` 首次启动或重启单实例后端。
5. 最长等待 60 秒，确认 `/api/health/ready` 的 `overall`、`mongo`、`storage`
   全部为 `UP`，再执行 `pm2 save`。

npm 默认使用 `https://registry.npmmirror.com/`；Maven Wrapper 和依赖默认使用
`https://maven.aliyun.com/repository/public`。必要时可在运行前通过
`NPM_REGISTRY` 或 `MVNW_REPOURL` 覆盖，但不得把带凭证的私有仓库地址写入仓库。

任何 Git、依赖安装、测试、构建或产物检查失败都会在操作 PM2 前终止。就绪检查
失败时脚本返回非零状态并输出目标应用的有限尾部日志；当前脚本不执行自动回滚，
后续生产更新前仍需按运维流程备份旧产物、MongoDB 和媒体文件。

常用脱敏诊断命令：

```bash
pm2 status
pm2 logs recording-platform-backend --lines 100 --nostream
curl --fail --silent http://127.0.0.1:8080/api/health/ready
```

脚本不安装或修改 MongoDB，不创建或修改 `.env`，不配置 Nginx、UFW、云安全组
或 Certbot，也不负责备份、回滚、微信合法域名、上传、提审和发布。任何密码、
AppSecret、原始集成 API Key 和完整 MongoDB URI 都不得粘贴到日志、文档或 Git。

## 本地数据重置

`reset-local-data.cmd recording_platform` 只用于已获确认的本地开发数据清空。命令行必须传入区分大小写的精确确认词；同时要求根目录 `.env` 具有 `INITIAL_ADMIN_USERNAME`、`INITIAL_ADMIN_PASSWORD`，并拒绝非 `recording_platform` 数据库。重置进程会临时关闭 Spring Data 自动索引创建，避免旧库中的冲突数据在清库 Runner 执行前阻断启动；普通服务启动仍保持自动创建索引。Java 端会再次校验确认词、真实库名和受限运行存储路径，完整删除本地开发数据库（因此会移除旧 `users` 以及当前 `web_users`、`miniprogram_users` 等所有身份集合）并清理录音、语音生成和采集员头像目录，再重建首个 `WEB-...` 管理员后自动退出。该命令不可恢复且不会生成备份，普通开发启动不要设置任何 `RECORDING_LOCAL_RESET_*` 变量。
