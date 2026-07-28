# AGENTS.md | 录音任务平台 Codex 长期执行规则

请始终使用中文回答。

## 1. 项目定位

本项目名称统一为：**录音任务平台**。

本项目用于管理录音任务的创建、领取、录制、上传和审核流程。当前仓库仍处于初始化和基础框架阶段，不是完整业务系统，也不是已经具备生产能力的平台。

核心业务链路：

```text
管理员创建录音任务
  -> 录音人员领取任务
录音人员录制并上传
  -> 系统进入审核流程
审核通过
  -> 任务完成
审核驳回
  -> 重新录制并再次提交
```

## 2. 固定阶段范围

当前阶段维护基础项目结构、最小必要说明和语音生成 Web 生产台：

```text
管理员 Web 端空项目
审核 Web 端后续预留
微信小程序录音采集端
Java Spring Boot 后端项目
MongoDB 身份、会话与统一 API 错误基础
语音生成 Web 生产台
任务配置、授权、任务池、录音媒体与导入后端闭环
根目录 README.md
根目录 log.md
本文件 AGENTS.md
```

当前已实现身份、会话、后台用户管理、新微信身份邀请码准入、语音生成持久化，以及嵌入式任务配置、授权申请、任务池领取、录音提交/返修/释放、人工审核、AI 辅助审核、动态状态、软废弃恢复、媒体读取和 CSV 导入后端闭环；Web 已实现后台身份、邀请码管理、任务配置、数据池/导入、权限、审核、用户、操作记录与统计页面，小程序已实现首次邀请准入、独立的按任务个人统计、每日分段和倒序提交记录。AI 只生成候选最终答案，不执行机器审核决定。

Spring Security 已配置为不透明服务端会话，不使用 JWT。除 Web/微信登录与接管接口外，其余 `/api/**` 默认认证；管理员、任务管理、授权管理、导入和语音生成接口按角色保护，采集写接口仅允许 `COLLECTOR` 小程序 Bearer 身份；外部集成仅允许专用 API Key 访问明确列出的四个端点。

## 3. 任务暗号

- `REC_READONLY`：只读检查，不修改文件，不提交。
- `REC_MAIN_TASK`：默认开发任务，允许在 `main` 单工作区修改；验证通过后按 Git 规则提交并 push。
- `REC_MAIN_HOTFIX`：小范围修复，必须控制影响面；验证通过后按 Git 规则提交并 push。
- `REC_API`：接口相关任务，必须同步更新本文件中的接口说明和 `README.md`。
- `REC_DATABASE`：数据库相关任务，必须同步更新本文件中的数据库说明和 `README.md`。
- `REC_VERIFY`：验收任务，优先查看 diff、运行验证、列风险，不做无关修改。
- `REC_ABORT_IF_DIRTY`：目录、分支或工作区异常时立即停止并报告。

## 4. Git 规则

- 默认使用 `main` 单工作区开发。
- 不默认创建分支、worktree 或 PR，除非用户明确要求。
- 执行前必须检查当前目录、当前分支和工作区状态。
- 验证通过后默认执行 `git add`、中文 commit、`git push origin main`。
- 验证失败、信息不足、目录异常、分支异常或工作区异常时，不得提交。
- commit message 必须清晰，优先使用中文，可保留英文范围名。

推荐 commit message：

```text
初始化: 调整录音任务平台项目结构
文档: 重写 Codex 长期执行规则
实现(web): 增加任务管理入口页面
实现(backend): 增加任务基础接口
修复(review): 修正审核状态流转
```

禁止 commit message：

```text
update
fix bug
修改
随便改一下
```

如果项目尚未 git 初始化，先提醒用户执行：

```bash
git init
git add .
git commit -m "初始化: 录音任务平台项目结构"
```

## 5. 每轮任务前必须阅读

每次执行开发任务前，先阅读：

```text
AGENTS.md
README.md
log.md
```

涉及具体模块时，还要阅读：

```text
apps/web/README.md
apps/miniprogram/README.md
backend/HELP.md
scripts/README.md
```

如果某个文档不存在，必须在最终输出中说明，不要编造内容，不要因此重建项目。

设计、计划、brainstorm 和其他过程文档统一放在仓库根目录 `.superpowers/`，不得放入 `docs/`；`.superpowers/` 为本地资料并保持 Git 忽略。

## 6. 默认技术栈

- Web 前端：Vite + Vue3 + JavaScript
- 微信小程序端：原生小程序，目录为 `apps/miniprogram`
- 后端：Java 17 + Spring Boot + Maven
- 数据库：MongoDB，默认数据库 `recording_platform`
- 文档记录：根目录 `README.md`、`AGENTS.md`、`log.md`

仓库内不再维护 Docker Compose 配置。后端运行需要开发者在本机或外部环境提供 MongoDB，默认 URI 为 `mongodb://localhost:27017/recording_platform`；仓库不得保存真实数据库密码。

不要默认引入复杂微服务、Kubernetes、对象存储、消息队列或独立权限中心。若确需更换技术栈或新增依赖，必须先说明原因、替代方案、影响范围和验证方式。

## 6.1 本地开发启动脚本

Windows PowerShell 本地联调可使用：

```powershell
.\scripts\start-dev.cmd
```

该脚本会先从当前进程环境或根目录 `.env` 读取配置，脱敏检查 MongoDB TCP 可达性和录音目录可写性。失败时必须在结束端口进程或启动服务之前退出；不得打印 MongoDB URI，不得安装/启动/停止 MongoDB，也不得结束 `27017` 进程。前置通过后，脚本检查并结束占用 `8080` 和 `5173` 端口的监听进程，然后打开两个可见的 `pwsh` 窗口分别运行：

```text
backend\mvnw.cmd spring-boot:run
npm run dev -- --host localhost --port 5173
```

两个窗口标题分别为 `Recording Backend` 和 `Recording Frontend`，用于查看实时日志。脚本只负责启动后端和 Web 前端，不创建 `.env`，不写入或打印 API Key，不再创建或写入根目录 `logs/`。语音生成真实联调仍需根目录 `.env` 提供 MiniMax 配置。

## 6.2 前端视觉规范

Web 端主题变量位于 `apps/web/src/styles/theme.css`，全局样式入口为 `apps/web/src/style.css`。

后续新增管理员端、审核端页面或通用组件时，必须优先使用主题变量，不要硬编码颜色：

- 主色使用 `--primary`
- 背景使用 `--background`
- 文字使用 `--foreground`
- 卡片使用 `--card`
- 边框使用 `--border`
- 圆角使用 `--radius`

当前只预留 `.dark` 深色主题变量，不实现复杂主题切换逻辑。管理员端导航壳允许使用 Vue Router；不得为本阶段引入 Tailwind、UI 组件库、CSS 预处理器、Pinia、图表库或复杂状态管理。

## 6.3 Web 管理端协作规则

管理员端侧边栏菜单统一由 `apps/web/src/config/adminSidebar.js` 管理，不允许把菜单项直接硬编码在 Sidebar 组件中。

管理员端路由统一放在 `apps/web/src/router/`。后续新增后台页面时，必须同步更新路由和侧边栏配置，保持菜单路径、页面文件和路由标题一致。

后台登录页为 `/login`，首次改密页为 `/first-password`。待改密账号登录后必须先选择修改或永久跳过；首次改密只输入新密码，普通改密仍校验原密码。所有 Web API 调用应优先复用 `apps/web/src/lib/httpClient.js`，由该模块统一携带同源 Cookie、获取并回传 CSRF、解析统一错误和处理 `SESSION_REPLACED`；`CSRF_TOKEN_INVALID` 只允许刷新令牌后自动重试一次，`ACCESS_DENIED` 不得自动重试。不得在业务模块另写一套绕过 CSRF 的 fetch。

Web 端表单校验、按钮操作、搜索、刷新和接口失败统一使用右上角 Toast，不得同时写入页面级加载错误或重复渲染行内红字。错误 Toast 默认显示约 4.5 秒并按相同文案去重，成功和信息提示默认约 2.6 秒；页面已有内容时刷新失败必须保留原内容。只有核心数据首次加载完全失败才使用 `AsyncState` 阻塞重试；删除、结束、接管等需要用户明确选择的操作继续使用确认弹窗。业务表单使用 `novalidate` 和手动校验，失败时聚焦第一个无效字段。

`apps/web/src/pages/admin/voice-generation/` 是语音生成 Web 生产台模块。非语音生成任务不要修改该目录下的页面，除非用户明确要求。

语音生成模块已接入后端真实接口，支持 0 元试听、付费克隆、日常合成、音色资产管理、默认声音配置和生成记录。前端不得保存、展示或提交 MiniMax API Key；后端从环境变量或本地 `.env` 读取 `MINIMAX_API_KEY`。

语音生成默认 MiniMax API Base URL 为 `https://api.minimaxi.com`。如果本地已配置 `MINIMAX_API_KEY` 但 MiniMax 返回 `2049 invalid api key`，优先检查 `MINIMAX_API_BASE_URL` 是否与账号区域一致：国内开放平台使用 `https://api.minimaxi.com`，国际开放平台使用 `https://api.minimax.io`。

付费克隆模式只允许上传母带音频并填写新音色 ID，不展示、不接收、不提交语速、音量或语调参数。MiniMax 克隆母带要求为 mp3、m4a 或 wav，时长 10 秒到 5 分钟，文件不超过 20MB；新音色 ID 需要以英文字母开头，只包含字母、数字、下划线或连字符，长度为 8 到 256 个字符，且不能以下划线或连字符结尾。后端全局 multipart 上限为单文件 100MB、完整请求 105MB；克隆接口仍执行 20MB 业务限制，超过限制时返回统一 HTTP 413 错误。

