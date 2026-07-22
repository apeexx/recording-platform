const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')

const { createSessionService } = require('../services/session.js')

function storageWx(stored) {
  return {
    getStorageSync: key => stored[key],
    setStorageSync: (key, value) => { stored[key] = value },
    removeStorageSync: key => { delete stored[key] },
  }
}

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

test('数字账号登录保存与微信相同的不透明会话结构', async () => {
  const stored={};const wx={getStorageSync:k=>stored[k],setStorageSync:(k,v)=>stored[k]=v,removeStorageSync:k=>delete stored[k]}
  const api={accountLogin:async body=>({token:'account-token',userId:'u1',account:body.account,profileComplete:true})}
  const session=createSessionService({wx,api});await session.accountLogin('123456','Password-1')
  assert.equal(stored.recSession.token,'account-token');assert.equal(stored.recSession.user.userId,'u1')
})

test('确认接管后仅保存后端签发的新小程序会话',async()=>{
  const stored={recSession:{token:'old-token',user:{userId:'MINI-old'}}}
  const wx={getStorageSync:k=>stored[k],setStorageSync:(k,v)=>stored[k]=v,removeStorageSync:k=>delete stored[k]}
  const calls=[]
  const api={takeover:async token=>{calls.push(token);return{token:'new-token',userId:'MINI-0123456789abcdef01234567',account:'123456'}}}
  const session=createSessionService({wx,api})
  const result=await session.takeover('takeover-token')
  assert.deepEqual(calls,['takeover-token'])
  assert.equal(result.userId,'MINI-0123456789abcdef01234567')
  assert.equal(stored.recSession.token,'new-token')
})

test('新账号登录整体覆盖旧账号资料缓存', async () => {
  const stored = { recSession: { token: 'old', user: { userId: 'MINI-old', name: '旧用户', profileComplete: true } } }
  const session = createSessionService({
    wx: storageWx(stored),
    api: { accountLogin: async () => ({ token: 'new', userId: 'MINI-new', name: '新用户', profileComplete: true }) },
  })
  await session.accountLogin('123456', 'Password-1')
  assert.deepEqual(stored.recSession.user, { token: 'new', userId: 'MINI-new', name: '新用户', profileComplete: true })
})

test('资料与头像响应统一合并到当前会话缓存', () => {
  const stored = { recSession: { token: 'token', user: { userId: 'MINI-1', name: '原姓名', profileComplete: true } } }
  const session = createSessionService({ wx: storageWx(stored), api: {} })
  const updated = session.updateProfile({ name: '新姓名', hasCustomAvatar: true })
  assert.equal(updated.userId, 'MINI-1')
  assert.equal(stored.recSession.user.name, '新姓名')
  assert.equal(stored.recSession.user.hasCustomAvatar, true)
})

test('退出登录删除 token 与个人资料缓存', () => {
  const stored = { recSession: { token: 'token', user: { userId: 'MINI-1', profileComplete: true } } }
  const session = createSessionService({ wx: storageWx(stored), api: {} })
  session.clear()
  assert.equal(stored.recSession, undefined)
})

function loadLoginPage({session,modalResult={confirm:false}}){
  const source=fs.readFileSync(path.join(__dirname,'../pages/login/index.js'),'utf8')
  const modalCalls=[]
  const switchTabs=[]
  const toasts=[]
  let definition
  const wx={showModal:options=>{modalCalls.push(options);options.success(modalResult)},showToast:options=>toasts.push(options),switchTab:options=>switchTabs.push(options)}
  const requireStub=()=>({success:title=>wx.showToast({title}),info:title=>wx.showToast({title,icon:'none'}),error:title=>wx.showToast({title,icon:'none'})})
  vm.runInNewContext(source,{Page:value=>{definition=value},wx,require:requireStub,getApp:()=>({globalData:{session}})},{filename:'pages/login/index.js'})
  const page={...definition,data:{...definition.data},setData(patch){Object.assign(this.data,patch)}}
  return {page,modalCalls,switchTabs,toasts}
}

function accountInUse(){return {code:'ACCOUNT_IN_USE',details:{takeoverToken:'one-time-token'},message:'账号已在其他设备登录'}}

test('微信登录确认接管后只调用一次 takeover 并进入任务页',async()=>{
  let takeoverCalls=0
  const {page,modalCalls,switchTabs}=loadLoginPage({session:{login:async()=>{throw accountInUse()},takeover:async token=>{takeoverCalls+=1;assert.equal(token,'one-time-token')}} ,modalResult:{confirm:true}})
  await page.wechatLogin()
  assert.equal(modalCalls.length,1)
  assert.equal(takeoverCalls,1)
  assert.equal(switchTabs.length,1)
  assert.equal(switchTabs[0].url,'/pages/tasks/index')
})

test('数字账号登录取消接管时不调用 takeover 且停留在登录页',async()=>{
  let takeoverCalls=0
  const {page,modalCalls,switchTabs,toasts}=loadLoginPage({session:{accountLogin:async()=>{throw accountInUse()},takeover:async()=>{takeoverCalls+=1}},modalResult:{confirm:false}})
  page.setData({account:'123456',password:'Password-1'})
  await page.accountLogin()
  assert.equal(modalCalls.length,1)
  assert.equal(takeoverCalls,0)
  assert.equal(switchTabs.length,0)
  assert.equal(toasts.at(-1).title,'已取消强制登录')
})

test('登录请求进行中再次触发 perform 会被忽略',async()=>{
  let resolveLogin
  let loginCalls=0
  const pending=new Promise(resolve=>{resolveLogin=resolve})
  const {page,switchTabs}=loadLoginPage({session:{login:()=>{loginCalls+=1;return pending}}})
  const first=page.wechatLogin()
  const second=page.wechatLogin()
  assert.equal(loginCalls,1)
  resolveLogin()
  await Promise.all([first,second])
  assert.equal(switchTabs.length,1)
})
