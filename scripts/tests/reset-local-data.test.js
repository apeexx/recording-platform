const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const root = path.resolve(__dirname, '..')
const powershell = fs.readFileSync(path.join(root, 'reset-local-data.ps1'), 'utf8')
const launcher = fs.readFileSync(path.join(root, 'reset-local-data.cmd'), 'utf8')

test('本地重置要求命令行精确确认词并传递给后端二次校验', () => {
  assert.match(powershell, /ConfirmDatabase -cne 'recording_platform'/)
  assert.match(powershell, /RECORDING_LOCAL_RESET_CONFIRMATION = \$ConfirmDatabase/)
  assert.match(launcher, /-ConfirmDatabase "%~1"/)
  assert.ok(powershell.indexOf("ConfirmDatabase -cne 'recording_platform'") < powershell.indexOf('Get-Content -LiteralPath'))
})

test('本地重置在启动 Java 前校验管理员凭证和数据库 URI', () => {
  const credentialCheck = powershell.indexOf('INITIAL_ADMIN_PASSWORD')
  const databaseCheck = powershell.indexOf("-notmatch '/recording_platform")
  const javaStart = powershell.indexOf('mvnw.cmd spring-boot:run')
  assert.ok(credentialCheck >= 0 && databaseCheck >= 0)
  assert.ok(credentialCheck < javaStart && databaseCheck < javaStart)
})

test('本地重置启动时关闭自动索引创建以兼容旧库中的冲突数据', () => {
  assert.match(powershell, /--spring\.data\.mongodb\.auto-index-creation=false/)
})
