const STORAGE_KEY = 'recSession'

function createSessionService({ wx, api }) {
  function current() { return wx.getStorageSync(STORAGE_KEY) || null }
  function save(token, user) { const value={token,user};wx.setStorageSync(STORAGE_KEY,value);return value }
  function updateProfile(profile) {
    const state=current()
    if(!state?.token)return profile
    const user={...(state.user||{}),...profile,userId:profile.userId||profile.id||state.user?.userId}
    save(state.token,user)
    return user
  }
  function login() {
    return new Promise((resolve, reject) => wx.login({
      success: async ({ code }) => { try { const result=await api.login({code});save(result.token,result);resolve(result) } catch(e){reject(e)} },
      fail: reject
    }))
  }
	async function accountLogin(account,password){const result=await api.accountLogin({account,password});save(result.token,result);return result}
	async function takeover(takeoverToken){const result=await api.takeover(takeoverToken);save(result.token,result);return result}
	async function refreshProfile(){return updateProfile(await api.profile())}
	async function completeProfile(body){return updateProfile(await api.completeProfile(body))}
  async function setName(name) { return updateProfile(await api.setName(name)) }
  function clear(){wx.removeStorageSync(STORAGE_KEY)}
  return { current, login, accountLogin, takeover, refreshProfile, completeProfile, setName, updateProfile, clear }
}

module.exports = { STORAGE_KEY, createSessionService }
