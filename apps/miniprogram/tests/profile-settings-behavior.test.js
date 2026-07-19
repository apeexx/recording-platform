const test=require('node:test')
const assert=require('node:assert/strict')
const fs=require('node:fs')
const path=require('node:path')
const vm=require('node:vm')

function loadPage({api={},profile={profileComplete:true,hasCustomAvatar:false}}={}){
  const source=fs.readFileSync(path.resolve('pages/profile-settings/index.js'),'utf8')
  const toasts=[]
  let definition
  const apiStub={
    avatar:async()=>'/tmp/avatar.png',
    uploadAvatar:async()=>profile,
    deleteAvatar:async()=>profile,
    changePassword:async()=>({}),
    ...api
  }
  const wx={
    chooseMedia(){},
    showToast(options){toasts.push(options)},
    showModal(){},
    reLaunch(){}
  }
  const session={refreshProfile:async()=>profile,completeProfile:async()=>profile,setName:async()=>profile,clear(){}}
  vm.runInNewContext(source,{Page:value=>{definition=value},wx,getApp:()=>({globalData:{api:apiStub,session}})},{filename:'pages/profile-settings/index.js'})
  const page={...definition,data:{...definition.data},setData(patch){Object.assign(this.data,patch)}}
  return {page,toasts,wx}
}

test('微信头像选择只打开预览，不提前调用头像接口',()=>{
  let uploadCalls=0,deleteCalls=0
  const {page}=loadPage({api:{uploadAvatar:async()=>{uploadCalls+=1},deleteAvatar:async()=>{deleteCalls+=1}}})
  page.chooseAvatar({detail:{avatarUrl:'/tmp/wechat-avatar.png'}})
  assert.equal(page.data.avatarPreviewVisible,true)
  assert.equal(page.data.pendingAvatarMode,'upload')
  assert.equal(page.data.pendingAvatarPath,'/tmp/wechat-avatar.png')
  assert.equal(uploadCalls,0)
  assert.equal(deleteCalls,0)
})

test('双击保存头像只发起一次上传',async()=>{
  let uploadCalls=0,resolveUpload
  const upload=new Promise(resolve=>{resolveUpload=resolve})
  const {page}=loadPage({api:{uploadAvatar:()=>{uploadCalls+=1;return upload}}})
  page.setData({avatarPreviewVisible:true,pendingAvatarMode:'upload',pendingAvatarPath:'/tmp/new-avatar.png'})
  const first=page.saveAvatar()
  const second=page.saveAvatar()
  assert.equal(uploadCalls,1)
  resolveUpload({profileComplete:true,hasCustomAvatar:true})
  await Promise.all([first,second])
  assert.equal(uploadCalls,1)
})

test('头像上传失败保留旧头像和预览，并显示中文 Toast',async()=>{
  const {page,toasts}=loadPage({api:{uploadAvatar:async()=>{throw new Error('internal detail')}}})
  page.setData({avatarSrc:'/tmp/old-avatar.png',avatarPreviewVisible:true,pendingAvatarMode:'upload',pendingAvatarPath:'/tmp/new-avatar.png'})
  await page.saveAvatar()
  assert.equal(page.data.avatarSrc,'/tmp/old-avatar.png')
  assert.equal(page.data.avatarPreviewVisible,true)
  assert.equal(page.data.pendingAvatarPath,'/tmp/new-avatar.png')
  assert.equal(toasts.length,1)
  assert.equal(toasts[0].icon,'none')
  assert.equal(toasts[0].title,'头像保存失败，请重试')
})

test('相册选择失败显示可见中文 Toast',()=>{
  const {page,toasts,wx}=loadPage()
  wx.chooseMedia=options=>options.fail({errMsg:'chooseMedia:fail permission denied'})
  page.chooseFromAlbum()
  assert.equal(toasts.length,1)
  assert.equal(toasts[0].icon,'none')
  assert.equal(toasts[0].title,'图片选择失败，请重试')
})

test('自定义头像读取失败静默回退默认头像',async()=>{
  const {page,toasts}=loadPage({profile:{profileComplete:true,hasCustomAvatar:true},api:{avatar:async()=>{throw new Error('missing')}}})
  page.setData({avatarSrc:'/tmp/old-avatar.png'})
  await page.load()
  assert.equal(page.data.avatarSrc,'/assets/icons/default-collector-avatar.svg')
  assert.equal(page.data.error,'')
  assert.equal(toasts.length,0)
})
