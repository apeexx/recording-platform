# 录音任务平台完整闭环实施清单

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:test-driven-development` for every behavior change and `superpowers:verification-before-completion` before each commit. The user requested inline continuation from this checklist; do not pause between completed tasks unless a real blocker requires user input.

**目标：** 在现有 Vue3/Vite、原生微信小程序、Spring Boot 3.5.15、MongoDB 和本地媒体存储基础上，交付账号、任务、采集、审核、返修、操作记录、统计和管理页面的可验收闭环。

**架构：** 保持模块化单体。后台 Web 使用 HttpOnly 服务端会话与 CSRF；小程序使用微信 `code2session` 和不透明 Bearer token；后端以 MongoDB 条件更新、幂等记录、租约和补偿任务保证一致性；媒体文件保存在单机本地目录，MongoDB 只保存相对路径和元数据。

**技术栈：** Java 17、Spring Boot 3.5.15、Spring Data MongoDB、Vue 3、Vue Router、Vite、Vitest、Vue Test Utils、原生微信小程序、Node.js `node:test`。

## 全局约束

- 固定角色仅为 `ADMIN`、`REVIEWER`、`COLLECTOR`；任务授权不是新角色。
- 首期不实现真实 AI 转写、AI 审核或甲方平台自动取数；AI 只保留后端结构，Web/小程序不展示可执行入口。
- 个人微信 AppID 只用于可清理测试环境；仓库不保存真实 AppID、AppSecret、Mongo 密码、API Key、签名 URL 或个人截图。
- MongoDB 由本机或服务器独立提供；仓库不添加 Docker Compose。
- 录音最终路径固定为 `recordings/{taskCode}/{itemCode}/current.wav|mp3`；旧音频不保留，历史元数据保留。
- 数据库时间统一保存 UTC `Instant`；前端展示为 `Asia/Shanghai`。
- `docs/daily-maintenance-log.md` 不属于本任务，禁止修改。
- `backend/HELP.md` 当前缺失，禁止为了本任务补造。
- 每个任务按 RED → GREEN → 全量验证 → 中文 commit 执行；最终是否 push 以项目规则和用户当前授权为准。

---

## 当前进度基线

- [x] `c511fef`：MongoDB、三角色身份、后台单会话、微信登录边界、统一错误、语音生成持久化。
- [x] `2f0dc7d`：修复身份接口 422 契约和认证过滤器数据库错误边界。
- [x] `83a3a4c`：平台、任务版本、授权、任务池、录音媒体、导入和持久化幂等。
- [x] `59f59de`：导入 lease/fencing、版本补偿、平台引用保护、媒体清理任务和单条添加补偿。
- [x] 阶段二本地复核完成；修复导入 worker 丢失租约时可能删除数据库仍引用源文件的问题，后端 153 个测试通过。
- [x] 人工审核、动态状态、释放、软废弃恢复后端。
- [x] 操作记录查询与统计后端。
- [x] 后台登录、账号接管、首次改密、CSRF 请求层与角色导航。
- [x] 管理员与审核员 Web 真实业务页面。
- [ ] 原生微信小程序采集端。
- [ ] 启动脚本、健康检查、安全加固和全链路验收。

---

### Task 0：复核阶段二最终提交

**文件：**

- Review: `backend/src/main/java/com/recording/platform/importing/ImportJobService.java`
- Review: `backend/src/main/java/com/recording/platform/importing/store/mongo/MongoImportJobStore.java`
- Review: `backend/src/main/java/com/recording/platform/task/service/TaskManagementService.java`
- Review: `backend/src/main/java/com/recording/platform/media/MediaCleanupService.java`
- Review: `backend/src/main/java/com/recording/platform/importing/TaskItemCreationService.java`
- Test: `backend/src/test/java/com/recording/platform/**`

**验收接口：**

- `ImportJob` 的 `FULL/FAILED_ROWS` 模式不能遗漏崩溃时尚未处理的行。
- worker 的 heartbeat/progress/finish 必须按 `leaseOwner` 做 fencing CAS。
- task/version 补偿必须使用保存后的最新 version；补偿失败返回 `TASK_CONSISTENCY_RECOVERY_FAILED`。
- `media_cleanup_jobs` 必须支持即时尝试、operationId 重放和启动恢复。

- [x] **Step 0.1：运行后端 clean 全量测试。**

  运行位置：`backend`

  ```powershell
  .\mvnw.cmd clean test
  ```

  预期：至少 152 个测试，0 failures、0 errors、0 skipped，`BUILD SUCCESS`。

- [x] **Step 0.2：只读检查最终两个提交。**

  ```powershell
  git diff --check 83a3a4c..59f59de
  git status --short --branch
  ```

  预期：无空白错误，工作区干净，`main` 至少 ahead 4。

- [x] **Step 0.3：确认没有未关闭的 Critical/Important。**

  检查 import fencing、版本补偿、平台引用、cleanup 重试和单条添加双重清理；若发现缺口，先补失败测试再修复。

  复核结果：新增租约接管回归测试；失败行文件使用 worker 唯一名称，只有 fenced `finish` 成功后才切换并清理旧源文件，丢失租约时保留数据库引用的原文件。

---

### Task 1：审核、动态状态与操作记录后端

**文件：**

- Create: `backend/src/main/java/com/recording/platform/review/model/ReviewAssignment.java`
- Create: `backend/src/main/java/com/recording/platform/review/service/ReviewService.java`
- Create: `backend/src/main/java/com/recording/platform/review/controller/ReviewController.java`
- Create: `backend/src/main/java/com/recording/platform/task/service/TaskItemAdministrationService.java`
- Create: `backend/src/main/java/com/recording/platform/task/controller/TaskItemAdministrationController.java`
- Modify: `backend/src/main/java/com/recording/platform/task/model/TaskItem.java`
- Modify: `backend/src/main/java/com/recording/platform/task/store/TaskItemStore.java`
- Modify: `backend/src/main/java/com/recording/platform/task/store/mongo/MongoTaskItemStore.java`
- Modify: `backend/src/main/java/com/recording/platform/config/SecurityConfig.java`
- Test: `backend/src/test/java/com/recording/platform/review/ReviewServiceTests.java`
- Test: `backend/src/test/java/com/recording/platform/review/ReviewApiSecurityIntegrationTests.java`
- Test: `backend/src/test/java/com/recording/platform/task/TaskItemAdministrationServiceTests.java`

**对外接口：**

- `GET /api/reviews/pool`
- `POST /api/reviews/claim`
- `POST /api/reviews/claim-batch`
- `POST /api/reviews/assign`
- `GET /api/reviews/{itemId}`
- `POST /api/reviews/{itemId}/release|approve|reject`
- `POST /api/task-items/{itemId}/status|discard|restore`
- `POST /api/task-items/batch/status|approve|release|discard|restore`

- [x] **Step 1.1：写审核领取红灯测试。**

  验证 REVIEWER 可跨任务看到 `REVIEW_PENDING`，随机领取和批量领取使用条件更新；同一条目不能被两个审核员领取；ADMIN 可分配；REVIEWER 可释放审核领取且不删除采集结果。

- [x] **Step 1.2：实现最小审核领取 Store/Service/API。**

  `findAndModify` 条件必须包含 `status=REVIEW_PENDING`、`reviewerId` 为空和 `revision`；领取后写 `reviewAssignmentId`、审核员和操作历史。

- [x] **Step 1.3：写通过/驳回红灯测试。**

  通过进入 `COMPLETED`；驳回要求“预设原因至少一项或补充说明非空”，回到 `RECORDING_PENDING`，保留原 collector/assignment，清 reviewer，并把审核结论写入对应 submission 历史。

- [x] **Step 1.4：实现逐条审核和 ADMIN 批量通过。**

  REVIEWER 不得调用批量通过；批量返回每条 `{itemId,success,code,message,revision}`，单条冲突不能回滚其他成功项。

- [x] **Step 1.5：写动态状态、废弃和恢复红灯测试。**

  普通状态调整不得进入 `AVAILABLE`；未启用人工审核不得进入 `REVIEW_PENDING`；首期不得进入 `AI_PROCESSING`；进入 `RECORDING_PENDING` 且无 collector 时必须提供 collectorId；`DISCARDED` 保存 `discardedFromStatus`、归属、结果和文件。

- [x] **Step 1.6：实现单条/批量状态、释放、废弃和恢复。**

  恢复必须重新验证任务版本允许的状态、collector 全局唯一占用和 revision；失败逐条返回冲突。返回池只能调用 release。

- [x] **Step 1.7：验证并提交。**

  ```powershell
  .\mvnw.cmd "-Dtest=ReviewServiceTests,ReviewApiSecurityIntegrationTests,TaskItemAdministrationServiceTests" test
  .\mvnw.cmd clean test
  ```

  预期：定向和全量均 0 failures/errors。提交信息：`实现(backend): 完成人工审核与状态管理`。

---

### Task 2：操作记录查询与统计后端

**文件：**

- Create: `backend/src/main/java/com/recording/platform/report/service/ReportService.java`
- Create: `backend/src/main/java/com/recording/platform/report/controller/ReportController.java`
- Create: `backend/src/main/java/com/recording/platform/report/dto/*`
- Create: `backend/src/main/java/com/recording/platform/operation/controller/OperationController.java`
- Modify: `backend/src/main/java/com/recording/platform/task/store/TaskItemStore.java`
- Modify: `backend/src/main/java/com/recording/platform/task/store/mongo/MongoTaskItemStore.java`
- Test: `backend/src/test/java/com/recording/platform/report/ReportServiceTests.java`
- Test: `backend/src/test/java/com/recording/platform/operation/OperationControllerTests.java`

**对外接口：**

- `GET /api/task-items/{itemId}/operations`
- `GET /api/operations`
- `GET /api/reports/tasks|collectors|reviewers|me|me/submissions`

- [x] **Step 2.1：写操作记录三列红灯测试。**

  对外字段固定为东八区时间、操作人、操作内容；内部仍保存 UTC、operatorId、姓名快照、actionCode、from/to、batchId。释放、废弃和状态调整不得删除旧记录。

- [x] **Step 2.2：实现分页查询和权限。**

  ADMIN 可查全部；COLLECTOR 只查本人条目；REVIEWER 按审核权限读取。分页固定 `{items,page,size,total}`。

- [x] **Step 2.3：写两套统计口径红灯测试。**

  累计工作量统计所有提交/返修次数和时长；当前有效结果只统计 `COMPLETED` 且非 `DISCARDED` 的当前结果；释放/废弃单列。审核员统计领取、释放、通过、驳回、平均处理时长。

- [x] **Step 2.4：实现 Mongo 聚合与个人详情。**

  查询使用 UTC 时间范围；API 返回 ISO 时间或东八区展示字段，不把东八区字符串写入 Mongo。

- [x] **Step 2.5：验证并提交。**

  ```powershell
  .\mvnw.cmd "-Dtest=ReportServiceTests,OperationControllerTests" test
  .\mvnw.cmd clean test
  ```

  提交信息：`实现(backend): 增加操作记录与工作统计`。

---

### Task 3：Web 登录、会话与角色导航

**文件：**

- Modify: `apps/web/package.json`
- Create: `apps/web/src/lib/httpClient.js`
- Create: `apps/web/src/lib/authApi.js`
- Create: `apps/web/src/composables/useAdminSession.js`
- Create: `apps/web/src/pages/auth/AdminLoginPage.vue`
- Create: `apps/web/src/pages/auth/FirstPasswordPage.vue`
- Create: `apps/web/src/router/guards.js`
- Modify: `apps/web/src/router/index.js`
- Modify: `apps/web/src/router/adminRoutes.js`
- Modify: `apps/web/src/config/adminSidebar.js`
- Modify: `apps/web/src/components/admin/AdminHeader.vue`
- Test: `apps/web/src/tests/httpClient.test.js`
- Test: `apps/web/src/tests/authFlow.test.js`
- Test: `apps/web/src/tests/adminNavigation.test.js`

**接口消费：** `/api/auth/web/login|takeover|me|csrf|logout|password`。

- [x] **Step 3.1：加入 Vitest、Vue Test Utils 和 jsdom 开发依赖。**

  只用于测试，不进入生产 bundle；新增 `npm test` 脚本。不得引入 Pinia、Tailwind 或 UI 库。

- [x] **Step 3.2：写 httpClient 红灯测试。**

  覆盖 Cookie credentials、CSRF 获取/回传、JSON/multipart、Idempotency-Key、非 JSON 错误、401/403/409/413/415/422；`SESSION_REPLACED` 只触发一次清会话和跳转。

- [x] **Step 3.3：实现登录、接管和首次改密页面。**

  `ACCOUNT_IN_USE` 显示“账号正在其他设备使用”并二次确认；接管使用 takeoverToken；首次改密完成后清会话并要求重新登录。

- [x] **Step 3.4：实现角色路由守卫和动态导航。**

  ADMIN 显示全部核心业务与语音生成；REVIEWER 只显示审核池、统计和个人账号；未业务化静态原型从导航和生产路由隐藏。

- [x] **Step 3.5：验证并提交。**

  ```powershell
  npm test -- --run
  npm run build
  ```

  提交信息：`实现(web): 完成后台登录与角色导航`。

---

### Task 4：Web 管理、审核与统计页面

**文件：**

- Create: `apps/web/src/lib/platformApi.js`
- Create: `apps/web/src/lib/taskApi.js`
- Create: `apps/web/src/lib/reviewApi.js`
- Create: `apps/web/src/lib/reportApi.js`
- Create: `apps/web/src/pages/admin/platforms/*`
- Create: `apps/web/src/pages/admin/tasks/TaskListPage.vue`
- Create: `apps/web/src/pages/admin/tasks/TaskEditorPage.vue`
- Create: `apps/web/src/pages/admin/tasks/TaskDetailPage.vue`
- Create: `apps/web/src/pages/admin/tasks/TaskPoolPage.vue`
- Create: `apps/web/src/pages/admin/tasks/TaskPermissionsPage.vue`
- Create: `apps/web/src/pages/admin/review/ReviewQueuePage.vue`
- Create: `apps/web/src/pages/admin/review/ReviewWorkbenchPage.vue`
- Replace: `apps/web/src/pages/admin/system/UsersPage.vue`
- Replace: `apps/web/src/pages/admin/system/OperationLogsPage.vue`
- Replace: `apps/web/src/pages/admin/reports/*`
- Test: `apps/web/src/tests/taskPages.test.js`
- Test: `apps/web/src/tests/reviewPages.test.js`
- Test: `apps/web/src/tests/reportPages.test.js`

- [x] **Step 4.1：实现平台、任务、版本和数据池页面。**

  包含加载/空/失败态、分页、发布/暂停/恢复/结束、版本锁定、单条添加、CSV/XLSX 导入进度、失败重试、详情媒体、状态/释放/废弃/恢复和逐条批量结果。

- [x] **Step 4.2：实现任务授权和用户管理。**

  支持按姓名/内部ID搜索、直接授权、撤销、申请批准/驳回；ADMIN 创建管理员/审核员、停用和密码重置。角色固定，不提供自定义角色页。

- [x] **Step 4.3：实现审核池和工作台。**

  随机/批量领取、ADMIN 分配、参考媒体、采集音频、补改文字、逐条通过、预设多选+补充说明驳回、释放审核领取；只有 ADMIN 显示批量通过。

- [x] **Step 4.4：实现操作记录和统计页面。**

  操作记录固定三列；统计分累计工作量和当前有效结果，释放/废弃单列。使用数字卡和表格，不引入图表库。

- [x] **Step 4.5：真实交互测试和构建。**

  ```powershell
  npm test -- --run
  npm run build
  ```

  提交信息：`实现(web): 完成任务管理与审核工作台`。

---

### Task 5：原生微信小程序采集端

**文件：**

- Create: `apps/miniprogram/app.js`
- Create: `apps/miniprogram/app.json`
- Create: `apps/miniprogram/app.wxss`
- Create: `apps/miniprogram/sitemap.json`
- Create: `apps/miniprogram/project.config.example.json`
- Create: `apps/miniprogram/config/index.js`
- Create: `apps/miniprogram/services/api.js`
- Create: `apps/miniprogram/services/session.js`
- Create: `apps/miniprogram/services/recorder.js`
- Create: `apps/miniprogram/pages/login/*`
- Create: `apps/miniprogram/pages/tasks/*`
- Create: `apps/miniprogram/pages/work/*`
- Create: `apps/miniprogram/pages/profile/*`
- Test: `apps/miniprogram/tests/*.test.js`

- [ ] **Step 5.1：建立无真实 AppID 的可编译骨架。**

  `project.config.json` 和 private 配置加入 `.gitignore`；仓库只提交占位 example。AppSecret 永远不进入小程序。

- [ ] **Step 5.2：写登录/任务/录音纯逻辑红灯测试。**

  覆盖 `wx.login` 只发送 code、姓名设置、token、授权申请、当前作业恢复、录音参数、提交校验、operationId 稳定、弱网安全重试和自动下一条决策。

- [ ] **Step 5.3：实现登录和任务页。**

  展示已授权/申请中/可申请状态、系统 userId、当前作业继续/释放；开始任务不接受客户端 userId。

- [ ] **Step 5.4：实现录音作业页。**

  按任务版本展示文字/音频/视频参考；支持权限请求、开始/暂停/继续/停止/试听/重录、可选文字、提交、释放。文字关闭时必须音频；开启时音频或文字至少一项。

- [ ] **Step 5.5：实现自动下一条和个人统计。**

  开关默认关闭并保存在本地；只在提交成功后领取下一条；权限撤销、任务暂停/结束或池空时停止。统计展示累计、当前有效、释放/废弃和明细。

- [ ] **Step 5.6：Node、结构和开发者工具验证。**

  ```powershell
  node --test apps/miniprogram/tests/*.test.js
  ```

  解析全部 JSON，检查 app.json 页面存在且无真实 AppID/AppSecret/域名。开发者工具 CLI 能无交互预检时执行；真机验证必须单独记录，不能伪称完成。

  提交信息：`实现(miniprogram): 完成微信录音采集闭环`。

---

### Task 6：启动、健康、安全与全链路验收

**文件：**

- Modify: `scripts/start-dev.ps1`
- Modify: `scripts/tests/start-dev.test.js`
- Create/Modify: `backend/src/main/java/com/recording/platform/health/*`
- Modify: `.env.example`
- Modify: `.gitignore`
- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `apps/web/README.md`
- Modify: `apps/miniprogram/README.md`
- Modify: `scripts/README.md`
- Modify: `log.md`

- [ ] **Step 6.1：写 Mongo/存储就绪红灯测试。**

  健康接口只返回总体、Mongo 和目录可写状态，不返回 URI、密码、绝对路径或异常堆栈。

- [ ] **Step 6.2：改启动脚本做脱敏前置检查。**

  脚本不安装、不启动、不停止 Mongo，也绝不结束 27017 进程；Mongo 不可用时在拉起前后端前停止并给脱敏提示；保留 `-Help`。

- [ ] **Step 6.3：全仓安全复核。**

  检查默认拒绝、CSRF、角色/本人/任务授权、幂等/revision、SSRF、路径穿越、Range、清理/导入恢复、日志脱敏和 Git 忽略规则。

- [ ] **Step 6.4：运行完整验证。**

  ```powershell
  cd backend
  .\mvnw.cmd clean test
  cd ..\apps\web
  npm test -- --run
  npm run build
  cd ..\..
  node --test scripts/tests/start-dev.test.js
  node --test apps/miniprogram/tests/*.test.js
  git diff --check
  git status --short --branch
  ```

- [ ] **Step 6.5：浏览器和微信验收。**

  使用真实浏览器验证登录接管、角色菜单、任务创建/发布/导入、授权、池状态、审核和统计。真实 Mongo、个人 AppSecret、微信合法 HTTPS 域名和真机麦克风缺失时，明确列为未验证，不使用 mock 结论替代。

- [ ] **Step 6.6：最终文档与提交。**

  `log.md` 记录真实测试数字和未验证项；不得修改每日维护日志。提交信息：`交付: 完成录音任务平台采集审核闭环`。

---

## 最终完成条件

- [ ] 后端身份、任务、采集、审核、返修、废弃、操作记录和统计全部有 API 与自动化测试。
- [ ] Web 不再依赖静态假按钮，ADMIN/REVIEWER 有真实路由、权限、加载/空/失败态。
- [ ] 小程序可完成微信登录、姓名、申请、领取、录音/文字、提交、释放、返修、自动下一条和个人统计。
- [ ] 所有业务 mutation 具备 operationId/Idempotency-Key 和 revision/CAS 边界。
- [ ] Mongo 索引、媒体目录、导入 lease、cleanup job、SSRF 和会话安全均有验证记录。
- [ ] `README.md`、`AGENTS.md`、模块 README 和 `log.md` 与真实代码一致。
- [ ] 全量测试、Web build、敏感扫描和 `git diff --check` 通过。
- [ ] 明确记录未执行的真实 Mongo、微信真机和付费 MiniMax 联调。
