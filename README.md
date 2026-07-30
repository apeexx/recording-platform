# 录音任务平台

用于管理录音任务的创建、数据准备、授权、领取、录制、提交、审核和结果统计。

当前仓库包含管理员/审核员 Web 端、原生微信小程序采集端、Java Spring Boot 后端和 MongoDB 数据层。平台已形成任务采集与人工审核闭环，并提供语音生成生产台、任务级 AI 辅助审核和受限的外部脚本集成；AI 只生成审核候选文本，不会自动作出审核决定。

## 项目定位

| 模块 | 当前入口 | 主要职责 |
|---|---|---|
| Web 管理端 | `apps/web/` | 真实数据大屏、后台身份、邀请码、任务、可筛选数据池、授权、审核、用户、操作记录、统计和语音生成 |
| 微信小程序 | `apps/miniprogram/` | 邀请准入、采集员登录、资料、权限申请、任务领取、录音/文字提交、返修、结果查看和按任务统计 |
| Spring Boot 后端 | `backend/` | 身份、邀请准入与会话、任务状态、媒体、导入、审核、统计和外部集成 API |
| MongoDB | `recording_platform` | 用户、邀请声明、会话、任务、条目、媒体、导入、幂等和语音生成数据 |
| 运维脚本 | `scripts/` | Windows 本地启动、生产部署、本地数据重置和相关检查 |

主要技术栈：

- Web：Vue 3、Vite、JavaScript
- 小程序：原生微信小程序
- 后端：Java 17、Spring Boot、Maven
- 数据库：MongoDB

## 当前能力

| 领域 | 已实现能力 |
|---|---|
| Web 管理 | ADMIN/REVIEWER 登录与会话接管、角色路由、带服务端生成时间的真实数据大屏、ADMIN 邀请码创建/脱敏查看/永久停用、用户邀请码准入来源展示、任务配置、带脚本来源筛选和中文全量 CSV 导出的数据池、审核池五维筛选、只读原始结果与独立最终答案、任务级 AI 辅助审核、CSV 导入、三目录分页权限管理、用户、操作记录、跨页异步批处理，以及支持任务/日期自动查询、范围日历、表头升降序、24 小时分布和独立人员详情的双阶段采集员统计 |
| 小程序采集 | 新微信身份邀请码首次准入、已有微信身份与数字账号登录、“我的”直达资料与头像设置、任务授权、领取、录音、当前录音删除、文本复制/补充、纯文本 JSON 与录音 multipart 提交、参考媒体时长自动检测、返修、释放备注、标记无效与恢复，以及支持单日筛选的独立任务统计 |
| 任务与审核 | 草稿、运行、暂停、结束状态；固定展示文本/音频/视频三类参考源；文字或录音成果；人工审核、驳回返修、单页/跨页批量操作和状态并发控制；AI 音频转写单条上限 20MB |
| 媒体与导入 | WAV/MP3 校验与受保护读取、HTTPS 参考媒体 URL、CSV 异步导入、失败行重试和持久化幂等 |
| 语音生成 | MiniMax 试听、付费克隆、日常合成、音色配置和生成记录 |
| 外部集成 | 专用机器 API Key 写入任务数据、按任务编号定位、来源绑定、完成状态查询和结果音频读取 |

详细业务规则、接口契约和数据库说明以 [AGENTS.md](AGENTS.md) 为准。

新微信身份注册时，`miniprogram_users` 保存邀请码 ID、名称快照、末四位和兑换时间，完整邀请码与哈希不会进入用户摘要；历史用户保持兼容并在 Web 用户管理中显示为“历史用户”。登录会话继续独立保存在 `sessions`，由 `expiresAt` TTL 自动清理，无需人工定期删除。

## 前置环境

本地开发至少需要：

- Java 17
- Node.js 与 npm
- MongoDB
- Windows PowerShell
- 微信开发者工具（验证小程序时）

生产部署还需要 Linux、NVM、Node.js 22、PM2、Git、curl，以及已单独配置的 MongoDB、持久化目录、Nginx 和 HTTPS 证书。完整要求见 [scripts/README.md](scripts/README.md)。

仓库不维护 Docker Compose，也不会自动安装、启动或停止 MongoDB。

## 快速开始

### 1. 准备本地配置

在仓库根目录复制公开模板：

```powershell
Copy-Item .env.example .env
```

根据本地环境填写 `.env`。至少确认 MongoDB 地址和存储目录可用；需要微信登录、语音生成、首管理员或外部集成时，再填写对应配置。

`.env` 已被 Git 忽略。不得把数据库密码、微信 AppSecret、MiniMax API Key、管理员初始密码或原始集成 API Key 写入仓库。

### 2. 启动 MongoDB

默认开发连接为：

```text
mongodb://localhost:27017/recording_platform
```

如使用其他实例，只在本地 `.env` 中修改 `MONGODB_URI`。启动脚本只检查 MongoDB 是否可达，不负责管理数据库进程。

