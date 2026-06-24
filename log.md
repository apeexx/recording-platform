# AI 修改日志

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
