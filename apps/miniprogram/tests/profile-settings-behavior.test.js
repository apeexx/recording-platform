const test=require('node:test')
const assert=require('node:assert/strict')
const fs=require('node:fs')
const path=require('node:path')
const vm=require('node:vm')

function loadPage({api={},profile={profileComplete:true,hasCustomAvatar:false},cachedProfile=null,profilePromise=null,onUpdateProfile=()=>{},onCompleteProfile=()=>{}}={}){
  const source=fs.readFileSync(path.resolve('pages/profile-settings/index.js'),'utf8')
  const toasts=[]
  let definition
  const apiStub={
    avatar:async()=>'/tmp/avatar.png',
    uploadAvatar:async()=>profile,
    changePassword:async()=>({}),
    ...api
  }
  const wx={
    chooseMedia(){},
    showToast(options){toasts.push(options)},
    showModal(){},
    reLaunch(){}
  }
  const session={
    current:()=>cachedProfile?{token:'token',user:cachedProfile}:null,
    refreshProfile:()=>profilePromise||Promise.resolve(profile),
    completeProfile:async body=>{onCompleteProfile(body);return profile},
    setName:async()=>profile,
    updateProfile:value=>{onUpdateProfile(value);return{...(cachedProfile||{}),...value}},
    clear(){},
  }
  const requireStub=()=>({success:title=>wx.showToast({title}),info:title=>wx.showToast({title,icon:'none'}),error:title=>wx.showToast({title,icon:'none'})})
  vm.runInNewContext(source,{Page:value=>{definition=value},wx,require:requireStub,getApp:()=>({globalData:{api:apiStub,session}})},{filename:'pages/profile-settings/index.js'})
  const page={...definition,data:{...definition.data},setData(patch){Object.assign(this.data,patch)}}
  return {page,toasts,wx}
}

test('微信头像选择只打开预览，不提前调用头像接口',()=>{
  let uploadCalls=0
  const {page}=loadPage({api:{uploadAvatar:async()=>{uploadCalls+=1}}})
  page.chooseAvatar({detail:{avatarUrl:'/tmp/wechat-avatar.png'}})
  assert.equal(page.data.avatarPreviewVisible,true)
  assert.equal(page.data.pendingAvatarPath,'/tmp/wechat-avatar.png')
  assert.equal(uploadCalls,0)
})

test('双击保存头像只发起一次上传',async()=>{
  let uploadCalls=0,resolveUpload
  const upload=new Promise(resolve=>{resolveUpload=resolve})
  const {page}=loadPage({api:{uploadAvatar:()=>{uploadCalls+=1;return upload}}})
  page.setData({avatarPreviewVisible:true,pendingAvatarPath:'/tmp/new-avatar.png'})
  const first=page.saveAvatar()
  const second=page.saveAvatar()
  assert.equal(uploadCalls,1)
  resolveUpload({profileComplete:true,hasCustomAvatar:true})
  await Promise.all([first,second])
  assert.equal(uploadCalls,1)
})

test('头像上传失败保留旧头像和预览，并显示中文 Toast',async()=>{
  const {page,toasts}=loadPage({api:{uploadAvatar:async()=>{throw new Error('internal detail')}}})
  page.setData({avatarSrc:'/tmp/old-avatar.png',avatarPreviewVisible:true,pendingAvatarPath:'/tmp/new-avatar.png'})
  await page.saveAvatar()
  assert.equal(page.data.avatarSrc,'/tmp/old-avatar.png')
  assert.equal(page.data.avatarPreviewVisible,true)
  assert.equal(page.data.pendingAvatarPath,'/tmp/new-avatar.png')
  assert.equal(toasts.length,1)
  assert.equal(toasts[0].icon,'none')
  assert.equal(toasts[0].title,'头像保存失败，请重试')
})

