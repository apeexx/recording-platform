# AI 修改日志

## 2026-07-23 优化 Web 错误 Toast 图标

- 将错误 Toast 的实心红底文字感叹号替换为浅红背景、红色描边的本地 SVG 圆形感叹号，避免系统字体差异导致字形和对齐不稳定。
- 保持 Toast 的位置、文案、时长、去重、点击关闭和无障碍播报行为不变，不引入图标库或其他依赖。
- TDD 红灯确认旧实现没有错误 SVG 且仍包含文字 `!`；修复后 Web Node 12/12、Vitest 58/58、生产构建和差异格式检查通过。
- Chrome 已登录任务详情实测新图标正常显示为 `24 × 24` 视图的描边 SVG，Toast 文案和页面布局保持稳定；清理重载后控制台无新增 error 或 warn。

## 2026-07-23 统一 Web 警告与错误 Toast

- 将登录、首次改密、任务、数据池、CSV、权限、审核、用户、统计和语音生成的表单校验与操作失败统一为右上角 Toast，移除操作类行内红字；任务详情的非法参考媒体 URL 不再同时触发 Toast 和整页重试状态。
- 页面级错误仅用于核心数据首次加载失败；已有数据刷新或弹窗提交失败时保留当前内容与输入。危险操作确认、账号接管和审核员输入提示继续使用原确认交互。
- 错误 Toast 默认显示 4.5 秒并按相同文案自动去重，成功和信息提示保持 2.6 秒；点击可关闭，并按错误/普通提示设置即时或礼貌播报。
- 所有生产业务表单增加 `novalidate`，必填项、密码规则和统计查询改为手动校验，失败后聚焦第一个无效字段。
- TDD 红灯确认旧 Toast 无错误时长和自动去重、任务详情共享加载错误、行内红字、原生表单校验及语音生成错误覆盖状态；修复后 Web Node 12/12、Vitest 57/57 和生产构建通过。
- Chrome 已登录任务详情输入非法 HTTP 参考音频地址后只出现 1 个错误 Toast，整页错误状态为 0，添加区与数据池保持显示；清理重载后控制台无新增 error 或 warn。

## 2026-07-23 CSV 实时进度与参考媒体 URL-only

- 时间：2026-07-23
- 修改内容：
  - CSV 解析后立即保存总行数，并在每行处理完成后用 `PROCESSING + leaseOwner` fencing 原子 checkpoint 成功/失败计数、失败摘要、心跳与租约；Web 保持每秒轮询并展示真实中间计数。
  - 单条添加、CSV 导入和待领取编辑统一使用轻量 HTTPS URL 校验，不再执行远端下载、DNS、重定向、类型、大小或魔数检查，也不再创建新参考媒体副本和资产。
  - 历史媒体保持兼容：URL 未变化保留媒体 ID，修改或移除 URL 后清空 ID并持久化清理旧副本；删除仍清理历史媒体，公开兼容读取端点保留。
  - 删除不再使用的远程媒体下载/传输/地址策略与三个环境配置；更新音频和混合 CSV 为 Samplelib 五个短 MP3 轮换素材。
- 验证结果：
  - 后端全量测试 271/271、Web Node 12/12 与 Vitest 50/50、小程序 96/96 通过；Web 生产构建、修改 JavaScript 语法检查和 `git diff --check` 通过。
  - 两份 CSV 均通过固定三列、10 行和音频 URL 白名单校验；五个 Samplelib MP3 在交付前重新 HEAD 检查均返回 HTTP 200 `audio/mpeg`。
  - 本地服务已重启到本次代码，Chrome 登录态下任务详情可正常加载；浏览器扩展未开启文件 URL 访问权限，自动设置本地 CSV 被阻止，因此真实文件导入保留为手工复测项。

## 2026-07-23 修复 CSV 导入多轮轮询中断

- 时间：2026-07-23
- commit ID：见本次 Git 提交
- 修改内容：
  - 根因是创建导入响应使用 `importJobId`，完整状态响应只使用 `id`；首次查询后展示对象被完整状态覆盖，第二轮定时查询因找不到 `importJobId` 提前退出。
  - 任务详情新增独立稳定的活动导入任务 ID，创建或重试后保存该 ID，后续每轮状态查询不再依赖展示对象字段。
  - 终态、选择新文件和组件卸载时停止旧轮询；部分成功后的失败行重试继续使用原导入任务 ID。
  - 未修改后端接口、MongoDB 数据结构，也未处理暂缓的测试 7、8、11。
- 验证结果：
  - TDD RED：新增多轮假定时器测试时，预期查询 3 次、旧实现实际只查询 1 次，稳定复现第二轮中断。
  - 定向测试 17/17 通过；覆盖 `PROCESSING → PROCESSING → COMPLETED`、部分成功重试、选择新文件停止轮询和组件卸载停止轮询。
  - Web 全量测试 Node 12/12、Vitest 50/50 通过；Vite 生产构建、修改 JavaScript 语法检查和 `git diff --check` 通过。
  - Chrome 已登录任务详情页加载正常，数据池显示 7 条且控制台无 error/warn；自动选择本地 CSV 被扩展文件访问权限阻止，因此真实文件重新导入仍保留为手工复测项。

## 2026-07-23 登录、任务数据池与小程序资料体验优化

- 时间：2026-07-23
- commit ID：`7a213df`、`844a42f`、`0da3d0a`、`7f749cd`，文档提交见本次提交
- 修改内容：
  - Web 登录增加密码显示按钮；首次登录提供修改或永久跳过选择，首次改密只填写新密码，普通改密保持原密码校验。
  - 任务配置缩短录音时长控件，参考类型与数据池选择统一彩色方形复选框，参考文字仅允许纵向拖动并限制高度。
  - 任务详情改为双栏工作台；CSV 支持拖放/点击、显式开始、实时进度、完成提示与自动刷新，并提供带 BOM 的固定三列示例。
  - 新增草稿任务删除、待领取条目 revision/CAS 编辑与删除；媒体替换和删除复用持久化清理任务，任务/条目编号均不复用。
  - CSV 按任务启用参考类型读取列，解析完成立即持久化总行数；未启用列忽略，过滤后全空的行失败。
  - 小程序“我的”移除用户 ID，资料设置继续支持完整 ID 复制；恢复默认头像移至头像卡底部右侧。
- 验证结果：
  - 后端全量测试 280/280、Web 全量测试 46/46、小程序全量测试 96/96 通过，Web 生产构建成功。
  - 修改过的 JavaScript 语法与 `git diff --check` 通过；微信原生头像来源面板仍需在微信开发者工具或真机确认。

## 2026-07-20 使用弹窗创建 Web 后台账号

- 时间：2026-07-20
- commit ID：见本次 Git 提交
- 修改内容：
  - 移除用户管理 Web 页签中常驻的后台账号创建表单，将“创建后台账号”按钮放到搜索栏最右侧。
  - 点击按钮后使用主题化居中弹窗填写用户名、姓名、角色和初始密码；支持遮罩、取消和 Escape 关闭，以及焦点恢复和 Tab 循环。
  - 创建请求增加重复提交锁；成功后关闭、清空并刷新列表，失败后保留弹窗和已输入内容；小程序页签不显示创建入口。
  - 增加按钮位置、弹窗字段、成功提交、失败保留、取消关闭和页签切换回归测试。
- 验证结果：
  - TDD RED：新增按钮与弹窗测试 2 项按预期失败；失败保留/取消测试因缺少稳定取消入口按预期失败 1 项。
  - 定向 Web 测试 6/6 通过；Web 全量测试 Node 9/9、Vitest 32/32 通过，Vite 生产构建成功。
  - Chrome 已登录页面验证按钮位于搜索栏右侧、常驻表单消失、四字段弹窗与初始焦点正确；取消后恢复触发按钮焦点，小程序页签隐藏创建入口，控制台无警告或错误。本轮未填写或提交测试账号。
  - 未修改后端接口、数据库或会话逻辑，未清空数据库。

