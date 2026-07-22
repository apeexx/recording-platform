# 录音任务平台

录音任务平台用于管理录音任务的创建、领取、录制、上传和审核流程。当前仓库已具备管理员/审核员 Web 管理闭环、MongoDB 任务与审核后端闭环，以及原生微信小程序的微信/数字账号双登录、独立个人资料、头像、授权申请、任务领取、录音/文字提交、审核领取前修改、只读查看和个人统计页面。

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
- `apps/miniprogram`：原生微信小程序录音采集端；录音作业页使用紧凑状态卡片、本地 Iconfont SVG、当前帧 RMS 中心对称波形和可拖动的独立播放器。
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

Web 管理端已建立 Vue Router 导航壳。未登录访问后台会进入 `/login`；ADMIN 默认进入 `/admin/dashboard`，REVIEWER 默认进入 `/admin/review` 并先选择任务。首次登录待改密账号只能访问 `/first-password`，改密后清除会话并要求重新登录。

侧边栏菜单统一配置在 `apps/web/src/config/adminSidebar.js`，路由统一位于 `apps/web/src/router/`。菜单按 ADMIN/REVIEWER 动态过滤，未业务化的旧静态原型不再暴露在生产导航和路由。`apps/web/src/lib/httpClient.js` 统一处理 Cookie、CSRF、JSON/multipart、幂等头、结构化错误和会话接管下线；CSRF 失效时只刷新令牌并自动重试一次，真实角色越权不会重试。任务、数据池、授权、审核、用户、记录和统计页面均通过该请求层接入真实接口。任务详情中的数据池使用数字服务端分页，支持每页 5/10/20 条并默认 10 条；独立“任务数据池”页面保持每页 20 条。

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
AVATAR_STORAGE_DIR=backend/storage/avatars
RECORDING_PATH_MIGRATION_ENABLED=false
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

`RECORDING_STORAGE_DIR` 使用相对路径时固定以仓库根目录为基准，因此上述开发配置始终对应根目录下的 `backend/storage/recordings/`，不会随 Spring Boot 从 `backend/` 或仓库根目录启动而改变。绝对路径仍按原值使用。

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

