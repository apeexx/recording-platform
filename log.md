# AI 修改日志

## 2026-07-12 15:43 增加操作记录查询与工作统计

- 时间：2026-07-12 15:43
- commit ID：待提交后补记
- 修改内容：
  - 增加条目级与全局操作记录分页，对外固定东八区时间、操作人和操作内容三列。
  - 增加任务、采集员、审核员、个人汇总和逐次提交明细接口。
  - 区分累计提交/时长与当前有效完成结果，释放和废弃单列；审核员统计领取、释放、通过、驳回及平均处理时长。
  - 提交历史保存提交时 collectorId，释放或重新分配后仍可正确归属历史工作量。
  - 汇总、操作过滤和个人提交分页使用 MongoDB aggregation。
- 验证结果：
  - `backend\\mvnw.cmd clean test`：187 tests，0 failures，0 errors，0 skipped。
  - 本轮未连接真实 MongoDB 执行大数据聚合性能测试，留待最终全链路验收。

## 2026-07-12 15:27 完成人工审核与动态状态后端

- 时间：2026-07-12 15:27
- commit ID：待提交后补记
- 修改内容：
  - 增加审核池、单条/批量领取、管理员分配、审核释放、补改文字、通过、原因多选驳回和管理员批量通过。
  - 增加管理员单条/批量状态调整、媒体安全释放、软废弃与恢复，批量操作逐条返回成功或冲突结果。
  - 审核与状态写接口接入 MongoDB 持久化幂等响应快照及 revision/CAS，批量响应支持泛型安全重放。
  - 更新角色安全规则、README、AGENTS 和完整实施清单。
- 验证结果：
  - `backend\\mvnw.cmd clean test`：176 tests，0 failures，0 errors，0 skipped。
  - 真实 MongoDB 多实例并发仍留待最终全链路验收，本轮 Spring 安全集成测试使用 mock service。

## 2026-07-12 14:58 完成阶段二复核并修复导入源文件竞态

- 时间：2026-07-12 14:58
- commit ID：待提交后补记
- 修改内容：
  - 补充导入 worker 在部分成功后丢失租约的回归测试，确认旧实现会删除 MongoDB 仍引用的原始导入文件。
  - 失败行重试文件改为按 worker 唯一命名；仅在 fenced `finish` 成功后删除旧源文件，租约被接管时保留原文件并清理未发布的重试文件。
  - 更新完整实施清单，将阶段二复核标记为完成。
- 验证结果：
  - `backend\\mvnw.cmd clean test`：153 tests，0 failures，0 errors，0 skipped。
  - `git diff --check 83a3a4c..59f59de` 与当前工作区 `git diff --check`：通过。

## 2026-07-12 14:48 增加完整闭环实施清单

- 时间：2026-07-12 14:48
- commit ID：f766f23
- 修改内容：
  - 新增 `docs/recording-platform-implementation-checklist.md`，记录录音任务平台从阶段二复核到审核后端、Web 管理端、原生微信小程序、启动加固和最终验收的完整任务清单。
  - 清单记录已完成的四个后端提交、剩余文件范围、接口、TDD 步骤、验证命令、提交节点和最终完成条件。
  - 明确 `docs/daily-maintenance-log.md`、真实密钥、个人截图和缺失的 `backend/HELP.md` 不在本任务修改范围。
- 验证结果：
  - 待执行 Markdown 内容检查、`git diff --check` 和 Git 状态检查。

## 2026-06-23 17:49 初始化录音任务平台仓库结构

- 时间：2026-06-23 17:49
- commit ID：cf9f8f2
- 修改内容：
  - 初始化录音任务平台仓库结构。
  - 使用 Vite 官方脚手架创建 Vue Web 空项目。
  - 使用 Spring Initializr 创建 Spring Boot Maven 后端空项目。
  - 创建微信小程序占位说明。
  - 创建 MongoDB Docker Compose 配置。
  - 创建项目文档骨架。
  - 当前不实现任何业务接口、页面逻辑、审核流或安全配置。
- 验证结果：初始化提交记录，未在当前轮重新运行验证。

## 2026-06-23 18:44 结构清理

- 时间：2026-06-23 18:44
- commit ID：afc2649
- 修改内容：
  - 删除仓库内 Docker Compose 配置，后续不再通过仓库提供本地 Docker 服务。
  - 删除 docs 目录文档骨架，接口和数据库说明入口迁移到 `AGENTS.md`。
  - 新建根目录 `log.md` 作为 AI 修改日志。
  - 按参考风格重写 `AGENTS.md` 为录音任务平台长期执行规则。
  - 更新 `README.md`，同步新的目录结构、文档位置和本地验证说明。
- 验证结果：历史提交记录，未在当前轮重新运行验证。

## 2026-06-23 19:24 小程序目录重命名

- 时间：2026-06-23 19:24
- commit ID：914594f
- 修改内容：
  - 调整微信小程序占位目录命名。
  - 删除 `AGENTS.md` 中的“角色分工”章节，并同步后续章节编号。
  - 更新 `README.md` 和 `AGENTS.md` 中的小程序目录路径说明。