## 2026-07-20 简化小程序用户 ID 复制提示

- 时间：2026-07-20
- commit ID：见本次 Git 提交
- 修改内容：
  - 移除“我的”和资料设置页面中独立显示的“点击复制”文字。
  - 保留整行用户 ID 的复制点击区、成功/失败 Toast，以及“我的”页阻止同时跳转资料设置的事件边界。
  - 更新页面契约测试，明确两个页面均不得再显示 `copy-hint` 或“点击复制”。
- 验证结果：
  - TDD RED：旧界面的页面契约测试按预期 1 项失败，原因为仍包含 `copy-hint` 和“点击复制”。
  - TDD GREEN：资料页面契约和复制服务定向测试 12/12 通过。
  - 小程序全量测试 54/54 通过；复制服务、“我的”和资料设置三个 JavaScript 文件语法检查均通过。
  - 未修改后端、数据库、登录或会话逻辑，未清空数据库。

## 2026-07-20 拆分用户管理页签并支持复制小程序用户 ID

- 时间：2026-07-20
- commit ID：见本次 Git 提交
- 修改内容：
  - 管理端用户管理页增加“Web 端账号”和“小程序端账号”两个页签，默认加载 Web 用户，切换后按 `userType` 严格查询对应集合。
  - 后端用户搜索接口增加可选 `userType` 参数；显式指定类型时只访问对应用户存储，类型与角色不匹配时返回空分页，未指定时保留原跨类型搜索兼容性。
  - 小程序“我的”和资料页支持点击完整用户 ID 复制，并分别提示复制成功或失败。
  - 增加页签切换、严格分表查询、复制成功/失败与页面绑定回归测试，并同步更新身份接口和两端使用说明。
- 验证结果：
  - `backend\mvnw.cmd test`：258/258 通过，0 failures、0 errors、0 skipped，`BUILD SUCCESS`。
  - Web `npm test -- --run`：Node 9/9、Vitest 29/29 通过；`npm run build` 成功。
  - 小程序 `npm test`：54/54 通过；新增复制用户 ID 的成功、失败和空值保护测试均通过。
  - 本地开发服务就绪检查为 MongoDB、存储和整体状态全部 `UP`，未清空数据库。
  - Chrome 因后端重启导致原登录会话失效，未读取或请求密码；页签视觉切换和小程序真机剪贴板 Toast 仍需登录后手工确认。

## 2026-07-20 同步用户分表文档与运行存储忽略边界

- 时间：2026-07-20
- commit ID：见本次 Git 提交
- 修改内容：
  - 将当前身份说明更新为 `web_users` 与 `miniprogram_users`，明确 `WEB-...` / `MINI-...` ID、各自的唯一约束，以及小程序用户文档不保存角色。
  - 补充后台用户响应的 `id`、`userType`、`loginName` 契约、小程序 `POST /api/auth/miniprogram/takeover` 及两端单会话确认接管规则。
  - 更新本地重置说明，明确完整删除本地开发数据库会移除旧 `users` 与新身份集合，且无备份后重建首个 `WEB-...` 管理员。
  - 当前无已跟踪的存储占位文件；将 `backend/storage/` 运行内容整体忽略，不保留占位文件例外规则。
- 验证结果：
  - `backend\mvnw.cmd test`：256/256 通过，0 failures、0 errors、0 skipped，`BUILD SUCCESS`。
  - Web `npm test`：Node 9/9、Vitest 28/28 通过；`npm run build` 成功。小程序 `npm test`：50/50 通过。
  - 首次执行重置时，旧会话数据先触发新唯一索引冲突；新增脚本回归测试，并让重置进程临时关闭自动索引创建后再次执行成功。普通服务启动仍保持自动创建索引。
  - 已按确认无备份清空 `recording_platform` 及关联录音、头像、导入临时和语音生成文件；重建后旧 `users` 不存在，`web_users` 为 1、`miniprogram_users` 为 0，首管理员 ID 符合 `WEB-{24位十六进制}` 且不存在 `internalUserNo`。
  - 重启后 `GET /api/health/ready` 的 `overall`、`mongo`、`storage` 全部为 `UP`；首管理员登录返回 200 并保持首次改密状态。运行存储三个目录均为空。

## 2026-07-16 增加批量导入部分失败测试数据

- 时间：2026-07-16
- commit ID：待提交后补记
- 修改内容：
  - 新增 `docs/test-data/task-items-import-partial-failure.csv`，混合 2 条有效数据和 3 条无参考源数据。
  - 预期首次导入状态为 `PARTIAL_SUCCESS`，成功 2 行、失败 3 行；失败码覆盖 `ITEM_REFERENCE_REQUIRED`。
  - README 补充样例用途、预期计数和失败行重试仍会失败的边界，避免把重试误解为自动修复源数据。
- 验证结果：
  - 使用 Artifact Tool 回读并渲染检查 6 行 4 列数据，固定表头、中文内容和空列位置正确。
  - 对照后端任务条目创建与导入状态逻辑，确认有成功且有失败时状态为 `PARTIAL_SUCCESS`；无参考源映射到预期错误码。
  - `backend\\mvnw.cmd "-Dtest=ImportFileParserTests,TaskItemCreationServiceTests,ImportJobServiceTests" test`：15/15 通过，0 failures、0 errors、0 skipped，`BUILD SUCCESS`。

## 2026-07-15 生成任务批量导入 CSV 测试数据

- 时间：2026-07-15
- commit ID：待提交后补记
- 修改内容：
  - 新增 `docs/test-data/task-items-import-valid.csv`，提供 8 条仅含中文参考文字的有效任务池数据。
  - 表头固定为 `referenceText`、`referenceAudioUrl`、`referenceVideoUrl`。
  - 文件使用 UTF-8 BOM，便于 Windows 表格工具正确显示中文；后端导入解析器会安全移除 BOM。
- 验证结果：
  - 使用 Artifact Tool 创建、回读并渲染检查 9 行 4 列数据，中文与空列位置正确。
  - 后端解析器源码已确认会在校验固定表头前安全移除 UTF-8 BOM；`ImportFileParserTests` 继续验证 CSV/XLSX 固定表头和行数限制。

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
  - 新增任务池条目、任务内 itemCode 序号和 Mongo `findAndModify` 原子领取；保证采集员全系统最多一条待录制作业。
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

## 2026-07-12 16:30 完成原生微信小程序采集端

- 时间：2026-07-12 16:30
- commit ID：待提交后补记
- 修改内容：
  - 新增微信 code 登录、真实姓名设置和不透明 Bearer 会话本地恢复。
  - 新增任务大厅、权限申请、当前作业继续和原子领取；后端任务列表增加 ACTIVE/PENDING/NONE 权限状态。
  - 新增参考文字/音频/视频、录音暂停/继续/停止/试听/重录、可选文字、时长校验、幂等提交和释放。
  - 新增默认关闭的自动下一条，在池空、权限失效或任务不可领取时停止；新增累计/有效/释放/废弃统计和提交明细。
  - 测试 AppID 和 API 域名只允许写入已忽略的本地配置，仓库中不保存 AppSecret。
- 验证结果：
  - 小程序 Node 纯逻辑测试 9/9 通过，全部 JavaScript 语法和 JSON 配置解析通过。
  - 后端全量 191/191 通过，52 个测试集均为 0 failures、0 errors；本机 MongoDB 未启动，仅有后台连接拒绝提示。
  - 已调用微信开发者工具 CLI，但 IDE 服务端口未开启，工具要求人工确认后停止；真机麦克风/播放/上传验收留待全链路验收。