- 身份数据分为 `web_users` 与 `miniprogram_users`：后台账号 ID 使用 `WEB-...` 前缀并保存 ADMIN/REVIEWER 角色，小程序采集员 ID 使用 `MINI-...` 前缀且不在文档中存储角色（鉴权与响应中固定为 COLLECTOR）。两类账号可使用相同登录名，因为唯一约束分别属于各自集合。后台账号使用 BCrypt 密码；所有新编码密码至少 8 个字符且 UTF-8 不超过 72 字节。首管理员仅在 `web_users` 没有 `ADMIN` 且同时配置 `INITIAL_ADMIN_USERNAME`、`INITIAL_ADMIN_PASSWORD` 时创建；首次登录必须改密。
- Web 登录使用 HttpOnly、SameSite=Lax Cookie `REC_WEB_SESSION`。服务端只保存不透明令牌的 SHA-256 哈希，默认空闲 12 小时；生产 HTTPS 可设置 `WEB_SESSION_COOKIE_SECURE=true`。
- 已登录 Web 用户先调用 `GET /api/auth/web/csrf` 获取可读的 `XSRF-TOKEN` Cookie；执行退出、改密或管理员写操作等非安全方法时，同时通过 `X-XSRF-TOKEN` 请求头回传该值。首次登录待改密账号也允许获取 CSRF token。
- 每个后台账号只允许一个活动 Web 会话，每个小程序采集员也只允许一个活动小程序会话。任一端重复登录均返回 `409 ACCOUNT_IN_USE` 和短时一次性 `takeoverToken`；必须调用对应端的 takeover 接口确认接管，接管后旧设备下次请求返回 `401 SESSION_REPLACED`。
- 小程序只向后端提交 `wx.login` 临时 `code`。后端使用 `WECHAT_APP_ID`、`WECHAT_APP_SECRET` 调用微信 `jscode2session`，不接受客户端直接提交 OpenID；兼容微信以 `text/plain` 返回 JSON 内容的实际响应，且不保存或输出 `session_key`；小程序 Bearer token 默认 30 天。
- 微信登录和 6–12 位数字账号密码登录映射到同一个 `MINI-...` 采集员用户 ID。首次资料设置原子写入姓名、在小程序集合内唯一的数字账号和密码；任务列表允许浏览，但申请、待办、领取、继续及提交前后端均校验资料完整性。发生小程序会话占用时，登录页先提示用户确认，再以返回的短时一次性凭证调用 `POST /api/auth/miniprogram/takeover`；不得自动接管。自定义头像保存到 `AVATAR_STORAGE_DIR`，仅支持魔数有效的 JPEG/PNG/WebP、最大 5MB；MongoDB 只保存相对路径和内容类型。
- 小程序登录页为“砚数声采”A 版，使用正式品牌图标。个人资料页的头像卡直接调用微信原生 `chooseAvatar` 面板，选择后仍需预览确认；恢复默认头像使用卡片下方独立入口。头像读取失败会静默回退本地默认头像，文件上传仍由既有后端执行 JPEG/PNG/WebP 和 5MB 限制。原生“任务”“我的”Tab 使用 81×81 本地 PNG 图标，不依赖运行时外链。
- 小程序将当前账号资料摘要缓存在 `recSession.user`：已完善资料时任务入口优先使用缓存并静默后台刷新，断网不再误弹“请先完善个人资料”；缓存未知且联网失败时仅显示网络 Toast。退出登录或修改密码重新登录会删除整个会话缓存，新登录或接管成功会整体覆盖旧账号资料；头像二进制文件不额外缓存。
- “我的”和资料设置页面均展示当前采集员的完整 `MINI-...` userId；页面不显示独立的复制提示，直接点击整行用户 ID 即复制完整值并显示成功或失败 Toast，其中“我的”页点击 ID 不触发资料卡跳转。“我的”页面同时展示最近 5 条提交记录及其状态。
- `/api/voice-generation/**` 与 `/api/admin/**` 仅 `ADMIN` 可访问；除 Web/微信登录与接管接口外，其余 `/api/**` 默认要求认证。
- 任务管理、授权管理、任务条目管理和导入仅 `ADMIN` 可写；`COLLECTOR` 通过小程序 Bearer token 申请权限、领取、提交和释放本人条目；`REVIEWER`/`ADMIN` 可驳回待审结果。
- Web Cookie 写请求必须携带 CSRF token。只有不含 `REC_WEB_SESSION` Cookie 的小程序 Bearer 采集写请求豁免 CSRF，夹带 Bearer 头不能绕过 Web CSRF。缺失或失效返回 `403 CSRF_TOKEN_INVALID`，Web 请求层刷新 token 后仅重试一次；真实角色越权仍返回 `403 ACCESS_DENIED`。
- Task 2 所有写接口必须提供幂等标识：任务、授权、领取和导入入口使用 `Idempotency-Key`；提交、释放、驳回使用请求中的 `operationId`。通用幂等记录按操作者、操作类型和幂等键唯一，重复请求返回首次完成结果；相同请求仍在处理时返回 `409 OPERATION_IN_PROGRESS`。
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
POST /api/auth/miniprogram/account-login
POST /api/auth/miniprogram/takeover
GET  /api/auth/miniprogram/profile
POST /api/auth/miniprogram/profile/complete
PUT  /api/auth/miniprogram/name
PUT  /api/auth/miniprogram/password
POST / GET / DELETE /api/auth/miniprogram/avatar
PUT  /api/auth/miniprogram/name
POST /api/admin/users
GET  /api/admin/users?page=0&size=20
POST /api/admin/users/{userId}/disable
PUT  /api/admin/users/{userId}/collector-account
POST /api/admin/users/{userId}/reset-password
```

后台用户列表接口 `GET /api/admin/users` 只返回 Web 用户；统一搜索使用 `GET /api/admin/users/search?query=&role=&userType=&page=&size=`。搜索结果中的每项固定展示 `id`、`userType`、`loginName`、name、role、status：`WEB-...` / `WEB` 表示后台用户，`MINI-...` / `MINIPROGRAM` 表示采集员。搜索条件同时匹配姓名、完整前缀 ID 和登录名；`userType=WEB` 只查询 `web_users`，`userType=MINIPROGRAM` 只查询 `miniprogram_users`，与指定角色不匹配时返回空分页；省略 `userType` 时保持现有跨集合合并搜索，指定 `COLLECTOR` 时只搜索采集员，指定 `ADMIN` 或 `REVIEWER` 时只搜索对应 Web 用户。Web 用户管理页顶部使用“Web 端账号 / 小程序端账号”页签并按当前类型加载和搜索；Web 页签的搜索栏右侧提供“创建后台账号”按钮，点击后在四字段弹窗中创建，成功关闭并刷新列表，失败保留输入。ADMIN 可对 ACTIVE Web 或 Mini 用户重置密码：前者要求下次登录改密，后者只废止小程序会话；采集员登录账号可由 `PUT /api/admin/users/{userId}/collector-account` 修改。

## 任务池、录音媒体与导入

- 任务：`/api/tasks` 提供创建、结构编辑、发布、暂停、恢复、结束和分页查询。任务编码由数据库序列自动生成 `T000001` 格式，不允许前端输入或修改；任务条目编码使用 `{taskCode}-{7位序号}`，单任务支持至 100 万条且序号不回收。所有任务都必须录音；最终成果为 `TEXT` 时提交录音和文本，为 `AUDIO` 时只提交录音。关闭人工审核时不显示也不保存驳回预设原因。已发布任务修改结构时创建下一不可变版本，旧条目继续绑定旧版本；首期固定 `aiEnabled=false`。
- 授权：`/api/tasks/{taskId}/grants` 与 `/access-requests` 管理直接授权、申请、原子批准/驳回和撤销。撤销只阻止新领取，不影响已领取条目的提交或释放；批准申请重放不会复活后来已撤销的授权。
- 数据池：ADMIN 使用 `POST /api/tasks/{taskId}/items` 单条添加并传 `Idempotency-Key`，或通过 `/api/import-jobs` 异步导入。COLLECTOR 使用 `POST /api/tasks/{taskId}/items/start` 原子领取；普通待录制作业不设持有数量上限，每个新的 `Idempotency-Key` 领取一条新数据，相同幂等键重放仍返回首次条目。
- 提交与返修：`POST /api/task-items/{itemId}/submit` 接收 multipart 的 `operationId`、`assignmentId`、`expectedRevision` 以及与任务成果类型匹配的文字或录音；重复 operationId 返回首次结果，过期修订返回 `409 STALE_STATE`。驳回进入独立 `REWORK_PENDING` 队列并保留原因、原采集员和 assignment；普通待录制和返修均可同时持有多条。释放清除当前结果但保留提交和操作历史。
- 待录制索引迁移在应用启动时先确保普通查询索引 `(collectorId,taskId,status)`，再按精确名称幂等删除旧全局和任务级待录制唯一索引；失败则终止启动且不改写 `task_items`。若需回滚任一旧唯一索引，必须先处理与旧口径冲突的多条 `RECORDING_PENDING`，否则索引无法恢复。
- 小程序作业页的“提交后自动领取下一条”首次默认开启并使用本地 `autoClaimNextEnabled` 记忆。待录制或待返修提交成功后直接领取并打开下一条；领取失败会提示原因并进入当前任务数据页；关闭开关时返回上一页，`SUBMITTED` 的“保存修改”不触发自动领取。
- 作业页的录音、参考音频、参考视频和录制结果彼此独立，可同时运行；离开页面时仍由各自生命周期释放资源。录音卡外层/状态层最小高度为 `460rpx / 392rpx`，作业页文本输入框固定为 `200rpx`。普通操作错误使用悬浮 Toast；任务大厅、任务数据、个人统计和作业整体加载失败保留可重试阻塞状态，其中作业页网络加载失败统一显示“网络链接失败，请检查网络。”，不暴露微信底层错误文本。
- 录音文件：`RECORDING_STORAGE_DIR` 的相对值按仓库根目录解析，目录下只使用相对媒体路径；当前录音固定为 `{taskCode}/{itemCode}.wav|mp3`。上传先进入 `temp/`，完成扩展名、魔数、100MB、单声道、采样率和时长校验后原子替换，失败恢复旧文件。待删除的旧稳定文件会先移动到 `temp/backups/` 唯一路径，避免后续重录复用同一路径时误删新文件；`GET /api/media/{mediaId}` 鉴权读取录音及受保护媒体并支持单 Range。`GET /api/media/public/reference/{mediaId}` 只公开参考音频和参考视频，历史条目由小程序使用该 URL 直接播放；录音结果通过此路径固定返回 404。
- 导入固定列为 `referenceText`、`referenceAudioUrl`、`referenceVideoUrl`，仅支持 `.csv`，支持部分成功、失败行重试及幂等。条目不再接收或保存外部编号，只使用系统生成的条目编号。单文件最多 50000 个数据行；每 100 行持久化一次进度，行错误摘要最多保存 1000 条，完整失败行号单独保留用于重试。
- 本地批量导入正向测试可直接使用 `docs/test-data/task-items-import-valid.csv`。该文件包含 8 条中文参考文字，不包含远程音频或视频 URL，可用于验证 CSV 解析、异步导入和数据池新增闭环。
- 分页导入测试可使用 `docs/test-data/task-items-import-pagination-101.csv`。该文件包含 101 条唯一中文参考文字，音频和视频 URL 均为空，适用于启用文字参考的任务；任务详情与小程序按每页 10 条可形成十一页边界，独立任务数据池按每页 20 条可形成六页边界。
- 部分失败测试可使用 `docs/test-data/task-items-import-partial-failure.csv`。该文件共 5 行：2 行具有参考文字并应成功，3 行参考源均为空并应返回 `ITEM_REFERENCE_REQUIRED`；首次导入预期为 `PARTIAL_SUCCESS`、成功 2 行、失败 3 行。失败行重试不会修正源数据，因此预期仍保持部分失败，可用于验证失败行保留和重试幂等。
- 远程参考媒体生产默认只允许 HTTPS；每次重定向重新执行协议、主机和地址策略，禁止本机、环回、私网、链路本地与多播地址，并将校验后的地址绑定到实际连接。开发环境只有显式设置 `REMOTE_MEDIA_ALLOW_HTTP=true` 才允许 HTTP，仍不允许私网目标。音频上限 100MB、视频上限 500MB。校验和后端副本保存成功后，`task_items` 同时保存规范化的 `referenceAudioUrl`、`referenceVideoUrl`，小程序优先直接播放原始公网 URL；正式环境必须在微信后台配置这些 URL 对应的合法音视频域名。历史条目没有 URL 字段时回退到上述公开参考媒体地址，不需要迁移或重新导入。

## 本地数据全量重置

仅在确认需要丢弃本地开发数据时运行 `scripts\reset-local-data.cmd recording_platform`。脚本读取未提交的根目录 `.env`，只有精确确认词、有效的 `INITIAL_ADMIN_USERNAME`、`INITIAL_ADMIN_PASSWORD` 和精确指向 `recording_platform` 的 `MONGODB_URI` 同时满足时才启用一次性重置。后端还会再次校验确认词、数据库真实名称及存储目录必须是受限的 recordings/recording-data/voice-generation 运行目录，然后完整删除本地开发数据库（包含旧 `users`、当前 `web_users` 与 `miniprogram_users` 等全部集合）并清理受限运行存储，随后按初始化配置重建首个 `WEB-...` 管理员。不要在生产环境运行，脚本不会备份数据。

## 一次性旧录音路径迁移

`RECORDING_PATH_MIGRATION_ENABLED` 默认必须为 `false`。它只用于把旧的 `recordings/{taskCode}/{itemCode}/current.wav|mp3` 一次性迁移为 `{taskCode}/{itemCode}.wav|mp3`，不是常规启动开关；本节命令仅为运维手册，本轮不会再次执行真实迁移。

执行前必须停止全部后端实例和其他会写入录音、`media_assets`、`task_items`、`media_cleanup_jobs`、`idempotency_records` 的进程，并同时备份这四个 MongoDB 集合与完整 `RECORDING_STORAGE_DIR`。确认 MongoDB 与文件备份都可恢复后，才允许以单实例启动迁移：

```powershell
cd C:\Projects\recording-platform\backend
$env:RECORDING_PATH_MIGRATION_ENABLED='true'
.\mvnw.cmd spring-boot:run
```

迁移器会先完成全部路径和文档预检，再移动文件并使用版本与精确路径 CAS 更新 MongoDB；成功后进程自动退出，日志只记录脱敏计数。保持业务停写，再运行同一命令一次，必须看到 `migrated=0`、`deduplicated=0`。随后立即执行 `Remove-Item Env:RECORDING_PATH_MIGRATION_ENABLED`（或显式设为 `false`），再按正常方式启动服务。

如果迁移失败，不得恢复业务写入。先核对新旧文件是否都存在及 SHA-256 是否一致，再检查上述四个集合的路径和版本。迁移器会尝试全局逆向补偿；如果 MongoDB 回滚结果不确定，会保留新文件并补回旧路径，避免任何仍指向新路径的文档失去可读文件。此时不得单独删除新文件；无法人工确认一致性时，应把 MongoDB 集合和录音目录作为同一恢复单元一并从备份还原。

## 人工审核与状态管理

- 人工审核任务提交后进入 `SUBMITTED`，采集员在审核领取前可覆盖保存录音和文本；管理员或审核员通过 `/api/reviews/{itemId}/claim` 领取、或管理员分配后原子进入 `REVIEW_PENDING`，此后采集端只读。审核释放回到 `SUBMITTED`；关闭人工审核的任务仍直接进入 `COMPLETED`。
- `/api/reviews/tasks` 先列出有已提交或待审核数据的任务，再通过 `/api/reviews/tasks/{taskId}/pool|claim|claim-batch` 进入指定任务审核池。管理员和审核员决定前都必须先领取或分配；审核员只能处理本人占用。
- 启用人工审核时，驳回使用任务版本配置的原因多选加补充说明并进入原采集员的返修队列；通过时仅文本成果允许补改文字，录音文件保持不变并进入已完成。
- ADMIN 可单条或批量调整状态、释放、软废弃和恢复；普通状态调整不能进入待领取或人工审核任务的已完成状态，返回池只能使用释放，人工审核完成必须走领取/分配后的审核决定。
- 未启用的审核或 AI 阶段不可进入；废弃保留归属、当前结果、文件和历史，恢复回废弃前状态。
- 所有写接口使用 operationId、持久化幂等快照及 revision/CAS；批量操作逐条返回成功或冲突结果。
- 提交修改、审核领取、释放和决定均使用状态与 revision/CAS；并发时先成功者生效，另一方返回 `STALE_STATE`。审核领取在同一次原子更新中递增 revision；接口返回的条目 revision 与追加操作记录的 resultRevision 使用同一更新后修订号。
- 应用启动会幂等地把 reviewerId 与 reviewAssignmentId 均为空的旧 `REVIEW_PENDING` 改为 `SUBMITTED`；已领取记录不修改，日志只记录数量，不需要清空数据库。

## 操作记录与统计

- `GET /api/task-items/{itemId}/operations` 按条目权限分页返回东八区时间、操作人和操作内容；`GET /api/operations` 使用 MongoDB operation 级展开分页。
- `/api/reports/tasks|collectors|reviewers|me|me/submissions` 提供任务、采集员、审核员、个人汇总和逐次提交明细。
- 累计工作量包含首次提交与全部返修；当前有效结果只统计当前 `COMPLETED`，释放和废弃单列。
- 提交历史保存提交当时的 collectorId，释放或重新分配后仍能正确归属历史工作量。
- 任务/采集汇总、审核员操作过滤和个人提交分页均下推到 MongoDB aggregation。

任务相关分页响应统一为 `{ items, page, size, total }`。常用端点：

```text
POST/GET/PUT        /api/tasks[/{taskId}]
POST                /api/tasks/{taskId}/publish|pause|resume|end
POST/GET/DELETE     /api/tasks/{taskId}/grants[/{userId}]
POST/GET            /api/tasks/{taskId}/access-requests
POST                /api/tasks/{taskId}/access-requests/{requestId}/approve|reject
POST/GET            /api/tasks/{taskId}/items
POST                /api/tasks/{taskId}/items/start
GET                 /api/task-items/{itemId}
GET                 /api/task-items/mine?kind=PENDING|SUBMITTED|FINISHED
POST                /api/task-items/{itemId}/submit|release|reject
GET                 /api/reviews/tasks
GET/POST            /api/reviews/tasks/{taskId}/pool|claim|claim-batch
GET/POST            /api/reviews/{itemId}|claim|release|approve|reject
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