- 验证结果：
  - 路径和章节引用检查通过。
  - `npm run build`：通过。
  - `.\mvnw.cmd test`：通过。

## 2026-06-23 19:33 AI 修改日志规则完善

- 时间：2026-06-23 19:33
- commit ID：c6a01fb
- 修改内容：
  - 在 `AGENTS.md` 中新增“AI 修改日志”章节。
  - 规定 AI 修改日志必须记录具体到分钟的时间、commit ID、修改内容和验证结果。
  - 将已有 `log.md` 记录统一补充具体时间和历史 commit ID。
- 验证结果：
  - `npm run build`：通过。
  - `.\mvnw.cmd test`：通过；测试期间本机未连接 MongoDB 时会输出连接拒绝日志，但 Maven 最终结果为 `BUILD SUCCESS`。

## 2026-06-23 19:46 小程序目录改为英文名

- 时间：2026-06-23 19:46
- commit ID：d8a45f5
- 修改内容：
  - 将微信小程序占位目录统一改为英文目录名 `apps/miniprogram`。
  - 更新 `README.md` 和 `AGENTS.md` 中的小程序目录路径说明。
  - 清理 `log.md` 中旧的小程序目录名引用，避免后续误用旧路径。
- 验证结果：
  - 旧小程序目录名全仓搜索：无匹配。
  - `npm run build`：通过。
  - `.\mvnw.cmd test`：通过；测试期间本机未连接 MongoDB 时会输出连接拒绝日志，但 Maven 最终结果为 `BUILD SUCCESS`。

## 2026-06-23 20:26 Web 端主题变量和基础视觉规范

- 时间：2026-06-23 20:26
- commit ID：待提交后补记
- 修改内容：
  - 新增 `apps/web/src/styles/theme.css`，落地浅色主题变量和 `.dark` 深色变量预留。
  - 更新 `apps/web/src/style.css`，引入主题变量并建立全局基础样式、卡片、按钮、状态标签等通用 class。
  - 调整 `apps/web/src/App.vue` 为静态主题预览页，仅展示项目名、当前阶段、三个示例卡片、主按钮和状态标签。
  - 更新 `README.md` 和 `AGENTS.md`，记录 Web 端主题变量位置和后续页面优先使用主题变量的要求。
- 验证结果：
  - `npm run build`：通过。
  - 本地浏览器访问 `http://127.0.0.1:5173/`：主题预览页加载成功，控制台无 error。
  - 390px 窄屏检查：三张示例卡片按单列展示，卡片宽度未超出视口。

## 2026-06-23 20:46 管理员端导航壳和模块化路由

- 时间：2026-06-23 20:46
- commit ID：待提交后补记
- 修改内容：
  - 引入 `vue-router@4`，建立管理员端路由壳。
  - 新增侧边栏菜单配置 `apps/web/src/config/adminSidebar.js`，菜单由配置统一驱动。
  - 新增管理员端布局、侧边栏、顶部栏和各模块占位页面。
  - 新增语音生成合作者预留模块占位页面，不实现业务逻辑。
  - 删除不再使用的 Vite 默认组件和图片资源。
  - 更新 `README.md`、`AGENTS.md` 和 `apps/web/README.md`，记录导航壳、协作边界和验证方式。
- 验证结果：
  - `node --check src/main.js`：通过。
  - `node --check src/router/index.js`：通过。
  - `node --check src/router/adminRoutes.js`：通过。
  - `node --check src/config/adminSidebar.js`：通过。
  - `node --test src/tests/adminSidebar.test.js`：通过。
  - `npm run build`：通过。
  - 本地浏览器访问 `http://127.0.0.1:5173/`：自动进入 `/admin/dashboard`，侧边栏菜单切换、页面标题、选中态和语音生成预留占位验证通过。
  - 390px 窄屏检查：无横向溢出，侧边栏可滚动，内容卡片宽度未超出视口。

## 2026-06-23 23:24 语音生成 Web 生产台

- 时间：2026-06-23 23:24
- commit ID：待提交后补记
- 修改内容：
  - 参考 `XiangTianzhen/tuanji` 项目中的 `app.py`，在管理员端实现语音生成 Web 生产台。
  - 新增后端语音生成接口，支持 0 元试听、付费克隆、日常合成、音色查询、音色删除、默认配置保存、生成记录查询和音频下载。
  - 新增 MiniMax 后端客户端、本地 `.env` 配置读取、MongoDB 记录/配置集合和本地音频文件保存。
  - 新增前端语音生成 API 封装、工作台页、声音配置页、生成记录页和 Vite `/api` 代理。
  - 新增 `.env.example`，只提供变量名和默认服务地址，不包含真实密钥。
  - 更新 `README.md`、`AGENTS.md` 和 `apps/web/README.md` 的语音生成说明、接口说明、数据库说明和验证方式。