当前语音生成记录和默认声音配置分别持久化到 MongoDB 的 `voice_generation_records`、`voice_generation_configs` 集合。生成音频文件仍保存到本地目录，用于播放和下载；默认生成目录 `backend/storage/voice-generation/` 是本地运行产物，必须保持 Git 忽略，不得提交。MiniMax 合成失败时必须将记录更新为 `FAILED`。

## 6.4 身份与环境变量规则

后端身份体系固定角色为 `ADMIN`、`REVIEWER`、`COLLECTOR`。后台密码使用 BCrypt；所有需要新编码的密码至少 8 个字符且 UTF-8 不超过 72 字节。首管理员只在数据库无 `ADMIN` 且同时配置初始化用户名、密码时创建，并强制首次改密；初始化密码不符合规则时必须使用脱敏错误安全停止，不得把密码写入日志或返回接口。

身份与存储环境变量：

```text
MONGODB_URI
RECORDING_STORAGE_DIR
AVATAR_STORAGE_DIR
RECORDING_PATH_MIGRATION_ENABLED（默认 false，仅一次性迁移窗口显式设 true）
WECHAT_APP_ID
WECHAT_APP_SECRET
INITIAL_ADMIN_USERNAME
INITIAL_ADMIN_PASSWORD
WEB_SESSION_IDLE_HOURS（默认 12）
MINIPROGRAM_SESSION_DAYS（默认 30）
WEB_SESSION_COOKIE_SECURE（默认 false，生产 HTTPS 应设 true）
RECORDING_INTEGRATION_API_KEY_SHA256（标注脚本中心机器密钥的 SHA-256；默认空）
DASHSCOPE_API_KEY（审核辅助 AI 服务端密钥；默认空）
DASHSCOPE_BASE_URL（默认 https://dashscope.aliyuncs.com/compatible-mode/v1，生产按 Key 地域显式配置）
```

`RECORDING_STORAGE_DIR` 为相对路径时必须按仓库根目录解析，默认 `backend/storage/recordings`；不得按 Spring Boot 的 `backend/` 工作目录再次拼接 `backend`。绝对路径保持原值。启动前置检查、录音存储、导入临时文件和就绪检查必须使用同一目录语义。

根目录 `.env.example` 只能提供空值或安全默认值。Web 会话 Cookie 必须为 HttpOnly、SameSite=Lax；CSRF 使用可读的 `XSRF-TOKEN` Cookie 与 `X-XSRF-TOKEN` 请求头，已登录 Web 用户通过 `GET /api/auth/web/csrf` 获取 token，首次登录待改密账号也必须允许访问该端点。CSRF 缺失或失效必须返回 `403 CSRF_TOKEN_INVALID`，真实角色越权返回 `403 ACCESS_DENIED`，不得使用同一错误码混淆两类问题。服务端和 MongoDB 只保存会话令牌哈希，`sessions.expiresAt` 使用 TTL 自动清理，失效记录无需人工定期删除。微信登录必须由后端用临时 code 调用 `jscode2session`，不得信任客户端直接提交的 OpenID。已有 `miniprogram_users` 全部视为已准入；只有新微信身份首次创建账号时需要有效邀请码，同一微信身份只能占用一个名额。新建用户保存邀请码 ID、名称快照、末四位和兑换时间供管理员审计；完整邀请码不得落库或进入通用幂等响应快照。

### 6.4.1 一次性旧录音路径迁移规则

`RECORDING_PATH_MIGRATION_ENABLED` 必须默认关闭，只能在停止全部业务写入、同时备份完整录音目录与 `media_assets`、`task_items`、`media_cleanup_jobs`、`idempotency_records` 四个集合后，以单后端实例显式启用。迁移成功后必须在停写状态下再运行一次并确认 `migrated=0`、`deduplicated=0`，随后立即删除该环境变量或设回 `false`，再恢复正常服务。

迁移失败时不得恢复业务写入或单独删除新路径文件。应检查新旧文件 SHA-256、四个集合的路径和版本；MongoDB 回滚无法确认时必须保留新文件并补回旧路径。无法人工确认一致性时，MongoDB 集合与录音目录必须作为同一恢复单元从备份一并还原。具体命令与故障处理以 `README.md` 的“一次性旧录音路径迁移”为准。

## 6.5 任务池、媒体与导入规则

任务结构固定使用 `tasks`，配置嵌入 `configuration`；不再维护 `task_versions`。任务仅在 DRAFT 状态允许修改名称、说明和配置，发布后永久冻结；运行中任务必须先暂停，只有 PAUSED 状态允许结束。任务至少启用 TEXT/AUDIO/VIDEO 一种参考组件。最终成果为 `TEXT` 时文本或录音至少提交一项，也允许同时提交；`AUDIO` 必须提交录音且不得夹带文本。只有实际提交录音时才校验格式、采样率、单声道、大小和时长。录音时长配置固定为 1–600 秒；Web 双端滑块使用统一像素坐标绘制 22px 胶囊轨道、16px 选区和 20px 白色圆点，原生 range 仅保留键盘与无障碍输入。关闭人工审核时不得保存驳回预设原因；首期 `aiEnabled` 必须为 false。任务编码由数据库序列自动生成 `T000001`，条目编码为 `{taskCode}-{7位序号}`，不接受前端输入且序号不复用。创建条目时先读取该任务 `task_items.sequence` 的最大值，再以 Mongo 单文档原子更新分配“`itemSequence` 与最大值取大后加一”；历史条目高于计数器时自动抬高，计数器更高或保存失败后的缺口均不得降低、重用或由客户端指定。

采集员领取必须同时满足任务 RUNNING 和 ACTIVE grant；普通 `RECORDING_PENDING` 不限制采集员持有数量，每个新的 `Idempotency-Key` 使用 Mongo `findAndModify` 从 `AVAILABLE` 按 sequence 原子领取一条新数据，相同幂等键重放仍返回首次结果。驳回进入独立 `REWORK_PENDING`，保留原采集员、assignment 和驳回原因；授权撤销只阻止新领取，不影响已领取条目的提交和释放。

启用人工审核时，采集员提交进入 `SUBMITTED`，审核领取或管理员分配后才原子进入 `REVIEW_PENDING`；`SUBMITTED` 期间本人可使用相同 assignment 与最新 revision 覆盖提交，继续复用录音原子替换、历史和旧媒体清理。审核释放回到 `SUBMITTED`，审核通过进入 `COMPLETED`，驳回进入 `REWORK_PENDING`；关闭人工审核时提交仍直接进入 `COMPLETED`。提交修改与审核领取必须以状态和 revision/CAS 竞争，失败统一返回 `STALE_STATE`。应用启动时仅将 reviewerId、reviewAssignmentId 均为空的旧 `REVIEW_PENDING` 幂等迁移为 `SUBMITTED`，不修改已领取记录，日志只输出迁移数量。

Task 2 所有不在请求体内携带 operationId 的写接口必须要求 `Idempotency-Key`。通用幂等记录按 `(actorUserId, action, operationKey)` 唯一，先持久化 IN_PROGRESS 声明，成功后保存 COMPLETED 响应快照；重复请求返回首次结果，跨实例仍在处理的重复请求返回 `409 OPERATION_IN_PROGRESS`，不得重复执行底层 mutation。

当前录音固定保存到 `RECORDING_STORAGE_DIR/{taskCode}/{itemCode}.wav|mp3`。上传必须先写 `temp/`，校验扩展名、魔数、100MB、格式、单声道、任务采样率和时长，再原子替换；同一条目的替换流程按本地条目锁串行化，失败恢复旧文件。提交或释放成功后的旧稳定文件必须先隔离到 `temp/backups/` 唯一路径，并以 `media_cleanup_jobs` 持久化旧路径和 media ID；即时清理失败由同 operationId 重放和应用启动恢复重试，不得回滚已成功的 Mongo 状态，也不得把备份暴露为媒体路径。Mongo 只保存相对路径和元数据。采集员只能读取本人条目和录音；ACTIVE grant 只额外开放任务信息与参考媒体；ADMIN/REVIEWER 按审核权限读取。

新建、CSV 导入和待领取条目编辑的参考音视频统一使用 URL-only：只接受包含有效主机、不含用户名/密码信息的绝对 HTTPS URL，允许查询参数、签名 URL、无扩展名地址和片段；服务端不执行 DNS、HEAD、GET、重定向、Content-Type、大小、时长或魔数检查，也不创建参考媒体本地副本和 `media_assets`。编辑历史条目时，URL 未变化保留原媒体 ID，修改或移除后清空媒体 ID并通过 `media_cleanup_jobs` 清理旧副本。现有参考媒体不迁移、不删除；历史条目没有 URL 时继续使用仅开放 `REFERENCE_AUDIO/REFERENCE_VIDEO` 的 `GET /api/media/public/reference/{mediaId}`，任何 `RECORDING` 都不得从公开路径读取。

导入只支持 `.csv`，固定列为 `referenceText`、`referenceAudioUrl`、`referenceVideoUrl`；按任务 `referenceTypes` 读取，未启用列忽略，过滤后全空的行失败。普通后台添加和 CSV 导入不接收来源绑定，只使用系统生成的 itemCode；专用机器写入可选成对保存 `sourcePlatform`、`sourceItemId`，并在同一任务内唯一。返回 HTTP 202 和 importJobId，解析后立即持久化 totalRows；每处理完一行，以 `status=PROCESSING` 与 leaseOwner fencing 原子更新成功/失败计数、失败行、有限脱敏错误、心跳和租约。支持幂等、部分成功与失败行重试。单文件最多 50000 个数据行，脱敏行错误摘要最多保存 1000 条。初始导入和过期 PROCESSING 恢复固定使用 `FULL` 模式幂等重放完整源文件，只有用户显式失败行重试使用 `FAILED_ROWS`。

## 7. 接口说明

