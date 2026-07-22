const test = require('node:test')
const assert = require('node:assert/strict')

function loadApi(wxMock) {
  global.wx = wxMock
  for (const modulePath of ['../services/api.js','../config/index.js','../services/session.js']) {
    delete require.cache[require.resolve(modulePath)]
  }
  return require('../services/api.js')
}

test('任务请求携带本地不透明 Bearer token', async () => {
  let captured
  const api = loadApi({
    getStorageSync: () => ({token:'opaque-token'}),
    request: options => { captured=options;options.success({statusCode:200,data:{items:[]}}) }
  })
  await api.tasks()
  assert.equal(captured.header.Authorization, 'Bearer opaque-token')
  assert.match(captured.url, /\/api\/tasks/)
})

test('权限申请携带唯一幂等键', async () => {
  let captured
  const api = loadApi({
    getStorageSync: () => ({token:'opaque-token'}),
    request: options => { captured=options;options.success({statusCode:201,data:{status:'PENDING'}}) }
  })
  await api.requestAccess('task-1')
  assert.match(captured.header['Idempotency-Key'], /^access-/)
})

test('鉴权媒体先下载为临时文件再交给播放器', async () => {
  let captured
  const api = loadApi({
    getStorageSync: () => ({token:'opaque-token'}),
    downloadFile: options => { captured=options;options.success({statusCode:200,tempFilePath:'wxfile://media'}) }
  })
  assert.equal(await api.media('media-1'), 'wxfile://media')
  assert.equal(captured.header.Authorization, 'Bearer opaque-token')
})

test('历史参考媒体生成无需鉴权头的公网播放地址', () => {
  const api = loadApi({getStorageSync: () => ({token:'opaque-token'})})
  assert.match(
    api.referenceMediaUrl('media/audio'),
    /^https?:\/\/[^/]+\/api\/media\/public\/reference\/media%2Faudio$/
  )
})

test('自定义头像通过鉴权上传到后端持久化', async () => {
  let captured
  const api=loadApi({getStorageSync:()=>({token:'opaque-token'}),uploadFile:options=>{captured=options;options.success({statusCode:200,data:'{"hasCustomAvatar":true}'})}})
  const result=await api.uploadAvatar('wxfile://avatar.png')
  assert.equal(captured.name,'avatar');assert.equal(captured.header.Authorization,'Bearer opaque-token');assert.equal(result.hasCustomAvatar,true)
})

test('小程序接管使用公开 takeover 接口且不携带旧 Bearer token',async()=>{
  let captured
  const api=loadApi({getStorageSync:()=>({token:'old-token'}),request:options=>{captured=options;options.success({statusCode:200,data:{token:'new-token',userId:'MINI-0123456789abcdef01234567'}})}})
  await api.takeover('takeover-token')
  assert.match(captured.url,/\/api\/auth\/miniprogram\/takeover$/)
  assert.equal(captured.method,'POST')
  assert.deepEqual(captured.data,{takeoverToken:'takeover-token'})
  assert.equal(captured.header.Authorization,undefined)
})

test('受保护请求收到 SESSION_REPLACED 时清除本地会话',async()=>{
  let removed
  const api=loadApi({getStorageSync:()=>({token:'old-token'}),removeStorageSync:key=>{removed=key},request:options=>options.success({statusCode:401,data:{code:'SESSION_REPLACED',message:'会话已被接管'}})})
  await assert.rejects(api.tasks(),error=>error.code==='SESSION_REPLACED')
  assert.equal(removed,'recSession')
})

test('公开接管请求即使返回 SESSION_REPLACED 也保留本地会话',async()=>{
  let removed
  const api=loadApi({getStorageSync:()=>({token:'old-token'}),removeStorageSync:key=>{removed=key},request:options=>options.success({statusCode:401,data:{code:'SESSION_REPLACED',message:'接管凭证失效'}})})
  await assert.rejects(api.takeover('expired-takeover-token'),error=>error.code==='SESSION_REPLACED')
  assert.equal(removed,undefined)
})

test('受保护普通请求仅在可信的 401 SESSION_REPLACED 时清除会话',async()=>{
  for(const response of [
    {statusCode:403,data:{code:'SESSION_REPLACED',message:'不是 401'}},
    {statusCode:401,data:{code:'ACCESS_DENIED',message:'不是会话接管'}}
  ]){
    let removed
    const api=loadApi({getStorageSync:()=>({token:'old-token'}),removeStorageSync:key=>{removed=key},request:options=>options.success(response)})
    await assert.rejects(api.tasks())
    assert.equal(removed,undefined)
  }
})

for(const directPath of [
  {
    name:'媒体下载',
    invoke:api=>api.media('media-1'),
    wx:response=>({downloadFile:options=>options.success(response)})
  },
  {
    name:'录音上传',
    invoke:api=>api.submit('item-1',{operationId:'op-1',assignmentId:'assignment-1',expectedRevision:1,audioPath:'wxfile://audio.mp3'}),
    wx:response=>({uploadFile:options=>options.success({...response,data:JSON.stringify(response.data)})})
  },
  {
    name:'头像上传',
    invoke:api=>api.uploadAvatar('wxfile://avatar.png'),
    wx:response=>({uploadFile:options=>options.success({...response,data:JSON.stringify(response.data)})})
  },
  {
    name:'受保护头像下载',
    invoke:api=>api.avatar(),
    wx:response=>({downloadFile:options=>options.success(response)})
  }
]){
  test(`${directPath.name}在可信 401 SESSION_REPLACED 时清除本地会话`,async()=>{
    let removed
    const api=loadApi({getStorageSync:()=>({token:'old-token'}),removeStorageSync:key=>{removed=key},...directPath.wx({statusCode:401,data:{code:'SESSION_REPLACED',message:'会话已被接管'}})})
    await assert.rejects(directPath.invoke(api),error=>error.code==='SESSION_REPLACED')
    assert.equal(removed,'recSession')
  })

  test(`${directPath.name}在其他失败时保留本地会话`,async()=>{
    let removed
    const api=loadApi({getStorageSync:()=>({token:'old-token'}),removeStorageSync:key=>{removed=key},...directPath.wx({statusCode:403,data:{code:'SESSION_REPLACED',message:'不是可信会话失效'}})})
    await assert.rejects(directPath.invoke(api),error=>error.code==='SESSION_REPLACED')
    assert.equal(removed,undefined)
  })
}