- 验证结果：
  - `node --test src/tests/adminSidebar.test.js src/tests/voiceGenerationApi.test.js`：通过。
  - `node --check src/main.js`、`node --check src/router/index.js`、`node --check src/router/adminRoutes.js`、`node --check src/config/adminSidebar.js`、`node --check src/lib/voiceGenerationApi.js`：通过。
  - `npm run build`：通过。
  - `.\mvnw.cmd test`：通过；本机未启动 MongoDB 时仍会输出连接拒绝日志，但 Maven 最终结果为 `BUILD SUCCESS`。
  - 本地浏览器访问 `http://localhost:5173/admin/voice-generation/workbench`：语音生成工作台加载成功，0 元试听/付费克隆/日常合成模式切换成功，配置页和记录页路由加载成功。
  - 本地调用 `POST /api/voice-generation/synthesize`：未配置 `MINIMAX_API_KEY` 时返回脱敏 400 摘要。
  - 真实 MiniMax 联通未执行：当前根目录未提供已填写的 `.env` 和本地 MongoDB 服务。

## 2026-06-23 23:42 开发环境一键启动脚本

- 时间：2026-06-23 23:42
- commit ID：待提交后补记
- 修改内容：
  - 新增 `scripts/start-dev.ps1`，用于在 Windows PowerShell 中一键启动 Spring Boot 后端和 Vite 前端。
  - 脚本启动前自动检查 `8080`、`5173` 端口，若端口被占用则结束对应监听进程并打印 PID 与进程名。
  - 新增 `scripts/tests/start-dev.test.js`，覆盖端口清理、启动命令和敏感信息检查。
  - 更新 `README.md`、`AGENTS.md` 和 `scripts/README.md`，记录一键启动方式、端口处理规则、日志位置和 MongoDB/.env 边界。
- 验证结果：
  - `node --test scripts/tests/start-dev.test.js`：通过。
  - `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-dev.ps1 -Help`：通过。
  - `rg -n "sk-|Authorization|MINIMAX_API_KEY\s*=" scripts`：未发现脚本内真实密钥或敏感头；仅测试断言中包含检查关键词。
  - `npm run build`：通过。
  - `.\mvnw.cmd test`：通过；本机未启动 MongoDB 时仍会输出连接拒绝日志，但 Maven 最终结果为 `BUILD SUCCESS`。
  - `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-dev.ps1`：通过，脚本启动后 `8080` 和 `5173` 均有监听，访问 `http://localhost:5173/admin/voice-generation/workbench` 返回 200；验证后已停止脚本启动的后端和前端进程。

## 2026-06-24 00:03 开发启动脚本改为实时日志窗口

- 时间：2026-06-24 00:03
- commit ID：待提交后补记
- 修改内容：
  - 调整 `scripts/start-dev.ps1`，不再隐藏启动进程，也不再重定向输出到根目录 `logs/`。
  - 新增 `scripts/start-dev.cmd`，用于双击启动开发环境。
  - 后端和前端分别在可见 `pwsh` 窗口中运行，窗口标题为 `Recording Backend` 和 `Recording Frontend`，用于查看实时日志。
  - 更新 `scripts/tests/start-dev.test.js`，覆盖双 `pwsh` 窗口、`.cmd` 启动器和无日志重定向行为。
  - 更新 `README.md`、`AGENTS.md` 和 `scripts/README.md`，同步新的启动方式和日志查看方式。
- 验证结果：
  - `node --test scripts/tests/start-dev.test.js`：通过。
  - `pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-dev.ps1 -Help`：通过。
  - `rg -n 'sk-|Authorization|MINIMAX_API_KEY\s*=|RedirectStandardOutput|RedirectStandardError|WindowStyle\s+Hidden|\$LogDir' scripts/start-dev.ps1 scripts/start-dev.cmd`：未发现真实密钥、敏感头、隐藏窗口、日志重定向或 `$LogDir`。
  - `npm run build`：通过。
  - `.\mvnw.cmd test`：通过；本机未启动 MongoDB 时仍会输出连接拒绝日志，但 Maven 最终结果为 `BUILD SUCCESS`。
  - `.\scripts\start-dev.cmd`：通过，脚本启动后 `8080` 和 `5173` 均有监听，访问 `http://localhost:5173/admin/voice-generation/workbench` 返回 200，并能看到 `Recording Backend` / `Recording Frontend` 对应的 `pwsh` 进程；验证后已停止脚本启动的后端、前端和开发窗口。
  - 已删除本地忽略目录 `logs/`，未提交任何日志文件。

## 2026-06-24 00:16 修复 MiniMax 2049 域名配置

- 时间：2026-06-24 00:16
- commit ID：待提交后补记
- 修改内容：
  - 排查 MiniMax `2049 invalid api key`，确认本地 `.env` 中 `MINIMAX_API_KEY` 已存在且非空，但当前 `MINIMAX_API_BASE_URL=https://api.minimax.io` 返回 2049。
  - 使用同一 API Key 对 `https://api.minimaxi.com` 执行脱敏连通检查，MiniMax 返回 `0 success`，定位为账号区域域名不匹配。
  - 将后端默认 MiniMax API Base URL 和 `.env.example` 调整为 `https://api.minimaxi.com`。
  - 已将本地 `.env` 的 `MINIMAX_API_BASE_URL` 同步改为 `https://api.minimaxi.com`，未输出、提交或记录真实 API Key。
  - 更新 `README.md` 和 `AGENTS.md`，补充 2049 与 MiniMax 国内/国际开放平台域名的排查说明。