当前后端提供身份、会话、后台用户管理、语音生成、任务配置、授权、任务池、人工审核、任务级 AI 辅助转写、动态状态、软废弃恢复、录音媒体、导入及外部完成结果读取 API；不提供机器自动通过或自动驳回。
当前同时提供操作记录与统计 API：条目操作记录按权限读取，全局操作记录仅 ADMIN/REVIEWER；任务和指定采集员汇总仅 ADMIN，审核员可查看本人统计；采集员按最近提交任务查看当前 assignment 汇总、每日分段及倒序提交明细。
Web 数据大屏使用仅 ADMIN 可访问的 `GET /api/reports/dashboard`，返回真实任务生命周期数量、条目状态数量、当前采集人数、Asia/Shanghai 当日及最近 7 日首次提交统计和最多 8 个任务排行；最近操作仍复用 `/api/operations`。

所有 API 响应必须带 `X-Request-Id`；错误响应统一为 `{ code, message, requestId, details? }`。未预期异常只能返回脱敏摘要，不得返回堆栈、数据库内部消息、密钥或完整第三方 payload。统一状态至少覆盖 400、401、403、404、409、413、415、422、429、500 和 503。

缺字段、未知字段、类型错误和 malformed JSON 等请求结构问题统一返回 400，不支持的 `Content-Type` 返回 415；结构有效但新密码少于 8 个字符或 UTF-8 超过 BCrypt 的 72 字节上限、非法姓名或非法后台角色等业务值问题返回 422。DTO 校验不得让文档明确约定的 422 业务错误在 Controller 层提前变成 400。

身份与用户接口：

```text
请求方法：POST
请求路径：/api/auth/web/login
请求参数：JSON；username、password
响应结构：后台用户摘要；同时设置 HttpOnly、SameSite=Lax 的 REC_WEB_SESSION Cookie
错误码：401 INVALID_CREDENTIALS；409 ACCOUNT_IN_USE，details.takeoverToken 为短时一次性接管凭证
权限要求：公开
数据一致性要求：服务端仅保存令牌 SHA-256 哈希；单账号只允许一个 ACTIVE Web 会话
前端调用位置：apps/web/src/lib/authApi.js、apps/web/src/pages/auth/AdminLoginPage.vue
```

```text
请求方法：POST
请求路径：/api/auth/web/takeover
请求参数：JSON；takeoverToken
响应结构：后台用户摘要；设置新的 REC_WEB_SESSION Cookie
错误码：401 TAKEOVER_TOKEN_INVALID
权限要求：公开，但凭证短时且一次性
数据一致性要求：旧会话标记 REPLACED，新会话生效；旧设备下次请求返回 SESSION_REPLACED
前端调用位置：apps/web/src/lib/authApi.js、apps/web/src/pages/auth/AdminLoginPage.vue
```

```text
请求方法：GET
请求路径：/api/auth/web/csrf
请求参数：无业务参数；携带有效 REC_WEB_SESSION Cookie
响应结构：{ headerName, parameterName, token }；同时设置可读的 XSRF-TOKEN Cookie
错误码：401 会话无效、过期或已被接管
权限要求：已登录后台账号；首次登录待改密账号也允许访问
数据一致性要求：只生成或读取 CSRF token，不写入用户或业务数据
前端调用位置：apps/web/src/lib/httpClient.js
```

```text
请求方法：GET / POST / PUT
请求路径：/api/auth/web/me、/api/auth/web/logout、/api/auth/web/password、/api/auth/web/initial-password、/api/auth/web/initial-password/skip
请求参数：普通改密 JSON currentPassword、newPassword；首次改密 JSON newPassword；跳过和其余接口无业务参数
响应结构：当前用户摘要或 { success, reloginRequired? }
错误码：401 会话或凭证错误；403 PASSWORD_CHANGE_REQUIRED；422 PASSWORD_TOO_WEAK（新密码少于 8 个字符或 UTF-8 超过 72 字节）
权限要求：已登录后台账号
数据一致性要求：退出、普通改密和首次改密废止会话；首次登录改密后需要重新登录；跳过只清除首次改密标记并保持当前会话
前端调用位置：apps/web/src/lib/authApi.js、apps/web/src/pages/auth/FirstPasswordPage.vue
```

```text
请求方法：POST / GET / PUT / DELETE
请求路径：/api/auth/miniprogram/login、/account-login、/takeover、/profile、/profile/complete、/name、/password、/avatar
请求参数：微信登录 JSON code、可选 invitationCode；账号登录 JSON account/password；资料完成 JSON name、可选成对 account/password；改密 currentPassword/newPassword；头像 multipart avatar
响应结构：登录返回不透明 Bearer token、account、profileComplete、hasCustomAvatar；资料接口返回采集员摘要；头像 GET 返回文件流
错误码：503 WECHAT_NOT_CONFIGURED/WECHAT_UNAVAILABLE；401 WECHAT_LOGIN_FAILED/INVALID_CREDENTIALS/TAKEOVER_TOKEN_INVALID；403 INVITATION_REQUIRED/INVITATION_CODE_INVALID；409 ACCOUNT_IN_USE（details.takeoverToken 为短时一次性接管凭证）/USERNAME_EXISTS；413 AVATAR_TOO_LARGE；422 INVALID_NAME/ACCOUNT_PASSWORD_REQUIRED/INVALID_COLLECTOR_ACCOUNT/PASSWORD_TOO_WEAK/INVALID_AVATAR_FILE
权限要求：两种登录公开；其余仅 COLLECTOR 小程序 Bearer
数据一致性要求：微信和数字账号登录始终映射同一 `MINI-...` 小程序用户 ID；已有微信用户不需要邀请码，新微信身份未提供邀请码时不创建用户或会话，无效、停用和用尽统一返回 INVITATION_CODE_INVALID；邀请兑换以 AppID/OpenID 身份哈希原子声明和计数，重试、并发或中途失败不得重复扣次；有效姓名存在即 profileComplete=true；数字账号和密码必须同时提供或同时省略，未设置时可后补一次，设置后用户不可自行修改；数字账号仅在 `miniprogram_users` 内唯一；头像 DELETE 接口保留兼容但小程序无入口
前端调用位置：apps/miniprogram/services/session.js、services/api.js、pages/login、pages/profile
```

```text
请求方法与路径：GET /api/admin/miniprogram-invitations?page=&size=；POST /api/admin/miniprogram-invitations；POST /api/admin/miniprogram-invitations/{id}/disable
请求参数：创建 JSON name、可选 note、maxUses；停用携带 Idempotency-Key
响应结构：列表返回 {items,page,size,total} 且只含邀请码末四位；创建响应仅本次额外返回 invitationCode；停用返回脱敏邀请码摘要
错误码：400 请求结构错误或停用缺少 Idempotency-Key；403 CSRF_TOKEN_INVALID/ACCESS_DENIED；404 INVITATION_CODE_NOT_FOUND；422 INVITATION_NAME_INVALID/INVITATION_NOTE_INVALID/INVITATION_MAX_USES_INVALID
权限要求：仅 ADMIN，所有写请求需要 Web CSRF
数据一致性要求：邀请码使用 SecureRandom 生成 12 位无歧义大写字符并显示为 XXXX-XXXX-XXXX；输入忽略大小写、空格和连字符；数据库只保存 SHA-256 和末四位。名称最长 64 字、备注最长 200 字、使用次数 1–1000。创建不使用通用幂等响应快照；停用持久化幂等且永久不可恢复
前端调用位置：apps/web/src/lib/invitationApi.js、apps/web/src/pages/admin/system/InvitationCodesPage.vue
```

```text
请求方法与路径：POST /api/admin/users；GET /api/admin/users?page=&size=；POST /api/admin/users/{userId}/disable
请求参数：创建 JSON username、name、role、initialPassword；查询分页；停用路径参数
响应结构：不含 passwordHash、OpenID 或令牌的用户摘要/分页；用户摘要固定包含 `id`、`userType`、`loginName`、name、role、status 等字段，其中后台用户为 `WEB-...` 与 `WEB`
错误码：404 USER_NOT_FOUND；409 USERNAME_EXISTS；422 INVALID_BACKEND_ROLE/PASSWORD_TOO_WEAK
权限要求：仅 ADMIN
数据一致性要求：后台账号仅 ADMIN/REVIEWER；停用账号同时废止其活动会话
前端调用位置：apps/web/src/lib/userApi.js、apps/web/src/pages/admin/system/UsersPage.vue
```

```text
请求方法与路径：GET /api/admin/users/search?query=&role=&userType=&page=&size=；POST /api/admin/users/{userId}/reset-password；PUT /api/admin/users/{userId}/collector-account
请求参数：搜索支持姓名、完整前缀用户 ID 或登录名，以及可选角色和 `userType=WEB|MINIPROGRAM`；重置 JSON newPassword；改采集员账号 JSON account
响应结构：搜索返回 Spring Page<UserResponse>；重置密码或改采集员账号返回用户摘要；小程序用户摘要额外包含可空 invitationId、invitationName、invitationCodeSuffix、invitationRedeemedAt，后台用户固定为空
错误码：404 USER_NOT_FOUND；409 ACCOUNT_STATE_CHANGED/USERNAME_EXISTS；422 PASSWORD_TOO_WEAK/INVALID_COLLECTOR_ACCOUNT
权限要求：仅 ADMIN
数据一致性要求：`userType=WEB` 只查询 `web_users`，`userType=MINIPROGRAM` 只查询 `miniprogram_users`，省略时保持跨两集合合并搜索；与角色不匹配时返回空分页，不跨类型降级；ADMIN 可重置 ACTIVE Web 或 Mini 用户密码；Web 用户密码 BCrypt 编码、强制下次改密并废止全部会话，Mini 用户密码 BCrypt 编码、废止全部小程序会话但不设置 Web 首改密标记；改采集员账号仅作用于 ACTIVE Mini 用户，账号在 `miniprogram_users` 内唯一并废止其会话
前端调用位置：apps/web/src/lib/userApi.js、用户管理与任务采集权限页
```

