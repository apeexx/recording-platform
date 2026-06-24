# 录音任务平台

录音任务平台用于管理录音任务的创建、领取、录制、上传和审核流程。当前仓库处于项目初始化阶段，只包含空项目脚手架和根目录协作文档，不实现业务功能。

## 项目定位

本项目包含管理员 Web 端、审核 Web 端、微信小程序录音端和 Java Spring Boot 后端。后续业务将围绕录音任务发布、任务领取锁定、录音上传、机器审核、一审、二审、驳回重录和任务完成展开。

## 根目录结构

```text
recording-platform/
├─ README.md
├─ AGENTS.md
├─ log.md
├─ .gitignore
├─ apps/
│  ├─ web/
│  └─ miniprogram/
├─ backend/
└─ scripts/
```

## 技术栈

- Web 前端：Vue + Vite，目录为 `apps/web`
- 微信小程序端：目录为 `apps/miniprogram`
- 后端：Java 17 + Spring Boot + Maven，目录为 `backend`
- 数据库：PostgreSQL 为项目默认关系型数据库，不再由仓库内 Docker Compose 提供本地服务

## 模块用途

- `apps/web`：管理员 Web 端和审核 Web 端的前端工程，当前为 Vite Vue 空项目。
- `apps/miniprogram`：微信小程序录音端，当前仅保留占位说明。
- `backend`：Spring Boot 后端服务，当前只生成基础启动项目，不提供业务接口。
- `scripts`：后续放置本地开发、数据处理或运维辅助脚本。
- `AGENTS.md`：Codex 长期执行规则，同时记录接口和数据库说明入口。
- `log.md`：AI 辅助修改日志。

## 当前阶段

当前已实现项目根目录、空项目脚手架、Web 端主题基础、管理员端前端导航壳和语音生成 Web 生产台。除语音生成模块外，仍不实现任务管理、用户登录、录音上传、审核流、权限控制、接口请求或数据模型。

仓库内不再维护 Docker Compose 配置。后续如需本地 PostgreSQL，请在本机或外部环境自行提供，并在具体开发任务中说明连接方式。

## Web 端主题基础

Web 端已建立基础主题变量，变量文件位于 `apps/web/src/styles/theme.css`，并由 `apps/web/src/style.css` 作为全局样式入口引入。

当前仅完成浅色优先的主题基础和 `.dark` 深色变量预留，不实现主题切换按钮、登录、任务、审核、上传、接口请求、路由或状态管理。

后续管理员端和审核端页面应优先使用主题变量：

- 主色：`--primary`
- 背景：`--background`
- 文字：`--foreground`
- 卡片：`--card`
- 边框：`--border`
- 圆角：`--radius`

## Web 管理端导航壳

Web 管理端已建立 Vue Router 导航壳，根路径 `/` 和 `/admin` 默认进入 `/admin/dashboard`。管理员端当前包含固定侧边栏、顶部栏、主内容区、工作台占位卡片和各模块占位页面。

侧边栏菜单统一配置在 `apps/web/src/config/adminSidebar.js`，管理员端路由统一位于 `apps/web/src/router/`。当前只是前端导航和布局框架，不调用接口，不实现登录、任务、审核、录音上传或权限控制。

“语音生成”模块位于 `apps/web/src/pages/admin/voice-generation/`，当前已接入后端真实接口，支持 0 元试听、付费克隆、日常合成、声音配置和生成记录。

布局参考浅色后台管理系统的结构形式，包括左侧菜单、顶部栏和卡片式内容区；颜色、字体、圆角、边框和选中态必须遵循本项目主题变量，不复制外部模板颜色或源码。

## 语音生成 Web 生产台

语音生成模块参考 `XiangTianzhen/tuanji` 本地项目中的 `app.py` Web 可视化生产台能力迁移，不包含桌面 RPA、VB-Cable、快捷键或微信小程序自动提交。

本模块由 Vue 前端调用 Spring Boot 后端，后端读取 MiniMax API Key 并直连 MiniMax，不使用 mock。前端不保存、不展示、不提交 API Key。

本地联调前，在根目录复制 `.env.example` 为 `.env`，填写：

```text
MINIMAX_API_KEY=
MINIMAX_API_BASE_URL=https://api.minimaxi.com
VOICE_GENERATION_STORAGE_DIR=backend/storage/voice-generation
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/recording_platform
SPRING_DATASOURCE_USERNAME=recording_platform
SPRING_DATASOURCE_PASSWORD=
```

其中 `MINIMAX_API_KEY` 和 `SPRING_DATASOURCE_PASSWORD` 需要按你的本地环境自行填写，不能提交到 Git。

如果已经填写 `MINIMAX_API_KEY` 但 MiniMax 返回 `2049 invalid api key`，优先检查 `MINIMAX_API_BASE_URL` 是否和账号所在开放平台一致。国内开放平台账号使用 `https://api.minimaxi.com`，国际开放平台账号可改为 `https://api.minimax.io`。

生成记录保存到 PostgreSQL 表 `voice_generation_records`，默认声音配置保存到 `voice_generation_configs`。生成音频文件保存到 `VOICE_GENERATION_STORAGE_DIR` 指定的本地目录，音频二进制不写入数据库。

如果合成时返回 `数据库未连接，无法保存语音生成记录`，说明后端无法连接 `SPRING_DATASOURCE_URL` 指定的 PostgreSQL。请先启动本机或外部 PostgreSQL，再重新执行合成。后端会在调用 MiniMax 合成前先确认生成记录可写入，避免数据库不可用时继续产生远程合成调用。

前端开发服务器已将 `/api` 代理到 `http://127.0.0.1:8080`。真实联调时需要同时启动 PostgreSQL、后端和 Web 前端。

本机已安装 PostgreSQL 并将 `psql` 加入 PATH 后，可运行建库脚本：

```powershell
.\scripts\create-postgres-db.ps1
```

后端启动时会通过 Flyway 自动创建语音生成所需表结构。

Windows PowerShell 本地联调可使用一键启动脚本：

```powershell
.\scripts\start-dev.cmd
```

脚本会在启动前检查并结束占用 `8080` 和 `5173` 端口的进程，然后打开两个可见的 `pwsh` 窗口分别运行 Spring Boot 后端和 Vite 前端，实时日志直接显示在窗口中。脚本不启动 PostgreSQL，不创建 `.env`，不再创建或写入根目录 `logs/`；语音生成真实联调仍需要开发者自行准备 PostgreSQL 和已填写的根目录 `.env`。

## 本地验证

Web 端：

```bash
cd apps/web
npm install
npm run build
```

后端：

```bash
cd backend
./mvnw test
```

Windows PowerShell 可使用：

```powershell
cd backend
.\mvnw.cmd test
```
