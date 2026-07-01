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

脚本启动前会检查固定开发端口：

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

脚本只启动后端和前端，不启动数据库，不创建 `.env`，也不会写入或打印任何 API Key。当前语音生成测试阶段不需要数据库；真实联调只需要在根目录 `.env` 中填写真实 `MINIMAX_API_KEY`。

脚本不再创建或写入根目录 `logs/`；实时输出直接显示在两个 `pwsh` 窗口中。
