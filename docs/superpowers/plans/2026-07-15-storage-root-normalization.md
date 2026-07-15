# 录音存储根目录规范化与数据迁移 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 统一后端相对存储路径的仓库根目录语义，安全迁移现有录音，并确保所有启动方式不再生成 `backend/backend/`。

**Architecture:** 新增无状态的 `StoragePathResolver`，集中处理绝对路径和仓库根目录相对路径。录音、导入和就绪检查复用同一解析结果；现有录音在后端停止后执行冲突检测和一次性本地迁移，MongoDB 相对媒体路径不变。

**Tech Stack:** Java 17、Spring Boot 3.5、JUnit 5、AssertJ、PowerShell 7、Node.js 测试。

## Global Constraints

- 不修改 `.env` 中现有的 `RECORDING_STORAGE_DIR=backend/storage/recordings`。
- 不修改 MongoDB 文档、media ID、任务状态或提交历史。
- 不覆盖目标目录中内容不同的文件；发现冲突立即停止。
- 只有迁移源目录为空后才删除空目录树。
- 不打印或提交 `.env` 密钥、Cookie、Bearer Token、数据库凭证或真实录音。
- 不引入新依赖，不重构语音生成存储模块。

---

### Task 1: 统一后端存储路径解析

**Files:**
- Create: `backend/src/main/java/com/recording/platform/config/StoragePathResolver.java`
- Create: `backend/src/test/java/com/recording/platform/config/StoragePathResolverTests.java`
- Modify: `backend/src/main/java/com/recording/platform/media/RecordingMediaStorage.java`
- Modify: `backend/src/main/java/com/recording/platform/importing/ImportSourceStorage.java`
- Modify: `backend/src/main/java/com/recording/platform/health/ReadinessService.java`

**Interfaces:**
- Consumes: 配置字符串 `RECORDING_STORAGE_DIR` 和 JVM `user.dir`。
- Produces: `StoragePathResolver.resolve(String configuredPath): Path`；测试可调用包内重载 `resolve(String configuredPath, Path workingDirectory): Path`。

- [x] **Step 1: 编写路径解析失败测试**

  新建 `StoragePathResolverTests`，覆盖从后端目录、仓库根目录和绝对目录启动：

  ```java
  @Test
  void resolvesBackendPrefixedStorageAgainstRepositoryRoot() {
      Path repository = tempDir.resolve("recording-platform").toAbsolutePath();
      Path backend = repository.resolve("backend");

      assertThat(StoragePathResolver.resolve("backend/storage/recordings", backend))
          .isEqualTo(repository.resolve("backend/storage/recordings"));
  }

  @Test
  void resolvesRelativeStorageAgainstCurrentRepositoryDirectory() {
      Path repository = tempDir.resolve("recording-platform").toAbsolutePath();

      assertThat(StoragePathResolver.resolve("backend/storage/recordings", repository))
          .isEqualTo(repository.resolve("backend/storage/recordings"));
  }

  @Test
  void preservesAbsoluteStoragePath() {
      Path absolute = tempDir.resolve("external-recordings").toAbsolutePath();

      assertThat(StoragePathResolver.resolve(absolute.toString(), tempDir))
          .isEqualTo(absolute.normalize());
  }
  ```

- [x] **Step 2: 运行测试并确认 RED**

  Run from `backend`:

  ```powershell
  .\mvnw.cmd "-Dtest=StoragePathResolverTests" test
  ```

  Expected: 测试编译失败，明确提示 `StoragePathResolver` 尚不存在。

- [x] **Step 3: 实现最小公共解析器**

  新建 `StoragePathResolver`：

  ```java
  public final class StoragePathResolver {
      private StoragePathResolver() { }

      public static Path resolve(String configuredPath) {
          return resolve(configuredPath, Path.of(System.getProperty("user.dir")));
      }

      static Path resolve(String configuredPath, Path workingDirectory) {
          Path configured = Path.of(configuredPath);
          if (configured.isAbsolute()) return configured.normalize();
          Path working = workingDirectory.toAbsolutePath().normalize();
          Path repository = "backend".equalsIgnoreCase(working.getFileName().toString())
              && working.getParent() != null ? working.getParent() : working;
          return repository.resolve(configured).normalize();
      }
  }
  ```

  将三个 Spring 字符串构造器改为调用 `StoragePathResolver.resolve(root)`，保留接受 `Path` 的测试构造器和现有业务行为。

- [x] **Step 4: 运行定向测试并确认 GREEN**

  Run from `backend`:

  ```powershell
  .\mvnw.cmd "-Dtest=StoragePathResolverTests,RecordingMediaStorageTests,ReadinessServiceTests,ImportJobServiceTests" test
  ```

  Expected: 新增路径测试和已有存储相关测试全部通过，0 failures、0 errors。

