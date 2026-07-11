# 录音任务平台

录音任务平台用于管理录音任务的创建、领取、录制、上传和审核流程。当前仓库处于基础设施阶段，已具备管理员/审核员后台身份、录音人员微信登录边界、MongoDB 会话与语音生成持久化；任务领取、录音上传和审核状态机仍待后续阶段实现。

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
- 数据库：MongoDB，默认数据库为 `recording_platform`

## 模块用途

- `apps/web`：管理员 Web 端和审核 Web 端的前端工程，当前为 Vite Vue 空项目。
- `apps/miniprogram`：微信小程序录音端，当前仅保留占位说明。
- `backend`：Spring Boot 后端服务，提供身份、会话、后台用户管理、微信登录边界和语音生成接口。
- `scripts`：后续放置本地开发、数据处理或运维辅助脚本。
- `AGENTS.md`：Codex 长期执行规则，同时记录接口和数据库说明入口。
- `log.md`：AI 辅助修改日志。

## 当前阶段

当前已实现项目根目录、Web 端主题基础、管理员端前端导航壳、后端身份/会话基础和语音生成 Web 生产台。任务管理、录音上传、审核流和小程序业务页面仍未实现。

仓库内不维护 Docker Compose 配置。后端运行需要开发者在本机或外部环境提供 MongoDB；默认连接为 `mongodb://localhost:27017/recording_platform`，真实账号密码只能放在本地环境变量或未提交的 `.env` 中。

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
MONGODB_URI=mongodb://localhost:27017/recording_platform
RECORDING_STORAGE_DIR=backend/storage/recordings
WECHAT_APP_ID=
WECHAT_APP_SECRET=
INITIAL_ADMIN_USERNAME=
INITIAL_ADMIN_PASSWORD=
WEB_SESSION_IDLE_HOURS=12
MINIPROGRAM_SESSION_DAYS=30
WEB_SESSION_COOKIE_SECURE=false
```

其中 `MINIMAX_API_KEY` 需要按你的本地环境自行填写，不能提交到 Git。

如果已经填写 `MINIMAX_API_KEY` 但 MiniMax 返回 `2049 invalid api key`，优先检查 `MINIMAX_API_BASE_URL` 是否和账号所在开放平台一致。国内开放平台账号使用 `https://api.minimaxi.com`，国际开放平台账号可改为 `https://api.minimax.io`。

语音生成记录和默认声音配置分别持久化到 MongoDB 的 `voice_generation_records`、`voice_generation_configs` 集合；生成音频文件仍保存到 `VOICE_GENERATION_STORAGE_DIR` 指定的本地目录。默认生成目录 `backend/storage/voice-generation/` 是本地运行产物，已加入 Git 忽略，不应提交到仓库。MiniMax 合成失败时，记录会更新为 `FAILED`。

付费克隆模式只上传母带音频并填写新音色 ID，不配置或提交语速、音量、语调；这些参数仅用于 0 元试听和日常合成。MiniMax 音色克隆要求母带音频为 mp3、m4a 或 wav，时长 10 秒到 5 分钟，文件不超过 20MB；新音色 ID 需要以英文字母开头，只包含字母、数字、下划线或连字符，长度为 8 到 256 个字符，且不能以下划线或连字符结尾。后端全局 multipart 上限为单文件 100MB、完整请求 105MB，克隆接口仍在业务层执行 20MB 限制；超过限制时返回统一 HTTP 413 错误。

前端开发服务器已将 `/api` 代理到 `http://127.0.0.1:8080`。真实联调时只需要同时启动后端和 Web 前端，并确保根目录 `.env` 已填写 MiniMax 配置。

Windows PowerShell 本地联调可使用一键启动脚本：

```powershell
.\scripts\start-dev.cmd
```

脚本会在启动前检查并结束占用 `8080` 和 `5173` 端口的进程，然后打开两个可见的 `pwsh` 窗口分别运行 Spring Boot 后端和 Vite 前端，实时日志直接显示在窗口中。脚本不启动 MongoDB，不创建 `.env`，不再创建或写入根目录 `logs/`；启动前需要自行确保 MongoDB 可用，语音生成真实联调还需要已填写 MiniMax 配置的根目录 `.env`。

## 身份、会话与接口边界

- 固定角色为 `ADMIN`、`REVIEWER`、`COLLECTOR`。后台账号使用 BCrypt 密码；所有新编码密码至少 8 个字符且 UTF-8 不超过 72 字节。首管理员仅在数据库没有 `ADMIN` 且同时配置 `INITIAL_ADMIN_USERNAME`、`INITIAL_ADMIN_PASSWORD` 时创建；首次登录必须改密。
- Web 登录使用 HttpOnly、SameSite=Lax Cookie `REC_WEB_SESSION`。服务端只保存不透明令牌的 SHA-256 哈希，默认空闲 12 小时；生产 HTTPS 可设置 `WEB_SESSION_COOKIE_SECURE=true`。
- 已登录 Web 用户先调用 `GET /api/auth/web/csrf` 获取可读的 `XSRF-TOKEN` Cookie；执行退出、改密或管理员写操作等非安全方法时，同时通过 `X-XSRF-TOKEN` 请求头回传该值。首次登录待改密账号也允许获取 CSRF token。
- 同一后台账号只允许一个活动 Web 会话。重复登录返回 `409 ACCOUNT_IN_USE` 和短时一次性 `takeoverToken`；确认接管后旧会话返回 `401 SESSION_REPLACED`。
- 小程序只向后端提交 `wx.login` 临时 `code`。后端使用 `WECHAT_APP_ID`、`WECHAT_APP_SECRET` 调用微信 `jscode2session`，不接受客户端直接提交 OpenID；小程序 Bearer token 默认 30 天。
- `/api/voice-generation/**` 与 `/api/admin/**` 仅 `ADMIN` 可访问；除 Web/微信登录与接管接口外，其余 `/api/**` 默认要求认证。
- 所有响应回传 `X-Request-Id`；统一错误结构为 `{ code, message, requestId, details? }`，未预期异常不返回内部堆栈、数据库消息或敏感 payload。
- 缺字段、未知字段、类型错误和 malformed JSON 等请求结构问题返回 400，不支持的 `Content-Type` 返回 415；字段结构有效但新密码少于 8 个字符或 UTF-8 超过 72 字节、非法姓名或非法后台角色等业务值问题返回 422。

当前身份接口：

```text
POST /api/auth/web/login
POST /api/auth/web/takeover
GET  /api/auth/web/me
GET  /api/auth/web/csrf
POST /api/auth/web/logout
PUT  /api/auth/web/password
POST /api/auth/miniprogram/login
PUT  /api/auth/miniprogram/name
POST /api/admin/users
GET  /api/admin/users?page=0&size=20
POST /api/admin/users/{userId}/disable
```

MongoDB 当前集合：

- `users`：后台用户名、内部用户编号和测试期 `(wechatAppId, wechatOpenId)` 唯一身份。
- `sessions`：仅保存令牌哈希，包含状态、最后访问时间和 TTL 到期时间。
- `voice_generation_records`：语音生成记录。
- `voice_generation_configs`：默认声音配置。

测试默认使用 mock/fake store，不依赖开发机 MongoDB；需要真实 Mongo 集成测试时单独提供 `MONGODB_TEST_URI`，不得复用生产库。

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
