# 审核音频 Range 播放修复设计

## 问题与目标

真机提交的标准 MP3 已成功保存并进入 `REVIEW_PENDING`，但 Web 审核页原生 `<audio>` 发起 `Range: bytes=0-` 请求时，`GET /api/media/{mediaId}` 返回 500 `INTERNAL_ERROR`，播放器显示 `0:00 / 0:00`。

目标是保持现有媒体鉴权接口和浏览器原生播放器不变，使单段 Range 请求稳定返回可播放的 206 响应，同时不把最大 100MB 的媒体完整加载到 JVM 内存。

## 方案

1. 用真实 Spring MVC HTTP 写出测试覆盖 `Range: bytes=0-`，断言状态、`Content-Type`、`Content-Range`、`Content-Length` 和响应字节。
2. 保留 `MediaAccessService` 的鉴权、路径校验和文件存在性校验。
3. 仅调整 `MediaController` 的响应写出方式，通过明确的 `StreamingResponseBody` 按请求范围分块读取文件；不改媒体 URL、数据库结构、任务状态或音频文件。
4. 无 Range 请求仍返回 200 完整资源；非法、多段或越界 Range 仍返回 416 `INVALID_RANGE`。

## 错误与安全边界

- 继续要求已认证且通过角色/条目权限校验。
- 不在日志、测试或文档中写入 Cookie、Bearer Token 或真实会话值。
- 不放宽路径穿越、文件缺失和 Range 校验。
- 不使用 `byte[]` 整体读取大文件。

## 验证

- 新增的 HTTP 回归测试必须先稳定复现 500，再在最小修复后返回 206。
- 运行媒体定向测试、后端全量测试、Web 全量测试与构建。
- 使用真实浏览器刷新审核页，确认播放器显示约 2.7 秒且可以播放。

## 非本次范围

当前本地 `RECORDING_STORAGE_DIR` 相对后端工作目录产生 `backend/backend/` 重复目录。该问题单独处理，避免与媒体响应修复混在同一变量中。
