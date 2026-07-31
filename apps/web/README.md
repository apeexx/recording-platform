# 录音任务平台 Web 端

本目录是录音任务平台的 Web 前端，使用 Vue3、Vite、JavaScript 和普通 CSS。

## 当前范围

当前已建立后台真实身份流程和按角色过滤的生产导航：

- `/login` 支持管理员/审核员账号密码登录、密码显示/隐藏、账号占用后的二次确认接管，以及首次登录“修改密码 / 不修改”的选择。
- `/first-password` 只输入新密码和确认密码；成功后废止全部会话并要求重新登录。“不修改”会永久清除首次改密标记并保持当前会话。
- `httpClient.js` 统一处理 Cookie、CSRF、JSON/multipart、Idempotency-Key、统一错误与 `SESSION_REPLACED`；每次受保护的写请求都重新获取当前 CSRF，避免会话或 Cookie 轮换后复用旧 token。
- ADMIN 默认进入 `/admin/dashboard`；REVIEWER 默认进入 `/admin/review`。审核入口请求包含已清空的人工审核任务，用全部返回项计算真实双进度和积压/待领取/审核中/今日完成四项指标，仅把仍有积压的任务展示为待办卡；积压全部处理完成后继续保留概览并显示完成态。桌面端轮播在左、指标以 2×2 放在右侧，窄屏自动上下排列；轮播每 6 秒切换，箭头、圆点或超过 48px 的拖动会锁定当前页直到离开入口，减少动态效果设置下不自动播放。
- 审核池同时展示 `SUBMITTED`（已提交）与 `REVIEW_PENDING`（待审核），顶部展示任务四项指标，五维筛选独立成工具栏，选中条目后再显示跨页批量操作条，并将同一筛选快照用于跨页批处理。审核工作台左侧吸顶展示参考源，右侧依次展示只读原始采集结果、AI 候选、独立最终答案和审核结论；视频使用固定画布与 `object-fit: contain`。
- ADMIN 可从审核池进入任务级 AI 设置；音频转写和文本修订以左右双卡同时展示，统一使用胶囊开关、自定义下拉框、参数字段卡和带字符计数的 Prompt 编辑区。配置字段仍为模型、Prompt、temperature、topP、maxTokens、timeoutMs，密钥始终由服务端读取。AI 音频转写单条录音上限为 20MB；作业刷新后可恢复轮询，完成后必须手动“采用结果”，不会自动保存或自动审核。
- 侧边栏和路由按角色保护，未业务化静态原型已从生产导航和路由隐藏。“录音审核”和“工作统计”为直接入口，审核池、审核工作台和采集员详情仍按父路径保持高亮。
- 左侧侧边栏、顶部栏和主内容区已经搭建。
- 侧边栏大分类支持展开和收缩，多个分类可以同时展开。
- 侧边栏二级菜单使用 CSS grid 过渡实现平滑展开和收起，不卸载菜单 DOM。
- 侧边栏一级菜单由 `adminSidebar.js` 的 `icon` 字段映射本地 Iconfont SVG，保留浅蓝圆角图标底；二级菜单仍使用小圆点。图标来源和授权边界记录在 `public/assets/icons/admin-sidebar/README.md`，运行时不访问 Iconfont CDN。
- 左上品牌标识使用与小程序字节一致的“砚数声采”SVG，Web 副本位于 `public/assets/branding/yanshu-avatar.svg`。
- 任务配置、数据池/CSV 导入、权限、审核、用户、操作记录和统计均使用真实 API；任务仅在草稿状态显示编辑入口，运行中仅允许暂停，暂停后才显示恢复和结束；发布后定义冻结；平台板块已整体移除。
- 工作统计收敛为管理员可见的“采集员统计”，旧 `/admin/reports/tasks` 重定向至数据大屏，审核统计入口、页面和 API 已移除。采集员统计必须选择任务，默认当天，并提供今天、昨天、近 7 日、本月、全部和自定义范围；任务选择和日期变化会立即查询。日期范围在选择开始日后随悬停实时预览，表头按降序、升序、默认三态循环；刷新保留旧数据并显示圆形加载层。点击人员进入独立详情页，任务、日期、显式排序、页码和每页条数通过 URL 恢复。
- 任务详情为双栏工作台：草稿、运行中和暂停任务均可立即单条添加或导入数据，结束任务拒绝新增。CSV 显式开始后使用稳定任务 ID 每秒刷新真实进度，完成时 Toast 并自动刷新；草稿任务支持安全级联删除。
- 后台数据列表统一使用包含总数、每页条数、首尾页、连续页码、省略号及前后箭头的数字分页。普通列表默认 20 条并可选 10/20/50，操作记录默认 50 条并可选 20/50/100，采集权限保留 5/10；用户、邀请码、操作记录和语音生成记录均使用真实服务端分页。数据池继续支持服务端筛选、脚本来源展示和全量中文 CSV 导出。
- 任务详情数据池、独立任务数据池和任务审核池均支持单条、本页全选与“选择全部筛选结果”。两个任务数据池按状态提供“查看、释放、废弃、恢复”行内快捷操作，批量入口仍保留；跨页预览、快照和执行携带相同筛选条件，避免处理不可见数据。
- 条目详情左侧原位编辑参考文本及音视频 URL，右侧按条目信息、采集结果、最近 3 条操作记录排列；“查看更多”分批读取后在独立滚动弹窗一次展示全部记录，旧记录路由仍兼容。
- 用户页使用“Web 端账号 / 小程序端账号”双页签统一管理：默认页签只加载 `WEB-...` 后台用户，小程序页签无需先搜索即可加载 `MINI-...` 采集员；切换时清空搜索条件。列表与搜索响应使用 `id`、`userType`、`loginName`、name、role、status；小程序用户还包含可空的邀请码 ID、名称、末四位和兑换时间。来源列对 Web 用户显示“后台创建”，对新小程序用户显示邀请码名称和脱敏尾号，对无绑定记录的兼容用户显示“历史用户”，不展示完整邀请码、哈希或 OpenID。Web 页签不常驻展示创建表单，搜索栏右侧的“创建后台账号”按钮打开用户名、姓名、角色和初始密码弹窗；成功后关闭并刷新列表，失败保留输入。管理员可在小程序页签修改登录账号、重置采集员密码，也可在 Web 页签重置或停用后台账号；Web 重置后强制下次改密，小程序重置仅废止其活动会话。
- “系统管理 / 邀请码管理”仅对 ADMIN 开放，可创建 1–1000 次使用的邀请码、分页查看脱敏尾号和剩余次数，并永久停用。完整邀请码只在创建成功弹层显示一次，创建请求不写入通用幂等响应快照；列表和后续接口不再返回完整邀请码。
- 左侧首项为“数据大屏”，使用 `/api/reports/dashboard` 和最近操作接口展示真实任务、条目、采集人数、近 7 日首次提交、任务排行、状态分布和服务端生成时间；刷新期间明确显示状态，失败时保留已有数据。
- 采集权限页使用等宽双栏，小程序用户默认每页 10 条并预留 10 行高度，左侧卡片按自身内容独立收口，不再跟随右侧两卡总高度拉伸；待审批申请和已授权用户默认每页 5 条并各预留 5 行高度，三块分页固定贴底，空数据、少于一页和窄屏单列时保持各自卡片高度稳定。