- [x] **Step 5: 提交代码检查点**

  ```powershell
  git add backend/src/main/java/com/recording/platform/config/StoragePathResolver.java backend/src/test/java/com/recording/platform/config/StoragePathResolverTests.java backend/src/main/java/com/recording/platform/media/RecordingMediaStorage.java backend/src/main/java/com/recording/platform/importing/ImportSourceStorage.java backend/src/main/java/com/recording/platform/health/ReadinessService.java
  git commit -m "修复(storage): 统一后端录音目录解析"
  ```

### Task 2: 安全迁移现有录音

**Files:**
- Move local runtime files from: `backend/backend/storage/recordings/`
- Move local runtime files to: `backend/storage/recordings/`
- Do not track: migrated recording files

**Interfaces:**
- Consumes: 源目录中的相对文件路径和 SHA-256 内容。
- Produces: 目标目录中保持相同相对路径和内容的录音文件；空的重复目录被移除。

- [x] **Step 1: 停止后端并锁定迁移边界**

  确认 `8080` 监听进程属于本项目后端后停止它。随后将源、目标解析为绝对路径，并验证二者都位于 `C:\Projects\recording-platform` 内，且源路径精确等于 `C:\Projects\recording-platform\backend\backend\storage\recordings`。

- [x] **Step 2: 生成冲突检查清单**

  对源目录每个文件计算相对路径；目标不存在时标记 `MOVE`，目标存在且 SHA-256 相同时标记 `DEDUPLICATE`，目标存在但哈希不同时标记 `CONFLICT`。

  Expected: `CONFLICT` 数量为 0；只输出相对路径、动作和计数，不输出录音内容。

- [x] **Step 3: 执行逐文件迁移**

  对 `MOVE` 创建目标父目录并使用 `Move-Item -LiteralPath` 移动；对 `DEDUPLICATE` 删除确认同哈希的源副本。任何异常立即停止，不继续清理目录。

- [x] **Step 4: 验证内容和清理空目录**

  重新计算目标 SHA-256，确认与迁移前源哈希一致；确认源目录无文件后，仅使用非递归 `Remove-Item -LiteralPath` 从最深层开始删除空目录，直到移除空的 `backend/backend`。

  Expected:

  ```text
  backend/backend 不存在
  backend/storage/recordings/recordings/TEST_TEXT_RECORDING/I000002/current.mp3 存在
  ```

### Task 3: 文档同步与全链路验证

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `scripts/README.md`
- Modify: `log.md`
- Modify: `docs/recording-platform-implementation-checklist.md`
- Modify: `docs/superpowers/plans/2026-07-15-storage-root-normalization.md`

**Interfaces:**
- Consumes: Task 1 的仓库根目录路径契约和 Task 2 的迁移结果。
- Produces: 可复核的启动说明、验收记录和完成状态。

- [x] **Step 1: 更新路径契约与迁移记录**

  文档明确：`RECORDING_STORAGE_DIR` 的相对值按仓库根目录解析；默认目标仍为 `backend/storage/recordings`；记录现有录音已完成无覆盖迁移，不记录媒体内容或绝对敏感配置。

- [x] **Step 2: 运行自动验证**

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

  Expected: 后端、启动脚本、小程序、Web 测试和构建全部通过；无空白错误。

- [x] **Step 3: 重启并验证就绪状态**

  Run from repository root:

  ```powershell
  .\scripts\start-dev.cmd
  ```

  Expected: MongoDB 和存储前置检查通过，8080/5173 正常监听；`GET http://localhost:8080/api/health/ready` 返回 200 且 `overall`、`mongo`、`storage` 均为 `UP`。

- [x] **Step 4: 验证历史录音与新录音路径**

  在审核工作台播放迁移后的 I000002，确认真实时长和声音正常。再通过真机领取一条新数据并提交录音，确认提交成功、审核页可播放，并检查新文件只出现在 `backend/storage/recordings`，`backend/backend` 没有重新生成。

- [x] **Step 5: 最终差异和敏感信息检查**

  仅检查暂存代码和文档，确认不包含 `.env`、录音文件、Cookie、Bearer Token、API Key 或带凭证 MongoDB URI。

- [x] **Step 6: 提交并推送文档收口**

  ```powershell
  git add README.md AGENTS.md scripts/README.md log.md docs/recording-platform-implementation-checklist.md docs/superpowers/plans/2026-07-15-storage-root-normalization.md
  git commit -m "文档(storage): 记录录音目录迁移验收"
  git push origin main
  ```
