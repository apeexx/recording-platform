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
微信小程序录音端占位目录
Java Spring Boot 后端项目
MongoDB 身份、会话与统一 API 错误基础
语音生成 Web 生产台
根目录 README.md
根目录 log.md
本文件 AGENTS.md
```

当前已允许实现身份、会话、后台用户管理、微信登录边界和语音生成持久化；仍不实现任务领取、录音上传、审核状态机或完整业务页面。

Spring Security 已配置为不透明服务端会话，不使用 JWT。除 Web/微信登录与接管接口外，其余 `/api/**` 默认认证；管理员接口和语音生成接口仅 `ADMIN` 可访问。

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

## 6. 默认技术栈

- Web 前端：Vite + Vue3 + JavaScript
- 微信小程序端：目录为 `apps/miniprogram`，当前仅保留占位说明
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

该脚本会在启动前检查并结束占用 `8080` 和 `5173` 端口的监听进程，然后打开两个可见的 `pwsh` 窗口分别运行：

```text
backend\mvnw.cmd spring-boot:run
npm run dev -- --host localhost --port 5173
```

两个窗口标题分别为 `Recording Backend` 和 `Recording Frontend`，用于查看实时日志。脚本只负责启动后端和 Web 前端，不启动 MongoDB，不创建 `.env`，不写入或打印 API Key，不再创建或写入根目录 `logs/`。后端联调前需自行确保 MongoDB 可用；语音生成真实联调仍需根目录 `.env` 提供 MiniMax 配置。

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
WECHAT_APP_ID
WECHAT_APP_SECRET
INITIAL_ADMIN_USERNAME
INITIAL_ADMIN_PASSWORD
WEB_SESSION_IDLE_HOURS（默认 12）
MINIPROGRAM_SESSION_DAYS（默认 30）
WEB_SESSION_COOKIE_SECURE（默认 false，生产 HTTPS 应设 true）
```

根目录 `.env.example` 只能提供空值或安全默认值。Web 会话 Cookie 必须为 HttpOnly、SameSite=Lax；CSRF 使用可读的 `XSRF-TOKEN` Cookie 与 `X-XSRF-TOKEN` 请求头，已登录 Web 用户通过 `GET /api/auth/web/csrf` 获取 token，首次登录待改密账号也必须允许访问该端点。服务端和 MongoDB 只保存会话令牌哈希。微信登录必须由后端用临时 code 调用 `jscode2session`，不得信任客户端直接提交的 OpenID。

## 7. 接口说明

当前后端提供身份、会话、后台用户管理和语音生成 API，尚不提供任务领取、录音上传或审核状态机 API。

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
前端调用位置：后续管理员/审核员登录页
```

```text
请求方法：POST
请求路径：/api/auth/web/takeover
请求参数：JSON；takeoverToken
响应结构：后台用户摘要；设置新的 REC_WEB_SESSION Cookie
错误码：401 TAKEOVER_TOKEN_INVALID
权限要求：公开，但凭证短时且一次性
数据一致性要求：旧会话标记 REPLACED，新会话生效；旧设备下次请求返回 SESSION_REPLACED
前端调用位置：后续管理员/审核员登录页
```

```text
请求方法：GET
请求路径：/api/auth/web/csrf
请求参数：无业务参数；携带有效 REC_WEB_SESSION Cookie
响应结构：{ headerName, parameterName, token }；同时设置可读的 XSRF-TOKEN Cookie
错误码：401 会话无效、过期或已被接管
权限要求：已登录后台账号；首次登录待改密账号也允许访问
数据一致性要求：只生成或读取 CSRF token，不写入用户或业务数据
前端调用位置：后续管理员/审核员写操作请求封装
```

```text
请求方法：GET / POST / PUT
请求路径：/api/auth/web/me、/api/auth/web/logout、/api/auth/web/password
请求参数：改密 JSON currentPassword、newPassword；其余无业务参数
响应结构：当前用户摘要或 { success, reloginRequired? }
错误码：401 会话或凭证错误；403 PASSWORD_CHANGE_REQUIRED；422 PASSWORD_TOO_WEAK（新密码少于 8 个字符或 UTF-8 超过 72 字节）
权限要求：已登录后台账号
数据一致性要求：退出和改密废止会话；首次登录改密后需要重新登录
前端调用位置：后续管理员/审核员账号模块
```

```text
请求方法：POST / PUT
请求路径：/api/auth/miniprogram/login、/api/auth/miniprogram/name
请求参数：登录 JSON 仅 code；设置姓名 JSON name
响应结构：登录返回不透明 Bearer token 和录音人员摘要；设置姓名返回用户摘要
错误码：503 WECHAT_NOT_CONFIGURED/WECHAT_UNAVAILABLE；401 WECHAT_LOGIN_FAILED；422 INVALID_NAME
权限要求：登录公开；设置姓名仅 COLLECTOR
数据一致性要求：后端调用微信 jscode2session，以测试期 (wechatAppId, wechatOpenId) 唯一；不得接受客户端直接提交 openId
前端调用位置：apps/miniprogram 后续登录模块
```

```text
请求方法：POST / GET / POST
请求路径：/api/admin/users、/api/admin/users?page=&size=、/api/admin/users/{userId}/disable
请求参数：创建 JSON username、name、role、initialPassword；查询分页；停用路径参数
响应结构：不含 passwordHash、OpenID 或令牌的用户摘要/分页
错误码：404 USER_NOT_FOUND；409 USERNAME_EXISTS；422 INVALID_BACKEND_ROLE/PASSWORD_TOO_WEAK
权限要求：仅 ADMIN
数据一致性要求：后台账号仅 ADMIN/REVIEWER；停用账号同时废止其活动会话
前端调用位置：后续系统用户管理页
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
集合名称：users
字段名称：internalUserNo、username、name、passwordHash、role、status、firstPasswordChangeRequired、wechatAppId、wechatOpenId、createdAt、updatedAt
字段类型：字符串、枚举、布尔值、UTC Instant
默认值：新后台账号 ACTIVE 且 firstPasswordChangeRequired=true；微信用户角色 COLLECTOR
唯一约束：internalUserNo；稀疏 username；测试期稀疏复合 (wechatAppId, wechatOpenId)
索引：上述唯一索引
数据兼容策略：后台账号与微信录音人员共用集合，以角色和身份字段区分；不得把明文密码、微信 session_key 或客户端提交的 openId 写入
迁移步骤：本阶段为首次建立，无旧身份数据迁移
回滚方式：回滚代码前备份集合；不要直接删除已创建用户
```

```text
集合名称：sessions
字段名称：userId、tokenHash、type、status、replacedSessionId、createdAt、lastAccessAt、expiresAt
字段类型：字符串、枚举、UTC Instant
默认值：新会话 ACTIVE；Web 空闲 12 小时；小程序 30 天；接管凭证 5 分钟
唯一约束：tokenHash；同一用户最多一个 ACTIVE WEB 会话
索引：expiresAt TTL；(userId, status)；ACTIVE WEB 部分唯一索引
数据兼容策略：只保存 SHA-256 tokenHash，不保存原始 Cookie/Bearer/takeoverToken
迁移步骤：本阶段为首次建立，无旧会话迁移
回滚方式：可废止全部会话并要求重新登录，不得导出或恢复原始令牌
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

后续任务、领取、录音文件、审核记录、状态流转和操作日志的数据结构必须在对应阶段重新确认原子更新、唯一约束、索引、兼容和回滚方案，不得沿用未确认的旧 PostgreSQL 设计假设。

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

当前阶段不实现审核流程。

后续审核流目标包括：

- 录音上传后进入机器审核。
- 根据机器审核结果进入一审或驳回。
- 一审通过后按规则进入二审或完成。
- 二审通过后任务完成。
- 任一审核环节驳回后，录音人员需要重新录制并上传。

正式实现前必须明确审核状态枚举、状态流转规则、权限边界和操作日志。

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
