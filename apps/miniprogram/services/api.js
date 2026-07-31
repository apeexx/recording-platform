const config = require('../config/index.js')
const { STORAGE_KEY } = require('./session.js')

function token() { return wx.getStorageSync(STORAGE_KEY)?.token || '' }
function operationId(prefix='mp') { return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}` }
function responseData(response) {
  if (typeof response.data !== 'string') return response.data || {}
  try { return JSON.parse(response.data || '{}') } catch (_) { return {} }
}

function parseError(response) {
  const data=responseData(response)
  const error=new Error(data.message||`请求失败（HTTP ${response.statusCode}）`)
  error.code=data.code||`HTTP_${response.statusCode}`
  error.status=response.statusCode
  error.details=data.details
  return error
}

function requestError(response, authenticated) {
  const error=parseError(response)
  if (authenticated && response.statusCode===401 && error.code==='SESSION_REPLACED') wx.removeStorageSync(STORAGE_KEY)
  return error
}

function request(path, options={}) {
  const authenticated=!options.noAuth
  const authorization=authenticated&&token()?{Authorization:`Bearer ${token()}`} : {}
  return new Promise((resolve,reject)=>wx.request({
    url:`${config.apiBaseUrl}${path}`,
    method:options.method||'GET',
    data:options.data,
    header:{...authorization,...(options.idempotencyKey?{'Idempotency-Key':options.idempotencyKey}:{}),...(options.header||{})},
    success:res=>res.statusCode>=200&&res.statusCode<300?resolve(res.data):reject(requestError(res,authenticated)),
    fail:reason=>{const error=new Error(reason.errMsg||'网络请求失败');error.network=true;reject(error)}
  }))
}

function downloadMedia(mediaId) {
  if (!mediaId) return Promise.resolve('')
  return new Promise((resolve,reject)=>wx.downloadFile({
    url:`${config.apiBaseUrl}/api/media/${encodeURIComponent(mediaId)}`,
    header:{Authorization:`Bearer ${token()}`},
    success:res=>res.statusCode>=200&&res.statusCode<300?resolve(res.tempFilePath):reject(requestError(res,true)),
    fail:reason=>{const error=new Error(reason.errMsg||'媒体下载失败');error.network=true;reject(error)}
  }))
}

function referenceMediaUrl(mediaId) {
  if (!mediaId) return ''
  return `${config.apiBaseUrl}/api/media/public/reference/${encodeURIComponent(mediaId)}`
}

function uploadSubmission(itemId, payload) {
  const submission={
    operationId:payload.operationId,
    assignmentId:payload.assignmentId,
    expectedRevision:payload.expectedRevision,
    text:payload.text||'',
    referenceAudioDurationMillis:Number(payload.referenceAudioDurationMillis)||0,
    referenceVideoDurationMillis:Number(payload.referenceVideoDurationMillis)||0,
  }
  if (!payload.audioPath) return request(`/api/task-items/${encodeURIComponent(itemId)}/submit`,{
    method:'POST',data:submission,header:{'Content-Type':'application/json'},
  })
  const formData=Object.fromEntries(Object.entries(submission).map(([key,value])=>[key,String(value)]))
  return new Promise((resolve,reject)=>wx.uploadFile({
    url:`${config.apiBaseUrl}/api/task-items/${encodeURIComponent(itemId)}/submit`,filePath:payload.audioPath,name:'audio',formData,
    header:{Authorization:`Bearer ${token()}`},
    success:res=>res.statusCode>=200&&res.statusCode<300?resolve(responseData(res)):reject(requestError(res,true)),
    fail:reason=>{const error=new Error(reason.errMsg||'上传失败');error.network=true;reject(error)}
  }))
}

function reportRangeQuery(value) {
  if (!value) return ''
  if (typeof value === 'string') return value ? `date=${encodeURIComponent(value)}` : ''
  const parts = []
  if (value.fromDate) parts.push(`fromDate=${encodeURIComponent(value.fromDate)}`)
  if (value.toDate) parts.push(`toDate=${encodeURIComponent(value.toDate)}`)
  return parts.join('&')
}

module.exports = {
  operationId,
  login: body=>request('/api/auth/miniprogram/login',{method:'POST',data:body}),
  accountLogin: body=>request('/api/auth/miniprogram/account-login',{method:'POST',data:body}),
  takeover: takeoverToken=>request('/api/auth/miniprogram/takeover',{method:'POST',data:{takeoverToken},noAuth:true}),
  profile: ()=>request('/api/auth/miniprogram/profile'),
  completeProfile: body=>request('/api/auth/miniprogram/profile/complete',{method:'POST',data:body}),
  setName: name=>request('/api/auth/miniprogram/name',{method:'PUT',data:{name}}),
  changePassword: body=>request('/api/auth/miniprogram/password',{method:'PUT',data:body}),
  uploadAvatar: filePath=>new Promise((resolve,reject)=>wx.uploadFile({
    url:`${config.apiBaseUrl}/api/auth/miniprogram/avatar`,
    filePath,
    name:'avatar',
    header:{Authorization:`Bearer ${token()}`},
    success:res=>res.statusCode>=200&&res.statusCode<300?resolve(responseData(res)):reject(requestError(res,true)),
    fail:reject
  })),
  avatar: ()=>downloadProtected('/api/auth/miniprogram/avatar'),
  tasks: ()=>request('/api/tasks?page=0&size=100'),
  task: id=>request(`/api/tasks/${encodeURIComponent(id)}`),
  requestAccess: id=>request(`/api/tasks/${encodeURIComponent(id)}/access-requests`,{method:'POST',idempotencyKey:operationId('access')}),
  start: (id, idempotencyKey=operationId('claim'))=>request(`/api/tasks/${encodeURIComponent(id)}/items/start`,{method:'POST',idempotencyKey}),
  item: id=>request(`/api/task-items/${encodeURIComponent(id)}`),
	myWork: ({taskId='',kind='ALL',page=0,size=20}={})=>request(`/api/task-items/mine?taskId=${encodeURIComponent(taskId)}&kind=${encodeURIComponent(kind)}&page=${page}&size=${size}`),
  media: downloadMedia,
  referenceMediaUrl,
  submit: uploadSubmission,
  release: (id,revision,op,note='')=>request(`/api/task-items/${encodeURIComponent(id)}/release`,{method:'POST',data:{operationId:op,expectedRevision:revision,note}}),
  discard: (id,revision,op,reason)=>request(`/api/task-items/${encodeURIComponent(id)}/discard`,{method:'POST',data:{operationId:op,expectedRevision:revision,reason}}),
  restore: (id,revision,op)=>request(`/api/task-items/${encodeURIComponent(id)}/restore`,{method:'POST',data:{operationId:op,expectedRevision:revision}}),
  reportTasks: ()=>request('/api/reports/me/tasks'),
  taskReport: (id,range='')=>{
    const query=reportRangeQuery(range)
    return request(`/api/reports/me/tasks/${encodeURIComponent(id)}${query?`?${query}`:''}`)
  },
  taskSubmissions: (id,page=0,size=20,range='')=>{
    const query=reportRangeQuery(range)
    return request(`/api/reports/me/tasks/${encodeURIComponent(id)}/submissions?page=${page}&size=${size}${query?`&${query}`:''}`)
  },
  taskCompletions: (id,page=0,size=5,range='')=>{
    const query=reportRangeQuery(range)
    return request(`/api/reports/me/tasks/${encodeURIComponent(id)}/completions?page=${page}&size=${size}${query?`&${query}`:''}`)
  },
  myReport: ()=>request('/api/reports/me'),
  mySubmissions: (page=0)=>request(`/api/reports/me/submissions?page=${page}&size=5`)
}

function downloadProtected(path) {
  return new Promise((resolve,reject)=>wx.downloadFile({
    url:`${config.apiBaseUrl}${path}`,
    header:{Authorization:`Bearer ${token()}`},
    success:res=>res.statusCode>=200&&res.statusCode<300?resolve(res.tempFilePath):reject(requestError(res,true)),
    fail:reject
  }))
}