## 2026-07-12 16:45 完成启动就绪与全链路自动验收

- 时间：2026-07-12 16:45
- commit ID：待提交后补记
- 修改内容：
  - 新增公开只读 `GET /api/health/ready`，只返回整体、MongoDB 和录音存储的 `UP/DOWN`；失败返回 503，不泄露 URI、绝对路径或异常文本。
  - 启动脚本在结束 `8080/5173` 进程之前检查 MongoDB TCP 可达性和录音目录可写性；不打印连接串，不启动/停止 MongoDB，不处理 `27017` 进程。
  - 完成默认鉴权、CSRF、角色/本人/任务授权、幂等/revision、SSRF、路径穿越、Range、媒体清理、导入租约恢复、日志脱敏和 Git 忽略规则复核。
  - 同步根目录、Web、小程序、脚本和长期执行文档，清理 Web README 中已过时的静态原型描述。
- 验证结果：
  - `backend\mvnw.cmd clean test`：195/195 通过，0 failures、0 errors、0 skipped，`BUILD SUCCESS`。
  - Web：Node 9/9、Vitest 18/18 通过；`npm run build` 通过。
  - 脚本 7/7、小程序 9/9 通过；本机 MongoDB 不可用时真实执行启动脚本，确认它在启动前脱敏退出。
  - Edge/Playwright 实测登录页桌面/窄屏布局和后端不可达 502 失败态。
  - 未验证：本机无 MongoDB 可执行文件或服务，因此未执行真实 Mongo 登录/索引/并发链路；微信开发者工具服务端口未开启，未执行真机麦克风/播放/上传；未执行付费 MiniMax 调用。

## 2026-07-14 修复 Web 会话切换后的 CSRF 缓存

- 时间：2026-07-14
- commit ID：待提交后补记
- 修改内容：
  - 真实 MongoDB 联调发现，首次改密重新登录或账号接管后，前端仍可能复用上一 Web 会话缓存的 CSRF token，造成写请求携带的请求头与当前 Cookie 不一致并返回 403。
  - Web 建立新会话时同步清空 CSRF token 和请求缓存，下一次写操作会重新调用 `/api/auth/web/csrf`。
  - 新增会话切换后重新获取 CSRF 的回归测试，不记录 Cookie、会话令牌或 token 实际值。
- 验证结果：
  - 回归测试在修复前稳定失败，确认第二次平台写请求跳过了 CSRF 获取；最小修复后定向测试通过。
  - 真实浏览器使用新会话验证 `/api/auth/web/csrf` 返回 200、平台创建返回 201、平台列表刷新返回 200。

## 2026-07-14 修复连续 Web 写请求复用旧 CSRF

- 时间：2026-07-14
- commit ID：待提交后补记
- 修改内容：
  - 真实浏览器联调确认任务发布成功后 CSRF Cookie 会更新，后续暂停请求仍复用内存中的旧 token，导致请求头与 Cookie 不一致并返回 403。
  - 每次受 CSRF 保护的写请求结束后清除 token 和请求缓存，后续写请求重新调用 `/api/auth/web/csrf`；登录和接管接口仍保持免 CSRF。
  - 新增同一会话连续执行发布和暂停时必须分别获取 CSRF 的回归测试，全程不记录真实 Cookie、会话令牌或 token。
- 验证结果：
  - 回归测试在修复前稳定失败，明确显示暂停请求复用了发布请求的 token；最小修复后定向测试通过。
  - 真实浏览器在账号接管后的新会话中刷新页面，任务暂停成功并进入 `PAUSED`，未再出现 403。

## 2026-07-14 修复小程序无效录音权限声明

- 时间：2026-07-14
- commit ID：待提交后补记
- 修改内容：
  - 删除微信开发者工具明确报错的 `app.json.permission["scope.record"]`；录音授权继续由实际开始录音时的 RecorderManager 流程处理。
  - 新增小程序配置回归测试，防止无效权限声明再次写回。
  - SharedArrayBuffer、灰度基础库和 `getSystemInfo` 提示与本次录音权限错误无关，未做无关修改。
- 验证结果：
  - 配置测试在修复前稳定失败，删除无效声明后转为通过。
  - 微信开发者工具重新编译后不再出现无效 `scope.record` 警告，模拟器正常显示微信登录页；仅保留不阻塞测试的基础库提示。

## 2026-07-14 兼容微信登录纯文本响应类型

- 时间：2026-07-14
- commit ID：待提交后补记
- 修改内容：
  - 本地小程序联调发现 `jscode2session` 返回 JSON 内容时可能使用 `text/plain` 响应类型，原有 Spring 消息转换按 JSON 媒体类型读取会失败并误返回 `503 WECHAT_UNAVAILABLE`。
  - 微信客户端改为先读取响应字符串，再将内容解析为受控字段映射；继续只使用 AppID 与 OpenID 建立身份，不保存或输出 `session_key`、临时 code、AppSecret 或第三方完整响应。
  - 新增 `text/plain` JSON 响应回归测试，覆盖微信实际响应类型兼容边界。
- 验证结果：
  - 回归测试在修复前稳定返回 `WECHAT_UNAVAILABLE`；最小修复后定向测试通过。
  - `backend\\mvnw.cmd clean test`：195/195 通过，0 failures、0 errors、0 skipped，`BUILD SUCCESS`；测试过程实际连接本机 MongoDB。
  - 微信开发者工具真实联调通过：微信登录后进入姓名设置，保存测试姓名后进入任务大厅，并正确显示已发布任务及未授权状态；未记录临时 code、Bearer token、OpenID 或第三方完整响应。

## 2026-07-14 修复采集权限侧边栏入口

- 时间：2026-07-14
- commit ID：待提交后补记
- 修改内容：
  - 修复 `/admin/permissions` 被路由重定向到任务列表，导致管理员无法从侧边栏进入采集权限管理的问题。
  - 新增采集权限任务入口页，提供任务分页、加载态、空状态、失败重试，并从所选任务进入已有申请审批、直接授权和撤销页面。
  - 新增路由回归测试，确保侧边栏入口绑定真实页面而不是重定向。
- 验证结果：
  - 路由回归测试在修复前明确收到 `/admin/tasks` 重定向；替换为真实页面后定向测试 5/5 通过。
  - Web 完整测试通过：Node 9/9、Vitest 21/21；`npm run build` 成功生成采集权限入口页产物。
  - 管理员真实登录页面验收通过：侧边栏进入采集权限任务入口后可选择测试任务，并在任务权限页看到小程序提交的 `PENDING` 申请及通过、驳回操作。

## 2026-07-15 修复审核音频 Range 播放

- 时间：2026-07-15
- commit ID：待提交后补记
- 修改内容：
  - 真机提交的标准 MP3 已成功入库，但审核页原生播放器请求 `Range: bytes=0-` 时，Spring MVC 无法为 `ResourceRegion` 与预设 `audio/mpeg` 找到消息转换器，统一错误处理后表现为 500 `INTERNAL_ERROR` 和 `0:00 / 0:00`。
  - 媒体接口改为明确的 `StreamingResponseBody` 类型；完整文件和单 Range 均按固定缓冲区流式复制，继续返回既有 200/206、`Content-Type`、`Content-Length`、`Content-Range` 与 `Accept-Ranges`，不整体加载最大 100MB 文件。
  - 保持媒体鉴权、路径校验、文件存在性校验、多段/非法 Range 416 规则、数据库记录和当前录音文件不变。
  - 新增真实 Spring MVC HTTP 回归测试，覆盖 MP3 的 `Range: bytes=0-` 响应状态、头和字节内容；未记录 Cookie、Bearer Token 或真实会话值。
