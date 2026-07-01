# 录音任务平台 Web 端

本目录是录音任务平台的 Web 前端，使用 Vue3、Vite、JavaScript 和普通 CSS。

## 当前范围

当前已建立管理员端前端导航壳，并完善除“语音生成”预留模块外的静态前端原型页面：

- Vue Router 管理 `/admin/*` 页面。
- `/` 和 `/admin` 默认进入 `/admin/dashboard`。
- 左侧侧边栏、顶部栏和主内容区已经搭建。
- 侧边栏大分类支持展开和收缩，多个分类可以同时展开。
- 侧边栏二级菜单使用 CSS grid 过渡实现平滑展开和收起，不卸载菜单 DOM。
- 基础设置、文本处理、录音任务、录音审核、录音结果、工作报表、系统管理页面已经替换为静态原型。
- 静态原型支持筛选、标签页、表格行详情、本地状态切换、模拟操作提示和窄屏响应式展示。
- 工作台展示静态待办、模块入口和流程进度，不跳转到语音生成业务页面。

当前不实现登录、JWT、权限守卫、任务管理、审核流程或录音上传业务。语音生成模块已接入后端真实接口，用于 MiniMax 试听、克隆、合成、音色管理和生成记录。

语音生成工作台中，付费克隆模式只上传母带音频并填写新音色 ID，不展示语速、音量、语调配置；这些调音参数只用于 0 元试听和日常合成。克隆母带需符合 MiniMax 限制：mp3、m4a 或 wav，10 秒到 5 分钟，不超过 20MB；超过后端上传限制时会返回 HTTP 413 和可读错误摘要。

## 目录约定

- `src/config/adminSidebar.js`：管理员侧边栏菜单配置。
- `src/router/`：管理员端路由配置。
- `src/layouts/AdminLayout.vue`：管理员端布局壳。
- `src/components/admin/`：侧边栏和顶部栏组件。
- `src/components/admin/AdminPrototypePage.vue`：非语音生成页面共用的静态原型渲染组件。
- `src/data/adminStaticData.js`：管理员端静态原型使用的脱敏示例数据和页面配置。
- `src/pages/admin/`：管理员端页面。
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

修改 JavaScript 文件后，可按需运行：

```powershell
node --check src/main.js
node --check src/router/index.js
node --check src/router/adminRoutes.js
node --check src/config/adminSidebar.js
node --check src/data/adminStaticData.js
node --test src/tests/adminPrototypePages.test.js
```