任务池与导入接口：

```text
请求方法：POST
请求路径：/api/integrations/tasks/{taskId}/items
请求参数：JSON；referenceText、referenceAudioUrl、referenceVideoUrl 任意非空组合；可选 sourcePlatform、sourceItemId 必须同时提供；请求头 X-API-Key、Idempotency-Key
响应结构：HTTP 201 {itemId,taskId,itemCode,status,createdAt}
错误码：401 INVALID_INTEGRATION_API_KEY；503 INTEGRATION_NOT_CONFIGURED；409 INVALID_TASK_STATE/OPERATION_IN_PROGRESS/SOURCE_ITEM_ALREADY_BOUND；422 ITEM_REFERENCE_REQUIRED/REFERENCE_TYPE_NOT_ENABLED/REMOTE_URL_INVALID/SOURCE_BINDING_INVALID
权限要求：仅固定集成身份 INTEGRATION_IMPORT；Web Cookie、CSRF、小程序 Bearer 或其他角色均不能替代 X-API-Key
数据一致性要求：复用任务条目创建、序号、参考类型、URL-only 和持久化幂等规则；操作人固定为 annotation-script-center；来源字段成对非空时按 taskId、sourcePlatform、sourceItemId 唯一，不下载或代理外部媒体
调用位置：外部项目 XiangTianzhen/annotation-script-center 的服务器后端；浏览器扩展不得持有机器 Key
```

```text
请求方法：POST
请求路径：/api/integrations/tasks/by-code/{taskCode}/items
请求参数、响应、权限和错误：与内部 taskId 写入端点相同
数据一致性要求：先按可见 taskCode 解析内部任务；同一来源在同一任务内返回 409 SOURCE_ITEM_ALREADY_BOUND，不同任务允许保存相同来源；正常后台添加和 CSV 导入不自动写来源字段
调用位置：台州话脚本中心后端等只掌握可见任务编号的机器调用方
```

```text
请求方法：GET
请求路径：/api/integrations/items/{itemId}
请求参数：路径参数 itemId；请求头 X-API-Key
响应结构：{itemId,taskId,itemCode,status,updatedAt,text,audioAvailable}；仅 COMPLETED 返回当前结果文字及是否包含当前录音，其他状态固定 text=null、audioAvailable=false
错误码：401 INVALID_INTEGRATION_API_KEY；503 INTEGRATION_NOT_CONFIGURED；404 TASK_ITEM_NOT_FOUND
权限要求：仅固定集成身份 INTEGRATION_IMPORT；Web Cookie、小程序 Bearer 或 CSRF 均不能替代 X-API-Key
数据一致性要求：只读 task_items 当前快照；不返回参考内容、采集员、审核员、revision、草稿、审核中或返修中的结果内容；不写数据库
调用位置：外部项目 XiangTianzhen/annotation-script-center 的服务器后端；浏览器扩展不得持有机器 Key
```

```text
请求方法：GET
请求路径：/api/integrations/items/{itemId}/audio
请求参数：路径参数 itemId；请求头 X-API-Key；可选单个 Range
响应结构：200 完整录音流或 206 单段录音流，保留 Content-Type、Content-Length、Content-Range、Accept-Ranges
错误码：401 INVALID_INTEGRATION_API_KEY；503 INTEGRATION_NOT_CONFIGURED；409 INTEGRATION_RESULT_NOT_COMPLETED；404 TASK_ITEM_NOT_FOUND/INTEGRATION_RESULT_AUDIO_NOT_FOUND/MEDIA_NOT_FOUND/MEDIA_FILE_MISSING；416 INVALID_RANGE
权限要求：仅固定集成身份 INTEGRATION_IMPORT；只允许 COMPLETED 条目的当前结果录音
数据一致性要求：复用受保护录音的相对路径、文件存在性和 Range 读取能力；媒体必须为与当前条目一致的 RECORDING，不新增公开路径、不写数据库
调用位置：外部项目 XiangTianzhen/annotation-script-center 的服务器后端；浏览器扩展不得持有机器 Key
```

```text
请求方法：GET
请求路径：/api/health/ready
请求参数：无
响应结构：{ overall, mongo, storage }，字段只使用 UP/DOWN；全部就绪返回 200，任一项不就绪返回 503
错误码：无业务错误体；503 仍返回同一脱敏状态结构
权限要求：公开只读
数据一致性要求：只执行 Mongo ping 和录音根目录临时可写探针；不返回 URI、绝对路径、密码或异常文本
前端调用位置：本地启动与运维就绪检查，当前 Web 无必须调用
```

```text
请求方法：POST / GET / PUT / DELETE
请求路径：/api/tasks、/api/tasks/{taskId}、/api/tasks/{taskId}/publish|pause|resume|end
请求参数：创建和草稿编辑含 name、description、configuration，taskCode 由服务端生成；configuration 含 referenceTypes、resultType、humanReviewEnabled、recordingFormat、sampleRates、channels、minDurationMillis、maxDurationMillis、rejectionReasons、aiEnabled；所有写操作携带 Idempotency-Key；列表 page、size
响应结构：任务/权限视图或 {items,page,size,total}；创建返回 201
错误码：404 TASK_NOT_FOUND；409 INVALID_TASK_STATE；422 REFERENCE_REQUIRED、RESULT_TYPE_REQUIRED、RESULT_CONTENT_MISMATCH、AI_NOT_SUPPORTED 等
权限要求：写操作仅 ADMIN；ADMIN/REVIEWER 查询全部，COLLECTOR 查询进行中/已暂停任务及 ACTIVE/PENDING/NONE 权限状态，单任务详情仍需 ACTIVE 授权
数据一致性要求：taskCode 使用 Mongo 原子序列生成且不复用；仅 DRAFT 可编辑或真正删除，删除先隔离活动导入，安全重试后级联清理条目、导入记录/源文件、授权与申请；发布后定义永久冻结；RUNNING 必须先暂停，只有 PAUSED 可结束；DRAFT/RUNNING/PAUSED 均允许管理员准备数据，ENDED 拒绝新增；时长范围固定 1–600 秒；写操作持久化幂等
前端调用位置：apps/web/src/lib/taskApi.js、apps/web/src/pages/admin/tasks/*、apps/miniprogram/pages/tasks/*
```

```text
请求方法：POST / GET / DELETE
请求路径：/api/tasks/{taskId}/grants、/grants/{userId}、/access-requests、/access-requests/{requestId}/approve|reject
请求参数：直接授权 JSON userId；驳回可选 reason；申请、决策、直接授权和撤销均携带 Idempotency-Key；授权列表支持 page、size、可选 status 与 query，申请列表支持 page、size、可选 query；query 按姓名或登录账号模糊匹配、按完整用户 ID 精确匹配
响应结构：TaskGrant、TaskAccessRequest 或 {items,page,size,total}；授权与申请视图均包含 userName、userId、userLoginName
错误码：404 TASK/USER/ACCESS_REQUEST/GRANT_NOT_FOUND；409 TASK_ALREADY_GRANTED/ACCESS_REQUEST_DECIDED；422 INVALID_COLLECTOR
权限要求：申请仅 COLLECTOR；查询、直接授权、批准、驳回、撤销仅 ADMIN
数据一致性要求：同一任务/用户仅一个 PENDING；决策使用 PENDING 条件 CAS；批准幂等创建授权；所有写操作持久化幂等；撤销不影响已领取条目，批准重放不复活 REVOKED；搜索通过任务授权/申请与 miniprogram_users 关联后统计和分页，搜索后的 total 必须准确，不新增冗余用户字段
前端调用位置：apps/web/src/pages/admin/tasks/TaskPermissionsPage.vue、apps/miniprogram/pages/tasks/*
```

```text
请求方法：POST / GET
请求路径：/api/tasks/{taskId}/items、/api/tasks/{taskId}/items/start、/api/task-items/{itemId}、/api/task-items/mine
请求参数：单条添加 JSON referenceText/referenceAudioUrl/referenceVideoUrl + Idempotency-Key；start 携带 Idempotency-Key；管理列表支持 page、size、可重复 itemCode、itemCodeQuery、可重复 group=ALL|PENDING|SUBMITTED|FINISHED|DISCARDED、可重复 collectorId、includeUnassigned 和可重复 result=ALL|NONE|TEXT_ONLY|AUDIO_ONLY|TEXT_AND_AUDIO；mine 支持 taskId、kind=PENDING|SUBMITTED|FINISHED|DISCARDED，兼容 ALL|RECORDING|REWORK
响应结构：TaskItem 或 {items,page,size,total}；TaskItem 可包含 referenceAudioUrl、referenceVideoUrl、collectorName、reviewerName、currentDiscard，历史参考 URL 可为空，未分配用户姓名为空
错误码：404 NO_AVAILABLE_ITEM/TASK_ITEM_NOT_FOUND；409 ITEM_CONFLICT/INVALID_TASK_STATE；422 ITEM_REFERENCE_REQUIRED/REMOTE_URL_INVALID
权限要求：添加和任务条目列表仅 ADMIN；start 仅 COLLECTOR；详情仅 ADMIN/REVIEWER/当前采集员
数据一致性要求：DRAFT/RUNNING/PAUSED 可新增，ENDED 返回 INVALID_TASK_STATE；管理列表的 PENDING=RECORDING_PENDING+REWORK_PENDING、FINISHED=REVIEW_PENDING+COMPLETED；编号、状态、采集员、未分配与结果筛选均在服务端分页执行，同维度多值 OR、跨维度 AND，空集合或 ALL 不过滤；新条目只绑定 taskId，参考音视频只保存通过轻量语法校验的 HTTPS URL；itemCode 任务内递增唯一且不复用；添加和领取均持久化幂等
前端调用位置：apps/web/src/pages/admin/tasks/TaskDetailPage.vue 与 TaskPoolPage.vue、apps/miniprogram/pages/tasks/*、apps/miniprogram/pages/work/*；任务详情使用数字分页并支持每页 5/10/20 条（默认 10），小程序任务数据固定每页 10 条，独立 Web 任务数据池固定每页 20 条
```

