# 录音文件扁平化与 Superpowers 目录合并实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将录音稳定路径改为 `{taskCode}/{itemCode}.wav|mp3`，安全迁移现有文件和 MongoDB 路径引用，并把过程文档统一迁入被 Git 忽略的根目录 `.superpowers/`。

**Architecture:** `RecordingMediaStorage` 负责生成新的扁平稳定路径并继续提供原子替换、备份和释放能力；一次性、显式启用的迁移 Runner 在停服维护窗口逐条迁移旧文件和数据库文档，失败时按条目回滚。过程文档合并只改变仓库资料位置，不影响运行时。

**Tech Stack:** Java 17、Spring Boot 3.5、Spring Data MongoDB、JUnit 5、AssertJ、Mockito、PowerShell、Git。

## Global Constraints

- `RECORDING_STORAGE_DIR` 仍以仓库根目录解析，默认值保持 `backend/storage/recordings`。
- 当前录音路径必须为 `{taskCode}/{itemCode}.wav|mp3`，不得再生成内层 `recordings/`、条目目录或 `current.ext`。
- 临时上传和替换备份继续位于存储根目录 `temp/`。
- 迁移目标存在且 SHA-256 不同必须停止，不得覆盖。
- MongoDB 继续只保存相对路径；不得写入绝对路径。
- 不引入新依赖，不改变任务状态机、角色、媒体鉴权或 MongoDB 索引。
- `.superpowers/` 整体本地保存并加入 `.gitignore`；`docs/superpowers/` 最终不存在。
- 真实录音、`.env`、Cookie、Bearer Token、AppSecret 和带凭证 URI 不得进入 Git 或日志。

---

### Task 1: 扁平化新录音稳定路径

**Files:**
- Modify: `backend/src/main/java/com/recording/platform/media/RecordingMediaStorage.java`
- Modify: `backend/src/test/java/com/recording/platform/media/RecordingMediaStorageTests.java`
- Modify: `backend/src/test/java/com/recording/platform/media/MediaAccessAndRangeTests.java`

**Interfaces:**
- Consumes: `RecordingMediaStorage.prepare(MultipartFile, TaskVersion, String taskCode, String itemCode)`。
- Produces: `SubmittedRecording.relativePath()` 固定返回 `{taskCode}/{itemCode}.{extension}`；`activate`、`stageRetirement`、`resolve` 的签名保持不变。

- [ ] **Step 1: 修改路径断言形成失败测试**

将录音测试中的稳定路径断言改为：

```java
assertThat(prepared.recording().relativePath()).isEqualTo("TASK-001/I000001.wav");
assertThat(storage.resolve(prepared.recording().relativePath()))
    .isEqualTo(tempDir.resolve("TASK-001/I000001.wav"));
```

新增一个不生成旧目录的断言：

```java
assertThat(tempDir.resolve("recordings/TASK-001/I000001/current.wav")).doesNotExist();
```

媒体 Range 测试的测试文件路径同步改为 `TASK-001/I000001.mp3`，数据库相对路径也使用相同值。

- [ ] **Step 2: 运行失败测试确认旧实现被捕获**

Run from `backend/`:

```powershell
.\mvnw.cmd -Dtest=RecordingMediaStorageTests,MediaAccessAndRangeTests test
```

Expected: FAIL，实际路径仍为 `recordings/TASK-001/I000001/current.wav`。

- [ ] **Step 3: 最小修改稳定路径生成**

在 `RecordingMediaStorage.prepare` 中把：

```java
String relative = "recordings/" + safeTaskCode + "/" + safeItemCode + "/current." + extension;
```

替换为：

```java
String relative = safeTaskCode + "/" + safeItemCode + "." + extension;
```

`activate` 保持先隔离 `previousRelativePath`、再原子激活新文件的顺序。若旧结果扩展名与新文件不同，`previousRelativePath` 指向旧扩展名，因此旧文件仍会进入唯一备份路径并由清理任务处理。

- [ ] **Step 4: 运行定向测试**

```powershell
.\mvnw.cmd -Dtest=RecordingMediaStorageTests,MediaAccessAndRangeTests test
```

Expected: 相关测试全部通过，测试临时目录中不存在 `current.wav|mp3`。