- `web_users`：后台 ADMIN/REVIEWER 身份，ID 为 `WEB-...`，用户名仅在本集合唯一。
- `miniprogram_users`：小程序采集员身份，ID 为 `MINI-...`，数字账号及微信 `(wechatAppId, wechatOpenId)` 身份仅在本集合唯一，文档不存储角色。
- `sessions`：仅保存令牌哈希，包含状态、最后访问时间和 TTL 到期时间；Web 与小程序各自有一条 ACTIVE 会话部分唯一索引。
- `voice_generation_records`：语音生成记录。
- `voice_generation_configs`：默认声音配置。
- `sequences`、`tasks`、`task_versions`：原子编号序列、任务生命周期和不可变版本快照。
- `task_grants`、`task_access_requests`：任务授权与待决申请。
- `task_items`：池条目、领取归属、当前结果、提交历史和操作历史。
- `media_assets`：参考媒体与当前录音元数据，只保存相对路径。
- `media_cleanup_jobs`：提交/释放后的旧文件备份与旧媒体元数据清理任务，记录 operationId、状态和重试次数。
- `import_jobs`：异步导入文件摘要、FULL/FAILED_ROWS 运行模式、分批进度、失败行号、有限脱敏行错误和带 owner fencing 的 worker 租约。
- `idempotency_records`：Task 2 通用写操作的 IN_PROGRESS/COMPLETED 状态与首次响应快照。

测试默认使用 mock/fake store，不依赖开发机 MongoDB；需要真实 Mongo 集成测试时单独提供 `MONGODB_TEST_URI`，不得复用生产库。

## 2026-07-15 本地全链路人工验收

已使用本机 MongoDB、真实 Chrome/Edge、微信开发者工具和微信真机完成开发环境闭环验收：

- 后台账号登录、单账号会话接管、首次改密及 ADMIN/REVIEWER 角色菜单通过。
- 任务版本、池数据、采集权限申请审批、领取、MP3 录音试听/上传通过。当时验收的“文字单独提交”旧行为已在 2026-07-18 调整为“录音＋文本”。
- 返修保持原采集员，同条目重录覆盖稳定文件；采集员释放后结果和当前文件清理，提交与操作历史保留。
- 管理员直接审核以及审核员领取、释放、再次领取、补充文字后通过均完成；审核音频 Range 播放正常。
- 任务、采集员、审核员和小程序个人统计与实际操作记录一致。

本次只验证开发环境和个人测试 AppID，不代表生产部署验收；正式上线仍需公司小程序账号、HTTPS 合法域名、生产 MongoDB、生产存储备份与监控。付费 MiniMax 调用未执行。

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
