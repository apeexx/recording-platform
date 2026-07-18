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

## 本地数据重置

`reset-local-data.cmd recording_platform` 只用于已获确认的本地开发数据清空。命令行必须传入区分大小写的精确确认词；同时要求根目录 `.env` 具有 `INITIAL_ADMIN_USERNAME`、`INITIAL_ADMIN_PASSWORD`，并拒绝非 `recording_platform` 数据库。Java 端会再次校验确认词、真实库名和受限运行存储路径，清库、清理媒体并重建首管理员后自动退出。该命令不可恢复且不会生成备份，普通开发启动不要设置任何 `RECORDING_LOCAL_RESET_*` 变量。