- [ ] **Step 5: 提交路径实现**

```powershell
git add backend/src/main/java/com/recording/platform/media/RecordingMediaStorage.java backend/src/test/java/com/recording/platform/media/RecordingMediaStorageTests.java backend/src/test/java/com/recording/platform/media/MediaAccessAndRangeTests.java
git commit -m "修复(storage): 扁平化录音文件路径"
```

---

### Task 2: 增加一次性旧路径迁移器

**Files:**
- Create: `backend/src/main/java/com/recording/platform/media/RecordingPathMigrationService.java`
- Create: `backend/src/main/java/com/recording/platform/media/RecordingPathMigrationRunner.java`
- Create: `backend/src/main/java/com/recording/platform/media/RecordingPathMigrationResult.java`
- Create: `backend/src/test/java/com/recording/platform/media/RecordingPathMigrationServiceTests.java`
- Modify: `backend/src/main/resources/application.properties`

**Interfaces:**
- Consumes: `RecordingMediaStorage.resolve(String)`、`MongoTemplate`、旧路径正则 `recordings/{taskCode}/{itemCode}/current.(wav|mp3)`。
- Produces: `RecordingPathMigrationService.migrate(): RecordingPathMigrationResult`；仅在 `recording.path-migration.enabled=true` 时运行。

- [ ] **Step 1: 为路径映射、冲突和文档替换写失败测试**

测试至少包含：

```java
assertThat(service.targetPath("recordings/TASK-001/I000001/current.mp3"))
    .isEqualTo("TASK-001/I000001.mp3");
assertThatThrownBy(() -> service.targetPath("references/TASK-001/I000001.mp3"))
    .isInstanceOf(IllegalArgumentException.class);
```

使用临时目录和 Mockito `MongoTemplate` 验证：

```java
// 目标不存在：移动后哈希相同，并更新 media_assets 与 task_items。
// 目标同哈希：只删除旧副本并更新数据库。
// 目标不同哈希：抛出冲突，源和目标都保持不变，MongoTemplate 无写调用。
// 任一数据库 replace 失败：已更新文档恢复原值，文件移回旧路径。
```

- [ ] **Step 2: 运行失败测试**

```powershell
.\mvnw.cmd -Dtest=RecordingPathMigrationServiceTests test
```

Expected: 测试编译失败，因为迁移类型尚不存在。

- [ ] **Step 3: 实现严格旧路径映射和哈希检查**

迁移服务使用固定正则：

```java
private static final Pattern LEGACY = Pattern.compile(
    "^recordings/([A-Za-z0-9_-]{1,128})/([A-Za-z0-9_-]{1,128})/current\\.(wav|mp3)$"
);

String targetPath(String oldPath) {
    Matcher matcher = LEGACY.matcher(oldPath);
    if (!matcher.matches()) throw new IllegalArgumentException("unsupported legacy recording path");
    return matcher.group(1) + "/" + matcher.group(2) + "." + matcher.group(3);
}
```

使用 `MessageDigest.getInstance("SHA-256")` 分块计算哈希，不把录音整体读入内存。迁移清单只保存媒体 ID、旧/新相对路径、大小和哈希，不保存音频内容。

- [ ] **Step 4: 实现 MongoDB 文档条件替换和逐条回滚**

迁移以 `media_assets.kind=RECORDING` 且 `relativePath` 符合旧正则的文档为入口。每个媒体 ID 依次处理以下集合：

```text
media_assets.relativePath
task_items.currentResult.audio.relativePath
task_items.operations[].resultSnapshot.audio.relativePath
media_cleanup_jobs.relativePaths[]
idempotency_records.responseJson
```

读取命中的原始 `Document` 快照，递归替换值等于旧路径的字符串；`responseJson` 只替换完整旧路径文本。写回使用 `_id` 和原 `version` 条件，任何一个写回数量不是 1 都视为冲突。失败时按逆序恢复已写文档，再把文件移回旧路径。

- [ ] **Step 5: 增加显式启用且执行后退出的 Runner**

配置默认关闭：

```properties
recording.path-migration.enabled=${RECORDING_PATH_MIGRATION_ENABLED:false}
```

