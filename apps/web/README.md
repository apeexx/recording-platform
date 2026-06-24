# 录音任务平台 Web 端

本目录是录音任务平台的 Web 前端，使用 Vue3、Vite、JavaScript 和普通 CSS。

## 当前范围

当前已建立管理员端前端导航壳：

- Vue Router 管理 `/admin/*` 页面。
- `/` 和 `/admin` 默认进入 `/admin/dashboard`。
- 左侧侧边栏、顶部栏和主内容区已经搭建。
- 侧边栏大分类支持展开和收缩，多个分类可以同时展开。
- 每个管理员端菜单都有对应占位页面。
- 工作台展示静态占位统计卡片和模块入口。

当前不实现登录、JWT、权限守卫、接口请求、任务管理、审核流程、录音上传、数据库或语音生成业务。

## 目录约定

- `src/config/adminSidebar.js`：管理员侧边栏菜单配置。
- `src/router/`：管理员端路由配置。
- `src/layouts/AdminLayout.vue`：管理员端布局壳。
- `src/components/admin/`：侧边栏和顶部栏组件。
- `src/pages/admin/`：管理员端页面。
- `src/pages/admin/voice-generation/`：合作者预留的语音生成模块占位页面。
- `src/styles/theme.css`：主题变量。
- `src/styles/admin-layout.css`：管理员端布局样式。

新增后台页面时，需要同时更新路由和 `adminSidebar.js`。不要把菜单项硬编码在 Sidebar 组件中。

带 `children` 的侧边栏大分类只负责展开和收缩，子菜单负责页面跳转；当前路由所在的大分类应保持展开。

## 本地命令

```powershell
npm install
npm run build
```

修改 JavaScript 文件后，可按需运行：

```powershell
node --check src/main.js
node --check src/router/index.js
node --check src/router/adminRoutes.js
node --check src/config/adminSidebar.js
```