- 验证结果：
  - 回归测试在修复前稳定复现 `expected 206, actual 500`，内部异常为 `HttpMessageNotWritableException`；修复后媒体定向测试 3/3 通过。
  - `backend\\mvnw.cmd test`：196/196 通过，0 failures、0 errors、0 skipped，`BUILD SUCCESS`。
  - `apps/web` 下 `npm test -- --run`：Node 9/9、Vitest 21/21 通过；`npm run build` 通过。
  - 重启后端并刷新审核工作台后，真实浏览器播放器可加载并播放真机提交的 MP3，人工听音确认录音内容正常。

## 2026-07-15 规范本地录音存储目录

- 时间：2026-07-15
- commit ID：待提交后补记
- 修改内容：
  - 定位到启动脚本按仓库根目录检查 `RECORDING_STORAGE_DIR`，而后端按 `backend/` 工作目录解析同一相对值，导致实际生成 `backend/backend/storage/recordings`。
  - 新增公共存储路径解析器，相对路径固定按仓库根目录解析；录音存储、导入临时文件和就绪检查统一复用该规则，绝对路径保持不变。
  - 停止后端后对重复目录中的现有录音执行逐文件 SHA-256 冲突检查；目标无冲突后移动 1 个文件，迁移前后哈希一致，并删除已经为空的重复目录。
  - MongoDB 中的媒体相对路径、media ID、任务状态和提交历史均未修改；真实录音和 `.env` 未加入 Git。
- 验证结果：
  - 新增测试先因缺少 `StoragePathResolver` 产生预期编译失败；最小实现后路径及存储相关定向测试 19/19 通过。
  - `backend\\mvnw.cmd test`：199/199 通过，0 failures、0 errors、0 skipped，`BUILD SUCCESS`。
  - 启动脚本测试 7/7、小程序测试 10/10、Web Node 测试 9/9、Vitest 21/21 通过，Web 构建成功。
  - 使用 `scripts\\start-dev.cmd` 重启后，8080/5173 正常监听；就绪接口返回 `overall`、`mongo`、`storage` 全部 `UP`，目标目录保留 1 个历史录音文件且 `backend/backend` 未重新生成。
  - 迁移后在真实浏览器审核工作台播放 I000002，时长显示正常且人工听音确认内容正常。
  - 微信真机领取并提交 I000003 后进入 `REVIEW_PENDING`；落盘文件大小与接口元数据一致，只写入规范目录，`backend/backend` 未重新生成，审核工作台显示约 3 秒且人工听音确认正常。

## 2026-07-15 13:45 扁平化录音路径并合并过程目录

- 时间：2026-07-15 13:45
- commit ID：待提交后补记
- 修改内容：
  - 当前录音稳定相对路径由 `recordings/{taskCode}/{itemCode}/current.wav|mp3` 扁平化为 `{taskCode}/{itemCode}.wav|mp3`，保留既有原子替换、备份清理、媒体鉴权和任务状态逻辑。
  - 增加默认关闭、仅显式启用的一次性旧路径迁移器，并完成本机 2 个历史录音文件及 MongoDB 旧路径引用迁移；迁移前后文件大小和 SHA-256 一致，第二次幂等复检迁移计数为 0。
  - 将 `docs/superpowers` 下 3 个计划和 3 个设计文件按相对路径、SHA-256 无冲突迁入根目录 `.superpowers/plans`、`.superpowers/specs`，保留 `.superpowers/sdd` 全部现有内容，并删除已为空的源目录。
  - 将 `.superpowers/` 加入 Git 忽略，并在 `AGENTS.md` 增加过程文档统一放置的长期规则；未修改 README、业务代码、接口、数据库结构或依赖。
- 验证结果：
  - 历史迁移首轮 `migrated=2`、`deduplicated=0`，第二轮 `migrated=0`、`deduplicated=0`；旧录音目录和迁移环境开关均不存在。
  - `backend\\mvnw.cmd test`：209/209 通过，0 failures、0 errors、0 skipped，`BUILD SUCCESS`。
  - 过程文档迁移冲突计数为 0，6 个目标文件 SHA-256 与源文件一致，`.superpowers/sdd` 基线 22 个文件保持完整。
  - 本轮目录整理不重复执行浏览器或真机验收；最近一次真实验收已确认迁移前录音可播放及新录音可提交，本次最终全链路验收由后续整合任务执行。

## 2026-07-15 13:53 完成录音路径扁平化整合验收

- 时间：2026-07-15 13:53
- commit ID：待提交后补记
- 修改内容：
  - 将 README、长期执行规则和实施清单中的当前录音路径统一更新为 `{taskCode}/{itemCode}.wav|mp3`，并同步旧稳定文件的隔离清理语义。
  - 在实施清单记录扁平化路径、一次性迁移、过程目录合并及本轮自动化验收状态；`.superpowers/` 继续仅作为本地忽略资料，不恢复 `docs/superpowers`。
  - 使用一键启动脚本恢复后端和 Web 开发服务，核对历史录音已位于新路径，旧嵌套目录、`current.*` 文件和重复 `backend/backend` 目录均不存在。
- 验证结果：
  - `backend\\mvnw.cmd test`：209/209 通过，0 failures、0 errors、0 skipped，`BUILD SUCCESS`。
  - 启动脚本测试 7/7、小程序测试 10/10、Web Node 测试 9/9、Vitest 21/21 通过；`npm run build` 成功。
  - `git diff --check` 通过；差异敏感扫描未发现 Bearer Token、API Key、AppSecret 或带凭证 MongoDB URI。
  - `scripts\\start-dev.cmd` 启动后，`GET /api/health/ready` 返回 `overall`、`mongo`、`storage` 全部 `UP`；8080 与 5173 均正常监听。
  - `backend/storage/recordings/` 下存在 `TEST_TEXT_RECORDING/I000002.mp3`、`TEST_TEXT_RECORDING/I000003.mp3`；旧嵌套 `recordings/` 目录不存在，`current.*` 文件计数为 0。
  - 未执行微信真机返修、同条目重录覆盖和释放清理；这些交互项保留人工验收。未调用付费 MiniMax。

## 2026-07-15 14:10 加固一次性录音路径迁移一致性

- 时间：2026-07-15 14:10
- commit ID：待提交后补记
- 修改内容：
  - 将迁移器调整为全路径、全 MongoDB 快照预检后统一提交；任一路径提交失败时，按逆序补偿此前已完成的文档和文件操作。
  - 正向替换按 `_id + 旧版本 + 精确旧路径` CAS 并推进版本；回滚按 `_id + 新版本 + 精确新路径` CAS 再推进版本，避免静默覆盖并发写入。
  - MongoDB 回滚 CAS 冲突或异常时保留新文件，并在旧路径缺失时复制补回，确保不确定状态下新旧引用都保持可读。
  - 补充多路径后段失败、全量快照先于首个 mutation、回滚冲突/异常、陈旧写入和重复媒体资产等回归测试。
  - README、长期规则和实施清单同步补充默认关闭、停写备份、单实例执行、二次零迁移复检、关闭开关与失败恢复手册；本轮未执行真实迁移。
- TDD 记录：
  - 首轮新增测试运行 15 项，出现 6 个预期失败，覆盖后续路径失败未全局恢复、CAS 不严格、版本未推进以及回滚失败误删新文件。
  - 补充缺失媒体资产快照测试后运行 16 项，出现 1 个预期失败；增加全量资产快照校验后 16/16 通过。
