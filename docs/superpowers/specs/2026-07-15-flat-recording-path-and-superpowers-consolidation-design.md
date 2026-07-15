# 录音文件扁平化与 Superpowers 目录合并设计

## 目标

本次调整同时解决两个独立但范围明确的问题：

1. 将当前录音从 `recordings/{taskCode}/{itemCode}/current.ext` 改为 `{taskCode}/{itemCode}.ext`，消除存储根目录下重复的 `recordings/` 和条目级目录。
2. 将 `docs/superpowers/` 合并到根目录 `.superpowers/`，并将 `.superpowers/` 作为本地过程资料整体加入 Git 忽略。

不改变任务状态机、媒体鉴权、录音格式、录音元数据、返修归属、操作历史或对象存储方向。

## 录音目标结构

`RECORDING_STORAGE_DIR` 仍表示录音存储根目录，开发默认值仍为仓库根目录下的 `backend/storage/recordings/`。当前录音的稳定路径改为：

```text
{taskCode}/{itemCode}.wav
{taskCode}/{itemCode}.mp3
```

例如：

```text
backend/storage/recordings/TEST_TEXT_RECORDING/I000002.mp3
backend/storage/recordings/TEST_TEXT_RECORDING/I000003.mp3
```

不再生成：

```text
backend/storage/recordings/recordings/TEST_TEXT_RECORDING/I000002/current.mp3
```

临时上传、替换备份和清理恢复文件仍保留在存储根目录的 `temp/` 下。它们使用随机文件名，不能改成条目编号，以免并发替换和失败恢复互相覆盖。

## 写入、返修与清理行为

- 首次提交根据任务编号、条目编号和实际格式生成稳定目标文件。
- 返修继续原子替换同一个 `{itemCode}.ext`，不保留旧录音正文。
- 如果任务版本改变录音格式，激活新文件时同时隔离并清理旧扩展名路径，不能留下同一条目的 `.wav` 和 `.mp3` 两个当前文件。
- 提交失败时保留原文件和原数据库结果；临时文件按现有规则清理。
- 释放成功后隔离当前文件并由持久化清理任务删除，提交历史和操作历史仍永久保留。
- `GET /api/media/{mediaId}` 继续只通过数据库中的相对路径读取，不暴露磁盘绝对路径。

## 历史数据迁移

迁移必须在停止后端写入后执行。迁移对象为现有稳定录音文件及所有仍有效的数据库路径引用。

迁移步骤：

1. 枚举旧结构 `recordings/{taskCode}/{itemCode}/current.ext`，校验任务编号、条目编号和扩展名。
2. 为每个源文件计算新路径 `{taskCode}/{itemCode}.ext`。
3. 目标不存在时标记为移动；目标存在且 SHA-256 相同时标记为去重；目标存在但哈希不同时停止，不覆盖任何文件。
4. 记录迁移前文件大小和 SHA-256，移动后再次校验。
5. 以媒体 ID 和旧相对路径为条件，同步 `media_assets.relativePath`、`task_items.currentResult.audio.relativePath` 以及仍被业务重放或清理使用的有效路径快照。
6. 数据库条件更新失败时恢复文件原位置；文件恢复失败必须停止并报告，不能继续清理旧目录。
7. 确认旧相对路径引用数量为零、旧目录无文件后，只删除空目录。

提交历史只保存时长、格式和是否存在音频，不需要路径迁移。操作历史中的结果快照若保存当前录音相对路径，则按媒体 ID 同步，避免后台记录返回过期路径。已完成的幂等响应快照不是媒体读取依据，但若包含同一媒体 ID 的旧路径，也同步为新路径，保证重复请求响应一致。

## 兼容与回滚

本次采用一次性完整迁移，不长期保留两套目录结构。上线代码与历史数据迁移作为同一维护窗口完成。

回滚时先停止后端，将数据库路径按迁移清单恢复为旧值，再把文件按相同清单移回旧路径并复核 SHA-256，最后回滚代码。不得只回滚代码或只回滚文件。

## Superpowers 目录合并

最终本地结构为：

```text
.superpowers/
├─ plans/
├─ specs/
└─ sdd/
```

规则如下：

- 将 `docs/superpowers/plans/` 和 `docs/superpowers/specs/` 全部移动到根目录 `.superpowers/`。
- 保留 `.superpowers/sdd/` 当前内容。
- 合并前检查同名冲突：同名同内容可去重，同名不同内容立即停止。
- 删除空的 `docs/superpowers/`。
- `.gitignore` 增加 `.superpowers/`；该目录只保存在本地，不再进入 Git。
- 当前已跟踪的 `docs/superpowers` 文件从仓库当前版本中删除，但本地副本保留在被忽略的 `.superpowers/`。
- `AGENTS.md` 只增加一条长期放置规则：设计、计划和过程文档放在根目录 `.superpowers/`，不放在 `docs/`。
- 仓库当前不存在指向 `docs/superpowers` 的外部引用，不创建 README、跳转文件或额外目录说明。

本设计文档在设计确认阶段暂存于 `docs/superpowers/specs/` 供审阅；实施目录合并时与其他设计文档一起迁入被忽略的 `.superpowers/specs/`。

## 测试与验收

自动化验证至少覆盖：

- 新录音路径为 `{taskCode}/{itemCode}.ext`。
- 首次提交、同格式返修覆盖、跨格式返修清理旧扩展名。
- 释放隔离、清理重放、媒体读取和路径穿越保护。
- 迁移路径映射、目标冲突、哈希一致性和数据库条件更新失败回滚。
- 后端全量测试、启动脚本测试、小程序测试、Web 测试与构建。
- `git diff --check`、敏感信息扫描和 Git 忽略验证。

真实联调验收至少覆盖：

- 迁移后的 I000002、I000003 在审核工作台可播放且时长、声音正常。
- 真机新增并提交一条录音，文件只出现在新结构中。
- 对新录音执行一次返修或重新提交，确认稳定文件被覆盖且没有生成 `current.ext` 或条目目录。
- 释放测试条目后确认当前结果和文件清理符合既有规则。
- `docs/superpowers/` 不存在，`.superpowers/` 本地文件完整且 `git status` 不显示其内容。

## 风险控制

- 录音迁移前必须停止后端，避免提交与迁移并发。
- 不在日志或 Git 中记录录音内容、绝对生产路径、Cookie、Bearer Token、AppSecret 或数据库凭证。
- 不使用递归删除处理录音目录；仅在确认目录无文件后逐级删除空目录。
- 不引入新依赖，不改变 MongoDB 集合结构和索引。
