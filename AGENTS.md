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
任务版本、授权、任务池、录音媒体与导入后端闭环
根目录 README.md
根目录 log.md
本文件 AGENTS.md
```

当前已实现身份、会话、后台用户管理、微信登录边界、语音生成持久化，以及不可变任务版本、授权申请、任务池领取、录音提交/返修/释放、人工审核、动态状态、软废弃恢复、媒体读取和 CSV 导入后端闭环；Web 已实现后台身份、任务/版本、数据池/导入、权限、审核、用户、操作记录与统计页面。平台领域已整体移除，仍不实现机器审核执行或真实 AI 转写。

Spring Security 已配置为不透明服务端会话，不使用 JWT。除 Web/微信登录与接管接口外，其余 `/api/**` 默认认证；管理员、任务管理、授权管理、导入和语音生成接口按角色保护，采集写接口仅允许 `COLLECTOR` 小程序 Bearer 身份。

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

后台登录页为 `/login`，首次改密页为 `/first-password`。所有 Web API 调用应优先复用 `apps/web/src/lib/httpClient.js`，由该模块统一携带同源 Cookie、获取并回传 CSRF、解析统一错误和处理 `SESSION_REPLACED`；`CSRF_TOKEN_INVALID` 只允许刷新令牌后自动重试一次，`ACCESS_DENIED` 不得自动重试。不得在业务模块另写一套绕过 CSRF 的 fetch。

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
REMOTE_MEDIA_ALLOW_HTTP（默认 false，生产必须保持 false）
REMOTE_MEDIA_TIMEOUT_SECONDS（默认 15）
REMOTE_MEDIA_MAX_REDIRECTS（默认 3）
WECHAT_APP_ID
WECHAT_APP_SECRET
INITIAL_ADMIN_USERNAME
INITIAL_ADMIN_PASSWORD
WEB_SESSION_IDLE_HOURS（默认 12）
MINIPROGRAM_SESSION_DAYS（默认 30）
WEB_SESSION_COOKIE_SECURE（默认 false，生产 HTTPS 应设 true）
```

`RECORDING_STORAGE_DIR` 为相对路径时必须按仓库根目录解析，默认 `backend/storage/recordings`；不得按 Spring Boot 的 `backend/` 工作目录再次拼接 `backend`。绝对路径保持原值。启动前置检查、录音存储、导入临时文件和就绪检查必须使用同一目录语义。

根目录 `.env.example` 只能提供空值或安全默认值。Web 会话 Cookie 必须为 HttpOnly、SameSite=Lax；CSRF 使用可读的 `XSRF-TOKEN` Cookie 与 `X-XSRF-TOKEN` 请求头，已登录 Web 用户通过 `GET /api/auth/web/csrf` 获取 token，首次登录待改密账号也必须允许访问该端点。CSRF 缺失或失效必须返回 `403 CSRF_TOKEN_INVALID`，真实角色越权返回 `403 ACCESS_DENIED`，不得使用同一错误码混淆两类问题。服务端和 MongoDB 只保存会话令牌哈希。微信登录必须由后端用临时 code 调用 `jscode2session`，不得信任客户端直接提交的 OpenID。

### 6.4.1 一次性旧录音路径迁移规则

`RECORDING_PATH_MIGRATION_ENABLED` 必须默认关闭，只能在停止全部业务写入、同时备份完整录音目录与 `media_assets`、`task_items`、`media_cleanup_jobs`、`idempotency_records` 四个集合后，以单后端实例显式启用。迁移成功后必须在停写状态下再运行一次并确认 `migrated=0`、`deduplicated=0`，随后立即删除该环境变量或设回 `false`，再恢复正常服务。

迁移失败时不得恢复业务写入或单独删除新路径文件。应检查新旧文件 SHA-256、四个集合的路径和版本；MongoDB 回滚无法确认时必须保留新文件并补回旧路径。无法人工确认一致性时，MongoDB 集合与录音目录必须作为同一恢复单元从备份一并还原。具体命令与故障处理以 `README.md` 的“一次性旧录音路径迁移”为准。

## 6.5 任务池、媒体与导入规则

任务结构固定使用 `tasks` + `task_versions`。任务发布后版本快照不可原地覆盖；结构修改必须创建下一版本，已有条目继续绑定旧版本。任务至少启用 TEXT/AUDIO/VIDEO 一种参考组件，所有任务都必须录音；最终成果为 `TEXT` 时提交录音和文本，为 `AUDIO` 时只提交录音。录音格式、采样率、声道和时长规则对两种成果类型均生效。关闭人工审核时不得保存驳回预设原因；首期 `aiEnabled` 必须为 false。任务编码由数据库序列自动生成 `T000001`，条目编码为 `{taskCode}-{7位序号}`，不接受前端输入且序号不复用。

采集员领取必须同时满足任务 RUNNING 和 ACTIVE grant；全系统每名采集员最多一条普通 `RECORDING_PENDING`，领取使用 Mongo `findAndModify` 与部分唯一索引双重保证。驳回进入独立 `REWORK_PENDING`，保留原采集员、assignment 和驳回原因；同一采集员可以同时持有多条返修。授权撤销只阻止新领取，不影响已领取条目的提交和释放。

启用人工审核时，采集员提交进入 `SUBMITTED`，审核领取或管理员分配后才原子进入 `REVIEW_PENDING`；`SUBMITTED` 期间本人可使用相同 assignment 与最新 revision 覆盖提交，继续复用录音原子替换、历史和旧媒体清理。审核释放回到 `SUBMITTED`，审核通过进入 `COMPLETED`，驳回进入 `REWORK_PENDING`；关闭人工审核时提交仍直接进入 `COMPLETED`。提交修改与审核领取必须以状态和 revision/CAS 竞争，失败统一返回 `STALE_STATE`。应用启动时仅将 reviewerId、reviewAssignmentId 均为空的旧 `REVIEW_PENDING` 幂等迁移为 `SUBMITTED`，不修改已领取记录，日志只输出迁移数量。

Task 2 所有不在请求体内携带 operationId 的写接口必须要求 `Idempotency-Key`。通用幂等记录按 `(actorUserId, action, operationKey)` 唯一，先持久化 IN_PROGRESS 声明，成功后保存 COMPLETED 响应快照；重复请求返回首次结果，跨实例仍在处理的重复请求返回 `409 OPERATION_IN_PROGRESS`，不得重复执行底层 mutation。

当前录音固定保存到 `RECORDING_STORAGE_DIR/{taskCode}/{itemCode}.wav|mp3`。上传必须先写 `temp/`，校验扩展名、魔数、100MB、格式、单声道、任务采样率和时长，再原子替换；同一条目的替换流程按本地条目锁串行化，失败恢复旧文件。提交或释放成功后的旧稳定文件必须先隔离到 `temp/backups/` 唯一路径，并以 `media_cleanup_jobs` 持久化旧路径和 media ID；即时清理失败由同 operationId 重放和应用启动恢复重试，不得回滚已成功的 Mongo 状态，也不得把备份暴露为媒体路径。Mongo 只保存相对路径和元数据。采集员只能读取本人条目和录音；ACTIVE grant 只额外开放任务信息与参考媒体；ADMIN/REVIEWER 按审核权限读取。

远程参考媒体生产默认只允许 HTTPS。策略必须阻止 localhost、环回、私网、链路本地、多播和危险重定向，并把校验后的公共 IP 绑定到实际 Socket；HTTPS 仍使用原 hostname 做 SNI、证书主机校验和 Host 请求头。`REMOTE_MEDIA_ALLOW_HTTP=true` 仅供显式开发联调，仍不允许私网目标。音频上限 100MB，视频上限 500MB；成功后不得保存完整签名 URL，只能保存 hostname、状态和脱敏错误摘要。

导入只支持 `.csv`，固定列为 `referenceText`、`referenceAudioUrl`、`referenceVideoUrl`；条目不接收或保存外部编号，只使用系统生成的 itemCode。返回 HTTP 202 和 importJobId，Mongo 持久化状态/计数/失败行号/有限脱敏行错误，支持幂等、部分成功与失败行重试。单文件最多 50000 个数据行，每 100 行持久化一次进度，脱敏行错误摘要最多保存 1000 条。初始导入和过期 PROCESSING 恢复固定使用 `FULL` 模式幂等重放完整源文件，只有用户显式失败行重试使用 `FAILED_ROWS`。

## 7. 接口说明

当前后端提供身份、会话、后台用户管理、语音生成、任务版本、授权、任务池、人工审核、动态状态、软废弃恢复、录音媒体和导入 API；尚不提供机器审核执行或真实 AI 转写 API。
当前同时提供操作记录与统计 API：条目操作记录按权限读取，全局操作记录仅 ADMIN/REVIEWER；任务和指定采集员汇总仅 ADMIN，审核员可查看本人统计，采集员可查看本人汇总及逐次提交明细。

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
请求路径：/api/auth/web/me、/api/auth/web/logout、/api/auth/web/password
请求参数：改密 JSON currentPassword、newPassword；其余无业务参数
响应结构：当前用户摘要或 { success, reloginRequired? }
错误码：401 会话或凭证错误；403 PASSWORD_CHANGE_REQUIRED；422 PASSWORD_TOO_WEAK（新密码少于 8 个字符或 UTF-8 超过 72 字节）
权限要求：已登录后台账号
数据一致性要求：退出和改密废止会话；首次登录改密后需要重新登录
前端调用位置：apps/web/src/lib/authApi.js、apps/web/src/pages/auth/FirstPasswordPage.vue
```

```text
请求方法：POST / GET / PUT / DELETE
请求路径：/api/auth/miniprogram/login、/account-login、/takeover、/profile、/profile/complete、/name、/password、/avatar
请求参数：微信登录 JSON code；账号登录 JSON account/password；首次资料 JSON name/account/password；改密 currentPassword/newPassword；头像 multipart avatar
响应结构：登录返回不透明 Bearer token、account、profileComplete、hasCustomAvatar；资料接口返回采集员摘要；头像 GET 返回文件流
错误码：503 WECHAT_NOT_CONFIGURED/WECHAT_UNAVAILABLE；401 WECHAT_LOGIN_FAILED/INVALID_CREDENTIALS/TAKEOVER_TOKEN_INVALID；409 ACCOUNT_IN_USE（details.takeoverToken 为短时一次性接管凭证）/USERNAME_EXISTS；413 AVATAR_TOO_LARGE；422 INVALID_NAME/INVALID_COLLECTOR_ACCOUNT/PASSWORD_TOO_WEAK/INVALID_AVATAR_FILE
权限要求：两种登录公开；其余仅 COLLECTOR 小程序 Bearer
数据一致性要求：微信和数字账号登录始终映射同一 `MINI-...` 小程序用户 ID；两种登录模式均只允许一个 ACTIVE 小程序会话，冲突后必须通过 `POST /api/auth/miniprogram/takeover` 确认接管，旧设备下次请求返回 SESSION_REPLACED；首次资料设置原子写入；数字账号仅在 `miniprogram_users` 内唯一；头像只保存安全相对路径，JPEG/PNG/WebP 最大 5MB 并校验魔数、原子替换
前端调用位置：apps/miniprogram/services/session.js、services/api.js、pages/login、pages/profile-settings
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
响应结构：搜索返回 Spring Page<UserResponse>；重置密码或改采集员账号返回用户摘要
错误码：404 USER_NOT_FOUND；409 ACCOUNT_STATE_CHANGED/USERNAME_EXISTS；422 PASSWORD_TOO_WEAK/INVALID_COLLECTOR_ACCOUNT
权限要求：仅 ADMIN
数据一致性要求：`userType=WEB` 只查询 `web_users`，`userType=MINIPROGRAM` 只查询 `miniprogram_users`，省略时保持跨两集合合并搜索；与角色不匹配时返回空分页，不跨类型降级；ADMIN 可重置 ACTIVE Web 或 Mini 用户密码；Web 用户密码 BCrypt 编码、强制下次改密并废止全部会话，Mini 用户密码 BCrypt 编码、废止全部小程序会话但不设置 Web 首改密标记；改采集员账号仅作用于 ACTIVE Mini 用户，账号在 `miniprogram_users` 内唯一并废止其会话
前端调用位置：apps/web/src/lib/userApi.js、用户管理与任务采集权限页
```

任务池与导入接口：

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
请求方法：POST / GET / PUT
请求路径：/api/tasks、/api/tasks/{taskId}、/api/tasks/{taskId}/publish|pause|resume|end
请求参数：创建含 name、description、version，taskCode 由服务端生成；结构编辑含 name、description、version；所有写操作携带 Idempotency-Key；列表 page、size
响应结构：任务/权限视图或 {items,page,size,total}；创建返回 201
错误码：404 TASK_NOT_FOUND/TASK_VERSION_NOT_FOUND；409 INVALID_TASK_STATE；422 REFERENCE_REQUIRED、RESULT_TYPE_REQUIRED、RESULT_CONTENT_MISMATCH、AI_NOT_SUPPORTED 等
权限要求：写操作仅 ADMIN；ADMIN/REVIEWER 查询全部，COLLECTOR 查询进行中/已暂停任务及 ACTIVE/PENDING/NONE 权限状态，单任务详情与版本仍需 ACTIVE 授权
数据一致性要求：taskCode 使用 Mongo 原子序列生成且不复用；发布后版本不可变；结构修改创建下一版本；所有任务均要求录音，TEXT 提交录音和文本，AUDIO 仅提交录音；关闭人工审核时 rejectionReasons 固定为空；旧条目继续绑定旧版本；写操作持久化幂等；aiEnabled 首期必须 false
前端调用位置：apps/web/src/lib/taskApi.js、apps/web/src/pages/admin/tasks/*、apps/miniprogram/pages/tasks/*
```

```text
请求方法：GET
请求路径：/api/tasks/{taskId}/versions
请求参数：任务 ID
响应结构：按 versionNumber 升序的不可变 TaskVersion 列表
错误码：404 TASK_NOT_FOUND
权限要求：ADMIN/REVIEWER；COLLECTOR 仍受任务授权边界保护
数据一致性要求：只读版本快照，不修改 published 或历史条目绑定
前端调用位置：apps/web/src/lib/taskApi.js、任务编辑与审核工作台、apps/miniprogram/pages/work/*
```

```text
请求方法：POST / GET / DELETE
请求路径：/api/tasks/{taskId}/grants、/grants/{userId}、/access-requests、/access-requests/{requestId}/approve|reject
请求参数：直接授权 JSON userId；驳回可选 reason；申请、决策、直接授权和撤销均携带 Idempotency-Key；列表 page、size
响应结构：TaskGrant、TaskAccessRequest 或 {items,page,size,total}
错误码：404 TASK/USER/ACCESS_REQUEST/GRANT_NOT_FOUND；409 TASK_ALREADY_GRANTED/ACCESS_REQUEST_DECIDED；422 INVALID_COLLECTOR
权限要求：申请仅 COLLECTOR；查询、直接授权、批准、驳回、撤销仅 ADMIN
数据一致性要求：同一任务/用户仅一个 PENDING；决策使用 PENDING 条件 CAS；批准幂等创建授权；所有写操作持久化幂等；撤销不影响已领取条目，批准重放不复活 REVOKED
前端调用位置：apps/web/src/pages/admin/tasks/TaskPermissionsPage.vue、apps/miniprogram/pages/tasks/*
```

```text
请求方法：POST / GET
请求路径：/api/tasks/{taskId}/items、/api/tasks/{taskId}/items/start、/api/task-items/{itemId}、/api/task-items/mine
请求参数：单条添加 JSON referenceText/referenceAudioUrl/referenceVideoUrl + Idempotency-Key；start 携带 Idempotency-Key；列表 page、size；mine 支持 taskId、kind=PENDING|SUBMITTED|FINISHED，兼容 ALL|RECORDING|REWORK
响应结构：TaskItem 或 {items,page,size,total}
错误码：404 NO_AVAILABLE_ITEM/TASK_ITEM_NOT_FOUND；409 ITEM_CONFLICT；422 ITEM_REFERENCE_REQUIRED/远程媒体错误
权限要求：添加和任务条目列表仅 ADMIN；start 仅 COLLECTOR；详情仅 ADMIN/REVIEWER/当前采集员
数据一致性要求：新条目绑定 currentVersion；itemCode 任务内递增唯一且是条目唯一业务编号；添加和领取均持久化幂等；领取 findAndModify 原子更新并以同一更新递增 revision、追加新 revision 的操作历史，同时保持采集员全局唯一待录制；mine 在各 kind 状态集合内统一按 updatedAt 倒序、sequence 升序后由 MongoDB 分页，不再按状态流程 rank 分组
前端调用位置：apps/web/src/pages/admin/tasks/TaskDetailPage.vue 与 TaskPoolPage.vue、apps/miniprogram/pages/tasks/*、apps/miniprogram/pages/work/*；任务详情和小程序任务数据固定每页 10 条，独立 Web 任务数据池固定每页 20 条
```

```text
请求方法：POST
请求路径：/api/task-items/{itemId}/submit、/release、/reject
请求参数：submit multipart operationId、assignmentId、expectedRevision、text?、audio?；release/reject JSON operationId、expectedRevision，reject 另含 reason
响应结构：{itemId,status,revision,assignmentId,result}
错误码：409 STALE_STATE；413 UPLOAD_TOO_LARGE；422 录音格式/采样率/声道/时长/驳回原因错误
权限要求：submit/release 仅当前 COLLECTOR（ADMIN 也可 release）；reject 仅 ADMIN/REVIEWER
数据一致性要求：operationId 绑定操作者并返回首次结果；人工审核任务提交到 SUBMITTED 且领取前可覆盖提交，免审任务直接 COMPLETED；稳定 current 文件原子替换；驳回保留原采集员；释放清当前结果但保留提交/操作历史；提交/释放成功后的旧文件和 metadata 清理持久化并可由 operation 重放/启动恢复重试
前端调用位置：apps/miniprogram/pages/work/*、apps/web/src/pages/admin/review/*
```

```text
请求方法：GET / POST
请求路径：/api/reviews/tasks、/api/reviews/tasks/{taskId}/pool|claim|claim-batch、/api/reviews/{itemId}/claim|release|approve|reject、/api/reviews/assign、/api/reviews/batch/approve
请求参数：领取头或请求体 operationId/Idempotency-Key；指定领取、释放和决定携带 expectedRevision；分配携带 reviewerId；通过可补改 text；驳回携带 reasons/note
响应结构：任务审核摘要、TaskItem、审核池分页或逐条批量结果
错误码：404 NO_REVIEW_ITEM；409 STALE_STATE；422 INVALID_REVIEWER/INVALID_BATCH_SIZE/审核内容错误
权限要求：ADMIN/REVIEWER 可查看审核池并领取指定条目；批量领取仅 REVIEWER；分配和批量通过仅 ADMIN；决定必须已有审核领取或分配
数据一致性要求：领取和分配只处理 SUBMITTED 并原子转 REVIEW_PENDING；释放原子回到 SUBMITTED；决定只处理已有 reviewerId 与 reviewAssignmentId 的 REVIEW_PENDING；所有写入保持持久化幂等和 revision/CAS
前端调用位置：apps/web/src/lib/reviewApi.js、apps/web/src/pages/admin/review/*
```

```text
请求方法：POST / GET
请求路径：/api/import-jobs、/api/import-jobs/{jobId}、/api/import-jobs/{jobId}/retry
请求参数：创建 multipart taskId、file + Idempotency-Key；重试路径参数 + Idempotency-Key
响应结构：创建/重试 HTTP 202 {importJobId,status}；查询返回 ImportJob
错误码：409 IMPORT_JOB_NOT_RETRYABLE；413/415 文件限制；422 IMPORT_HEADER_INVALID/IMPORT_FILE_INVALID/行错误
权限要求：仅 ADMIN
数据一致性要求：taskId+operationId 唯一；通用写幂等；最多 50000 行、每 100 行持久化进度、错误摘要上限 1000 条；保留完整失败行号；10 分钟租约、心跳和启动恢复；允许部分成功和失败行重试；成功行完整签名 URL 不留存
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
字段名称：id（`MINI-` 前缀）、version、account、name、passwordHash、status、wechatAppId、wechatOpenId、avatarPath、avatarContentType、avatarUpdatedAt、createdAt、updatedAt
字段类型：字符串、布尔值、UTC Instant
默认值：新微信用户 ACTIVE；角色不在本集合存储，小程序身份在鉴权与响应中固定视为 COLLECTOR
唯一约束：稀疏 account；稀疏复合 (wechatAppId, wechatOpenId)
索引：account 唯一稀疏索引；(wechatAppId, wechatOpenId) 唯一稀疏复合索引
数据兼容策略：仅保存小程序采集员身份；不得把明文密码、微信 session_key 或客户端提交的 openId 写入
迁移步骤：身份拆分后使用 `MINI-` 前缀 ID，旧统一身份集合不再作为当前运行集合
回滚方式：拆分或清库前备份相关集合；不要直接删除已创建用户
```

```text
集合名称：sessions
字段名称：userId、tokenHash、type、status、replacedSessionId、createdAt、lastAccessAt、expiresAt
字段类型：字符串、枚举、UTC Instant
默认值：新会话 ACTIVE；Web 空闲 12 小时；小程序 30 天；接管凭证 5 分钟
唯一约束：tokenHash；同一用户最多一个 ACTIVE WEB 会话，且最多一个 ACTIVE MINIPROGRAM 会话
索引：expiresAt TTL；(userId, status)；ACTIVE WEB 部分唯一索引；ACTIVE MINIPROGRAM 部分唯一索引
数据兼容策略：只保存 SHA-256 tokenHash，不保存原始 Cookie/Bearer/takeoverToken
迁移步骤：本阶段为首次建立，无旧会话迁移
回滚方式：可废止全部会话并要求重新登录，不得导出或恢复原始令牌
```

```text
集合名称：tasks
字段名称：taskCode、name、description、lifecycle、currentVersionId、currentVersionNumber、itemSequence、createdAt、updatedAt
字段类型：字符串、枚举、整数、UTC Instant
默认值：DRAFT、currentVersionNumber=1、itemSequence=0
唯一约束：taskCode
索引：taskCode 唯一；lifecycle
数据兼容策略：只通过 currentVersionId 指向当前版本，旧条目保存自身 taskVersionId
迁移步骤：本阶段首次建立，无旧任务迁移
回滚方式：先备份 tasks/task_versions，不直接删除任务或重置序号
```

```text
集合名称：task_versions
字段名称：taskId、versionNumber、referenceTypes、resultType、humanReviewEnabled、recordingFormat、sampleRates、channels、minDurationMillis、maxDurationMillis、rejectionReasons、aiEnabled、aiProvider、aiModel、published、createdAt、publishedAt
字段类型：字符串、集合、布尔值、枚举、整数、UTC Instant
默认值：humanReviewEnabled=true、channels=1、minDurationMillis=1000、maxDurationMillis=600000、aiEnabled=false
唯一约束：(taskId, versionNumber)
索引：(taskId, versionNumber) 唯一
数据兼容策略：发布后不可原地覆盖；新结构写下一版本
迁移步骤：本阶段首次建立，无旧版本迁移
回滚方式：保留已被条目引用的版本，不降写 versionNumber
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
字段名称：taskId、taskVersionId/Number、sequence、itemCode、creationOperationId、status、collectorId、reviewerId、assignmentId、reviewAssignmentId、revision、参考字段、currentResult、currentRejection、submissions（含提交时 collectorId）、operations、discardedPreviousStatus、createdAt、updatedAt
字段类型：字符串、枚举、数值、嵌套文档、数组、UTC Instant
默认值：新条目 AVAILABLE、revision=0、历史数组为空
唯一约束：(taskId,itemCode)；creationOperationId 存在时任务内唯一；仅普通 RECORDING_PENDING 的 collectorId 全系统唯一，REWORK_PENDING 不限条数
索引：上述唯一/部分唯一索引；(taskId,status,sequence) 领取索引；(collectorId,status)
数据兼容策略：条目固定绑定创建时版本；当前结果可替换/清除，提交与操作历史只追加
迁移步骤：本阶段首次建立，无旧条目迁移
回滚方式：备份集合与本地媒体；不得只回滚 Mongo 或只回滚文件
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

当前已实现可配置的单层人工审核：先选择有已提交或待审核数据的任务，再进入对应审核池。采集员提交后处于 `SUBMITTED` 并可继续修改；管理员或审核员领取、或管理员分配后才进入 `REVIEW_PENDING` 并锁定采集修改。审核员可在所选任务内单条或批量领取、释放本人占用、补改文本、通过或驳回；管理员可领取指定条目、指定审核员并对已领取/已分配条目作出单条或批量决定，不得直接决定未领取的 `SUBMITTED`。驳回进入原采集员的 `REWORK_PENDING` 返修队列，提交和操作历史永久保留。前端不得向未占用该条目的审核员展示决定按钮；后端服务和 Spring Security 的角色规则必须保持一致。

管理员状态管理支持任务版本允许的动态阶段、批量逐条结果、媒体安全释放、软废弃和恢复。普通状态调整不能进入待领取、待审核，也不能绕过人工审核直接进入已完成；返回池必须调用释放，人工审核完成必须走领取或分配后的审核决定。废弃不删除归属、结果或文件，恢复回废弃前状态并重新校验版本阶段和 revision。

机器审核、真实 AI 转写及多级一审/二审仍属于后续范围，启用前必须另行确认状态与接口契约。

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
