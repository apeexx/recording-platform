# 录音任务平台启动说明书

## 1. 文档用途

本文档用于记录录音任务平台当前版本的本地启动、依赖安装、验证命令和常见问题。

当前仓库仍处于基础框架和空业务阶段。本文档只说明现有项目如何启动和验证，不代表平台已经具备完整业务能力。

## 2. 当前项目状态

当前版本处于 Web 管理员端框架和空业务阶段：

- Web 管理员端已有基础布局、顶部栏、侧边栏、路由壳和模块占位页面。
- 管理员端侧边栏菜单由 `apps/web/src/config/adminSidebar.js` 统一管理。
- 语音生成模块位于 `apps/web/src/pages/admin/voice-generation/`，当前为合作者预留模块，只保留占位页面。
- 后端当前是 Spring Boot 基础项目，未发现业务 Controller 映射，当前阶段不提供业务 API。
- 小程序端当前仍为 `apps/miniprogram` 占位目录。
- 当前版本不实现真实登录、JWT、权限控制、任务管理、录音上传、审核流程、语音生成业务、数据库集合或接口路由。

当前前端没有发现接口调用，后端可以独立测试，前端也可以独立启动。是否需要同时启动前后端，应以后续实际接口调用为准。

## 3. 项目目录说明

```text
recording-platform/
├─ apps/web
├─ apps/miniprogram
├─ backend
├─ scripts
├─ README.md
├─ AGENTS.md
└─ log.md
```

- `apps/web`：Web 前端工程，当前为 Vue3 + Vite + JavaScript 管理员端导航壳和占位页面。
- `apps/miniprogram`：微信小程序录音端占位目录，当前不实现页面和业务逻辑。
- `backend`：Java Spring Boot 后端基础项目，当前不提供业务接口。
- `scripts`：启动说明和后续本地开发、数据处理、运维辅助脚本预留目录。
- `README.md`：项目总览说明。
- `AGENTS.md`：Codex 长期执行规则，以及接口和数据库说明入口。
- `log.md`：AI 辅助修改日志。

## 4. 环境要求

### 4.1 Git

需要安装 Git，用于拉取代码、查看变更、提交和推送。

常用检查命令：

```powershell
git --version
git status
```

### 4.2 Node.js 和 npm

Web 前端需要 Node.js 和 npm。

当前前端依赖以 `apps/web/package.json` 和 `apps/web/package-lock.json` 为准：

- `vue`：`^3.5.34`
- `vue-router`：`^4.6.4`
- `@vitejs/plugin-vue`：`^6.0.6`
- `vite`：`^8.0.12`

当前 `package-lock.json` 中实际锁定的 Vite 版本要求 Node.js 满足：

```text
^20.19.0 || >=22.12.0
```

建议使用满足上述范围的 Node.js LTS 版本。当前开发环境曾验证 Node `v24.16.0` 和 npm `11.13.0` 可用，但不要求所有开发者必须使用完全相同版本。

检查命令：

```powershell
node --version
npm --version
```

### 4.3 Java

后端需要 Java。项目目标版本以 `backend/pom.xml` 中的 `java.version` 为准：

```text
java.version = 17
```

当前开发环境曾验证 Java `21.0.11` 可运行当前后端测试。项目目标仍是 Java 17，不要求所有开发者必须使用 Java 21。

检查命令：

```powershell
java -version
```

### 4.4 Maven

不要求安装全局 Maven。本项目后端提供 Maven Wrapper：

- Windows：`backend\mvnw.cmd`
- macOS/Linux：`backend/mvnw`

后端命令应优先使用 Maven Wrapper，不要要求开发者额外安装全局 Maven。

### 4.5 微信开发者工具

小程序端后续需要使用微信开发者工具打开：

```text
apps/miniprogram
```

当前小程序端仍为占位目录，不实现任务大厅、领取任务、录音、上传或进度展示。

## 5. 新电脑首次拉取后的完整步骤

