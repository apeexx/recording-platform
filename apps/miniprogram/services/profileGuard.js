async function requireCompleteProfile(app) {
  try {
    const profile=await app.globalData.session.refreshProfile()
    if(profile.profileComplete)return true
  }catch(error){if(error.status===401)throw error}
  return new Promise(resolve=>wx.showModal({title:'请先完善个人资料',content:'设置姓名、数字账号和密码后，才可以申请权限或处理录音任务。',confirmText:'去设置',success:result=>{if(result.confirm)wx.navigateTo({url:'/pages/profile-settings/index'});resolve(false)}}))
}
module.exports={requireCompleteProfile}
