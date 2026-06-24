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
Java Spring Boot 后端空项目
语音生成 Web 生产台
根目录 README.md
根目录 log.md
本文件 AGENTS.md
```

除语音生成模块外，当前不实现完整业务功能，不新增任务领取、录音上传、审核、登录、JWT、权限配置、数据库集合、接口路由或状态机逻辑。

Spring Security 当前只作为依赖引入，后续登录/JWT 阶段再配置 `SecurityConfig`。

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
- 数据库：MongoDB 作为后续计划数据库
- 文档记录：根目录 `README.md`、`AGENTS.md`、`log.md`

仓库内不再维护 Docker Compose 配置。需要本地 MongoDB 时，由开发者在本机或外部环境自行提供，并在具体任务中说明连接方式、风险和验证方式。

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

两个窗口标题分别为 `Recording Backend` 和 `Recording Frontend`，用于查看实时日志。脚本只负责启动后端和 Web 前端，不启动 MongoDB，不创建 `.env`，不写入或打印 API Key，不再创建或写入根目录 `logs/`。语音生成真实联调仍需开发者自行准备 MongoDB 和根目录 `.env`。

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

语音生成合成、试听、克隆、记录查询和默认配置保存依赖 MongoDB。后端遇到 MongoDB 不可用时必须返回脱敏 JSON 错误摘要，不得让数据库异常穿透为 Internal Server Error。日常合成应先确认生成记录可写入，再调用 MiniMax 远程合成，避免数据库不可用时产生不必要的远程调用。

## 7. 接口说明

当前阶段除语音生成模块外，后端不提供业务 API。

语音生成接口说明：

```text
请求方法：POST
请求路径：/api/voice-generation/preview
请求参数：multipart/form-data；audio=参考音频文件，text=合成文本，speed=语速，volume=音量，pitch=语调
响应结构：{ recordId, mode, status, message, audioUrl }
错误码：400 参数缺失、MiniMax 配置缺失或调用失败
权限要求：当前阶段不接入登录/JWT；语音生成接口为本地开发联调用途
数据一致性要求：成功后写入 voice_generation_records，并将音频保存到本地存储目录
前端调用位置：apps/web/src/lib/voiceGenerationApi.js
```

```text
请求方法：POST
请求路径：/api/voice-generation/synthesize
请求参数：JSON；voiceId、text、speed、volume、pitch
响应结构：{ recordId, mode, status, message, audioUrl }
错误码：400 参数缺失、MiniMax 配置缺失或调用失败
权限要求：当前阶段不接入登录/JWT；语音生成接口为本地开发联调用途
数据一致性要求：成功后写入 voice_generation_records，并将音频保存到本地存储目录
前端调用位置：apps/web/src/lib/voiceGenerationApi.js
```

```text
请求方法：POST
请求路径：/api/voice-generation/voices/clone
请求参数：multipart/form-data；audio=母带音频文件，voiceId=新音色 ID
响应结构：{ success, message }
错误码：400 参数缺失、MiniMax 配置缺失或调用失败
权限要求：当前阶段不接入登录/JWT；语音生成接口为本地开发联调用途
数据一致性要求：成功后写入一条 CLONE 类型 voice_generation_records
前端调用位置：apps/web/src/lib/voiceGenerationApi.js
```

```text
请求方法：GET
请求路径：/api/voice-generation/voices
请求参数：excludeSystem=true|false
响应结构：MiniMax 音色列表 JSON；excludeSystem=true 时过滤 system_voice
错误码：400 MiniMax 配置缺失或调用失败
权限要求：当前阶段不接入登录/JWT；语音生成接口为本地开发联调用途
数据一致性要求：只读 MiniMax 音色资产，不写入 MongoDB
前端调用位置：apps/web/src/lib/voiceGenerationApi.js
```

```text
请求方法：DELETE
请求路径：/api/voice-generation/voices/{voiceId}
请求参数：路径参数 voiceId
响应结构：{ success, message }
错误码：400 MiniMax 配置缺失或调用失败
权限要求：当前阶段不接入登录/JWT；语音生成接口为本地开发联调用途
数据一致性要求：调用 MiniMax 删除音色，不删除本地生成记录
前端调用位置：apps/web/src/lib/voiceGenerationApi.js
```

```text
请求方法：GET
请求路径：/api/voice-generation/records
请求参数：page、size
响应结构：{ items, page, size, total }
错误码：400 查询失败
权限要求：当前阶段不接入登录/JWT；语音生成接口为本地开发联调用途
数据一致性要求：从 voice_generation_records 按创建时间倒序分页读取
前端调用位置：apps/web/src/lib/voiceGenerationApi.js
```

```text
请求方法：GET
请求路径：/api/voice-generation/audio/{recordId}
请求参数：路径参数 recordId
响应结构：音频文件流
错误码：400 记录不存在或本地音频文件已清理
权限要求：当前阶段不接入登录/JWT；语音生成接口为本地开发联调用途
数据一致性要求：只读取本地音频文件，不写入数据库
前端调用位置：apps/web/src/lib/voiceGenerationApi.js
```

```text
请求方法：GET / PUT
请求路径：/api/voice-generation/config/default
请求参数：GET 无参数；PUT JSON voiceId、speed、volume、pitch
响应结构：{ id, voiceId, speed, volume, pitch, updatedAt }
错误码：400 参数缺失或保存失败
权限要求：当前阶段不接入登录/JWT；语音生成接口为本地开发联调用途
数据一致性要求：写入或读取 voice_generation_configs 中 id=default 的默认配置
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

涉及登录、JWT、任务发布、任务领取锁定、录音上传、机器审核、一审、二审、驳回重录和任务完成的接口，必须在实现前确认字段和状态流转。

前端只负责采集、展示、调用接口和触发下载；后端负责数据持久化、文件落盘、聚合、接口和服务启动。

## 8. 数据库说明

当前阶段除语音生成模块外，暂不定义正式 MongoDB 集合、字段、索引或迁移脚本。

语音生成 MongoDB 集合说明：

```text
集合名称：voice_generation_records
字段名称：id、mode、status、text、voiceId、speed、volume、pitch、audioPath、audioFormat、durationMillis、message、createdAt
字段类型：字符串、枚举、数字、Instant
默认值：mode/status 由后端生成流程写入，audioPath 仅生成成功后写入
唯一约束：MongoDB _id
索引：当前未新增索引；后续记录量增加时可为 createdAt 增加倒序索引
数据兼容策略：音频二进制不入库，只保存本地文件路径和元数据
迁移步骤：无迁移脚本，新集合由 Spring Data MongoDB 首次写入时创建
回滚方式：停止语音生成接口后可按需备份并删除该集合和本地音频目录
```

```text
集合名称：voice_generation_configs
字段名称：id、voiceId、speed、volume、pitch、updatedAt
字段类型：字符串、数字、Instant
默认值：id=default，voiceId=sichuan_native_01，speed=0.9，volume=1.0，pitch=0
唯一约束：MongoDB _id
索引：当前不新增索引
数据兼容策略：仅保存默认声音参数，不保存 API Key
迁移步骤：无迁移脚本，首次保存默认配置时创建
回滚方式：删除 id=default 文档即可恢复后端默认值
```

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