Windows PowerShell 示例：

```powershell
git clone <仓库地址>
cd D:\recording\recording-platform
```

如果已经克隆仓库，直接进入项目根目录即可：

```powershell
cd D:\recording\recording-platform
```

确认当前分支和工作区状态：

```powershell
git status --short --branch
```

首次启动建议先安装并验证 Web 前端依赖，再运行后端测试：

```powershell
cd D:\recording\recording-platform\apps\web
npm install
npm run build
```

```powershell
cd D:\recording\recording-platform\backend
.\mvnw.cmd test
```

## 6. Web 前端启动

运行位置：

```powershell
cd D:\recording\recording-platform\apps\web
```

首次安装依赖：

```powershell
npm install
```

本地开发启动：

```powershell
npm run dev
```

构建验证：

```powershell
npm run build
```

预览已构建产物：

```powershell
npm run preview
```

以上命令来自 `apps/web/package.json`，当前实际 scripts 为：

- `dev`：启动 Vite 开发服务。
- `build`：执行 Vite 构建。
- `preview`：预览构建产物。

## 7. 后端启动

运行位置：

```powershell
cd D:\recording\recording-platform\backend
```

Windows 测试：

```powershell
.\mvnw.cmd test
```

Windows 启动：

```powershell
.\mvnw.cmd spring-boot:run
```

macOS/Linux 测试和启动：

```bash
./mvnw test
./mvnw spring-boot:run
```

后端当前 `application.properties` 只配置了应用名：

```properties
spring.application.name=recording-platform-backend
```

当前没有配置 `server.port`。如未通过其他方式覆盖端口，Spring Boot 默认通常使用 `8080`。

## 8. 后端依赖说明

后端依赖以 `backend/pom.xml` 为准。当前主要依赖包括：

- `spring-boot-starter-web`
- `spring-boot-starter-data-mongodb`
- `spring-boot-starter-security`
- `spring-boot-starter-validation`
- `lombok`
- `spring-boot-starter-test`
- `spring-security-test`

当前 MongoDB 只是后续计划数据库。仓库内不维护 Docker Compose，本版本不通过仓库提供本地 MongoDB 服务。

由于后端已引入 MongoDB starter，在未连接本地 MongoDB 时，测试或启动过程中可能出现 MongoDB 连接拒绝相关日志。当前阶段判断测试是否通过，应以 Maven 最终是否输出 `BUILD SUCCESS` 和命令退出码为准。

Spring Security 当前只作为依赖引入，后续登录/JWT 阶段再配置，不代表当前版本已有登录能力。

## 9. 小程序端说明

当前目录：

```text
apps/miniprogram
```

当前状态：

- 当前仅为占位目录。
- 后续使用微信开发者工具打开。
- 当前不实现页面、登录、任务大厅、领取任务、录音、上传、审核结果展示或我的进度功能。

## 10. 当前版本推荐启动顺序

当前前端没有发现接口调用，因此前端可以独立启动，后端也可以独立测试。

推荐顺序：

1. 在 `backend` 目录运行 `.\mvnw.cmd test`，确认后端基础测试通过。
2. 如需要本地启动后端，在 `backend` 目录运行 `.\mvnw.cmd spring-boot:run`。
3. 在 `apps/web` 目录运行 `npm install`。
4. 在 `apps/web` 目录运行 `npm run dev`。
5. 根据 Vite 终端输出，在浏览器打开前端开发地址，通常是 `http://localhost:5173/` 或自动切换后的端口。
6. 小程序端当前无需启动；后续可用微信开发者工具打开 `apps/miniprogram`。

## 11. 验证命令清单

检查当前分支和工作区：

```powershell
cd D:\recording\recording-platform
git status --short --branch
```

前端构建验证：

```powershell
cd D:\recording\recording-platform\apps\web
npm run build
```

后端测试：

```powershell
cd D:\recording\recording-platform\backend
.\mvnw.cmd test
```

