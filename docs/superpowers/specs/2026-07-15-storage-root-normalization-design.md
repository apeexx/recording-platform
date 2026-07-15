# 录音存储根目录规范化与数据迁移设计

## 问题与目标

根目录 `.env` 使用 `RECORDING_STORAGE_DIR=backend/storage/recordings`。启动脚本把该相对路径按仓库根目录检查，但 Spring Boot 在 `backend` 目录启动后直接按当前工作目录解析，实际写入了 `backend/backend/storage/recordings`。

本次目标是统一所有后端启动方式的路径语义，将相对存储路径固定按仓库根目录解析，并把重复目录中的现有录音安全迁移到 `backend/storage/recordings`。MongoDB 中的媒体相对路径保持不变。

## 采用方案

新增单一职责的公共路径解析器，规则如下：

- 绝对路径规范化后原样使用。
- 相对路径固定以仓库根目录为基准。
- 当进程工作目录名为 `backend` 时，仓库根目录取其父目录；否则以当前工作目录作为根目录。
- 不修改 `.env` 中现有的 `backend/storage/recordings`，也不依赖启动脚本把配置改写为绝对路径。

`RecordingMediaStorage`、`ImportSourceStorage` 和 `ReadinessService` 使用同一解析器，避免录音文件、导入临时文件和就绪探针检查不同目录。语音生成现有的仓库根目录解析保持不变，本次不重构该模块。

## 数据迁移

迁移源目录：

```text
backend/backend/storage/recordings
```

迁移目标目录：

```text
backend/storage/recordings
```

迁移过程必须满足：

1. 先停止后端，避免迁移期间继续写文件或读取半迁移状态。
2. 枚举源文件并逐个计算目标位置。
3. 若目标文件已存在且内容不同，立即停止并报告冲突，不覆盖任一文件。
4. 若目标文件不存在，则创建父目录并移动文件；若内容完全相同，则保留目标并删除重复源文件。
5. 迁移后确认数据库引用的相对路径能在新根目录解析到文件。
6. 只有源目录已经为空时，才删除空的 `backend/backend` 目录树。

当前 MongoDB 保存的录音路径形如 `recordings/{taskCode}/{itemCode}/current.mp3`，因此迁移不修改数据库文档、media ID、任务状态或提交历史。

## 错误与安全边界

- 不覆盖目标目录中的不同内容文件。
- 不删除仍含未迁移文件的源目录。
- 不打印或写入 `.env` 密钥、Cookie、Bearer Token 或数据库凭证。
- 不改变路径穿越防护；业务媒体路径仍必须是存储根目录内的相对路径。
- 迁移只处理本项目当前重复产生的录音存储目录，不扫描或移动其他目录。

## 测试与验收

1. 先新增失败测试，证明从 `backend` 工作目录解析 `backend/storage/recordings` 时当前实现会得到重复目录。
2. 实现公共路径解析器后，定向测试应解析为仓库根目录下的 `backend/storage/recordings`。
3. 覆盖绝对路径保持不变，以及从仓库根目录启动时的相对路径解析。
4. 运行后端全量测试、启动脚本测试、Web 测试与构建、小程序逻辑测试和 `git diff --check`。
5. 重新启动开发服务，确认 `/api/health/ready` 为 `UP`。
6. 在审核工作台重新播放已迁移的真机录音，确认时长与声音正常。
7. 创建一条新的测试录音并提交，确认新文件写入 `backend/storage/recordings`，且不再生成 `backend/backend`。

## 非本次范围

- 不修改对象存储方案或媒体数据库结构。
- 不迁移语音生成历史文件。
- 不改变任务领取、录制、审核或统计业务流程。
- 不引入新依赖。