- 验证结果：
  - `.\mvnw.cmd -Dtest=VoiceGenerationBackendConfigTests test`：先失败于默认值仍为 `https://api.minimax.io`，修复后通过。
  - 使用同一 `.env` API Key 对 MiniMax 两个域名做脱敏连通检查：`https://api.minimax.io` 返回 `2049 invalid api key`，`https://api.minimaxi.com` 返回 `0 success`。
  - `.\scripts\start-dev.cmd`：通过，重启后端和前端并重新读取本地 `.env`。
  - `GET http://localhost:8080/api/voice-generation/voices?excludeSystem=true`：通过，MiniMax 返回 `base_resp.status_code=0`、`status_msg=success`。
  - `GET http://localhost:5173/admin/voice-generation/workbench`：返回 200。
  - `npm run build`：通过。
  - `.\mvnw.cmd test`：通过；本机未启动 MongoDB 时仍会输出连接拒绝日志，但 Maven 最终结果为 `BUILD SUCCESS`。

## 2026-06-24 08:28 修复语音合成 MongoDB 异常 500

- 时间：2026-06-24 08:28
- commit ID：待提交后补记
- 修改内容：
  - 排查后端 `Internal Server Error`，定位为语音合成完成后写入 `voice_generation_records` 时 MongoDB `localhost:27017` 不可用，`DataAccessResourceFailureException` 未被处理。
  - 调整语音合成流程：先保存 `PENDING` 生成记录，确认 MongoDB 可写后再调用 MiniMax 合成，避免数据库不可用时产生不必要的远程合成调用。
  - 为数据库读写异常新增脱敏 JSON 错误处理，返回 `MongoDB 未连接，无法保存或读取语音生成数据`，不再穿透为 Internal Server Error。
  - 更新 `README.md` 和 `AGENTS.md`，补充 MongoDB 未连接时的排查说明和安全边界。
- 验证结果：
  - `.\mvnw.cmd "-Dtest=VoiceGenerationServiceTests,VoiceGenerationControllerTests" test`：先失败，确认数据库异常会穿透为 Servlet 500，且服务层不会转换为业务错误；修复后通过。
  - `.\mvnw.cmd test`：通过；本机未启动 MongoDB 时仍会输出连接拒绝日志，但 Maven 最终结果为 `BUILD SUCCESS`。
  - `npm run build`：通过。
  - `.\scripts\start-dev.cmd`：通过，重启后端和前端到新代码。
  - `POST http://localhost:8080/api/voice-generation/synthesize`：MongoDB 未连接时返回 HTTP 400，响应体为脱敏错误摘要 `MongoDB 未连接，无法保存语音生成记录`，不再返回 Internal Server Error。
  - `GET http://localhost:8080/api/voice-generation/voices?excludeSystem=true`：通过，MiniMax 返回 `base_resp.status_code=0`、`status_msg=success`。
  - `GET http://localhost:5173/admin/voice-generation/workbench`：返回 200。

## 2026-06-24 08:53 迁移语音生成持久化到 PostgreSQL

- 时间：2026-06-24 08:53
- commit ID：待提交后补记
- 修改内容：
  - 将项目默认数据库方向确定为 PostgreSQL，后续任务、领取、录音、审核和日志均以关系型模型设计。
  - 后端移除 MongoDB 持久化依赖，改用 Spring Data JPA、PostgreSQL JDBC 和 Flyway。
  - 将语音生成记录和默认声音配置迁移为 PostgreSQL 表 `voice_generation_records`、`voice_generation_configs`，音频文件仍保存在本地目录。
  - 新增 Flyway 建表脚本和 `scripts/create-postgres-db.ps1` 本地建库脚本。
  - 更新 `.env.example`、`README.md`、`AGENTS.md` 和 `scripts/README.md`，同步 PostgreSQL 环境变量、建库方式和联调边界。
- 验证结果：
  - 已先新增失败测试，确认旧 MongoDB 配置、缺失 Flyway SQL、缺失建库脚本和旧数据库错误文案会导致验证失败。
  - `.\mvnw.cmd test`：通过。
  - `npm run build`：通过。
  - `node --test scripts/tests/start-dev.test.js`：通过。
  - `pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\create-postgres-db.ps1 -Help`：通过。
  - `.\scripts\create-postgres-db.ps1`：本机未找到 `psql`，脚本按预期停止并提示先安装 PostgreSQL 或将 `psql` 加入 PATH。
  - 敏感信息扫描：未发现真实 MiniMax API Key、数据库密码或 Authorization Bearer 值。

## 2026-06-24 09:02 语音生成测试阶段取消持久化依赖

