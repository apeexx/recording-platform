# 录音任务平台 Web 端

本目录是录音任务平台的 Web 前端，使用 Vue3、Vite、JavaScript 和普通 CSS。

## 当前范围

当前已建立后台真实身份流程和按角色过滤的生产导航：

- `/login` 支持管理员/审核员账号密码登录及账号占用后的二次确认接管。
- `/first-password` 处理首次登录强制改密，成功后清会话并要求重新登录。
- `httpClient.js` 统一处理 Cookie、CSRF、JSON/multipart、Idempotency-Key、统一错误与 `SESSION_REPLACED`；每次受保护的写请求都重新获取当前 CSRF，避免会话或 Cookie 轮换后复用旧 token。
- ADMIN 默认进入 `/admin/dashboard`；REVIEWER 默认进入 `/admin/review/queue`。
- 侧边栏和路由按角色保护，未业务化静态原型已从生产导航和路由隐藏。
- 左侧侧边栏、顶部栏和主内容区已经搭建。
- 侧边栏大分类支持展开和收缩，多个分类可以同时展开。
- 侧边栏二级菜单使用 CSS grid 过渡实现平滑展开和收起，不卸载菜单 DOM。
- 平台、任务/版本、数据池/导入、权限、审核、用户、操作记录和统计均使用真实 API，静态原型不再出现于生产导航。
- 工作台只展示已业务化的模块入口，不使用模拟待办数据。

当前不使用 JWT 或 Pinia。平台、任务/不可变版本、数据池/导入、采集权限、审核池/工作台、后台用户、操作记录和统计页面已接入真实接口；语音生成模块同样复用统一鉴权请求客户端。

语音生成工作台中，付费克隆模式只上传母带音频并填写新音色 ID，不展示语速、音量、语调配置；这些调音参数只用于 0 元试听和日常合成。克隆母带需符合 MiniMax 限制：mp3、m4a 或 wav，10 秒到 5 分钟，不超过 20MB；超过后端上传限制时会返回 HTTP 413 和可读错误摘要。

## 目录约定

- `src/config/adminSidebar.js`：管理员侧边栏菜单配置。
- `src/lib/httpClient.js`、`src/lib/authApi.js`：统一请求与后台身份 API。
- `src/composables/useAdminSession.js`：无 Pinia 的后台会话状态。
- `src/pages/auth/`：登录与首次改密页面。
- `src/router/`：管理员端路由配置。
- `src/layouts/AdminLayout.vue`：管理员端布局壳。
- `src/components/admin/`：侧边栏和顶部栏组件。
- `src/components/admin/AdminPrototypePage.vue`：非语音生成页面共用的静态原型渲染组件。
- `src/data/adminStaticData.js`：管理员端静态原型使用的脱敏示例数据和页面配置。
- `src/pages/admin/`：管理员端页面。
- `src/pages/admin/platforms/`、`tasks/`、`review/`、`reports/`、`system/`：真实业务管理页面。
- `src/lib/platformApi.js`、`taskApi.js`、`reviewApi.js`、`reportApi.js`、`userApi.js`：业务 API 封装。
- `src/pages/admin/voice-generation/`：语音生成 Web 生产台页面。
- `src/lib/voiceGenerationApi.js`：语音生成前端 API 封装；不传递 API Key。
- `src/styles/theme.css`：主题变量。
- `src/styles/admin-layout.css`：管理员端布局样式。

新增后台页面时，需要同时更新路由和 `adminSidebar.js`。不要把菜单项硬编码在 Sidebar 组件中。

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
