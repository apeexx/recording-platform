# 录音任务平台

录音任务平台用于管理录音任务的创建、领取、录制、上传和审核流程。当前仓库已具备管理员/审核员 Web 管理闭环、MongoDB 任务与审核后端闭环，以及原生微信小程序的登录、实名、授权申请、任务领取、录音/文字提交、释放、自动下一条和个人统计页面。

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

- `apps/web`：管理员 Web 端和审核 Web 端，已实现真实后台身份流程、角色路由和语音生成生产台。
- `apps/miniprogram`：原生微信小程序录音采集端。
- `backend`：Spring Boot 后端服务，提供身份、会话、用户管理、任务池、录音媒体、导入和语音生成接口。
- `scripts`：后续放置本地开发、数据处理或运维辅助脚本。
- `AGENTS.md`：Codex 长期执行规则，同时记录接口和数据库说明入口。
- `log.md`：AI 辅助修改日志。

## 当前阶段

当前已实现 Web 后台身份与主要管理/审核页面、后端身份/会话、语音生成生产台、任务采集审核闭环和小程序业务页面。机器审核和真实 AI 转写仍未实现。

仓库内不维护 Docker Compose 配置。后端运行需要开发者在本机或外部环境提供 MongoDB；默认连接为 `mongodb://localhost:27017/recording_platform`，真实账号密码只能放在本地环境变量或未提交的 `.env` 中。

## Web 端主题基础

Web 端已建立基础主题变量，变量文件位于 `apps/web/src/styles/theme.css`，并由 `apps/web/src/style.css` 作为全局样式入口引入。

当前完成浅色优先主题、`.dark` 深色变量预留、登录/首次改密页面及管理端布局；不实现主题切换按钮或复杂前端状态管理。

后续管理员端和审核端页面应优先使用主题变量：

- 主色：`--primary`
- 背景：`--background`
- 文字：`--foreground`
- 卡片：`--card`
- 边框：`--border`
- 圆角：`--radius`

## Web 管理端导航壳

Web 管理端已建立 Vue Router 导航壳。未登录访问后台会进入 `/login`；ADMIN 默认进入 `/admin/dashboard`，REVIEWER 默认进入 `/admin/review/queue`。首次登录待改密账号只能访问 `/first-password`，改密后清除会话并要求重新登录。

侧边栏菜单统一配置在 `apps/web/src/config/adminSidebar.js`，路由统一位于 `apps/web/src/router/`。菜单按 ADMIN/REVIEWER 动态过滤，未业务化的旧静态原型不再暴露在生产导航和路由。`apps/web/src/lib/httpClient.js` 统一处理 Cookie、CSRF、JSON/multipart、幂等头、结构化错误和会话接管下线；平台、任务、数据池、授权、审核、用户、记录和统计页面均通过该请求层接入真实接口。

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
REMOTE_MEDIA_ALLOW_HTTP=false
REMOTE_MEDIA_TIMEOUT_SECONDS=15
REMOTE_MEDIA_MAX_REDIRECTS=3
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

脚本现在会在结束 `8080/5173` 旧进程之前，先脱敏检查 MongoDB TCP 可达性和 `RECORDING_STORAGE_DIR` 可写性。失败时直接停止；不打印 URI，不安装、启动、停止 MongoDB，不处理 `27017` 端口进程。

后端提供公开只读就绪接口 `GET /api/health/ready`：仅返回 `overall`、`mongo`、`storage` 的 `UP/DOWN`；全部就绪时为 HTTP 200，任一项失败时为 HTTP 503，不返回 URI、绝对路径、密码或异常文本。

## 身份、会话与接口边界