- 时间：2026-06-24 09:02
- commit ID：待提交后补记
- 修改内容：
  - 按当前测试需求取消语音生成模块的数据库持久化依赖。
  - 移除后端 JPA、Flyway、PostgreSQL 和 H2 相关依赖与启动配置，后端启动不再需要本地数据库。
  - 将生成记录和默认声音配置改为后端进程内存保存，支持当前会话内生成记录查询、音频播放和下载，后端重启后记录清空。
  - 删除临时 PostgreSQL 建库脚本和 Flyway 建表脚本。
  - 更新 `.env.example`、`README.md`、`AGENTS.md` 和 `scripts/README.md`，说明当前阶段只测试生成和下载功能，不做持久化保存。
- 验证结果：
  - 已先修改测试期望，确认旧 datasource/Flyway/JPA 配置会导致验证失败。
  - `.\mvnw.cmd test`：通过。
  - `npm run build`：通过。
  - `node --test scripts/tests/start-dev.test.js`：通过。
  - 无数据库依赖残留扫描：仅测试断言中保留 `spring.datasource`、`spring.jpa`、`spring.flyway`、`spring.data.mongodb` 等禁止项检查文本。

## 2026-06-24 09:39 修复 MiniMax 音色克隆请求和页面参数

- 时间：2026-06-24 09:39
- commit ID：待提交后补记
- 修改内容：
  - 排查付费音色克隆失败，定位为后端提交 MiniMax 克隆请求时将上传返回的 `file_id` 作为字符串传递，已调整为数值类型。
  - 调整 MiniMax 错误摘要，克隆失败时返回 `status_msg` 或状态码摘要，避免前端只显示笼统失败文案。
  - 调整语音生成工作台，付费克隆模式只保留母带音频和新音色 ID，不再展示或提交语速、音量、语调配置。
  - 补充前后端测试，覆盖克隆请求 `file_id` 类型、MiniMax 失败摘要和克隆模式参数栏隐藏。
  - 更新 `README.md`、`AGENTS.md` 和 `apps/web/README.md`，同步克隆模式输入边界和 MiniMax 音频限制。
- 验证结果：
  - `.\mvnw.cmd "-Dtest=DefaultMiniMaxVoiceClientTests" test`：通过。
  - `node --test src\tests\voiceGenerationWorkbench.test.js`：通过。
  - 未执行真实付费克隆调用，避免产生付费克隆成本；真实联调需使用符合 MiniMax 要求的母带音频和唯一音色 ID。

## 2026-06-24 14:00 修复音色克隆上传 413

- 时间：2026-06-24 14:00
- commit ID：待提交后补记
- 修改内容：
  - 排查音色克隆上传返回 HTTP 413，定位为 Spring Boot multipart 默认上传限制在业务代码前拦截母带文件。
  - 将后端 multipart 上传上限配置为单文件 20MB、完整请求 21MB，对齐 MiniMax 克隆母带大小限制并预留 multipart 边界开销。
  - 为 `MaxUploadSizeExceededException` 增加 JSON 错误处理，超过限制时返回 HTTP 413 和可读脱敏错误摘要。
  - 更新 `README.md`、`AGENTS.md` 和 `apps/web/README.md`，同步上传大小限制和 413 行为。
- 验证结果：
  - 已先新增失败测试，确认旧配置缺失上传上限，且上传过大时 HTTP 413 没有 JSON 错误摘要。
  - `.\mvnw.cmd "-Dtest=VoiceGenerationBackendConfigTests" test`：通过。
  - `.\mvnw.cmd "-Dtest=VoiceGenerationControllerTests" test`：通过。
  - `.\mvnw.cmd test`：通过。
  - `npm run build`：通过。
  - `node --test src\tests\adminSidebar.test.js src\tests\voiceGenerationApi.test.js src\tests\voiceGenerationWorkbench.test.js`：通过。
  - 敏感信息扫描：未发现真实 API Key、Authorization、Cookie 或长 Bearer token。

## 2026-07-01 11:20 解决语音生成工作台合并冲突

- 时间：2026-07-01 11:20
- commit ID：待提交后补记
- 修改内容：
  - 将 `origin/codex/voice-generation-workbench` 合入 `main`，解决 PR #1 的文档合并冲突。
  - 冲突处理以 PR 分支内容优先，保留语音生成 Web 生产台、后端 MiniMax 联调接口、内存记录存储、上传大小限制、`.env.example` 和一键启动脚本说明。
  - 保留自动合并后的管理员端侧边栏配置、非语音生成静态原型页面和语音生成页面路由。
  - 调整旧主线测试断言，将语音生成模块从“占位页”预期改为真实联调工作台预期。
  - 新增本轮合并冲突解决日志，不写入真实 API Key、Token、Cookie 或完整音频 URL。
