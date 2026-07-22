const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const root = path.resolve(__dirname, '..')
const read = file => fs.readFileSync(path.join(root, file), 'utf8')

test('登录和资料操作错误使用悬浮反馈而非底部错误文字', () => {
  for (const page of ['login', 'profile-settings']) {
    assert.match(read(`pages/${page}/index.js`), /feedback\.error/)
    assert.doesNotMatch(read(`pages/${page}/index.wxml`), /wx:if="\{\{error\}\}"/)
  }
})

test('任务大厅、任务数据和统计保留可重试阻塞状态', () => {
  for (const page of ['tasks', 'work-list', 'profile']) {
    const script = read(`pages/${page}/index.js`)
    const template = read(`pages/${page}/index.wxml`)
    assert.match(script, /loadError/)
    assert.match(template, /loadError/)
    assert.match(template, /bindtap="load"/)
  }
})

test('领取和权限申请错误使用悬浮反馈', () => {
  const script = read('pages/tasks/index.js')
  assert.match(script, /feedback\.error/)
  assert.doesNotMatch(script, /catch\(err\)\{this\.setData\(\{error:/)
})