test('自定义头像读取失败静默回退默认头像',async()=>{
  const {page,toasts}=loadPage({profile:{profileComplete:true,hasCustomAvatar:true},api:{avatar:async()=>{throw new Error('missing')}}})
  page.setData({avatarSrc:'/tmp/old-avatar.png'})
  await page.load()
  assert.equal(page.data.avatarSrc,'/assets/icons/default-collector-avatar.svg')
  assert.equal('error' in page.data,false)
  assert.equal(toasts.length,0)
})

test('资料成功重新加载不维护底部错误状态',async()=>{
  const {page}=loadPage({profile:{profileComplete:true,hasCustomAvatar:false,name:'测试用户',account:'123456'}})
  page.setData({avatarSrc:'/tmp/old-avatar.png'})
  await page.load()
  assert.equal('error' in page.data,false)
  assert.equal(page.data.avatarSrc,'/assets/icons/default-collector-avatar.svg')
})

test('较慢的资料加载完成时不会产生底部错误状态',async()=>{
  let resolveProfile
  const pendingProfile=new Promise(resolve=>{resolveProfile=resolve})
  // 用延迟完成的 refreshProfile 模拟页面加载与用户操作并发。
  const source=fs.readFileSync(path.resolve('pages/profile-settings/index.js'),'utf8')
  let definition
  const wx={showToast(){},showModal(){},reLaunch(){}}
  vm.runInNewContext(source,{Page:value=>{definition=value},wx,require:()=>({success(){},info(){},error(){}}),getApp:()=>({globalData:{api:{avatar:async()=>'/tmp/avatar.png'},session:{refreshProfile:()=>pendingProfile}}})})
  const slowPage={...definition,data:{...definition.data},setData(patch){Object.assign(this.data,patch)}}
  const loading=slowPage.load()
  resolveProfile({profileComplete:true,hasCustomAvatar:false})
  await loading
  assert.equal('error' in slowPage.data,false)
})

test('没有自定义头像时不会请求头像文件',async()=>{
  let avatarCalls=0
  const {page}=loadPage({profile:{profileComplete:true,hasCustomAvatar:false},api:{avatar:async()=>{avatarCalls+=1;return '/tmp/avatar.png'}}})
  await page.load()
  assert.equal(avatarCalls,0)
})

test('只填写姓名即可完成资料设置',async()=>{
  let submitted
  const {page}=loadPage({profile:{profileComplete:false,hasCustomAvatar:false},onCompleteProfile:body=>{submitted=body}})
  page.setData({name:'张三',account:'',password:'',confirmPassword:''})
  await page.save()
  assert.equal(submitted.name,'张三')
  assert.deepEqual(Object.keys(submitted),['name'])
})

test('后补数字账号时必须同时填写有效账号和一致密码',async()=>{
  let calls=0
  const {page,toasts}=loadPage({profile:{profileComplete:true,hasCustomAvatar:false},onCompleteProfile:()=>{calls+=1}})
  page.setData({name:'张三',account:'123456',password:'',confirmPassword:''})
  await page.save()
  assert.equal(calls,0)
  assert.equal(toasts.at(-1).title,'登录密码至少需要 8 个字符')
})

test('资料设置页先展示当前会话缓存',()=>{
  const cached={userId:'MINI-1',name:'缓存姓名',account:'123456',profileComplete:true}
  const {page}=loadPage({cachedProfile:cached,profilePromise:new Promise(()=>{})})
  page.onShow()
  assert.equal(page.data.profile.name,'缓存姓名')
  assert.equal(page.data.account,'123456')
})

test('头像更新成功后同步会话资料缓存',async()=>{
  let cached
  const {page}=loadPage({
    profile:{profileComplete:true,hasCustomAvatar:true},
    onUpdateProfile:profile=>{cached=profile},
  })
  page.setData({pendingAvatarPath:'/tmp/new.png'})
  await page.saveAvatar()
  assert.equal(cached.hasCustomAvatar,true)
})