```text
请求方法：PUT / DELETE
请求路径：/api/task-items/{itemId}
请求参数：编辑 JSON expectedRevision、referenceText、referenceAudioUrl、referenceVideoUrl；删除 query expectedRevision；均携带 Idempotency-Key
响应结构：编辑返回 TaskItem；删除返回 {itemId,deleted}
错误码：404 TASK_ITEM_NOT_FOUND；409 STALE_STATE；422 ITEM_REFERENCE_REQUIRED/REFERENCE_TYPE_NOT_ENABLED/REMOTE_URL_INVALID
权限要求：仅 ADMIN
数据一致性要求：仅 AVAILABLE 可按状态与 revision 原子编辑或删除；编辑使用 URL-only 校验，URL 未变化保留历史媒体 ID，修改或移除后清空媒体 ID并让旧媒体进入 media_cleanup_jobs；删除后的历史参考媒体同样持久化清理；itemCode 与 sequence 不复用
前端调用位置：apps/web/src/pages/admin/tasks/TaskItemDetailPage.vue
```

```text
请求方法：POST
请求路径：/api/task-items/{itemId}/submit、/release、/reject
请求参数：submit 在包含录音时使用 multipart operationId、assignmentId、expectedRevision、text?、audio、referenceAudioDurationMillis、referenceVideoDurationMillis；纯文本提交在同一路径使用 application/json，字段相同但不含 audio；release JSON operationId、expectedRevision、可选 note（最多 200 字）；reject JSON operationId、expectedRevision、reason
响应结构：{itemId,status,revision,assignmentId,result}
错误码：409 STALE_STATE/REFERENCE_DURATION_MISMATCH；413 UPLOAD_TOO_LARGE；422 录音格式/采样率/声道/时长/驳回原因错误，以及 REFERENCE_DURATION_REQUIRED/REFERENCE_DURATION_NOT_APPLICABLE
权限要求：submit/release 仅当前 COLLECTOR（ADMIN 也可 release）；reject 仅 ADMIN/REVIEWER
数据一致性要求：operationId 绑定操作者并返回首次结果；每次提交均核对参考音视频时长，不存在的参考源必须为 0，存在的参考源必须为正数；首次提交保存基准值，后续误差不超过 1000 毫秒时沿用基准，超过时拒绝；人工审核任务提交到 SUBMITTED 且领取前可覆盖提交，免审任务直接 COMPLETED；稳定 current 文件原子替换；驳回保留原采集员；释放备注写入操作记录，释放清当前结果、当前 assignment 的统计锚点与采集员归属，但保留提交/操作历史；提交/释放成功后的旧文件和 metadata 清理持久化并可由 operation 重放/启动恢复重试
前端调用位置：apps/miniprogram/pages/work/*、apps/web/src/pages/admin/review/*
```

```text
请求方法：POST
请求路径：/api/task-items/{itemId}/discard、/api/task-items/{itemId}/restore
请求参数：JSON operationId、expectedRevision；discard 可选 reason，COLLECTOR 必须填写 1–200 字，ADMIN 省略时使用安全默认原因
响应结构：TaskItem，废弃时 currentDiscard 含原因、操作人 ID/姓名/角色和时间
错误码：403 ACCESS_DENIED；409 STALE_STATE；422 INVALID_DISCARD_STATE/INVALID_DISCARD_REASON/INVALID_RESTORE_STATE
权限要求：ADMIN 可废弃和恢复；COLLECTOR 仅可废弃、恢复本人且废弃前为 RECORDING_PENDING 或 REWORK_PENDING 的条目
数据一致性要求：废弃、恢复均使用 revision/CAS 与持久化幂等；废弃保留归属、当前结果、参考源和媒体，外部结果继续返回 DISCARDED；恢复回废弃前状态并清除 currentDiscard，操作历史永久保留；历史 currentDiscard 缺失时前端安全占位，不迁移
前端调用位置：apps/miniprogram/pages/work/*、apps/web/src/pages/admin/tasks/*
```

```text
请求方法：POST / GET
请求路径：/api/batch-operation-jobs/preview、/api/batch-operation-jobs、/api/batch-operation-jobs/{jobId}、/api/batch-operation-jobs?taskId=&source=
请求参数：预览携带 taskId、source=TASK_DETAIL|TASK_POOL|REVIEW_QUEUE、excludedItemIds，以及可选 itemCodes、groups、collectorIds、includeUnassigned、results；旧 group、result 单值字段继续兼容；创建另含 operationId、action、可选 targetStatus/reviewerId
响应结构：预览返回 selectedCount、各动作 applicableCounts；创建返回 HTTP 202 BatchOperationJob；查询返回单个任务或当前用户最近 10 个任务
错误码：403 ACCESS_DENIED；404 BATCH_JOB_NOT_FOUND；409 BATCH_JOB_CONFLICT；422 INVALID_BATCH_SELECTION/EMPTY_BATCH_SELECTION/TARGET_STATUS_REQUIRED/REVIEWER_REQUIRED
权限要求：ADMIN 可使用三个来源页面及全部动作；REVIEWER 仅可在 REVIEW_QUEUE 创建 REVIEW_CLAIM
数据一致性要求：预览、快照、执行和重试统一使用编号、状态、采集员、未分配及结果筛选；创建时固化 itemId、revision、状态和动作所需结果快照；(actorUserId,operationId) 唯一并幂等重放；后台按条 CAS 执行、每条独立幂等键，状态变化计跳过，其他失败保存有限脱敏摘要；PROCESSING 使用租约、心跳和 nextSequence 检查点，租约过期或服务重启后续跑
前端调用位置：apps/web/src/lib/batchOperationApi.js、任务详情、独立任务数据池与审核池
```

```text
请求方法：GET
请求路径：/api/reports/me/tasks、/api/reports/me/tasks/{taskId}、/api/reports/me/tasks/{taskId}/submissions
请求参数：任务列表无业务参数；任务汇总使用 taskId 和可选 date=YYYY-MM-DD；完整提交记录使用 taskId、page、size 和可选 date=YYYY-MM-DD；日期为空表示全部
响应结构：任务列表 {items:[{taskId,taskCode,taskName,latestSubmittedAt}]}；任务汇总 {taskId,taskCode,taskName,summary,days,recentSubmissions}，summary 含 submissionCount、completedCount、recordingDurationMillis、referenceAudioDurationMillis、referenceVideoDurationMillis，days 按日期倒序且不含每日完成数，recentSubmissions 固定最近 3 条；完整记录返回分页结构
错误码：404 REPORT_TASK_NOT_FOUND
权限要求：仅当前 COLLECTOR 小程序 Bearer
数据一致性要求：直接聚合 task_items 当前数据库快照，仅统计仍属于当前采集员且具有 firstSubmittedAt 的当前 assignment；同一条目在一个 assignment 内只计 1 条，返修和覆盖提交不重复增加；当前结果录音时长始终取最新值，COMPLETED 时即最终值；参考音视频分别统计，无对应媒体时为 0；指定 date 后汇总、每日数据、最近 3 条和完整提交记录均限定 Asia/Shanghai 当天；管理员释放回 AVAILABLE 后该条目立即退出原采集员统计，历史 submissions/operations 不删除；任务列表和提交明细均按 latestSubmittedAt 倒序；Mongo 原始查询必须将 task_items 中的字符串 taskId 转换为 tasks 集合实际使用的 ObjectId 后读取任务资料
前端调用位置：apps/miniprogram/pages/statistics/*、apps/miniprogram/pages/submission-records/*
```

```text
请求方法：GET / POST
请求路径：/api/reviews/tasks、/api/reviews/tasks/{taskId}/pool|filter-users|claim|claim-batch、/api/reviews/{itemId}/claim|release|approve|reject、/api/reviews/assign、/api/reviews/batch/claim|assign|approve
请求参数：审核池支持可重复 itemCode、status=SUBMITTED|REVIEW_PENDING、collectorId、reviewerId、result，以及 itemCodeQuery、includeUnassignedReviewer；filter-users 仅接受 role=COLLECTOR|REVIEWER 和可选 query，并只从当前角色可见条目生成候选；领取头或请求体 operationId/Idempotency-Key；指定领取、释放和决定携带 expectedRevision；单条/批量分配携带 reviewerId；所选条目批量领取与分配携带 operationId 和最多 100 个 itemId/expectedRevision；通过的 text 表示审核最终答案；驳回携带 reasons/note
响应结构：任务审核摘要、TaskItem、审核池分页或逐条批量结果
错误码：404 NO_REVIEW_ITEM；409 STALE_STATE；422 INVALID_REVIEWER/INVALID_BATCH_SIZE/审核内容错误
权限要求：ADMIN/REVIEWER 可查看审核池并领取指定条目；按数量随机批量领取仅 REVIEWER；所选条目批量领取允许 ADMIN/REVIEWER；分配和批量通过仅 ADMIN；ADMIN/REVIEWER 只有作为当前 reviewerId 时才能释放，决定必须已有审核领取或分配
数据一致性要求：审核筛选始终与角色可见范围 AND 组合，并同步用于跨页预览、快照和执行；领取和分配只处理 SUBMITTED 并原子转 REVIEW_PENDING；释放原子回到 SUBMITTED；审核通过保留 currentResult 并单独写 reviewFinalAnswer，TEXT 为空时回退原采集文本且仍为空返回 REVIEW_FINAL_ANSWER_REQUIRED；所选条目批量操作按输入顺序返回逐条成功/失败结果
前端调用位置：apps/web/src/lib/reviewApi.js、apps/web/src/pages/admin/review/*
```

