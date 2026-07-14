const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

test('app.json 不声明无效的录音 permission 配置', () => {
  const appConfig = JSON.parse(
    fs.readFileSync(path.resolve(__dirname, '../app.json'), 'utf8')
  )

  assert.equal(Object.hasOwn(appConfig.permission || {}, 'scope.record'), false)
})
