# 架构说明

## 模块划分

- `apps/web`：Vue + Vite Web 前端，后续承载管理员端和审核端。
- `apps/recorder-miniprogram`：微信小程序录音端。
- `backend`：Java Spring Boot 后端，使用 Maven 构建。
- `docs`：项目文档。
- `scripts`：后续辅助脚本目录。

## 技术选型

- 前端：Vue + Vite
- 后端：Java 17 + Spring Boot + Maven
- 数据库：MongoDB
- 本地基础服务：Docker Compose

## 当前阶段说明

当前只生成基础脚手架，不定义前后端接口、不实现页面路由、不实现数据库集合和审核状态机。
