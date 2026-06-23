# AI 修改日志

## 2026-06-23

- 初始化录音任务平台仓库结构。
- 使用 Vite 官方脚手架创建 Vue Web 空项目。
- 使用 Spring Initializr 创建 Spring Boot Maven 后端空项目。
- 创建微信小程序占位说明。
- 创建 MongoDB Docker Compose 配置。
- 创建项目文档骨架。
- 当前不实现任何业务接口、页面逻辑、审核流或安全配置。

## 2026-06-23 结构清理

- 删除仓库内 Docker Compose 配置，后续不再通过仓库提供本地 Docker 服务。
- 删除 docs 目录文档骨架，接口和数据库说明入口迁移到 `AGENTS.md`。
- 新建根目录 `log.md` 作为 AI 修改日志。
- 按参考风格重写 `AGENTS.md` 为录音任务平台长期执行规则。
- 更新 `README.md`，同步新的目录结构、文档位置和本地验证说明。

## 2026-06-23 小程序目录重命名

- 将微信小程序占位目录从 `apps/recorder-miniprogram` 重命名为 `apps/xiaochengxu`。
- 删除 `AGENTS.md` 中的“角色分工”章节，并同步后续章节编号。
- 更新 `README.md` 和 `AGENTS.md` 中的小程序目录路径说明。