当前不使用 JWT 或 Pinia。通用下拉框、胶囊开关和 1–600 秒双端时长滑块均为无依赖组件；时长组件复用已验收独立原型的统一像素坐标，使用 22px 浅蓝灰胶囊轨道、16px 柔和浅蓝选区和 20px 白色圆点，圆点边框、阴影与焦点状态均由主题蓝低比例派生，选区始终连接两个圆心，边缘和相邻数值不会溢出或重叠。透明原生 range 仅保留 Tab、方向键、Home/End 与无障碍输入，自绘轨道负责点击和拖动。表单校验、操作、搜索、刷新及接口失败统一使用右上角 Toast；只有核心数据首次加载失败使用 `AsyncState` 重试状态。

语音生成工作台中，付费克隆模式只上传母带音频并填写新音色 ID，不展示语速、音量、语调配置；这些调音参数只用于 0 元试听和日常合成。克隆母带需符合 MiniMax 限制：mp3、m4a 或 wav，10 秒到 5 分钟，不超过 20MB；超过后端上传限制时会返回 HTTP 413 和可读错误摘要。

## 目录约定

- `src/config/adminSidebar.js`：管理员侧边栏菜单配置。
- `src/components/admin/AdminSidebarIcon.vue`：一级菜单本地 SVG 图标渲染组件。
- `src/lib/httpClient.js`、`src/lib/authApi.js`：统一请求与后台身份 API。
- `src/composables/useAdminSession.js`：无 Pinia 的后台会话状态。
- `src/pages/auth/`：登录与首次改密页面。
- `src/router/`：管理员端路由配置。
- `src/layouts/AdminLayout.vue`：管理员端布局壳。
- `src/components/admin/`：侧边栏和顶部栏组件。
- `src/components/form/`：自定义下拉、可搜索用户选择器、胶囊开关和双端时长滑块。
- `src/components/admin/AdminPrototypePage.vue`：非语音生成页面共用的静态原型渲染组件。
- `src/data/adminStaticData.js`：管理员端静态原型使用的脱敏示例数据和页面配置。
- `src/pages/admin/`：管理员端页面。
- `src/pages/admin/tasks/`、`review/`、`reports/`、`system/`：真实业务管理页面。
- `src/lib/taskApi.js`、`reviewApi.js`、`reportApi.js`、`userApi.js`：业务 API 封装。
- `src/pages/admin/voice-generation/`：语音生成 Web 生产台页面。
- `src/lib/voiceGenerationApi.js`：语音生成前端 API 封装；不传递 API Key。
- `src/styles/theme.css`：主题变量。
- `src/styles/admin-layout.css`：管理员端布局样式。

新增后台页面时，需要同时更新路由和 `adminSidebar.js`。不要把菜单项硬编码在 Sidebar 组件中。

侧边栏 `/admin/permissions` 是采集权限任务入口页，先选择任务，再进入 `/admin/tasks/{taskId}/permissions` 处理采集员申请、直接授权和撤销；不得将该入口重定向回任务列表。

带 `children` 的侧边栏大分类只负责展开和收缩，子菜单负责页面跳转；当前路由所在的大分类应保持展开。维护侧边栏折叠动画时，应保持二级菜单 DOM 常驻，通过 class 和 CSS transition 控制动画状态。

## 本地命令

```powershell
npm install
npm run build
```

语音生成真实联调需要后端服务运行在 `http://127.0.0.1:8080`，Vite 已将 `/api` 代理到后端。

可先访问 `GET http://127.0.0.1:8080/api/health/ready` 检查后端是否能访问 MongoDB 且录音目录可写。返回 503 时不应继续业务联调。

修改 JavaScript 文件后，可按需运行：

```powershell
node --check src/main.js
node --check src/router/index.js
node --check src/router/adminRoutes.js
node --check src/config/adminSidebar.js
node --check src/data/adminStaticData.js
node --test src/tests/adminPrototypePages.test.js
npm test -- --run
npm run build
```
