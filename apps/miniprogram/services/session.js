const STORAGE_KEY = 'recSession'

function createSessionService({ wx, api }) {
  function current() { return wx.getStorageSync(STORAGE_KEY) || null }
  function save(token, user) { const value={token,user};wx.setStorageSync(STORAGE_KEY,value);return value }
  function login() {
    return new Promise((resolve, reject) => wx.login({
      success: async ({ code }) => { try { const result=await api.login({code});save(result.token,result);resolve(result) } catch(e){reject(e)} },
      fail: reject
    }))
  }
  async function setName(name) { const user=await api.setName(name);const state=current();save(state.token,{...state.user,...user,userId:user.id||state.user.userId});return user }
  function clear(){wx.removeStorageSync(STORAGE_KEY)}
  return { current, login, setName, clear }
}

module.exports = { STORAGE_KEY, createSessionService }