```text
请求方法：GET / PUT / POST
请求路径：/api/reviews/tasks/{taskId}/ai-config、/api/reviews/{itemId}/ai-jobs、/api/reviews/ai-jobs/{jobId}
请求参数：配置含 audio/text 两套 enabled、model、prompt、temperature、topP、maxTokens、timeoutMs，PUT 使用 Idempotency-Key；创建作业含 type=AUDIO_TRANSCRIBE|TEXT_REFINE、expectedRevision、operationId
响应结构：配置视图；作业仅返回 id、type、status、itemRevision、reviewAssignmentId、resultText、model、requestId、durationMillis 和脱敏失败摘要
错误码：409 STALE_STATE；413 REVIEW_AI_AUDIO_TOO_LARGE；422 AI_CONFIG_REQUIRED/AI_MODEL_INVALID/AI_PROMPT_INVALID/AI_TEMPERATURE_INVALID/AI_TOP_P_INVALID/AI_MAX_TOKENS_INVALID/AI_TIMEOUT_INVALID/REVIEW_AI_DISABLED/REVIEW_AI_AUDIO_REQUIRED/REVIEW_AI_TEXT_REQUIRED；503 REVIEW_AI_NOT_CONFIGURED
权限要求：ADMIN/REVIEWER 可读取配置，只有 ADMIN 可修改；只有当前条目的 reviewerId 可创建作业，作业仅创建者或 ADMIN 可读取
数据一致性要求：review_ai_configs 以 taskId 为主键；temperature 范围为 [0,2)，topP 范围为 (0,1]；review_ai_jobs 按 actorUserId+operationId 唯一并以 expiresAt 24 小时 TTL 清理，保存配置和媒体标识快照但不保存密钥、第三方原始响应或原始文本副本；音频作业上限 20MB；执行前再次校验 revision、reviewAssignmentId 和 reviewerId；并发 2、队列 100，使用唯一租约令牌恢复且不自动重试第三方 429/5xx
前端调用位置：apps/web/src/lib/reviewApi.js、ReviewAiSettingsPage.vue、ReviewWorkbenchPage.vue
```

```text
请求方法：GET
请求路径：/api/reports/tasks、/api/reports/tasks/{taskId}/collectors、/api/reports/collectors、/api/reports/reviewers
请求参数：任务汇总支持可选 fromDate、toDate；采集员/审核员汇总支持 userId、可选 taskId、fromDate、toDate；任务采集员排名另支持 sortBy=completedCount|submissionCount|recordingDurationMillis|referenceAudioDurationMillis|referenceVideoDurationMillis、page、size
响应结构：汇总保留 cumulativeSubmissions、cumulativeDurationMillis、currentCompletedCount、currentDurationMillis、releaseCount、discardCount，并增加 submissionCount、completedCount、recordingDurationMillis、referenceAudioDurationMillis、referenceVideoDurationMillis；排名分页返回采集员 ID、姓名及五项统计
错误码：403 ACCESS_DENIED；422 INVALID_REPORT_DATE_RANGE
权限要求：任务、指定采集员汇总及排名仅 ADMIN；审核员汇总 ADMIN 可查指定审核员，REVIEWER 仅可查本人
数据一致性要求：日期使用 Asia/Shanghai 自然日闭区间，允许单边日期；fromDate 晚于 toDate 返回 422；统计直接聚合当前有效 task_items，按 firstSubmittedAt 计提交/完成，AVAILABLE 与 DISCARDED 不计入当前五项统计，时长缺失按 0；姓名通过身份目录批量补全，不冗余写入任务条目
前端调用位置：apps/web/src/lib/reportApi.js、apps/web/src/pages/admin/reports/*
```

```text
请求方法：POST / GET
请求路径：/api/import-jobs、/api/import-jobs/{jobId}、/api/import-jobs/{jobId}/retry
请求参数：创建 multipart taskId、file + Idempotency-Key；重试路径参数 + Idempotency-Key
响应结构：创建/重试 HTTP 202 {importJobId,status}；查询返回 ImportJob
错误码：409 IMPORT_JOB_NOT_RETRYABLE；413/415 文件限制；422 IMPORT_HEADER_INVALID/IMPORT_FILE_INVALID/行错误
权限要求：仅 ADMIN
数据一致性要求：taskId+operationId 唯一；通用写幂等；最多 50000 行、每行原子 checkpoint 进度与租约、错误摘要上限 1000 条；保留完整失败行号；10 分钟租约、心跳和启动恢复；允许部分成功和失败行重试；成功行完整签名 URL 不留存在导入重试文件
前端调用位置：后续管理员数据导入页
```

```text
请求方法：GET
请求路径：/api/media/{mediaId}
请求参数：可选单个 Range 请求头
响应结构：200 文件流或 206 单段 ResourceRegion
错误码：403 MEDIA_ACCESS_DENIED；404 MEDIA_NOT_FOUND/MEDIA_FILE_MISSING；416 INVALID_RANGE
权限要求：已认证并通过条目/角色鉴权
数据一致性要求：只读相对路径文件，防路径穿越；不写数据库
前端调用位置：后续录音与审核播放器
```

```text
请求方法：GET
请求路径：/api/media/public/reference/{mediaId}
请求参数：可选单个 Range 请求头
响应结构：200 文件流或 206 单段 ResourceRegion
错误码：404 MEDIA_NOT_FOUND/MEDIA_FILE_MISSING；416 INVALID_RANGE
权限要求：公开，但仅允许 REFERENCE_AUDIO、REFERENCE_VIDEO；RECORDING 固定不可读取
数据一致性要求：只读后端参考媒体副本，不返回录音结果，不写数据库
前端调用位置：apps/miniprogram/pages/work/* 的历史条目兼容播放
```

语音生成接口说明：

```text
请求方法：POST
请求路径：/api/voice-generation/preview
请求参数：multipart/form-data；audio=参考音频文件，text=合成文本，speed=语速，volume=音量，pitch=语调
响应结构：{ recordId, mode, status, message, audioUrl }
错误码：400 参数缺失、MiniMax 配置缺失或调用失败
权限要求：仅 ADMIN
数据一致性要求：成功后写入 MongoDB 记录并将音频保存到本地存储目录；MiniMax 失败时记录更新为 FAILED
前端调用位置：apps/web/src/lib/voiceGenerationApi.js
```

```text
请求方法：POST
请求路径：/api/voice-generation/synthesize
请求参数：JSON；voiceId、text、speed、volume、pitch
响应结构：{ recordId, mode, status, message, audioUrl }
错误码：400 参数缺失、MiniMax 配置缺失或调用失败
权限要求：仅 ADMIN
数据一致性要求：成功后写入 MongoDB 记录并将音频保存到本地存储目录；MiniMax 失败时记录更新为 FAILED
前端调用位置：apps/web/src/lib/voiceGenerationApi.js
```

```text
请求方法：POST
请求路径：/api/voice-generation/voices/clone
请求参数：multipart/form-data；audio=母带音频文件，voiceId=新音色 ID
响应结构：{ success, message }
错误码：400 参数缺失、MiniMax 配置缺失或调用失败；413 母带音频超过上传大小限制；调用失败时只返回 MiniMax 状态摘要，不返回完整请求 payload
权限要求：仅 ADMIN
数据一致性要求：后端先执行 20MB 业务限制，再上传母带获取 MiniMax file_id 并以数值类型提交克隆请求；成功后写入一条 CLONE 类型 MongoDB 记录
前端调用位置：apps/web/src/lib/voiceGenerationApi.js
```

```text
请求方法：GET
请求路径：/api/voice-generation/voices
请求参数：excludeSystem=true|false
响应结构：MiniMax 音色列表 JSON；excludeSystem=true 时过滤 system_voice
错误码：400 MiniMax 配置缺失或调用失败
权限要求：仅 ADMIN
数据一致性要求：只读 MiniMax 音色资产，不写入本地数据库
前端调用位置：apps/web/src/lib/voiceGenerationApi.js
```

```text
请求方法：DELETE
请求路径：/api/voice-generation/voices/{voiceId}
请求参数：路径参数 voiceId
响应结构：{ success, message }
错误码：400 MiniMax 配置缺失或调用失败
权限要求：仅 ADMIN
数据一致性要求：调用 MiniMax 删除音色，不删除本地生成记录
前端调用位置：apps/web/src/lib/voiceGenerationApi.js
```

```text
请求方法：GET
请求路径：/api/voice-generation/records
请求参数：page、size
响应结构：{ items, page, size, total }
错误码：400 查询失败
权限要求：仅 ADMIN
数据一致性要求：从 MongoDB 按创建时间倒序分页读取
前端调用位置：apps/web/src/lib/voiceGenerationApi.js
```

```text
请求方法：GET
请求路径：/api/voice-generation/audio/{recordId}
请求参数：路径参数 recordId
响应结构：音频文件流
错误码：400 记录不存在或本地音频文件已清理
权限要求：仅 ADMIN
数据一致性要求：只读取本地音频文件，不写入数据库
前端调用位置：apps/web/src/lib/voiceGenerationApi.js
```

```text
请求方法：GET / PUT
请求路径：/api/voice-generation/config/default
请求参数：GET 无参数；PUT JSON voiceId、speed、volume、pitch
响应结构：{ id, voiceId, speed, volume, pitch, updatedAt }
错误码：400 参数缺失或保存失败
权限要求：仅 ADMIN
数据一致性要求：写入或读取 MongoDB `voice_generation_configs` 的 `default` 文档；未保存时返回安全默认值
前端调用位置：apps/web/src/lib/voiceGenerationApi.js
```

后续设计接口时，必须在本文件记录：

```text
请求方法
请求路径
请求参数
响应结构
错误码
权限要求
数据一致性要求
前端调用位置
```