- 验证结果：
  - `backend\\mvnw.cmd "-Dtest=RecordingPathMigrationServiceTests,RecordingMediaStorageTests,MediaAccessAndRangeTests,RecordingPlatformBackendApplicationTests" test`：27/27 通过，0 failures、0 errors、0 skipped，`BUILD SUCCESS`。
  - `backend\\mvnw.cmd clean test`：215/215 通过，0 failures、0 errors、0 skipped，`BUILD SUCCESS`。
  - `git diff --check` 通过；变更文件敏感扫描未发现真实 Authorization、Bearer Token、API Key、AppSecret 或带凭证 MongoDB URI。
  - 本轮未启用 `RECORDING_PATH_MIGRATION_ENABLED`，未修改真实 MongoDB 数据或录音文件；未执行微信真机验收和付费 MiniMax 调用。

## 2026-07-15 15:10 修复管理员单条审核权限与 CSRF 误报

- 时间：2026-07-15 15:10
- commit ID：待提交后补记
- 修改内容：
  - 修复 Spring Security 允许 ADMIN 访问审核接口、但 ReviewService 二次门禁仅接受 REVIEWER 的权限规则冲突；管理员现在可直接单条通过或驳回任意待审核条目，仍使用 revision、提交 operationId 和原子条件更新。
  - 保持审核员必须领取并只能处理本人审核占用；管理员审核池隐藏领取、批量领取和释放审核按钮，审核员不再看到无法直接处理的未领取“审核”入口。
  - 管理员审核池查询全部待审核条目（含已分配给审核员的条目），审核员池仍只查询未占用条目；审核员手工进入非本人占用工作台时不展示通过、驳回或释放按钮。
  - CSRF 缺失或失效改为返回 `CSRF_TOKEN_INVALID`，真实角色越权继续返回 `ACCESS_DENIED`；Web 请求层仅对 CSRF 失效刷新令牌并自动重试一次。
  - 管理员驳回仍回到原采集员的待录制状态，清除审核占用并追加“审核环节驳回到采集环节”操作记录。
- TDD 记录：
  - 修复前管理员单条通过抛出 `ACCESS_DENIED`，管理员驳回无法进入原子状态校验；页面角色入口断言失败。
  - CSRF 分类和单次重试测试在修复前分别返回 `ACCESS_DENIED` 和直接抛错，证明错误分类及恢复逻辑缺失。
- 验证结果：
  - `backend\\mvnw.cmd "-Dtest=ReviewServiceTests,SecurityAuthorizationTests" test`：25/25 通过。
  - `npx vitest run src/tests/httpClient.test.js src/tests/reviewPages.test.js`：8/8 通过。
  - `backend\\mvnw.cmd test -q`：218/218 通过，0 failures、0 errors、0 skipped。
  - `npm test`：Web Node 测试 9/9、Vitest 23/23 通过；`npm run build` 成功。
  - `scripts\\start-dev.cmd` 重启后，`GET /api/health/ready` 返回 `overall`、`mongo`、`storage` 全部 `UP`。
  - 真实管理员在 Web 审核工作台驳回 `I000004` 返回 HTTP 200；状态由 `REVIEW_PENDING` 变为 `RECORDING_PENDING`，revision 由 2 变为 3，原采集员、assignmentId、录音结果和提交历史保留，审核占用清空，操作记录写入管理员及驳回原因。

## 2026-07-15 18:10 完成本地采集审核闭环人工验收

- 时间：2026-07-15 18:10
- commit ID：见本次 Git 提交
- 验收环境：
  - 本机 MongoDB、Spring Boot 后端、Vite Web、Chrome/Edge、微信开发者工具及微信真机。
  - 使用个人测试 AppID 和脱敏测试数据；未记录 Cookie、Bearer Token、OpenID、AppSecret 或真实密码。
- 真实业务验收：
  - 后台登录、会话接管、首次改密和 ADMIN/REVIEWER 角色菜单通过；审核员不能看到管理员任务、用户或语音生成入口。
  - 完成平台、任务版本、池数据、权限申请审批、领取、MP3 录音试听与上传、文字单独提交、返修重录、采集员释放、软废弃与恢复。
  - 同条目返修后录音稳定路径保持 `{taskCode}/{itemCode}.mp3`，新录音覆盖旧文件且审核页可播放；采集员释放后条目回到 `AVAILABLE`，当前结果与稳定文件清理，历史提交和操作记录保留。
  - 管理员直接驳回后原采集员继续返修；独立 REVIEWER 账号完成首次改密、领取审核、释放审核、再次领取、补充普通话文本并审核通过。
  - 审核释放后条目仍为 `REVIEW_PENDING`，审核归属清空，采集音频和提交记录不删除；审核通过后音频与补充文字同时保留并进入 `COMPLETED`。
- 统计核对：
  - 审核员通过 `I000003` 前的任务、采集员和小程序个人统计快照一致：累计提交 5、累计时长 12 秒、当前有效完成 1、当前有效时长 0 秒、释放 2、废弃 1；当时的当前有效结果为纯文字，因此有效音频时长为 0 秒。
  - 审核员统计与操作一致：领取 2、释放 1、通过 1、驳回 0、平均处理 18 秒。
- 自动化复验：
  - `backend\\mvnw.cmd test`：218/218 通过，0 failures、0 errors、0 skipped，`BUILD SUCCESS`。
  - `apps/web` 下 `npm test -- --run`：Node 9/9、Vitest 23/23 通过；`npm run build` 成功。
  - 启动脚本测试 7/7、小程序测试 10/10 通过；`git diff --check` 与文档敏感信息扫描通过。
- 未执行项：
  - 未执行付费 MiniMax 调用。
  - 本次为开发环境与个人测试 AppID 验收；正式上线仍需公司小程序账号、HTTPS 合法域名、生产 MongoDB、生产存储备份和监控验收。

## 2026-07-16 修复审核领取修订号记录偏差

- 修复审核员单条及批量领取时，操作历史 `resultRevision` 在已递增的 revision 上再次加一的问题。
- 保持审核领取状态流和 CAS 条件不变；返回条目 revision 与 `REVIEW_CLAIM` 操作记录的 `resultRevision` 现在一致。
- 新增 Mongo 聚合更新回归测试，验证修订号只递增一次且操作记录复用更新后的 revision。
- 针对性后端测试已通过。
# 2026-07-17 全面优化：项目化任务、返修队列与录音交互

- 整体删除平台领域、Web 平台页面及 `/api/platforms` 接口；任务编号改由 Mongo 原子序列生成 `T000001`，条目编号按任务拼接 7 位序号。
- 任务版本新增最终成果类型 `TEXT`/`AUDIO`：两类任务均必须录音，`TEXT` 提交录音和文本，`AUDIO` 仅提交录音；关闭人工审核时隐藏并清空驳回预设原因。参考输入框按启用组件显示，Web 状态统一映射为中文。
- 审核流程调整为先选任务再进入审核池；驳回进入独立 `REWORK_PENDING`，小程序新增普通/返修待办列表并展示驳回原因。
- Web 新增全局悬浮操作提示和登录接管弹窗；权限审批后即时移除待审批项并展示用户姓名、编号。
- 小程序录音改为 PCM 样本计时和 RMS 波形，提供麦克风、暂停、结束状态；WAV 流式写入，MP3 固定使用 `@breezystack/lamejs@1.2.7` 本地 vendor。
- 导入范围收敛为 CSV；新增带数据库名、初始管理员和存储目录保护的本地全量重置入口。

## 2026-07-18 删除任务条目外部编号

- Web 单条添加和数据池列表移除外部编号，条目只展示系统生成编号。
- 后端请求、任务条目模型、Mongo 唯一索引和 CSV 导入契约删除外部编号字段。
- CSV 表头调整为 `referenceText,referenceAudioUrl,referenceVideoUrl`，同步更新测试数据、接口文档和回归测试。
- 修复本地重置的非 Web 启动模式：跳过仅 Servlet 环境需要的安全配置，并在清库、清理媒体及重建首管理员后自动退出。
## 2026-07-19 小程序双登录、独立资料与头像系统

