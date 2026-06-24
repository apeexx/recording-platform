# 录音任务平台 Web 端

本目录是录音任务平台的 Web 前端，使用 Vue3、Vite、JavaScript 和普通 CSS。

## 当前范围

当前已建立管理员端前端导航壳：

- Vue Router 管理 `/admin/*` 页面。
- `/` 和 `/admin` 默认进入 `/admin/dashboard`。
- 左侧侧边栏、顶部栏和主内容区已经搭建。
- 每个管理员端菜单都有对应占位页面。
- 工作台展示静态占位统计卡片和模块入口。

当前不实现登录、JWT、权限守卫、任务管理、审核流程或录音上传业务。语音生成模块已接入后端真实接口，用于 MiniMax 试听、克隆、合成、音色管理和生成记录。

语音生成工作台中，付费克隆模式只上传母带音频并填写新音色 ID，不展示语速、音量、语调配置；这些调音参数只用于 0 元试听和日常合成。克隆母带需符合 MiniMax 限制：mp3、m4a 或 wav，10 秒到 5 分钟，不超过 20MB。

## 目录约定

- `src/config/adminSidebar.js`：管理员侧边栏菜单配置。
- `src/router/`：管理员端路由配置。
- `src/layouts/AdminLayout.vue`：管理员端布局壳。
- `src/components/admin/`：侧边栏和顶部栏组件。
- `src/pages/admin/`：管理员端页面。
- `src/pages/admin/voice-generation/`：语音生成 Web 生产台页面。
- `src/lib/voiceGenerationApi.js`：语音生成前端 API 封装；不传递 API Key。
- `src/styles/theme.css`：主题变量。
- `src/styles/admin-layout.css`：管理员端布局样式。

新增后台页面时，需要同时更新路由和 `adminSidebar.js`。不要把菜单项硬编码在 Sidebar 组件中。

## 本地命令

```powershell
npm install
npm run build
```

语音生成真实联调需要后端服务运行在 `http://127.0.0.1:8080`，Vite 已将 `/api` 代理到后端。

修改 JavaScript 文件后，可按需运行：

```powershell
node --check src/main.js
node --check src/router/index.js
node --check src/router/adminRoutes.js
node --check src/config/adminSidebar.js
```