- 固定角色为 `ADMIN`、`REVIEWER`、`COLLECTOR`。后台账号使用 BCrypt 密码；所有新编码密码至少 8 个字符且 UTF-8 不超过 72 字节。首管理员仅在数据库没有 `ADMIN` 且同时配置 `INITIAL_ADMIN_USERNAME`、`INITIAL_ADMIN_PASSWORD` 时创建；首次登录必须改密。
- Web 登录使用 HttpOnly、SameSite=Lax Cookie `REC_WEB_SESSION`。服务端只保存不透明令牌的 SHA-256 哈希，默认空闲 12 小时；生产 HTTPS 可设置 `WEB_SESSION_COOKIE_SECURE=true`。
- 已登录 Web 用户先调用 `GET /api/auth/web/csrf` 获取可读的 `XSRF-TOKEN` Cookie；执行退出、改密或管理员写操作等非安全方法时，同时通过 `X-XSRF-TOKEN` 请求头回传该值。首次登录待改密账号也允许获取 CSRF token。
- 同一后台账号只允许一个活动 Web 会话。重复登录返回 `409 ACCOUNT_IN_USE` 和短时一次性 `takeoverToken`；确认接管后旧会话返回 `401 SESSION_REPLACED`。
- 小程序只向后端提交 `wx.login` 临时 `code`。后端使用 `WECHAT_APP_ID`、`WECHAT_APP_SECRET` 调用微信 `jscode2session`，不接受客户端直接提交 OpenID；兼容微信以 `text/plain` 返回 JSON 内容的实际响应，且不保存或输出 `session_key`；小程序 Bearer token 默认 30 天。
- `/api/voice-generation/**` 与 `/api/admin/**` 仅 `ADMIN` 可访问；除 Web/微信登录与接管接口外，其余 `/api/**` 默认要求认证。
- `/api/platforms/**`、任务管理、授权管理、任务条目管理和导入仅 `ADMIN` 可写；`COLLECTOR` 通过小程序 Bearer token 申请权限、领取、提交和释放本人条目；`REVIEWER`/`ADMIN` 可驳回待审结果。
- Web Cookie 写请求必须携带 CSRF token。只有不含 `REC_WEB_SESSION` Cookie 的小程序 Bearer 采集写请求豁免 CSRF，夹带 Bearer 头不能绕过 Web CSRF。
- Task 2 所有写接口必须提供幂等标识：平台、任务、授权、领取和导入入口使用 `Idempotency-Key`；提交、释放、驳回使用请求中的 `operationId`。通用幂等记录按操作者、操作类型和幂等键唯一，重复请求返回首次完成结果；相同请求仍在处理时返回 `409 OPERATION_IN_PROGRESS`。
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

## 任务池、录音媒体与导入

- 平台和任务：`/api/platforms` 提供 ADMIN CRUD，仍被任务引用的平台返回 `409 PLATFORM_IN_USE`，禁止删除后形成悬空引用；`/api/tasks` 提供创建、结构编辑、发布、暂停、恢复、结束和分页查询。任务编码限制为字母、数字、下划线或连字符。已发布任务修改结构时创建下一不可变版本，旧条目继续绑定旧版本；跨 `tasks`/`task_versions` 写入失败时使用保存后最新版本号执行补偿，补偿本身失败返回受控一致性错误；首期固定 `aiEnabled=false`。
- 授权：`/api/tasks/{taskId}/grants` 与 `/access-requests` 管理直接授权、申请、原子批准/驳回和撤销。撤销只阻止新领取，不影响已领取条目的提交或释放；批准申请重放不会复活后来已撤销的授权。
- 数据池：ADMIN 使用 `POST /api/tasks/{taskId}/items` 单条添加并传 `Idempotency-Key`，或通过 `/api/import-jobs` 异步导入。COLLECTOR 使用 `POST /api/tasks/{taskId}/items/start` 原子领取或继续全系统唯一的当前条目。
- 提交与返修：`POST /api/task-items/{itemId}/submit` 接收 multipart 的 `operationId`、`assignmentId`、`expectedRevision`、可选文字和录音；重复 operationId 返回首次结果，过期修订返回 `409 STALE_STATE`。驳回保留原采集员，释放清除当前结果但保留提交和操作历史。提交或释放成功后，旧文件备份和旧媒体元数据由持久化清理任务处理；即时清理失败不改变首次业务结果，同 operationId 重放和应用启动恢复都会继续重试。
- 录音文件：`RECORDING_STORAGE_DIR` 下只使用相对路径；当前录音固定为 `recordings/{taskCode}/{itemCode}/current.wav|mp3`。上传先进入 `temp/`，完成扩展名、魔数、100MB、单声道、采样率和时长校验后原子替换，失败恢复旧文件。待删除的 current 会先移动到 `temp/backups/` 唯一路径，避免后续重录复用稳定路径时误删新文件；`GET /api/media/{mediaId}` 鉴权读取并支持单 Range。
- 导入固定列为 `externalItemId`、`referenceText`、`referenceAudioUrl`、`referenceVideoUrl`，支持 `.csv` 和 `.xlsx`、部分成功、失败行重试及幂等。单文件最多 50000 个数据行；每 100 行持久化一次进度，行错误摘要最多保存 1000 条，完整失败行号单独保留用于重试。初始导入与过期 PROCESSING 恢复使用 `FULL` 模式幂等重放完整源文件，只有用户显式重试使用 `FAILED_ROWS`；worker 的心跳、进度和完成状态均以 `leaseOwner` 条件原子更新，旧 worker 失去租约后不能覆盖最终状态。部分成功时先生成只含失败行的 worker 唯一重试 CSV，只有 fenced 完成写入成功后才切换文件并清理旧源，成功行签名 URL 不再落盘。
- 远程参考媒体生产默认只允许 HTTPS；每次重定向重新执行协议、主机和地址策略，禁止本机、环回、私网、链路本地与多播地址，并将校验后的地址绑定到实际连接。开发环境只有显式设置 `REMOTE_MEDIA_ALLOW_HTTP=true` 才允许 HTTP，仍不允许私网目标。音频上限 100MB、视频上限 500MB。