- 新增微信快捷登录与数字账号密码登录，同一采集员跨设备保持同一用户 ID、权限和任务数据。
- 新增独立个人资料设置、资料完整性前后端守卫、微信头像上传与本地耳机默认头像；头像限制 JPEG/PNG/WebP 5MB 并使用原子替换。
- Web 用户管理新增采集员筛选、修改数字账号和重置密码；本地全量重置同步清理头像目录。
- 补充后端身份/头像/守卫测试、小程序双登录与页面契约测试、Web 采集员管理测试，并同步接口、数据库和运维说明。

## 2026-07-19 小程序 A 版登录与头像交互优化

- 时间：2026-07-19 17:19
- commit ID：见本次 Git 提交
- 修改内容：
  - 登录页调整为“砚数声采”A 版视觉并使用正式品牌图标。
  - 头像交互改为弹窗选择微信头像、相册或默认头像，三种来源均先预览确认。
  - “我的”页面展示真实 userId，以及最近 20 条提交记录和状态。
- 验证结果：
  - 小程序测试 30/30 通过；修改过的 JavaScript 语法检查和页面 JSON 解析通过。
  - `git diff --check` 通过；变更差异未发现 Bearer token、带凭据 MongoDB URI、API Key、AppSecret 或 Access Token 值。
  - `chooseAvatar`、`chooseMedia`、遮罩关闭和保存锁仍需在微信开发者工具及真机验收。

## 2026-07-19 小程序头像直达与 Tab 图标优化

- 头像卡改为微信原生 `chooseAvatar` 按钮，点击后直接进入微信头像来源面板；移除应用内重复的来源菜单，保留选择后的预览确认和上传锁。
- “恢复默认头像”改为头像卡下方独立文字入口，确认后才删除自定义头像；头像读取失败继续静默回退默认头像。
- 资料页每次重新加载前清除历史错误，资料成功或头像文件缺失时不再残留“请求资源不存在”。该错误的本地根因是 8080 仍运行在资料/头像路由提交之前的旧后端版本，本轮通过重启现有开发服务同步运行版本。
- 原生“任务”“我的”Tab 新增普通与选中状态的 81×81 本地 PNG 图标，颜色分别为 `#94A3B8` 和 `#2563EB`，未增加依赖或外链。
- TDD 回归在旧实现上出现 5 个预期失败；独立审查再发现并修复慢速资料加载覆盖新操作错误的竞态。实现后小程序测试 34/34、后端头像与资料契约测试 5/5 通过，JavaScript 语法、`app.json`、图标尺寸/格式/体积和差异格式检查通过。
- `scripts\start-dev.cmd` 已停止旧的 8080/5173 进程并启动当前代码；新 Java/Node 进程启动于 19:04，`GET /api/health/ready` 返回 `overall`、`mongo`、`storage` 全部 `UP`。微信原生头像面板和 Tab 选中态仍需在开发者工具及真机确认。
## 2026-07-21 改造提交后修改与审核领取状态流

- 新增 `SUBMITTED`：人工审核任务提交后仍允许原采集员覆盖保存；审核领取或分配后进入 `REVIEW_PENDING` 并锁定采集修改，审核释放回到 `SUBMITTED`，免审任务仍直接完成。
- 新增指定条目审核领取接口，管理员和审核员决定前均需领取或分配；批量通过不再接受未领取条目，所有竞争继续使用 revision/CAS 和 `STALE_STATE`。
- `/api/task-items/mine` 新增 `PENDING`、`SUBMITTED`、`FINISHED`，保留旧枚举兼容，并按流程顺序、更新时间倒序和条目序号升序分页。
- 应用启动幂等迁移 reviewerId、reviewAssignmentId 均为空的旧待审核记录；本轮完整后端测试启动时迁移 1 条，未清空数据库且未修改已领取记录。
- Web 审核池区分已提交与待审核；小程序入口改为“查看任务”，任务数据页改为三页签，作业页支持已提交修改以及待审核/已完成只读。
- 后端完整 Maven 测试 265/265、Web Node 9/9 与 Vitest 33/33、Web 构建、小程序 Node 测试 57/57 均已通过；两枚 Iconfont 本地 SVG 已归档并记录搜索来源、SVG 标识与许可边界，未使用 CDN。

## 2026-07-21 优化小程序录音作业页视觉与播放器

- 录音作业页重构为固定高度的待录音、录音中、已暂停、处理中和已录制状态区域，暂停与继续使用同尺寸控件，消除切换时的文字和图标错位。
- 参考音频、录制结果及只读结果统一接入本地自定义播放器，支持互斥播放、拖动进度、结束复位、错误反馈和销毁清理；页面移除原生 `<audio>` 及重复时长。
- 麦克风、播放、暂停、继续、停止和垃圾桶统一为本地 Iconfont SVG，并补充搜索来源和许可边界；未增加依赖、未修改后端接口、MongoDB 或任务状态流。
- 独立代码审查发现并修复组件卸载后延迟音频事件仍更新页面、播放器内部时长回写同名 property 触发 observer 重置资源两个生命周期问题；最终复核无 Critical/Important。
- 小程序全量 Node 测试 63/63 通过，3 个修改 JavaScript 文件语法检查、页面 JSON 与组件路径检查、`git diff --check` 和差异敏感信息扫描均通过。
- 微信开发者工具已核对待录音、录音中和已暂停状态的固定布局；完成态播放器实际加载、拖动及较窄屏幕仍需在保有测试会话的开发者工具或真机完成最终人工验收。

## 2026-07-21 修复小程序播放器空资源解码错误

- 定位到 `audio-player` 组件初始化时 observer 与 attached 会短暂传入空 `src`，播放服务仍将空字符串写入 `InnerAudioContext.src`，触发微信基础库的空资源解码错误。
- 空资源现在只复位播放器内部显示，不再写入微信音频上下文；有效本地路径的加载、互斥播放、拖动、结束复位和销毁逻辑保持不变。
- 新增空资源回归测试，修复前稳定记录两次空 `src` 写入，修复后不再写入；播放器专项测试 6/6、小程序全量测试 64/64 通过。
- 微信开发者工具重新编译并进入录音作业页后，控制台未再出现空资源解码错误；测试录音完成后自定义播放器正常显示有效本地音频。

## 2026-07-21 优化小程序当前帧对称波形

- 将原先从左向右累积的等高柱状波形替换为当前 PCM 帧 RMS 映射；不保存历史音量，也不使用移动平均。
- 7 根柱子使用中心最高、左右对称的权重；RMS `0.02` 及以下保持 `8rpx` 平线，RMS `0.25` 及以上按最高幅度显示，最大柱高限制为 `100rpx`。
- 开始录音、暂停和错误时立即恢复平线，继续录音后由下一帧更新；录音、文件生成、上传、后端接口和数据库均未修改。
- 新增静音、中等音量、满幅、边界和帧独立性测试；波形映射并入既有 `services/pcm.js`，避免开发者工具启用“忽略未使用文件”时漏打包新增独立模块。小程序全量测试 68/68 通过；微信开发者工具已确认录音时波形中心最高且左右对称，暂停后立即恢复七根平线，控制台不再出现模块缺失。

## 2026-07-21 优化前端按钮圆角、刷新与导航图标

- 小程序全局清除微信原生按钮附加边框，普通按钮统一中等圆角，迷你、胶囊和圆形按钮保留各自语义尺寸，避免双层边框造成圆角失真。
- 任务数据页补充下拉刷新，并与任务大厅、我的统计共同作为仅有的三个下拉刷新数据页；登录、资料设置和录音作业不启用，避免打断输入或录音。
- Web 管理端一级菜单不再截取标题首字，改为配置驱动的本地 Iconfont SVG；左上品牌标识使用与小程序字节一致的“砚数声采”SVG，二级菜单圆点和右上用户头像保持不变。
- 新增小程序圆角/刷新静态测试和 Web 图标/品牌资产测试；未新增依赖，未修改后端接口、数据库或业务状态流。