修改 JavaScript 文件后，可按实际修改文件运行语法检查，例如：

```powershell
cd D:\recording\recording-platform\apps\web
node --check src/main.js
node --check src/router/index.js
node --check src/router/adminRoutes.js
node --check src/config/adminSidebar.js
```

本轮如果只修改 Markdown 启动说明文档，不强制运行 `node --check`。

## 12. 常见问题

### 12.1 npm install 很慢或失败

可以检查：

- 当前网络是否能访问 npm registry。
- npm 源是否可用。
- Node.js 版本是否满足 Vite 要求。
- 是否在 `D:\recording\recording-platform\apps\web` 目录执行命令。

### 12.2 npm run dev 端口占用

Vite 默认开发端口通常是 `5173`。如果端口被占用，Vite 可能自动切换到其他端口。

以终端实际输出的访问地址为准，不要只固定访问 `5173`。

### 12.3 Java 版本不匹配

先检查 Java 版本：

```powershell
java -version
```

项目目标版本是 Java 17。当前开发环境曾使用 Java 21 运行测试，但如果本机 Java 版本过低，可能导致 Maven 编译或测试失败。

### 12.4 mvnw.cmd 无法运行

请确认当前目录是：

```powershell
D:\recording\recording-platform\backend
```

然后运行：

```powershell
.\mvnw.cmd test
```

不要在仓库根目录直接运行 `.\mvnw.cmd`，因为 Maven Wrapper 位于 `backend` 目录。

### 12.5 后端测试出现 MongoDB 连接拒绝日志

当前后端引入了 `spring-boot-starter-data-mongodb`，但当前版本没有正式数据库配置，也不要求仓库内提供 Docker Compose。

如果测试期间看到 MongoDB 连接拒绝日志，但 Maven 最终输出 `BUILD SUCCESS`，这通常是依赖初始化或本地环境日志，不等于测试失败。最终以 Maven 是否 `BUILD SUCCESS` 和命令退出码为准。

### 12.6 后端启动或测试输出 Spring Security 开发密码提示

当前后端引入了 `spring-boot-starter-security`，但还没有配置正式登录、JWT 或权限规则。

启动或测试时，Spring Security 可能输出临时生成的开发密码提示。该提示只用于开发环境，不代表当前平台已经实现真实登录，也不要把实际临时密码写入文档、日志或提交记录。

### 12.7 页面不是最新

可以按顺序排查：

1. 确认当前分支和代码状态：

   ```powershell
   git status --short --branch
   ```

2. 重启 Vite dev server。
3. 清理浏览器缓存或强制刷新页面。
4. 查看终端输出的实际 Vite 访问地址和端口。

## 13. Git 基础流程

本项目当前默认使用 `main` 单工作区开发。常用流程：

```powershell
git status
git pull
git add .
git commit -m "说明本次修改"
git push origin main
```

提交前需要确认不要提交：

- `.env`
- `node_modules`
- `dist`
- 真实录音文件
- API Key / Token / Cookie
- Authorization 头
- 真实数据库密码
- 未脱敏截图内容

## 14. 敏感信息规则

启动文档和项目文档中不得写入：

- API Key
- Token
- Cookie
- Authorization
- 真实数据库密码
- 真实用户隐私
- 真实客户数据
- 真实录音 URL
- 完整签名 URL
- 未脱敏截图内容

如需记录问题，只保留必要摘要，例如状态码、requestId、hostname、模型名、耗时或错误摘要。

## 15. 后续维护规则

每次新增或调整以下内容时，都需要同步更新本文档：

- 启动命令
- 依赖安装方式
- Node.js、npm、Java 或 Maven Wrapper 要求
- 环境变量
- 服务端口
- 模块运行方式
- 前后端验证命令
- 小程序打开方式
- 常见问题和排查步骤

如果后续新增真实业务接口、数据库配置、文件上传、登录认证或审核流程，也需要同步更新 `README.md`、`AGENTS.md` 和 `log.md` 中的相关说明。
