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

test('自定义头像通过鉴权上传到后端持久化', async () => {
  let captured
  const api=loadApi({getStorageSync:()=>({token:'opaque-token'}),uploadFile:options=>{captured=options;options.success({statusCode:200,data:'{"hasCustomAvatar":true}'})}})
  const result=await api.uploadAvatar('wxfile://avatar.png')
  assert.equal(captured.name,'avatar');assert.equal(captured.header.Authorization,'Bearer opaque-token');assert.equal(result.hasCustomAvatar,true)
})