## 2026-07-22 增加任务数据分页与时间排序

- 小程序“任务数据”三个页签改为服务端每页 20 条的显式分页，提供上一页、页码摘要和下一页；切换页签回到第一页，下拉刷新保持当前页，越界时回到新的最后一页。
- 采集员条目查询取消状态流程 rank，统一按 `updatedAt` 倒序、`sequence` 升序，并由 MongoDB 直接执行排序和分页。
- 新增 `docs/test-data/task-items-import-pagination-101.csv`，包含 101 条唯一中文参考文字和空媒体 URL，用于验证六页边界。
- TDD 红灯分别验证旧后端仍按状态分组且未应用 Mongo 分页、旧小程序仍固定读取 100 条；针对性后端测试 10/10、小程序分页相关测试 9/9 通过。
- 完整回归通过：后端 266/266、小程序 73/73、Web Node 12/12 与 Vitest 33/33，Web 构建成功；修改过的 JavaScript 语法、CSV 101 行结构与唯一性、`git diff --check` 均通过。本地 MongoDB 隔离夹具在提交后单独写入，不计入可提交业务文件。

## 2026-07-22 优化小程序页码选择与任务详情分页

- 小程序“任务数据”从每页 20 条调整为每页 10 条，保留上一页和下一页，并将中间摘要改为微信原生页码选择器，可直接跳转任意页。
- 分页边界逻辑并回页面脚本并删除独立 `pagination.js`，避免开发者工具开启“忽略未使用文件”时再次漏打包页面辅助模块。
- Web 任务详情中的数据池从固定加载 100 条改为每页 10 条的服务端分页，标题展示总条数，翻页清空当前页勾选，数据减少时自动回到新的最后一页；独立“任务数据池”页面仍保持每页 20 条。
- 本轮未修改后端接口、MongoDB 数据、排序规则或业务状态流；排序继续使用 `updatedAt` 倒序、`sequence` 升序。
- TDD 红灯分别确认旧小程序仍依赖每页 20 条辅助模块、旧 Web 任务详情仍固定加载 100 条；最终小程序 73/73、Web Node 12/12、Vitest 34/34 通过，Web 构建成功。

## 2026-07-22 完善任务详情数字分页

- Web 任务详情数据池启用可选数字 Pagination，支持每页 5/10/20 条并默认 10 条，显示首尾页、当前页附近五个页码、必要的省略号和左右箭头。
- 切换每页条数会回到第一页并清空勾选；数字翻页同样清空当前页勾选，继续使用服务端分页和现有越界回退。
- 共享分页组件通过可选 `numbered` 模式扩展，任务列表、审核池、权限页和独立“任务数据池”的原简洁分页保持不变。
- 本轮未修改后端接口、MongoDB 数据或小程序，也未新增依赖。
- TDD 红灯确认旧组件没有数字模式且任务详情仍使用固定常量；最终 Web Node 12/12、Vitest 36/36 通过，Vite 构建成功。Chrome 实页验证默认 10 行、切换 5 行回到第一页、数字跳转第二页首条为 `TPAGE20260722-0000006`，控制台无 error/warn。

## 2026-07-22 按任务隔离未完成作业

- 普通 `RECORDING_PENDING` 从采集员全局唯一调整为 `(collectorId,taskId)` 唯一；同一任务再次领取返回已有条目，其他任务可各自领取一条，返修、已提交和审核状态流保持不变。
- 新增幂等启动索引迁移：先创建 `unique_collector_task_recording_pending`，再精确删除旧 `unique_collector_recording_pending`；创建失败不删除旧索引、不改写任务条目，本地全量重置模式跳过该迁移。
- 小程序任务大厅删除“当前有未完成作业”全局卡片、继续入口和 `currentTaskItemId` 缓存；每个任务通过自身的“查看任务”或“领取新数据”进入对应作业。
- TDD 红灯分别确认跨任务领取被旧作业劫持、跨任务并发无法分别领取、复合索引缺失、旧首页提示仍存在，以及迁移 Runner 错误依赖自动建索引开关；修复后专项后端 25/25、小程序 73/73、完整后端 274/274 通过。
- 新后端启动后就绪接口返回 MongoDB 与存储均 `UP`；只读索引核对确认新复合部分唯一索引已生效且旧全局索引已移除。未清空数据库，也未迁移或改写现有 `task_items` 文档。

## 2026-07-22 支持公网参考音视频直接播放

- 新建或导入条目在现有远程媒体安全校验和后端副本保存成功后，同时持久化规范化的 `referenceAudioUrl`、`referenceVideoUrl`，小程序作业页优先直接绑定公网 URL 播放。
- 历史条目无法恢复原始 URL 时改用公开的 `/api/media/public/reference/{mediaId}` 兼容地址；该端点只允许参考音频和参考视频，录音结果继续要求鉴权且从公开路径固定不可读取。
- 参考音频、参考视频和已提交录音改为独立加载；单个媒体失败只显示对应错误，不再使条目编号、版本、录音区和另一媒体一起消失。
- 根因是微信开发者工具 `downloadFile` 默认临时目录产生 `ENOENT`，原有 `Promise.all` 又把单个媒体失败扩大为整页加载失败；新的参考媒体播放链路不再使用该临时目录。

## 2026-07-22 支持连续作业与多条领取

- 普通 `RECORDING_PENDING` 取消采集员数量唯一限制；每个新的领取幂等键从 AVAILABLE 按 sequence 原子领取一条新数据，相同键重放继续返回首次结果。
- 启动迁移先确保普通索引 `collector_task_status`，再精确删除全局和任务级两个旧待录制唯一索引；失败终止启动，不清空数据库，也不改写已有条目、结果或历史。
- 小程序作业页新增默认开启并本机记忆的“提交后自动领取下一条”开关；待录制或待返修提交后可直接进入下一条，弱网领取重试复用同一幂等键，失败则提示并进入当前任务数据页；已提交修改不触发领取。
- 录音、参考音频、参考视频和结果音频统一互斥，只暂停其他活动媒体而不自动恢复；录音暂停仍保持平线且不写 PCM。登录、领取、提交、录音保存与媒体播放等操作错误改为悬浮 Toast，完整加载失败保留重试状态。
- TDD 红灯覆盖无限领取、普通索引迁移、稳定领取键、开关持久化、媒体互斥和 Toast；最终后端 274/274、小程序 84/84 通过，未新增依赖。

## 2026-07-22 允许小程序媒体并行并收紧作业布局

- 修正上一轮媒体口径：删除全局活动音频和作业页媒体协调逻辑，录音、参考音频、结果音频、参考视频以及多个音频播放器现在可同时运行，不再互相暂停。
- 页面卸载仍由音频组件销毁、原生视频生命周期和录音会话释放分别停止资源；连续作业、Toast、录音暂停平线和接口行为保持不变。
- 录音区外层/状态层最小高度调整为 `460rpx / 392rpx`，作业页专用文字输入框固定为 `200rpx`，不影响全局 textarea 或其他页面。
- TDD 先确认旧互斥行为、协调绑定和旧高度产生预期红灯，再以最小修改恢复专项测试；小程序全量测试 84/84、修改 JavaScript 语法检查、JSON/组件路径检查均通过，未新增依赖，未修改后端、API 或 MongoDB。

## 2026-07-22 缓存小程序个人资料并精简提交记录