涉及任务发布、任务领取锁定、录音上传、机器审核、一审、二审、驳回重录和任务完成的接口，必须在实现前确认字段和状态流转。当前身份体系固定使用服务端不透明会话，不得未经确认改为 JWT。

前端只负责采集、展示、调用接口和触发下载；后端负责数据持久化、文件落盘、聚合、接口和服务启动。

## 8. 数据库说明

当前后端使用 MongoDB，连接变量为 `MONGODB_URI`，默认数据库为 `recording_platform`，允许 Spring Data 自动创建声明式索引。数据库时间字段统一使用 UTC `Instant`。

当前集合：

```text
集合名称：web_users
字段名称：id（`WEB-` 前缀）、version、username、name、passwordHash、role、status、firstPasswordChangeRequired、createdAt、updatedAt
字段类型：字符串、枚举、布尔值、UTC Instant
默认值：新后台账号 ACTIVE 且 firstPasswordChangeRequired=true
唯一约束：username
索引：username 唯一索引
数据兼容策略：仅保存后台 ADMIN/REVIEWER 身份；不得把明文密码写入
迁移步骤：身份拆分后使用 `WEB-` 前缀 ID，旧统一身份集合不再作为当前运行集合
回滚方式：拆分或清库前备份相关集合；不要直接删除已创建用户
```

```text
集合名称：miniprogram_users
字段名称：id（`MINI-` 前缀）、version、account、name、passwordHash、status、wechatAppId、wechatOpenId、invitationId、invitationName、invitationCodeSuffix、invitationRedeemedAt、avatarPath、avatarContentType、avatarUpdatedAt、createdAt、updatedAt
字段类型：字符串、布尔值、UTC Instant
默认值：新微信用户 ACTIVE；角色不在本集合存储，小程序身份在鉴权与响应中固定视为 COLLECTOR
唯一约束：稀疏 account；稀疏复合 (wechatAppId, wechatOpenId)
索引：account 唯一稀疏索引；(wechatAppId, wechatOpenId) 唯一稀疏复合索引
数据兼容策略：仅保存小程序采集员身份；新微信用户保存脱敏邀请码来源，历史用户允许四个邀请码来源字段为空；不得把完整邀请码、邀请码哈希、明文密码、微信 session_key 或客户端提交的 openId 写入管理员响应
迁移步骤：不回填历史用户；身份拆分后使用 `MINI-` 前缀 ID，旧统一身份集合不再作为当前运行集合
回滚方式：拆分或清库前备份相关集合；不要直接删除已创建用户
```

```text
集合名称：miniprogram_invitation_codes
字段名称：name、note、codeHash、codeSuffix、maxUses、usedCount、status、redemptionIdentityHashes、createdByUserId、createdByName、createdAt、disabledByUserId、disabledByName、disabledAt
字段类型：字符串、整数、ACTIVE/DISABLED、数组、UTC Instant
默认值：新邀请码 ACTIVE、usedCount=0
唯一约束：codeHash
索引：codeHash 唯一索引
数据兼容策略：只保存规范化邀请码 SHA-256 与末四位，不保存或恢复完整邀请码；EXHAUSTED 为 usedCount 达到 maxUses 时的派生状态
迁移步骤：无需迁移现有小程序用户，历史 `miniprogram_users` 全部视为已准入且不扣次数
回滚方式：只允许停用，不编辑、删除或重新启用；不得单独回滚准入门禁使公众身份重新自动建号
```

```text
集合名称：miniprogram_invitation_claims
字段名称：identityHash（主键）、invitationId、userId、createdAt、completedAt
字段类型：字符串、UTC Instant
默认值：预留阶段 userId、completedAt 为空，用户创建成功后补全
唯一约束：identityHash
索引：identityHash 主键唯一
数据兼容策略：identityHash 为 AppID 与 OpenID 组合的 SHA-256；同一微信身份只允许声明一个邀请码名额，不保存原始 OpenID
迁移步骤：本阶段首次建立，无历史声明迁移
回滚方式：排障时需连同邀请码计数和新建用户一起核对，禁止只删声明或只回退 usedCount
```

```text
集合名称：sessions
字段名称：userId、tokenHash、type、status、replacedSessionId、createdAt、lastAccessAt、expiresAt
字段类型：字符串、枚举、UTC Instant
默认值：新会话 ACTIVE；Web 空闲 12 小时；小程序 30 天；接管凭证 5 分钟
唯一约束：tokenHash；同一用户最多一个 ACTIVE WEB 会话，且最多一个 ACTIVE MINIPROGRAM 会话
索引：expiresAt TTL（expireAfterSeconds=0，由 MongoDB 异步自动删除过期文档）；(userId, status)；ACTIVE WEB 部分唯一索引；ACTIVE MINIPROGRAM 部分唯一索引
数据兼容策略：只保存 SHA-256 tokenHash，不保存原始 Cookie/Bearer/takeoverToken
迁移步骤：本阶段为首次建立，无旧会话迁移
回滚方式：可废止全部会话并要求重新登录，不得导出或恢复原始令牌
```

```text
集合名称：tasks
字段名称：taskCode、name、description、lifecycle、configuration、itemSequence、createdAt、updatedAt；configuration 含 referenceTypes、resultType、humanReviewEnabled、recordingFormat、sampleRates、channels、minDurationMillis、maxDurationMillis、rejectionReasons、aiEnabled、aiProvider、aiModel
字段类型：字符串、枚举、嵌套文档、集合、布尔值、整数、UTC Instant
默认值：DRAFT、itemSequence=0、humanReviewEnabled=true、channels=1、minDurationMillis=1000、maxDurationMillis=600000、aiEnabled=false
唯一约束：taskCode
索引：taskCode 唯一；lifecycle
数据兼容策略：`itemSequence` 是任务内编号持久化计数器；历史导入或迁移使其低于 `task_items.sequence` 最大值时，下一次有效创建自动抬高，无需手工改库；计数器已高于最大值时继续递增，不回退或复用。发布后任务定义不再修改，条目通过 taskId 读取配置
迁移步骤：停止服务后使用受保护脚本清空本地开发数据库并按新模型重建；不在生产数据上直接执行
回滚方式：恢复代码与重置前备份；不得在存在业务数据的环境直接删除集合
```

```text
集合名称：task_grants、task_access_requests
字段名称：grant 含 taskId、userId、status、grantedBy、时间；request 含 taskId、userId、status、decidedBy、decisionReason、时间
字段类型：字符串、ACTIVE/REVOKED、PENDING/APPROVED/REJECTED、UTC Instant
默认值：新授权 ACTIVE；新申请 PENDING
唯一约束：grant(taskId,userId)；request 同一 taskId/userId 仅一个 PENDING
索引：grant 复合唯一与 user/status；request PENDING 部分唯一与 task/status
数据兼容策略：撤销保留授权文档；申请决策追加状态且使用 PENDING CAS
迁移步骤：本阶段首次建立，无旧授权迁移
回滚方式：不删除历史申请；必要时显式撤销授权
```

```text
集合名称：task_items
字段名称：taskId、sequence、itemCode、creationOperationId、status、collectorId、reviewerId、assignmentId、reviewAssignmentId、revision、参考字段、referenceAudioDurationMillis、referenceVideoDurationMillis、firstSubmittedAt、latestSubmittedAt、currentResult、currentRejection、currentDiscard、submissions（含提交时 collectorId）、operations、discardedPreviousStatus、createdAt、updatedAt
字段类型：字符串、枚举、数值、嵌套文档、数组、UTC Instant
默认值：新条目 AVAILABLE、revision=0、历史数组为空
唯一约束：(taskId,itemCode)；creationOperationId 存在时任务内唯一；普通 RECORDING_PENDING 与 REWORK_PENDING 均不设采集员持有数量唯一约束
索引：上述业务唯一索引；(taskId,status,sequence) 领取索引；(collectorId,status)；普通查询索引 (collectorId,taskId,status)
数据兼容策略：条目通过 taskId 读取已冻结任务配置；currentDiscard 只表示当前废弃标记，恢复后清除，历史缺失时安全占位；当前结果可替换/清除，提交与操作历史只追加；个人统计只读当前 assignment 字段，不回扫历史 submissions；采集员废弃次数与管理员废弃次数均计入流程统计
迁移步骤：本轮按已确认方案不兼容旧统计数据，更新后重置数据库；应用启动时仍先确保普通索引 `collector_task_status` 创建成功，再幂等删除旧 `unique_collector_recording_pending` 与 `unique_collector_task_recording_pending`，失败则终止启动
回滚方式：备份集合与本地媒体；恢复任一旧唯一索引前必须先处理与其口径冲突的多条普通 RECORDING_PENDING，否则索引无法重建；不得只回滚 Mongo 或只回滚文件
```

```text
集合名称：batch_operation_jobs、batch_operation_items
字段名称：job 含 operationId、taskId、source、action、动作参数、操作者、状态、选中/适用/处理/成功/失败/跳过计数、nextSequence、失败摘要、租约和时间；item 含 jobId、sequence、itemId、expectedRevision、状态及动作所需快照
字段类型：字符串、枚举、数值、数组、UTC Instant
默认值：任务 PENDING、计数和 nextSequence 为 0、失败摘要为空
唯一约束：job(actorUserId,operationId)；item(jobId,sequence)
索引：上述复合唯一索引；job(actorUserId,createdAt)
数据兼容策略：跨页选择只依赖创建时固化快照，不回写 task_items 冗余批次字段；失败摘要不保存敏感 URL 或异常堆栈
迁移步骤：无需迁移，首次运行由 Spring Data 建立新集合与索引
回滚方式：停止新建批处理并等待运行中任务结束；回滚代码后可保留两个集合供审计，不影响 task_items
```