- 验证结果：
  - 冲突标记扫描：无残留。
  - `git diff --check`：通过。
  - `node --check src/main.js`、`node --check src/router/index.js`、`node --check src/router/adminRoutes.js`、`node --check src/config/adminSidebar.js`、`node --check src/lib/voiceGenerationApi.js`、`node --check src/data/adminStaticData.js`：通过。
  - `node --test src/tests/adminSidebar.test.js src/tests/adminPrototypePages.test.js src/tests/voiceGenerationApi.test.js src/tests/voiceGenerationWorkbench.test.js`：通过，9 个测试全部通过。
  - `npm run build`：通过。
  - `node --test scripts/tests/start-dev.test.js`：通过，5 个测试全部通过。
  - `pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-dev.ps1 -Help`：未通过，当前机器未安装或未配置 `pwsh`；改用 `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-dev.ps1 -Help` 验证通过。
  - `.\mvnw.cmd test`：通过，14 个测试全部通过，Maven 最终结果为 `BUILD SUCCESS`；测试期间输出 Spring Security 开发密码和 Mockito 动态 agent 警告。
  - 敏感信息扫描：未发现真实 API Key、Token、Cookie、Authorization 头或长 Bearer token；命中项仅为空 `.env.example`、文档中的安全规则、测试假值和源码构造请求头位置。

## 2026-07-01 16:07 忽略语音生成音频产物

- 时间：2026-07-01 16:07
- commit ID：待提交后补记
- 修改内容：
  - 将 `backend/storage/voice-generation/` 加入 `.gitignore`，避免提交语音生成产生的本地音频文件。
  - 更新 `README.md` 和 `AGENTS.md`，说明该目录属于本地运行产物，不应提交到仓库。
- 验证结果：
  - `git check-ignore -v backend/storage/voice-generation/test.mp3`：通过，命中 `.gitignore` 中的 `backend/storage/voice-generation/` 规则。
  - `git status --short --branch`：通过，`backend/storage/` 不再显示为未跟踪目录。
  - `git diff --check`：通过。

## 2026-07-01 22:47 每日项目维护日志

- 时间：2026-07-01 22:47
- commit ID：待提交后补记
- 修改内容：
  - 执行每日项目维护前置检查，确认当前仓库工作树干净后追加 `docs/daily-maintenance-log.md`。
  - 记录本次维护不修改任何业务代码、接口、页面、API、数据库、配置或环境变量文件。
  - 本次自动化同时扫描 `D:\aimanju`、`D:\dunan`、`D:\kaokao`、`D:\Kaizhou-Golden-Chef`，阻塞项未写入对应仓库。
- 验证结果：
  - `git status --short --branch`：修改前当前仓库干净。
  - `git diff --check -- docs/daily-maintenance-log.md`：通过，仅提示 CRLF 换行转换警告。
  - 字节级对比：`docs/daily-maintenance-log.md` 的 HEAD 原始内容前缀保持一致，仅追加本次记录。

## 2026-07-11 20:27 MongoDB 身份、会话与语音生成持久化基础

- 时间：2026-07-11 20:27
- commit ID：待提交后补记
- 修改内容：
  - 引入 Spring Data MongoDB，配置 `users`、`sessions`、`voice_generation_records`、`voice_generation_configs` 集合与唯一索引、TTL 索引，统一使用 UTC `Instant`。
  - 新增 `ADMIN`、`REVIEWER`、`COLLECTOR` 三角色、BCrypt 后台密码、首管理员安全初始化和首次登录强制改密。
  - 统一 BCrypt 编码前密码规则为至少 8 个字符且 UTF-8 不超过 72 字节，覆盖管理员初始密码、首管理员配置和改密；超限登录凭证按无效凭证处理，不穿透编码器异常。
  - 新增后台不透明单会话、HttpOnly/SameSite=Lax Cookie、空闲续期、重复登录冲突、短时一次性接管、旧会话 `SESSION_REPLACED`、退出/改密/停用废止会话，以及认证态 CSRF token 获取与 Cookie/Header 校验。
  - 新增微信 `jscode2session` 服务端边界、小程序 30 天不透明 Bearer token 和录音人员姓名设置；严格拒绝客户端额外提交 OpenID。
  - 新增后台登录/接管/当前用户/退出/改密、微信登录/姓名和管理员创建/查询/停用后台账号接口，并按角色保护 `/api/**` 与语音生成接口。
  - 统一 API 错误为 `{ code, message, requestId, details? }`，响应回传 `X-Request-Id`，覆盖常见 4xx/5xx、脱敏异常和上传限制；请求结构错误保持 400，不支持的 Content-Type 返回 415，改密、姓名和后台角色等业务值错误使用 422。
  - 将语音生成记录、默认声音配置从内存迁到 MongoDB；MiniMax 合成失败持久化 `FAILED`，全局 multipart 提升为 100MB/105MB，克隆保留 20MB 业务限制。
  - 更新 `.env.example`、`README.md`、`AGENTS.md`、`scripts/README.md`，仅保留空值或安全默认值；未修改 Web、小程序和每日维护日志。
