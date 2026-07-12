const test = require('node:test')
const assert = require('node:assert/strict')

const { createSessionService } = require('../services/session.js')

test('微信登录只向后端发送临时 code 并保存不透明 token', async () => {
  const stored = {}
  const wx = {
    login: ({ success }) => success({ code: 'temporary-code' }),
    setStorageSync: (key, value) => { stored[key] = value },
    getStorageSync: (key) => stored[key],
    removeStorageSync: (key) => { delete stored[key] }
  }
  const calls = []
  const api = { login: async (body) => { calls.push(body); return { token: 'opaque-token', userId: 'u1', name: null } } }

  const session = createSessionService({ wx, api })
  const user = await session.login()

  assert.deepEqual(calls, [{ code: 'temporary-code' }])
  assert.equal('openId' in calls[0], false)
  assert.equal(stored.recSession.token, 'opaque-token')
  assert.equal(user.userId, 'u1')
})

test('设置姓名后更新本地实名摘要', async () => {
  const stored = { recSession: { token: 't', user: { userId: 'u1' } } }
  const wx = { getStorageSync: k => stored[k], setStorageSync: (k,v) => stored[k]=v, removeStorageSync:k=>delete stored[k] }
  const session = createSessionService({ wx, api: { setName: async name => ({ id:'u1',name }) } })
  await session.setName('张三')
  assert.equal(stored.recSession.user.name, '张三')
})