## 人工审核与状态管理

- `/api/reviews` 支持审核池、单条/批量领取、管理员指定审核员、释放审核占用、通过、驳回和管理员批量通过。
- 驳回使用任务版本配置的原因多选加补充说明，回到原采集员的待录制状态；通过可补改文字并进入已完成。
- ADMIN 可单条或批量调整状态、释放、软废弃和恢复；普通状态调整不能进入待领取，返回池只能使用释放。
- 未启用的审核或 AI 阶段不可进入；废弃保留归属、当前结果、文件和历史，恢复回废弃前状态。
- 所有写接口使用 operationId、持久化幂等快照及 revision/CAS；批量操作逐条返回成功或冲突结果。

## 操作记录与统计

- `GET /api/task-items/{itemId}/operations` 按条目权限分页返回东八区时间、操作人和操作内容；`GET /api/operations` 使用 MongoDB operation 级展开分页。
- `/api/reports/tasks|collectors|reviewers|me|me/submissions` 提供任务、采集员、审核员、个人汇总和逐次提交明细。
- 累计工作量包含首次提交与全部返修；当前有效结果只统计当前 `COMPLETED`，释放和废弃单列。
- 提交历史保存提交当时的 collectorId，释放或重新分配后仍能正确归属历史工作量。
- 任务/采集汇总、审核员操作过滤和个人提交分页均下推到 MongoDB aggregation。

任务相关分页响应统一为 `{ items, page, size, total }`。常用端点：

```text
POST/GET/PUT/DELETE /api/platforms[/{id}]
POST/GET/PUT        /api/tasks[/{taskId}]
POST                /api/tasks/{taskId}/publish|pause|resume|end
POST/GET/DELETE     /api/tasks/{taskId}/grants[/{userId}]
POST/GET            /api/tasks/{taskId}/access-requests
POST                /api/tasks/{taskId}/access-requests/{requestId}/approve|reject
POST/GET            /api/tasks/{taskId}/items
POST                /api/tasks/{taskId}/items/start
GET                 /api/task-items/{itemId}
POST                /api/task-items/{itemId}/submit|release|reject
GET/POST            /api/reviews/pool|claim|claim-batch|assign
GET/POST            /api/reviews/{itemId}|release|approve|reject
POST                /api/reviews/batch/approve
POST                /api/task-items/{itemId}/status|discard|restore
POST                /api/task-items/batch/status|release|discard|restore
GET                 /api/task-items/{itemId}/operations
GET                 /api/operations
GET                 /api/reports/tasks|collectors|reviewers|me|me/submissions
POST/GET            /api/import-jobs[/{jobId}]
POST                /api/import-jobs/{jobId}/retry
GET                 /api/media/{mediaId}
```

MongoDB 当前集合：

- `users`：后台用户名、内部用户编号和测试期 `(wechatAppId, wechatOpenId)` 唯一身份。
- `sessions`：仅保存令牌哈希，包含状态、最后访问时间和 TTL 到期时间。
- `voice_generation_records`：语音生成记录。
- `voice_generation_configs`：默认声音配置。
- `platforms`、`tasks`、`task_versions`：平台、任务生命周期和不可变版本快照。
- `task_grants`、`task_access_requests`：任务授权与待决申请。
- `task_items`：池条目、领取归属、当前结果、提交历史和操作历史。
- `media_assets`：参考媒体与当前录音元数据，只保存相对路径。
- `media_cleanup_jobs`：提交/释放后的旧文件备份与旧媒体元数据清理任务，记录 operationId、状态和重试次数。
- `import_jobs`：异步导入文件摘要、FULL/FAILED_ROWS 运行模式、分批进度、失败行号、有限脱敏行错误和带 owner fencing 的 worker 租约。
- `idempotency_records`：Task 2 通用写操作的 IN_PROGRESS/COMPLETED 状态与首次响应快照。

测试默认使用 mock/fake store，不依赖开发机 MongoDB；需要真实 Mongo 集成测试时单独提供 `MONGODB_TEST_URI`，不得复用生产库。

## 本地验证

Web 端：

```bash
cd apps/web
npm install
npm test -- --run
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