- 验证结果：
  - 按 TDD 先运行身份/Mongo/统一错误/语音持久化测试，确认缺少生产能力时测试编译失败；并发会话、未知 OpenID、非法 requestId、统一 400、首次改密 CSRF、密码 422、非法姓名 422、不支持媒体类型 415、管理员初始密码和首管理员 BCrypt 上限场景均先观察到预期红灯，再完成最小修复。
  - MiniMax 失败测试在保存时记录不可变状态快照；临时移除异常分支第二次保存后测试按预期红灯，恢复后转绿，排除同一可变对象别名造成的假阳性。
  - `backend\\mvnw.cmd test`：通过，73 个测试全部通过；本机未启动 MongoDB，测试期间驱动后台连接提示被拒绝，但测试不依赖开发机 MongoDB，Maven 最终为 `BUILD SUCCESS`。

## 2026-07-11 22:02 统一身份接口错误契约

- 时间：2026-07-11 22:02
- commit ID：待提交后补记
- 修改内容：
  - 调整管理员创建请求的初始密码 DTO 约束：缺字段或 `null` 仍按结构错误返回 400，空、短或超过 BCrypt 上限的已提供值统一交由服务层返回 422 `PASSWORD_TOO_WEAK`。
  - 会话认证过滤器复用统一 `ApiErrorWriter`；访问会话存储遇到数据库不可用时返回脱敏 503 `DATABASE_UNAVAILABLE`，其他未预期运行时异常返回脱敏 500 `INTERNAL_ERROR`。
  - 新增独立身份错误契约集成测试，覆盖管理员真实创建端点、真实 Spring Security 过滤器链、请求 ID 回传及异常敏感文本不泄漏。
  - 现有 `README.md` 与 `AGENTS.md` 已明确上述 422、503/500 和脱敏契约，本轮未重复修改；未修改 Web、小程序或每日维护日志。
- 验证结果：
  - 管理员创建端点测试先确认空、短、129 字符初始密码均错误返回 400，修复后 3 个用例统一返回 422 `PASSWORD_TOO_WEAK`。
  - 会话过滤器测试先确认数据库异常和未预期异常会直接穿透，修复后分别返回统一脱敏 503 与 500 响应。
  - `backend\\mvnw.cmd "-Dtest=GlobalApiExceptionHandlerTests,AdminUserServiceTests,SecurityAuthorizationTests,IdentityErrorContractIntegrationTests" test`：35/35 通过。
  - `backend\\mvnw.cmd test`：78/78 通过，0 failures、0 errors、0 skipped，`BUILD SUCCESS`；本机未启动 MongoDB，驱动后台连接提示被拒绝但不影响测试结果。
  - `git diff --check`：exit 0，仅有工作区 LF/CRLF 转换提示，无空白错误。
  - 敏感扫描：API Key/长 Bearer、已填写敏感环境变量、带凭证 MongoDB URI 均无匹配。

## 2026-07-11 23:24 平台、任务池与录音采集后端闭环

- 时间：2026-07-11 23:24
- commit ID：待提交后补记
- 修改内容：
  - 新增平台 CRUD、任务生命周期和发布后不可变版本；任务结构变更创建下一版本，限制任务编码、参考组件、微信采样率、单声道和首期 AI 禁用。
  - 新增任务授权、权限申请及 PENDING 条件原子决策；授权撤销只阻止新领取，已批准申请重放不会复活已撤销授权。
  - 新增任务池条目、可选 externalItemId 部分唯一索引、任务内 itemCode 序号和 Mongo `findAndModify` 原子领取；保证采集员全系统最多一条待录制作业。
  - 新增 operationId/assignmentId/revision 条件提交、人工待审、驳回返修和释放；幂等历史绑定原操作者，释放清当前结果并保留提交/操作历史。
  - 新增 WAV/MP3 录音校验、100MB 限制、稳定 `current.ext` 临时写入/原子替换/失败回滚、条目级并发串行、媒体鉴权和单 Range 读取。
  - 新增单条数据添加与 CSV/XLSX 异步导入，支持 operationId 幂等、部分成功和失败行重试；部分成功后只留失败行重试 CSV，移除成功行签名 URL。
  - 为平台、任务生命周期、授权、领取和导入重试等全部 Task 2 非 operationId 写入口增加持久化 Idempotency-Key，按操作者与操作类型保存 IN_PROGRESS/COMPLETED 首次响应并阻止重复执行。
  - 为任务/版本跨文档写入增加失败补偿；导入增加 50000 行上限、每 100 行进度、1000 条错误摘要上限、完整失败行号、10 分钟 worker 租约、心跳和启动恢复。
  - 新增远程参考媒体下载策略，默认 HTTPS，限制超时、重定向、类型和大小，阻止本机/私网等地址并将校验 IP 绑定到实际连接；持久化只保留 hostname、状态和脱敏摘要。
  - 修复 Web Cookie 请求夹带 Bearer 头时可能跳过 CSRF、重复 Mongo status 条件、跨采集员 operationId 重放、驳回快照不一致、领取 revision/历史非同次更新、删除异常被吞掉及大媒体魔数检查整文件入堆问题。
  - 更新 `.env.example`、`.gitignore`、`README.md`、`AGENTS.md` 和后端配置；未修改 Web、小程序或每日维护日志。