Runner 必须：

```java
@Component
@ConditionalOnProperty(name = "recording.path-migration.enabled", havingValue = "true")
final class RecordingPathMigrationRunner implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) {
        RecordingPathMigrationResult result = service.migrate();
        log.info("Recording path migration completed: migrated={}, deduplicated={}",
            result.migrated(), result.deduplicated());
        int exitCode = SpringApplication.exit(context, () -> 0);
        System.exit(exitCode);
    }
}
```

日志只输出计数，不输出 URI、绝对路径或媒体内容。迁移 Runner 测试不触发 `System.exit`；只测试服务，Runner 通过默认关闭的 Spring 上下文测试覆盖。

- [ ] **Step 6: 运行迁移器与存储相关测试**

```powershell
.\mvnw.cmd -Dtest=RecordingPathMigrationServiceTests,RecordingMediaStorageTests,MediaAccessAndRangeTests,RecordingPlatformBackendApplicationTests test
```

Expected: 全部通过，默认 Spring 上下文不会执行迁移。

- [ ] **Step 7: 提交迁移器**

```powershell
git add backend/src/main/java/com/recording/platform/media/RecordingPathMigrationService.java backend/src/main/java/com/recording/platform/media/RecordingPathMigrationRunner.java backend/src/main/java/com/recording/platform/media/RecordingPathMigrationResult.java backend/src/test/java/com/recording/platform/media/RecordingPathMigrationServiceTests.java backend/src/main/resources/application.properties
git commit -m "实现(storage): 增加旧录音路径迁移器"
```

---

### Task 3: 执行本机历史录音和 MongoDB 迁移

**Files:**
- Runtime move: `backend/storage/recordings/recordings/TEST_TEXT_RECORDING/I000002/current.mp3` → `backend/storage/recordings/TEST_TEXT_RECORDING/I000002.mp3`
- Runtime move: `backend/storage/recordings/recordings/TEST_TEXT_RECORDING/I000003/current.mp3` → `backend/storage/recordings/TEST_TEXT_RECORDING/I000003.mp3`
- No tracked runtime files

**Interfaces:**
- Consumes: Task 2 的显式迁移 Runner。
- Produces: 文件系统和 MongoDB 中不再存在旧录音路径。

- [ ] **Step 1: 停止并确认后端端口空闲**