```text
集合名称：media_assets
字段名称：taskId、itemId、kind、relativePath、contentType、sizeBytes、audioFormat、sampleRate、channels、durationMillis、sourceHostname、sourceStatus、sourceErrorSummary、createdAt
字段类型：字符串、枚举、数值、UTC Instant
默认值：无
唯一约束：MongoDB _id；relativePath 非唯一以支持 current 录音元数据替换
索引：taskId、itemId、(taskId,itemId,kind)
数据兼容策略：只保存相对路径；不保存签名 URL、Cookie 或请求头
迁移步骤：本阶段首次建立，无旧媒体元数据迁移
回滚方式：Mongo 与本地文件一起备份，不提交 storage 运行产物
```

```text
集合名称：media_cleanup_jobs
字段名称：itemId、operationId、relativePaths、mediaAssetIds、status、attempt、lastErrorSummary、createdAt、updatedAt、completedAt
字段类型：字符串、字符串数组、PENDING/COMPLETED、整数、UTC Instant
默认值：PENDING、attempt=0、路径和 media ID 数组为空
唯一约束：(itemId,operationId)
索引：(itemId,operationId) 唯一；(status,createdAt) 恢复索引
数据兼容策略：只保存相对 backup 路径和旧 media ID；每次尝试前先持久化 attempt；文件与 metadata 删除均按幂等方式重试，错误摘要不得包含绝对路径或敏感 URL
迁移步骤：本阶段首次建立，无旧清理任务迁移；应用启动扫描 PENDING
回滚方式：回滚前先完成或人工核对 PENDING，Mongo 与 `temp/backups/` 必须一起处理
```

```text
集合名称：import_jobs
字段名称：taskId、operationId、actorUserId/Username、originalFilename、fileSha256、fileSizeBytes、sourceRelativePath、status、runMode、totalRows、successRows、failureRows、rowErrors、retryRowNumbers、leaseOwner、leaseExpiresAt、heartbeatAt、attempt、时间
字段类型：字符串、枚举、数值、数组、UTC Instant
默认值：PENDING、runMode=FULL、计数为 0、错误和失败行号为空、attempt=0
唯一约束：(taskId,operationId)
索引：(taskId,operationId) 唯一；(status,createdAt)；(status,leaseExpiresAt) 恢复索引
数据兼容策略：错误信息脱敏且最多 1000 条，完整失败行号单独保存；FULL 恢复重放完整源并依赖逐行 operationId 防重复，FAILED_ROWS 只用于用户显式重试；所有 worker 状态写按 leaseOwner fencing；部分成功源文件改写为失败行 retry.csv；完成后删除临时源文件；旧 PENDING 或无租约 PROCESSING 可由恢复 worker 接管
迁移步骤：本阶段首次建立，无旧导入迁移
回滚方式：保留 Mongo 摘要；临时源缺失时不得声称可重试
```

```text
集合名称：idempotency_records
字段名称：actorUserId、action、operationKey、status、responseJson、createdAt、updatedAt、version
字段类型：字符串、IN_PROGRESS/COMPLETED、UTC Instant、数值
默认值：新声明 IN_PROGRESS，业务成功并保存首次响应后 COMPLETED
唯一约束：(actorUserId,action,operationKey)
索引：上述复合唯一索引；updatedAt
数据兼容策略：只保存响应 JSON，不保存 Cookie、Token、Authorization 或原始敏感请求；IN_PROGRESS 不得被重复 mutation 绕过
迁移步骤：本阶段首次建立，无旧幂等记录迁移
回滚方式：回滚前备份；不得在仍可能重放写请求时直接清空记录
```

```text
集合名称：voice_generation_records
字段名称：mode、status、text、voiceId、speed、volume、pitch、audioPath、audioFormat、durationMillis、message、createdAt
字段类型：枚举、字符串、数值、UTC Instant
默认值：生成开始 PENDING，成功 COMPLETED，MiniMax 合成失败 FAILED
唯一约束：MongoDB _id
索引：createdAt
数据兼容策略：保留现有 VoiceGenerationRecordStore 抽象和接口路径；本地音频路径继续使用
迁移步骤：旧实现为进程内存，无可迁移历史数据；部署后新记录写入 MongoDB
回滚方式：回滚代码会停止读取持久化记录，但不得删除集合或本地音频
```

```text
集合名称：voice_generation_configs
字段名称：_id=default、voiceId、speed、volume、pitch、updatedAt
字段类型：字符串、数值、UTC Instant
默认值：未保存时返回安全默认声音配置
唯一约束：MongoDB _id
索引：MongoDB _id
数据兼容策略：Controller 无状态，通过 service/repository 读写
迁移步骤：旧实现为进程内存，无可迁移历史数据
回滚方式：回滚前备份 default 文档；不得写入 MiniMax API Key
```

后续机器审核、一审/二审记录、完整状态流转和操作日志扩展必须在对应阶段重新确认原子更新、唯一约束、索引、兼容和回滚方案，不得沿用未确认的旧 PostgreSQL 设计假设。

后续涉及数据库设计时，必须在本文件记录：

```text
集合名称
字段名称
字段类型
默认值
唯一约束
索引
数据兼容策略
迁移步骤
回滚方式
```

计划中的核心数据对象包括：

- 用户
- 任务
- 任务领取记录
- 录音文件元数据
- 审核记录
- 审核状态流转

没有迁移脚本时，不得声称迁移已经完成。不得直接破坏已有数据结构。

## 9. 审核流程原则

当前已实现可配置的单层人工审核：先选择有已提交或待审核数据的任务，再进入对应审核池。采集员提交后处于 `SUBMITTED` 并可继续修改；管理员或审核员领取、或管理员分配后才进入 `REVIEW_PENDING` 并锁定采集修改。审核员和管理员均可释放本人占用；非当前审核人统一返回状态冲突。审核通过将审核最终答案保存到 `reviewFinalAnswer`，不得覆盖只读的 `currentResult`；外部完成结果优先返回最终答案。驳回进入原采集员的 `REWORK_PENDING` 返修队列，提交和操作历史永久保留。

管理员状态管理支持任务配置允许的动态阶段、批量逐条结果、媒体安全释放、软废弃和恢复。普通状态调整不能进入待领取、待审核，也不能绕过人工审核直接进入已完成；返回池必须调用释放，人工审核完成必须走领取或分配后的审核决定。废弃不删除归属、结果或文件，恢复回废弃前状态并重新校验任务配置和 revision。

AI 辅助审核使用 `review_ai_configs` 和带 24 小时 TTL 的 `review_ai_jobs`，并发固定为 2、队列上限 100。音频只读取当前采集录音，AI 转写单条录音上限为 20MB，超限返回 413 `REVIEW_AI_AUDIO_TOO_LARGE`；文本只处理当前采集文本。配置、作业和返回均不得保存或泄露 API Key、第三方原始响应或参考源。机器自动审核及多级一审/二审仍属于后续范围。

## 10. 文档同步要求

代码、接口、数据库、环境变量、目录结构或验证步骤变化时，必须同步更新：

```text
README.md
AGENTS.md
log.md
```

AI 辅助产生的明确改动记录写入 `log.md`。不要恢复 docs 目录，除非用户明确要求重新建立文档体系。

## 11. AI 修改日志

`log.md` 是本项目固定的 AI 修改日志文件。每次由 Codex 修改代码、文档、目录结构、配置或验证流程时，都必须同步更新 `log.md`。

每条 AI 修改日志必须包含：

```text
时间：YYYY-MM-DD HH:mm
commit ID：<对应提交的短 hash>
修改内容
验证结果
```

时间必须精确到分钟，例如 `2026-06-23 19:30`，优先使用当前本地时间或实际提交时间。

提交前如果尚不知道最终 commit ID，可以先写 `commit ID：待提交后补记`。由于 Git commit hash 会受到文件内容影响，不能把“本次提交最终 hash”稳定写入产生该 hash 的同一个提交中；需要固化到 `log.md` 时，应在后续日志维护中补记上一轮已完成提交的 commit ID。仅用于把上一轮 `commit ID：待提交后补记` 替换为实际 hash 的日志维护提交，可以不新增单独日志条目，避免形成无限补记。最终回复仍必须包含本轮实际 commit hash 和 push 结果。

日志中不得写入 API Key、Token、Cookie、Authorization 头、真实客户数据、员工敏感信息、完整签名 URL、完整音频 URL 或未脱敏截图内容。

## 12. 安全与禁止事项

禁止提交：

- API Key、Token、Cookie、Authorization 头
- 真实用户隐私数据、员工敏感信息、客户数据、合同内容
- 未脱敏截图、完整签名 URL、完整音频 URL
- `.env` 中的真实配置
- `node_modules/`、`dist/`、运行日志、数据库文件、上传音频文件

日志和文档只能保留必要摘要，例如状态码、请求 ID、hostname、错误摘要和耗时。

如果用户提供的请求、截图或日志包含敏感信息，先提醒脱敏，不要把敏感内容写入代码、文档或测试文件。

## 13. 验证要求

优先运行项目中真实存在的可用命令：

```bash
npm run build
node --check <file>
```

后端在 Windows PowerShell 中优先运行：

```powershell
.\mvnw.cmd test
```

如果命令不存在，需要说明原因，不得伪造验证结果。

修改 JavaScript 文件时，优先对实际修改过的 JS 文件运行：

```bash
node --check <修改过的 JS 文件>
```

修改 JSON 文件时，需要确认 JSON 可解析。修改前后端启动、构建或测试逻辑时，需要运行对应模块的真实验证命令。

## 14. Codex 最终输出要求

每次执行完成后，最终输出必须包含：

```text
1. 修改摘要
2. 修改文件列表
3. 文档更新情况
4. 验证命令和结果
5. 风险点
6. Git 状态
7. 后续建议
```

如果已提交 commit 与 push，最终输出必须包含 commit hash 和 push 结果。

如果没有提交，最终输出必须给出建议的 git commit 命令。