### 3. 安装前端依赖

首次运行或依赖变化后执行：

```powershell
Set-Location apps/web
npm install
Set-Location ../miniprogram
npm install
Set-Location ../..
```

后端使用仓库自带 Maven Wrapper，无需全局安装 Maven。

### 4. 启动本地服务

在仓库根目录执行：

```powershell
.\scripts\start-dev.cmd
```

脚本会先脱敏检查 MongoDB 可达性和录音目录可写性，再打开两个 PowerShell 窗口运行：

```text
backend\mvnw.cmd spring-boot:run
npm run dev -- --host localhost --port 5173
```

本地入口：

- Web：`http://localhost:5173`
- 后端：`http://127.0.0.1:8080`
- 就绪检查：`http://127.0.0.1:8080/api/health/ready`
- 语音生成工作台：`http://localhost:5173/admin/voice-generation/workbench`

就绪接口只有 `overall`、`mongo`、`storage` 全部为 `UP` 时返回 HTTP 200；返回 503 时不应继续业务联调。

小程序导入和本地 API 地址配置见 [apps/miniprogram/README.md](apps/miniprogram/README.md)。

## 开发与验证

Web：

```powershell
Set-Location apps/web
npm test -- --run
npm run build
```

微信小程序：

```powershell
Set-Location apps/miniprogram
npm test
```

后端：

```powershell
Set-Location backend
.\mvnw.cmd test
```

修改后还应按影响范围运行语法检查和 `git diff --check`。各端的专项验证方式见对应模块 README。

## 生产部署

Ubuntu 生产服务器完成环境、私密配置和持久化目录准备后，在干净的 `main` 工作区执行：

```bash
./scripts/deploy-production.sh
```

脚本通过 `git pull --ff-only origin main` 更新，依次验证和构建 Web、后端与微信小程序；全部成功后才启动或重启单实例 PM2 后端，并以本机就绪接口三项全 `UP` 作为成功条件。

脚本不会创建或修改 `.env`、MongoDB、Nginx、Certbot、防火墙、备份、回滚或微信发布配置。生产前置条件、失败行为和诊断命令见 [scripts/README.md](scripts/README.md)。

本地数据重置和旧录音路径迁移都是高风险运维操作，不属于日常启动流程：

- 本地重置会不可恢复地删除 `recording_platform` 开发数据库和受限运行存储，且不会生成备份。
- 旧录音路径迁移必须停写、备份 MongoDB 集合与完整录音目录，并以单后端实例执行。

执行前必须阅读 [scripts/README.md](scripts/README.md) 和 [AGENTS.md](AGENTS.md) 中的完整规则。

## 目录导航

| 目录或文件 | 职责 |
|---|---|
| `apps/web/` | Vue 3 管理端、审核端和语音生成页面 |
| `apps/miniprogram/` | 原生微信小程序录音采集端 |
| `backend/` | Spring Boot API、MongoDB 持久化和媒体处理 |
| `docs/` | CSV 验收数据和补充资料 |
| `scripts/` | 本地启动、生产部署、重置与脚本测试 |
| `.env.example` | 可提交的空值或安全默认配置模板 |
| `AGENTS.md` | 长期项目规则、接口说明和数据边界 |
| `log.md` | AI 辅助修改与验收记录 |

## 文档入口

- [项目长期规则、接口与数据库说明](AGENTS.md)
- [Web 管理端](apps/web/README.md)
- [微信小程序录音端](apps/miniprogram/README.md)
- [本地启动、部署和重置脚本](scripts/README.md)
- [修改与验收记录](log.md)

`backend/HELP.md` 当前不存在；后端命令和接口边界分别以本 README、`AGENTS.md` 与源码测试为准。

## 安全与行为边界

- 不提交 `.env`、API Key、token、cookie、Authorization、微信 AppSecret、数据库凭据、管理员密码、真实客户数据或完整签名 URL。
- Web 会话使用 HttpOnly Cookie 与 CSRF；小程序使用不透明 Bearer token；服务端和 MongoDB 只保存会话令牌哈希。
- 外部集成只允许专用机器身份访问明确列出的集成端点，浏览器、Web Cookie、小程序 Bearer 和管理员身份都不能替代机器 API Key。
- 参考音视频新数据只保存通过语法校验的绝对 HTTPS URL；服务端不探测、下载或代理外部参考媒体。
- 录音结果保持受保护访问，公开参考媒体兼容端点不得暴露任何 `RECORDING` 类型文件。
- 小程序不面向公众开放注册，仅面向重庆砚数科技有限公司内部员工及经项目管理员邀请、授权的录音采集人员；登录板块下方常驻说明身份核验和任务授权限制，不增加游客模式。
- AI 结果和语音生成能力不自动领取、审核、提交或流转录音任务。
- 生产环境必须独立配置 HTTPS、备份、监控、微信合法域名和秘密值管理。
