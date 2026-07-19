const config = require('../config/index.js')
const { STORAGE_KEY } = require('./session.js')

function token() { return wx.getStorageSync(STORAGE_KEY)?.token || '' }
function operationId(prefix='mp') { return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}` }
function parseError(response) { const data=response.data||{};const error=new Error(data.message||`请求失败（HTTP ${response.statusCode}）`);error.code=data.code||`HTTP_${response.statusCode}`;error.status=response.statusCode;error.details=data.details;return error }

function request(path, options={}) {
  return new Promise((resolve,reject)=>wx.request({
    url:`${config.apiBaseUrl}${path}`,method:options.method||'GET',data:options.data,
    header:{...(token()?{Authorization:`Bearer ${token()}`} : {}),...(options.idempotencyKey?{'Idempotency-Key':options.idempotencyKey}:{}),...(options.header||{})},
    success:res=>res.statusCode>=200&&res.statusCode<300?resolve(res.data):reject(parseError(res)),
    fail:reason=>{const error=new Error(reason.errMsg||'网络请求失败');error.network=true;reject(error)}
  }))
}

function downloadMedia(mediaId) {
  if (!mediaId) return Promise.resolve('')
  return new Promise((resolve,reject)=>wx.downloadFile({
    url:`${config.apiBaseUrl}/api/media/${encodeURIComponent(mediaId)}`,
    header:{Authorization:`Bearer ${token()}`},
    success:res=>res.statusCode>=200&&res.statusCode<300?resolve(res.tempFilePath):reject(parseError({...res,data:{}})),
    fail:reason=>{const error=new Error(reason.errMsg||'媒体下载失败');error.network=true;reject(error)}
  }))
}

function uploadSubmission(itemId, payload) {
  const formData={operationId:payload.operationId,assignmentId:payload.assignmentId,expectedRevision:String(payload.expectedRevision),text:payload.text||''}
  if (!payload.audioPath) return multipartTextSubmit(itemId,formData)
  return new Promise((resolve,reject)=>wx.uploadFile({
    url:`${config.apiBaseUrl}/api/task-items/${encodeURIComponent(itemId)}/submit`,filePath:payload.audioPath,name:'audio',formData,
    header:{Authorization:`Bearer ${token()}`},
    success:res=>{let data={};try{data=JSON.parse(res.data||'{}')}catch(_){data={}};res.statusCode>=200&&res.statusCode<300?resolve(data):reject(parseError({...res,data}))},
    fail:reason=>{const error=new Error(reason.errMsg||'上传失败');error.network=true;reject(error)}
  }))
}

function multipartTextSubmit(itemId, fields) {
  const boundary=`----RecordingPlatform${Date.now()}`
  const body=Object.entries(fields).map(([key,value])=>`--${boundary}\r\nContent-Disposition: form-data; name="${key}"\r\n\r\n${value}\r\n`).join('')+`--${boundary}--\r\n`
  return request(`/api/task-items/${encodeURIComponent(itemId)}/submit`,{method:'POST',data:body,header:{'Content-Type':`multipart/form-data; boundary=${boundary}`}})
}

module.exports = {
  operationId,
  login: body=>request('/api/auth/miniprogram/login',{method:'POST',data:body}),
  accountLogin: body=>request('/api/auth/miniprogram/account-login',{method:'POST',data:body}),
  profile: ()=>request('/api/auth/miniprogram/profile'),
  completeProfile: body=>request('/api/auth/miniprogram/profile/complete',{method:'POST',data:body}),
  setName: name=>request('/api/auth/miniprogram/name',{method:'PUT',data:{name}}),
  changePassword: body=>request('/api/auth/miniprogram/password',{method:'PUT',data:body}),
  uploadAvatar: filePath=>new Promise((resolve,reject)=>wx.uploadFile({url:`${config.apiBaseUrl}/api/auth/miniprogram/avatar`,filePath,name:'avatar',header:{Authorization:`Bearer ${token()}`},success:res=>{let data={};try{data=JSON.parse(res.data||'{}')}catch(_){};res.statusCode>=200&&res.statusCode<300?resolve(data):reject(parseError({...res,data}))},fail:reject})),
  avatar: ()=>downloadProtected('/api/auth/miniprogram/avatar'),
  deleteAvatar: ()=>request('/api/auth/miniprogram/avatar',{method:'DELETE'}),
  tasks: ()=>request('/api/tasks?page=0&size=100'),
  task: id=>request(`/api/tasks/${encodeURIComponent(id)}`),
  versions: id=>request(`/api/tasks/${encodeURIComponent(id)}/versions`),
  requestAccess: id=>request(`/api/tasks/${encodeURIComponent(id)}/access-requests`,{method:'POST',idempotencyKey:operationId('access')}),
  start: id=>request(`/api/tasks/${encodeURIComponent(id)}/items/start`,{method:'POST',idempotencyKey:operationId('claim')}),
  item: id=>request(`/api/task-items/${encodeURIComponent(id)}`),
	myWork: ({taskId='',kind='ALL',page=0,size=20}={})=>request(`/api/task-items/mine?taskId=${encodeURIComponent(taskId)}&kind=${encodeURIComponent(kind)}&page=${page}&size=${size}`),
  media: downloadMedia,
  submit: uploadSubmission,
  release: (id,revision,op)=>request(`/api/task-items/${encodeURIComponent(id)}/release`,{method:'POST',data:{operationId:op,expectedRevision:revision}}),
  myReport: ()=>request('/api/reports/me'),
  mySubmissions: (page=0)=>request(`/api/reports/me/submissions?page=${page}&size=20`)
}

function downloadProtected(path) {
  return new Promise((resolve,reject)=>wx.downloadFile({url:`${config.apiBaseUrl}${path}`,header:{Authorization:`Bearer ${token()}`},success:res=>res.statusCode>=200&&res.statusCode<300?resolve(res.tempFilePath):reject(parseError({...res,data:{}})),fail:reject}))
}
