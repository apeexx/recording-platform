# 录音任务平台

录音任务平台用于管理录音任务的创建、领取、录制、上传和审核流程。当前仓库处于项目初始化阶段，只包含空项目脚手架和根目录协作文档，不实现业务功能。

## 项目定位

本项目包含管理员 Web 端、审核 Web 端、微信小程序录音端和 Java Spring Boot 后端。后续业务将围绕录音任务发布、任务领取锁定、录音上传、机器审核、一审、二审、驳回重录和任务完成展开。

## 根目录结构

```text
recording-platform/
├─ README.md
├─ AGENTS.md
├─ log.md
├─ .gitignore
├─ apps/
│  ├─ web/
│  └─ miniprogram/
├─ backend/
└─ scripts/
```

## 技术栈

- Web 前端：Vue + Vite，目录为 `apps/web`
- 微信小程序端：目录为 `apps/miniprogram`
- 后端：Java 17 + Spring Boot + Maven，目录为 `backend`
- 数据库：MongoDB 为后续计划数据库，不再由仓库内 Docker Compose 提供本地服务

## 模块用途

- `apps/web`：管理员 Web 端和审核 Web 端的前端工程，当前为 Vite Vue 空项目。
- `apps/miniprogram`：微信小程序录音端，当前仅保留占位说明。
- `backend`：Spring Boot 后端服务，当前只生成基础启动项目，不提供业务接口。
- `scripts`：后续放置本地开发、数据处理或运维辅助脚本。
- `AGENTS.md`：Codex 长期执行规则，同时记录接口和数据库说明入口。
- `log.md`：AI 辅助修改日志。

## 当前阶段

当前仅实现项目根目录、空项目脚手架、Web 端主题基础和管理员端前端导航壳，不实现任务管理、用户登录、录音上传、审核流、权限控制、接口请求或数据模型。

仓库内不再维护 Docker Compose 配置。后续如需本地 MongoDB，请在本机或外部环境自行提供，并在具体开发任务中说明连接方式。

## Web 端主题基础

Web 端已建立基础主题变量，变量文件位于 `apps/web/src/styles/theme.css`，并由 `apps/web/src/style.css` 作为全局样式入口引入。

当前仅完成浅色优先的主题基础和 `.dark` 深色变量预留，不实现主题切换按钮、登录、任务、审核、上传、接口请求、路由或状态管理。

后续管理员端和审核端页面应优先使用主题变量：

- 主色：`--primary`
- 背景：`--background`
- 文字：`--foreground`
- 卡片：`--card`
- 边框：`--border`
- 圆角：`--radius`

## Web 管理端导航壳

Web 管理端已建立 Vue Router 导航壳，根路径 `/` 和 `/admin` 默认进入 `/admin/dashboard`。管理员端当前包含固定侧边栏、顶部栏、主内容区、工作台占位卡片和各模块占位页面。

侧边栏菜单统一配置在 `apps/web/src/config/adminSidebar.js`，管理员端路由统一位于 `apps/web/src/router/`。当前只是前端导航和布局框架，不调用接口，不实现登录、任务、审核、录音上传或权限控制。

管理员端侧边栏支持大分类展开和收缩，多个分类可以同时展开。侧边栏菜单配置由 `apps/web/src/config/adminSidebar.js` 统一管理。

“语音生成”模块位于 `apps/web/src/pages/admin/voice-generation/`，当前作为合作者预留模块，只保留占位页面，不实现语音生成业务。

布局参考浅色后台管理系统的结构形式，包括左侧菜单、顶部栏和卡片式内容区；颜色、字体、圆角、边框和选中态必须遵循本项目主题变量，不复制外部模板颜色或源码。

## 本地验证

Web 端：

```bash
cd apps/web
npm install
npm run build
```

后端：

```bash
cd backend
./mvnw test
```

Windows PowerShell 可使用：

```powershell
cd backend
.\mvnw.cmd test
```
