const test = require('node:test')
const assert = require('node:assert/strict')
const { copyUserId } = require('../services/userIdClipboard.js')

test('复制完整用户 ID 并明确提示成功', () => {
  const toasts = []
  global.wx = {
    setClipboardData({ data, success }) {
      assert.equal(data, 'MINI-0123456789abcdef01234567')
      success()
    },
    showToast(options) { toasts.push(options) }
  }
  copyUserId('MINI-0123456789abcdef01234567')
  assert.equal(toasts.at(-1).title, '用户 ID 已复制')
})

test('剪贴板失败时只提示失败', () => {
  const toasts = []
  global.wx = {
    setClipboardData({ fail }) { fail() },
    showToast(options) { toasts.push(options) }
  }
  copyUserId('MINI-0123456789abcdef01234567')
  assert.equal(toasts.length, 1)
  assert.equal(toasts[0].title, '复制失败，请重试')
  assert.equal(toasts[0].icon, 'none')
})

test('用户 ID 缺失时不调用剪贴板', () => {
  const toasts = []
  let clipboardCalls = 0
  global.wx = {
    setClipboardData() { clipboardCalls += 1 },
    showToast(options) { toasts.push(options) }
  }
  copyUserId('')
  assert.equal(clipboardCalls, 0)
  assert.equal(toasts.at(-1).title, '复制失败，请重试')
})
