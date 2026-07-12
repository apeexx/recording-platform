# 微信小程序录音端

本目录是原生微信小程序，面向 `COLLECTOR` 采集员，已实现微信登录、姓名设置、任务权限申请、单条领取、参考文字/音频/视频展示、录音、可选文字、提交、释放、自动下一条和个人统计。

## 本地导入

1. 复制 `project.config.example.json` 为 `project.config.json`，在本地文件中填写测试 AppID。
2. 需要修改 API 地址时，新建不受 Git 跟踪的 `config/private.js`：

   ```js
   module.exports = { apiBaseUrl: 'https://your-test-api.example.com' }
   ```

3. 在微信开发者工具中导入 `apps/miniprogram`。本地 HTTP 联调可暂时关闭“不校验合法域名”；真机和正式环境必须使用 HTTPS 并在微信后台配置 request/uploadFile/downloadFile 合法域名。

AppSecret 只存在后端环境变量 `WECHAT_APP_SECRET`，绝不得放入小程序。`project.config.json`、`project.private.config.json` 和 `config/private.js` 均已忽略。

## 验证

```powershell
node --test apps/miniprogram/tests/*.test.js
```

Node 测试覆盖微信 code 登录、姓名、Bearer token、幂等申请、录音参数、提交要求、弱网重试和自动下一条。麦克风授权拒绝、暂停/继续、播放、上传及池空提示必须使用微信开发者工具和真机单独验收。
