const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')

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

function loadLoginPage({session,modalResult={confirm:false}}){
  const source=fs.readFileSync(path.join(__dirname,'../pages/login/index.js'),'utf8')
  const modalCalls=[]
  const switchTabs=[]
  let definition
  const wx={showModal:options=>{modalCalls.push(options);options.success(modalResult)},switchTab:options=>switchTabs.push(options)}
  vm.runInNewContext(source,{Page:value=>{definition=value},wx,getApp:()=>({globalData:{session}})},{filename:'pages/login/index.js'})
  const page={...definition,data:{...definition.data},setData(patch){Object.assign(this.data,patch)}}
  return {page,modalCalls,switchTabs}
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
  const {page,modalCalls,switchTabs}=loadLoginPage({session:{accountLogin:async()=>{throw accountInUse()},takeover:async()=>{takeoverCalls+=1}},modalResult:{confirm:false}})
  page.setData({account:'123456',password:'Password-1'})
  await page.accountLogin()
  assert.equal(modalCalls.length,1)
  assert.equal(takeoverCalls,0)
  assert.equal(switchTabs.length,0)
  assert.equal(page.data.error,'已取消强制登录，原设备会话保持不变')
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
