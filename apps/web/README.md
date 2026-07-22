# 录音任务平台 Web 端

本目录是录音任务平台的 Web 前端，使用 Vue3、Vite、JavaScript 和普通 CSS。

## 当前范围

当前已建立后台真实身份流程和按角色过滤的生产导航：

- `/login` 支持管理员/审核员账号密码登录及账号占用后的二次确认接管。
- `/first-password` 处理首次登录强制改密，成功后清会话并要求重新登录。
- `httpClient.js` 统一处理 Cookie、CSRF、JSON/multipart、Idempotency-Key、统一错误与 `SESSION_REPLACED`；每次受保护的写请求都重新获取当前 CSRF，避免会话或 Cookie 轮换后复用旧 token。
- ADMIN 默认进入 `/admin/dashboard`；REVIEWER 默认进入 `/admin/review`，先选择任务再进入审核池。
- 审核池同时展示 `SUBMITTED`（已提交）与 `REVIEW_PENDING`（待审核）；管理员和审核员必须先领取指定已提交条目，或由管理员分配审核员，才可进入审核决定。审核释放后回到已提交，管理员批量通过也只接受已领取/已分配条目。
- 侧边栏和路由按角色保护，未业务化静态原型已从生产导航和路由隐藏。
- 左侧侧边栏、顶部栏和主内容区已经搭建。
- 侧边栏大分类支持展开和收缩，多个分类可以同时展开。
- 侧边栏二级菜单使用 CSS grid 过渡实现平滑展开和收起，不卸载菜单 DOM。
- 侧边栏一级菜单由 `adminSidebar.js` 的 `icon` 字段映射本地 Iconfont SVG，保留浅蓝圆角图标底；二级菜单仍使用小圆点。图标来源和授权边界记录在 `public/assets/icons/admin-sidebar/README.md`，运行时不访问 Iconfont CDN。
- 左上品牌标识使用与小程序字节一致的“砚数声采”SVG，Web 副本位于 `public/assets/branding/yanshu-avatar.svg`。
- 任务配置、数据池/CSV 导入、权限、审核、用户、操作记录和统计均使用真实 API；任务仅在草稿状态显示编辑入口，发布后定义冻结；平台板块已整体移除。
- 任务详情中的数据池使用数字服务端分页，支持每页 5/10/20 条并默认 10 条，显示首尾页、省略号和左右箭头；翻页或切换条数会清空当前页勾选，独立“任务数据池”页面继续保持每页 20 条。
- 用户页使用“Web 端账号 / 小程序端账号”双页签统一管理：默认页签只加载 `WEB-...` 后台用户，小程序页签无需先搜索即可加载 `MINI-...` 采集员；切换时清空搜索条件。列表与搜索响应使用 `id`、`userType`、`loginName`、name、role、status，并通过 `userType` 将姓名、完整前缀用户 ID 或登录名搜索严格限定到当前页签。Web 页签不常驻展示创建表单，搜索栏右侧的“创建后台账号”按钮打开用户名、姓名、角色和初始密码弹窗；成功后关闭并刷新列表，失败保留输入。管理员可在小程序页签修改登录账号、重置采集员密码，也可在 Web 页签重置或停用后台账号；Web 重置后强制下次改密，小程序重置仅废止其活动会话。
- 工作台只展示已业务化的模块入口，不使用模拟待办数据。

当前不使用 JWT 或 Pinia。任务配置、数据池/CSV 导入、采集权限、审核池/工作台、后台用户、操作记录和统计页面已接入真实接口；通用下拉框、胶囊开关和 1–600 秒双端时长滑块均为无依赖组件。全局操作反馈使用右上角悬浮提示，账号接管使用悬浮确认弹窗。

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
- `src/components/form/`：自定义下拉、胶囊开关和双端时长滑块。
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