只结束命令行确认属于本仓库的 8080 Java 进程，不处理 MongoDB 27017。确认：

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
```

Expected: 迁移开始前无 8080 监听。

- [ ] **Step 2: 备份迁移摘要并执行迁移 Runner**

从根目录加载本地 `.env` 后，在 `backend/` 执行：

```powershell
$env:RECORDING_PATH_MIGRATION_ENABLED='true'
.\mvnw.cmd spring-boot:run
Remove-Item Env:RECORDING_PATH_MIGRATION_ENABLED
```

Expected: 进程以 0 退出，摘要为迁移 2 个文件、冲突 0；输出不包含 Mongo URI 或绝对路径。

- [ ] **Step 3: 验证文件和旧目录**

```powershell
Test-Path ..\backend\storage\recordings\TEST_TEXT_RECORDING\I000002.mp3
Test-Path ..\backend\storage\recordings\TEST_TEXT_RECORDING\I000003.mp3
Test-Path ..\backend\storage\recordings\recordings
```

Expected: 前两项为 `True`，最后一项为 `False`。新文件大小分别保持 16632 和 20520 字节，SHA-256 与迁移前一致。

- [ ] **Step 4: 以迁移服务只读复检旧引用**

再次显式运行迁移 Runner。

Expected: 迁移 0、去重 0、冲突 0，证明旧 `media_assets` 入口已经归零；后端全量测试随后验证当前结果和媒体读取映射。

---

### Task 4: 合并并忽略 Superpowers 过程目录

**Files:**
- Move locally: `docs/superpowers/plans/*` → `.superpowers/plans/*`
- Move locally: `docs/superpowers/specs/*` → `.superpowers/specs/*`
- Preserve: `.superpowers/sdd/*`
- Modify: `.gitignore`
- Modify: `AGENTS.md`
- Modify: `log.md`
- Delete from Git: `docs/superpowers/**`

**Interfaces:**
- Consumes: 当前已确认的设计、计划和 SDD 文件。
- Produces: 单一且被忽略的根目录 `.superpowers/`，以及长期目录放置规则。

- [ ] **Step 1: 只读检查目标冲突**

按相对路径比较源和目标。目标不存在标记 `MOVE`；同名同 SHA-256 标记 `DEDUPLICATE`；同名不同 SHA-256 标记 `CONFLICT`。

Expected: `CONFLICT=0`；否则停止，不移动或删除任何文件。

- [ ] **Step 2: 移动过程文档并删除空源目录**

使用单一 PowerShell 进程和 `Move-Item -LiteralPath` 逐文件移动。确认源目录无文件后，只用非递归 `Remove-Item -LiteralPath` 删除空的 `plans`、`specs` 和 `docs/superpowers`。

- [ ] **Step 3: 加入忽略和长期规则**

`.gitignore` 增加：

```gitignore
.superpowers/
```

`AGENTS.md` 增加：

```text
设计、计划、brainstorm 和其他过程文档统一放在仓库根目录 `.superpowers/`，不得放入 `docs/`；`.superpowers/` 为本地资料并保持 Git 忽略。
```

`log.md` 记录录音扁平化、历史迁移数量、真实验收结果和目录合并，不记录录音内容或凭证。

- [ ] **Step 4: 验证忽略和文件完整性**

```powershell
git check-ignore -v .superpowers/specs/2026-07-15-flat-recording-path-and-superpowers-consolidation-design.md
git status --short
```

Expected: 设计文件命中 `.gitignore`；状态只显示业务代码、`.gitignore`、`AGENTS.md`、`log.md` 和已跟踪 `docs/superpowers` 删除，不显示 `.superpowers` 内容。

---

### Task 5: 全量验证、人工验收和交付

**Files:**
- Modify: `README.md`
- Modify: `docs/recording-platform-implementation-checklist.md`
- Modify: `log.md`

**Interfaces:**
- Consumes: Task 1–4 的代码、运行时迁移和目录规则。
- Produces: 完整验证证据、清洁 Git 状态和已推送的 `main`。

- [ ] **Step 1: 更新正式业务文档**

把 README 和 AGENTS 中的旧路径：

```text
recordings/{taskCode}/{itemCode}/current.wav|mp3
```

更新为：

```text
{taskCode}/{itemCode}.wav|mp3
```

实施清单新增本次完成项；不再引用 `docs/superpowers`。

- [ ] **Step 2: 运行完整自动化验证**

```powershell
cd backend
.\mvnw.cmd test
cd ..
node --test scripts/tests/start-dev.test.js
node --test apps/miniprogram/tests/*.test.js
cd apps/web
npm test -- --run
npm run build
cd ..\..
git diff --check
```

Expected: 后端、启动脚本、小程序、Web 测试和构建全部通过，`git diff --check` 退出 0。

- [ ] **Step 3: 重启并执行真实浏览器/真机验收**

```powershell
.\scripts\start-dev.cmd
```

验证：

1. 审核工作台播放迁移后的 I000002、I000003。
2. 真机新增并提交一条录音，磁盘只出现 `{taskCode}/{itemCode}.mp3`。
3. 驳回并重新录制同一条，文件被原子覆盖且没有生成 `current.mp3` 或条目目录。
4. 释放测试条目，确认当前文件和结果清理、历史保留。
5. 就绪接口返回 `overall`、`mongo`、`storage` 全部 `UP`。

- [ ] **Step 4: 最终敏感信息与暂存检查**

确认差异不包含 `.env`、录音文件、`.superpowers`、Cookie、Bearer Token、API Key、AppSecret 或带凭证 MongoDB URI。

- [ ] **Step 5: 提交并推送**

```powershell
git add backend README.md AGENTS.md .gitignore log.md docs/recording-platform-implementation-checklist.md
git add -u docs/superpowers
git commit -m "实现(storage): 扁平化录音文件并合并过程目录"
git push origin main
```

Expected: `main...origin/main`，工作区干净；`.superpowers/` 本地文件仍完整但不被 Git 跟踪。
