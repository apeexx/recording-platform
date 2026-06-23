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
│  └─ xiaochengxu/
├─ backend/
└─ scripts/
```

## 技术栈

- Web 前端：Vue + Vite，目录为 `apps/web`
- 微信小程序端：目录为 `apps/xiaochengxu`
- 后端：Java 17 + Spring Boot + Maven，目录为 `backend`
- 数据库：MongoDB 为后续计划数据库，不再由仓库内 Docker Compose 提供本地服务

## 模块用途

- `apps/web`：管理员 Web 端和审核 Web 端的前端工程，当前为 Vite Vue 空项目。
- `apps/xiaochengxu`：微信小程序录音端，当前仅保留占位说明。
- `backend`：Spring Boot 后端服务，当前只生成基础启动项目，不提供业务接口。
- `scripts`：后续放置本地开发、数据处理或运维辅助脚本。
- `AGENTS.md`：Codex 长期执行规则，同时记录接口和数据库说明入口。
- `log.md`：AI 辅助修改日志。

## 当前阶段

当前仅保留项目根目录和空项目脚手架，不实现任务管理、用户登录、录音上传、审核流、权限控制或数据模型。

仓库内不再维护 Docker Compose 配置。后续如需本地 MongoDB，请在本机或外部环境自行提供，并在具体开发任务中说明连接方式。

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
