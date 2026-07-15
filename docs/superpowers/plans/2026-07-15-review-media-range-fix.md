# 审核音频 Range 播放修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复审核页原生音频播放器请求 `Range: bytes=0-` 时媒体接口返回 500 的问题。

**Architecture:** 保持 `/api/media/{mediaId}`、媒体鉴权和文件分段读取边界不变。通过 Spring MVC HTTP 回归测试复现响应写出失败，再最小调整 `MediaController` 的 Range 响应，使浏览器获得标准 206 响应。

**Tech Stack:** Java 17、Spring Boot 3.5、Spring MVC、JUnit 5、MockMvc、Vue 3 原生 `<audio>`。

## Global Constraints

- 不修改数据库结构、任务状态或现有录音文件。
- 不把最大 100MB 媒体完整读取为 `byte[]`。
- 保持单 Range 支持，多段和非法 Range 返回 416。
- 不记录或提交 Cookie、Token、真实媒体请求头。
- 不处理 `backend/backend/` 存储目录问题。

---

### Task 1: 修复媒体 Range HTTP 写出

**Files:**
- Modify: `backend/src/test/java/com/recording/platform/media/MediaAccessAndRangeTests.java`
- Modify: `backend/src/main/java/com/recording/platform/media/MediaController.java`
- Modify: `README.md`
- Modify: `log.md`

**Interfaces:**
- Consumes: `MediaAccessService.open(String, PlatformPrincipal)` 返回 `ReadableMedia(path, contentType, length)`。
- Produces: `GET /api/media/{mediaId}` 对 `Range: bytes=0-` 返回 206、`audio/mpeg`、标准 `Content-Range` 和对应文件字节。

- [x] **Step 1: 编写失败的真实 HTTP Range 测试**

  在现有媒体测试中通过 MockMvc 请求 MP3 资源并携带 `Range: bytes=0-`，断言：

  ```java
  mockMvc.perform(get("/api/media/media-mp3")
      .header(HttpHeaders.RANGE, "bytes=0-")
      .principal(authentication))
    .andExpect(status().isPartialContent())
    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "audio/mpeg"))
    .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-7/8"))
    .andExpect(content().bytes(mp3Bytes));
  ```

- [x] **Step 2: 运行测试并确认 RED**

  Run: `backend\mvnw.cmd "-Dtest=MediaAccessAndRangeTests" test`

  Expected: 新增 HTTP 测试因 Range 响应写出异常而失败，复现当前 500，不是编译或测试装配错误。

- [x] **Step 3: 最小修复 Range 响应**

  调整 `MediaController` 的完整文件和单 Range 输出，使 Spring MVC 使用明确的 `StreamingResponseBody` 响应类型和媒体类型；保留现有 200、403、404、416 行为及 `MediaAccessService`。

- [x] **Step 4: 验证 GREEN 和回归**

  Run: `backend\mvnw.cmd "-Dtest=MediaAccessAndRangeTests" test`

  Expected: 媒体测试全部通过。

  Run: `backend\mvnw.cmd test`

  Expected: 后端全量测试 0 failures、0 errors。

  Run: `npm test -- --run`

  Expected: Web Node 与 Vitest 测试全部通过。

  Run: `npm run build`

  Expected: Vite 构建成功。

- [x] **Step 5: 更新文档与浏览器验收**

  在 `README.md` 和 `log.md` 记录 Range 修复、验证命令和真实浏览器验收结果。刷新 `/admin/review/{itemId}`，确认播放器显示真实时长并可播放。

- [x] **Step 6: 提交并推送**

  ```powershell
  git add backend/src/main/java/com/recording/platform/media/MediaController.java backend/src/test/java/com/recording/platform/media/MediaAccessAndRangeTests.java README.md log.md docs/superpowers/specs/2026-07-15-review-media-range-design.md docs/superpowers/plans/2026-07-15-review-media-range-fix.md
  git commit -m "修复(media): 支持审核音频 Range 播放"
  git push origin main
  ```