- 验证结果：
  - 按 TDD 分批观察任务/版本、授权/领取、媒体/SSRF、导入、持久化幂等、失败补偿和租约恢复的预期红灯并完成最小实现。
  - Task 2 定向 `clean test`：60/60 通过，0 failures、0 errors、0 skipped，`BUILD SUCCESS`。
  - 后端全量 `clean test`：138/138 通过，0 failures、0 errors、0 skipped，`BUILD SUCCESS`；本机未启动 MongoDB，Spring 上下文仅输出后台连接拒绝提示，不影响退出码和测试结果。

## 2026-07-12 00:42 加固导入恢复与媒体补偿

- 时间：2026-07-12 00:42
- commit ID：待提交后补记
- 修改内容：
  - 区分导入初始/过期恢复的 `FULL` 模式与用户显式失败行重试的 `FAILED_ROWS` 模式；过期 worker 全源幂等重放，避免把已知失败行误当作恢复白名单而永久漏行。
  - 将导入 heartbeat、分批进度和最终状态统一改为 `leaseOwner` fencing CAS，并使用 Mongo 返回的新状态；旧 owner 失去租约后不能覆盖完成态。
  - 修复任务发布及草稿版本覆盖的跨文档补偿，使用保存后最新 `@Version` 恢复；补偿本身失败时记录并返回受控一致性错误。
  - 平台删除前检查任务引用，仍被使用时返回 `409 PLATFORM_IN_USE`。
  - 新增 `media_cleanup_jobs` 持久化提交/释放后的旧录音备份和旧媒体元数据清理；即时失败保持 PENDING，同 operationId 重放和应用启动恢复均会重试。
  - 释放或纯文本提交前把稳定 current 隔离到 `temp/backups/` 唯一路径；单条添加在视频下载失败或条目保存失败时同时清理已下载文件和 `media_assets`，清理失败不再静默吞掉。
- 验证结果：
  - 按审查项逐条新增失败测试并实际观察 RED，随后完成最小修复。
  - Task 2 clean 定向：74/74 通过，0 failures、0 errors、0 skipped，`BUILD SUCCESS`。
  - 后端全量 `clean test`：152/152 通过，0 failures、0 errors、0 skipped，`BUILD SUCCESS`；本机 MongoDB 未启动，仅输出后台连接拒绝提示。
  - `git diff --check`：exit 0，仅有工作区 LF/CRLF 转换提示，无空白错误。
  -  tracked-file 敏感扫描：未发现真实 API Key、长 Bearer、带凭证 MongoDB URI 或已填写的敏感环境变量。

## 2026-07-12 16:00 完成后台登录与角色导航

- 时间：2026-07-12 16:00
- commit ID：待提交后补记
- 修改内容：
  - 新增后台统一 HTTP 客户端，支持同源 Cookie、CSRF、JSON/multipart、Idempotency-Key、统一错误和会话被接管通知。
  - 新增管理员/审核员登录、账号占用确认接管、首次登录强制改密及重新登录流程。
  - 新增会话初始化、ADMIN/REVIEWER 路由守卫和动态侧边栏；旧静态原型从生产导航及路由隐藏。
  - 语音生成 API 改为复用统一请求客户端，避免登录后写请求缺失 CSRF。
  - 增加 Vitest、Vue Test Utils、jsdom 测试基础，同时保留原有 Node 源码测试。
- 验证结果：
  - TDD 红灯覆盖请求客户端、登录/接管、首次改密、角色越权、身份服务不可用和重复会话接管。
  - `npm test -- --run`：原有 Node 9 项和 Vitest 10 项全部通过。
  - `npm run build`：通过。
  - 浏览器验证：后端未启动时登录页仍正常显示，提交显示脱敏 502；390px 窄屏无横向溢出。
  - 未验证项：真实 MongoDB 后台账号登录/接管和首次改密联调，留待最终全链路验收。
## 2026-07-12 16:12 完成 Web 任务管理与审核工作台

- 时间：2026-07-12 16:12
- commit ID：待提交后补记
- 修改内容：
  - 新增平台、任务与版本、数据池、单条添加、CSV/XLSX 导入进度与失败重试页面。
  - 新增任务授权搜索、直接授权、撤销、申请审批，以及后台用户创建、停用和密码重置页面。
  - 新增审核池、随机/批量领取、管理员分配/批量通过、参考媒体、补改文字、通过/驳回与审核释放工作台。
  - 新增任务/采集员/审核员统计、条目/全局操作记录及批量状态/释放/废弃/恢复交互。
  - 后端补充任务版本读取、用户跨角色搜索和后台密码重置接口；重置强制下次改密并废止会话。
  - 管理工作台移除模拟数字和旧原型死链接，生产导航只指向真实页面。
- 验证结果：
  - 前端 API、空/失败态和批量请求均先观察 RED，再实现为 GREEN。
  - `backend\\mvnw.cmd clean test`：190 tests，0 failures，0 errors，0 skipped，`BUILD SUCCESS`。
  - `npm test -- --run`：原有 Node 9 项与 Vitest 18 项通过；`npm run build` 通过。
  - 真实 MongoDB 页面联调留待最终全链路验收，不使用 mock 结果替代。