- 根因是资料完整性守卫每次强制刷新资料，并把除 401 外的网络失败统一落入“请先完善个人资料”弹窗；断网因此被误判为资料未完善。
- 资料守卫改为优先使用 `recSession.user`：已完善缓存立即放行并静默刷新，缓存未知时才同步联网确认；网络失败使用悬浮 Toast，服务端明确未完善时才显示资料设置弹窗。
- 登录和接管继续整体覆盖当前会话，退出登录与修改密码继续删除整个 `recSession`；姓名、完成资料和头像元数据更新后统一写回缓存，头像二进制文件不新增缓存。
- “我的”页面提交记录请求从最近 20 条调整为最近 5 条，继续使用后端现有排序和响应结构；未修改后端、API 契约或 MongoDB。
- TDD 红灯分别复现缓存完整仍被阻止、网络错误未显示 Toast、页面未先展示缓存、头像元数据未回写及请求仍为 20 条；最终小程序全量测试 94/94、修改 JavaScript 语法检查、JSON/组件路径检查均通过。

## 2026-07-22 优化小程序断网加载提示

- 根因是作业页整体加载失败时直接渲染微信请求层的 `error.message`，断网产生的 `request:fail` 因此作为界面文案暴露。
- 作业页现在仅对带 `network` 标记的加载错误显示“网络链接失败，请检查网络。”；其他业务错误继续保留原提示，阻塞状态和“重新加载”入口保持不变。
- TDD 先锁定网络错误映射和重试入口并确认旧实现红灯，修复后小程序全量测试 95/95 通过；未修改后端、API、MongoDB 或媒体播放逻辑。

## 2026-07-22 简化任务配置与成果提交规则

- 删除任务版本模型、存储、索引和 `/api/tasks/{taskId}/versions` 接口，将参考类型、成果类型、审核、录音参数、时长和驳回原因统一嵌入 `tasks.configuration`；条目只保留 `taskId`。任务仅在 DRAFT 状态可编辑，发布后名称、说明和配置永久冻结。
- `TEXT` 成果改为文本或录音至少一项，也允许同时提交；两项皆空返回 `RESULT_REQUIRED`。`AUDIO` 继续强制录音并拒绝夹带文本。没有音频时不执行媒体格式、采样率、声道、大小和时长校验；录音时长配置统一限制为 1–600 秒。
- Web 新增无依赖自定义下拉、胶囊开关和双端时长滑块，任务列表展示最终成果、格式、采样率和时长；发布后的任务不再显示编辑入口。小程序直接读取任务配置，新增 PCM 样本 `MM:SS` 计时、胶囊页码触发器与开关样式，并移除版本文案。
- 自动测试：后端 `clean test` 272/272、Web 40/40、小程序 96/96 通过；未新增第三方依赖。按照本轮明确约定，不迁移旧开发数据，完整验证后使用受保护脚本重置本地 `recording_platform`。
- 首次重置暴露旧状态迁移 Runner 在重置上下文关闭后继续访问 Mongo 的顺序缺陷；补充条件注册与回归测试后，状态迁移和索引迁移均在 reset 模式跳过，受保护脚本成功清库并重建首管理员。

## 2026-07-22 增加四类任务导入验收数据

- 新增纯文本、纯音频、纯视频和混合四份 CSV，每份固定 10 条数据并使用 `referenceText,referenceAudioUrl,referenceVideoUrl` 表头。
- 音视频字段复用公开 HTTPS 测试素材；混合数据覆盖两两组合和三种参考内容同时存在，所有行至少包含一种参考内容。
- 使用工作区表格运行时生成并回读校验 CSV，同时补充 README 中的文件用途和远程素材可用性说明。

## 2026-07-23 修复草稿数据准备与小程序资料设置

- Web 修正双端时长滑块单轨几何，草稿列表改为“发布、删除”，数据池分页拆分为左侧条数与右侧页码，并对齐管理壳分隔线和采集权限顶部卡片高度。
- 草稿、运行中和暂停任务均可立即添加或导入数据；结束任务返回 `INVALID_TASK_STATE`。草稿删除先取消并 fence 活动导入，安全重试后级联清理条目、导入记录/源文件、授权和申请。
- 小程序资料完整性改为只要求姓名，数字账号和密码可成对留空并稍后后补；增加账号用途说明，移除恢复默认头像入口及前端调用，保留后端兼容接口。
- 未引入新依赖；定向回归覆盖 Web 布局、草稿状态与删除、小程序资料契约和头像交互。

## 2026-07-23 细化时长滑块与任务结束状态

- Web 时长范围改为细灰轨、蓝色选区和白色小圆点，最小值与最大值随各自圆点移动；边缘值保持在控件内，相邻值使用错位显示避免重叠。
- 任务运行中只显示“暂停”，暂停后才显示“恢复、结束”；后端同步限制只有 `PAUSED` 可以结束，其他状态返回 `INVALID_TASK_STATE`。
- 管理端品牌区和顶部栏统一为固定 72px 高度，品牌分隔线扩展到侧栏完整宽度，修复两侧横线错位和中间断开。
- 未新增依赖；按红灯到绿灯补充滑块结构、任务状态与管理壳布局回归测试。

## 2026-07-23 迁移双端时长滑块验收原型

- 将 `.superpowers/slider-preview.html` 已验收的统一坐标模型迁移到 `DurationRangeSlider`，使用 22px 胶囊轨道、16px 蓝色选区和 20px 白色圆点，圆点、选区和标签共享同一像素换算。
- 保留现有 `v-model:min-value`、`v-model:max-value` 和 1–600 秒约束；新增轨道点击最近圆点、自定义拖动、方向键、Home/End、焦点反馈和相邻标签避让。
- 透明原生 range 仅作为键盘与无障碍输入源，视觉层完全由自定义 DOM 绘制；未新增依赖，未修改后端接口、数据库或其他任务状态逻辑。
- TDD 红灯确认旧组件缺少自定义圆点、统一像素几何和轨道点击，绿灯后组件专项测试 6/6、Web Node 12/12、Vitest 62/62 与生产构建通过。
- 已登录 Chrome 实页验证极值端点、普通拖动、`300–301` 相邻标签避让、方向键与 Home/End，控制台无 error/warn；验收后恢复未保存表单为 `1–600`，未写入任务数据。

## 2026-07-24 调整双端时长滑块主题配色

- 保持已验收的 22px 轨道、16px 选区、20px 圆点及统一像素坐标不变，仅将高饱和主色选区调整为由 `--primary` 与 `--card` 混合的柔和浅蓝。
- 未选轨道改用 `--accent` 与 `--card` 派生的浅蓝灰，圆点边框、阴影、悬停与键盘焦点状态统一使用低比例主题蓝，避免滑块比同页按钮和开关更抢眼。
- 未修改 Vue 组件、数据流、后端接口或数据库；新增生产 CSS 配色契约回归测试，防止选区再次直接使用高饱和 `--primary`。

## 2026-07-24 增加标注脚本中心机器写入接口

- 新增 `POST /api/integrations/tasks/{taskId}/items`，允许标注脚本中心使用专用 `X-API-Key` 与 `Idempotency-Key` 添加参考文本、参考音频 URL、参考视频 URL 的任意非空组合。
- 机器身份只具有 `INTEGRATION_IMPORT` 权限，不复用 ADMIN、Web Cookie、CSRF 或小程序 Bearer；服务端仅配置原始 Key 的 SHA-256，未配置、错误凭证和非法配置均按脱敏错误安全失败。
- 外部写入复用现有任务状态、参考类型、HTTPS URL、序号与持久化幂等规则；操作人固定为 `annotation-script-center`，不新增 MongoDB 字段，不下载、代理或保存外部媒体文件。
- 新增鉴权、权限隔离、请求结构、字段组合、业务边界和专用操作人测试；同步更新 `.env.example`、README 与长期接口规则。
