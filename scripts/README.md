# scripts

本目录用于后续放置本地开发、数据处理、部署或运维辅助脚本。

## start-dev.ps1

`start-dev.ps1` 用于在 Windows PowerShell 中一键启动录音任务平台的本地开发服务。

推荐在仓库根目录运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-dev.ps1
```

查看帮助：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-dev.ps1 -Help
```

脚本启动前会检查固定开发端口：

- `8080`：Spring Boot 后端。
- `5173`：Vite Web 前端。

如果端口已被占用，脚本会直接结束监听该端口的进程，并打印端口、PID 和进程名，然后继续启动服务。请在运行前确认这两个端口上没有需要保留的其他程序。

脚本实际启动命令：

```powershell
backend\mvnw.cmd spring-boot:run
npm run dev -- --host localhost --port 5173
```

启动成功后可访问：

```text
http://localhost:5173/admin/voice-generation/workbench
```

脚本只启动后端和前端，不启动 MongoDB，不创建 `.env`，也不会写入或打印任何 API Key。语音生成真实联调仍需要本机或外部 MongoDB 可用，并在根目录 `.env` 中填写真实 `MINIMAX_API_KEY`。

启动日志写入根目录 `logs/`，该目录不应提交到 Git。
