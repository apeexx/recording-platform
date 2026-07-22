const test = require('node:test')
const assert = require('node:assert/strict')

let modals
let toasts
let navigations

function requireGuard() {
  modals = []
  toasts = []
  navigations = []
  global.wx = {
    showModal(options) {
      modals.push(options)
      options.success({ confirm: false })
    },
    showToast(options) { toasts.push(options) },
    navigateTo(options) { navigations.push(options) },
  }
  delete require.cache[require.resolve('../services/feedback.js')]
  delete require.cache[require.resolve('../services/profileGuard.js')]
  return require('../services/profileGuard.js').requireCompleteProfile
}

test.afterEach(() => { delete global.wx })

test('完整资料缓存断网时立即放行且不弹完善资料', async () => {
  const refresh = new Promise((_, reject) => setImmediate(() => reject(new Error('offline'))))
  const app = { globalData: { session: {
    current: () => ({ user: { profileComplete: true } }),
    refreshProfile: () => refresh,
  } } }
  assert.equal(await requireGuard()(app), true)
  await refresh.catch(() => {})
  assert.equal(modals.length, 0)
  assert.equal(toasts.length, 0)
})

test('缓存未知且联网失败时只提示网络错误', async () => {
  const app = { globalData: { session: {
    current: () => null,
    refreshProfile: async () => { throw new Error('offline') },
  } } }
  assert.equal(await requireGuard()(app), false)
  assert.equal(toasts.at(-1).title, '网络不可用，请稍后重试')
  assert.equal(modals.length, 0)
  assert.equal(navigations.length, 0)
})

test('服务器明确返回未完善时才显示资料设置弹窗', async () => {
  const app = { globalData: { session: {
    current: () => ({ user: { profileComplete: false } }),
    refreshProfile: async () => ({ profileComplete: false }),
  } } }
  assert.equal(await requireGuard()(app), false)
  assert.equal(modals.length, 1)
  assert.equal(toasts.length, 0)
})

test('缓存未知且资料请求返回 401 时保留会话错误', async () => {
  const unauthorized = { status: 401, code: 'SESSION_REPLACED' }
  const app = { globalData: { session: {
    current: () => null,
    refreshProfile: async () => { throw unauthorized },
  } } }
  await assert.rejects(requireGuard()(app), error => error === unauthorized)
  assert.equal(toasts.length, 0)
  assert.equal(modals.length, 0)
})
