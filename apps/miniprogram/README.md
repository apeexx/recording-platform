# 微信小程序录音端

本目录是原生微信小程序，面向 `COLLECTOR` 采集员，已实现微信快捷登录、数字账号密码登录、独立个人资料与头像、任务权限申请、普通待办/返修列表、参考源展示、录音、文本补充、释放、自动下一条和个人统计。所有任务都必须录音；最终成果为文本时提交录音和文本，最终成果为音频时只提交录音。

登录页为“砚数声采”A 版，使用正式品牌图标；默认微信快捷登录，可切换 6–12 位非零开头数字账号登录。微信首次进入不再要求填写姓名；资料不完整仍可浏览任务，但申请权限、查看待办、领取和继续作业会提示并跳转 `pages/profile-settings/index`。点击头像后可从微信头像（`chooseAvatar`）、相册（`chooseMedia`）或本地 `assets/icons/default-collector-avatar.svg` 耳机默认头像三种来源选择，三种来源均先预览确认再使用。上传文件仍由现有后端校验为 JPEG/PNG/WebP 且最大 5MB；`chooseAvatar` 和 `chooseMedia` 需在微信开发者工具及真机分别验证。

“我的”页面展示当前采集员的真实 userId，以及最近 20 条提交记录及其状态。

录音界面默认显示麦克风按钮，不显示计时器；录制时按 PCM 帧 RMS 显示音量波形，暂停期间不累计样本时长，完成后仅显示“录制音频”和按样本数计算的实际时长。WAV 采用流式写入，MP3 使用固定版本 `@breezystack/lamejs@1.2.7` 的本地 vendor 文件，运行时不依赖网络。

## 本地导入

1. 复制 `project.config.example.json` 为 `project.config.json`，在本地文件中填写测试 AppID。
2. 需要修改 API 地址时，新建不受 Git 跟踪的 `config/private.js`：

   ```js
   module.exports = { apiBaseUrl: 'https://your-test-api.example.com' }
   ```

3. 在微信开发者工具中导入 `apps/miniprogram`。本地 HTTP 联调可暂时关闭“不校验合法域名”；真机和正式环境必须使用 HTTPS 并在微信后台配置 request/uploadFile/downloadFile 合法域名。

AppSecret 只存在后端环境变量 `WECHAT_APP_SECRET`，绝不得放入小程序。`project.config.json`、`project.private.config.json` 和 `config/private.js` 均已忽略。

录音授权由实际调用 `wx.getRecorderManager()` 开始录音时触发；不要在 `app.json.permission` 中声明无效的 `scope.record`。麦克风被拒绝后的重新授权仍需在开发者工具和真机验证。

## 品牌头像

小程序名称使用“砚数声采”，头像资产位于：

- `assets/branding/yanshu-avatar-144.png`：144×144 PNG，文件小于 2MB，用于微信小程序后台上传。
- `assets/branding/yanshu-avatar.svg`：同构矢量源文件，用于代码引用或后续无损导出。

头像固定使用暖米白背景、墨黑录音声纹、朱砂红录音点和“砚数”文字。修改时应同步更新 PNG 与 SVG，并重新检查 48px 列表尺寸的可读性。

## 验证

```powershell
node --test apps/miniprogram/tests/*.test.js
```

Node 测试覆盖微信 code 登录、姓名、Bearer token、幂等申请、PCM 时长/RMS、WAV 文件头、成果提交约束、弱网重试和自动下一条。麦克风授权拒绝、PCM 帧回调、暂停/继续、波形、播放和上传仍必须使用微信开发者工具及真机单独验收。

2026-07-15 已在个人测试 AppID 下完成开发者工具与微信真机联调：微信登录、姓名、权限申请、领取、MP3 录音/试听/上传、返修重录覆盖、释放清理和个人统计均通过。当时的“文字单独提交”旧行为已在 2026-07-18 调整为“录音＋文本”。该记录只代表开发环境验收；正式上线前仍需切换公司小程序账号和 HTTPS 合法域名。
