const feedback=require('./feedback.js')

function promptForProfile(){
  return new Promise(resolve=>wx.showModal({title:'请先完善个人资料',content:'设置姓名、数字账号和密码后，才可以申请权限或处理录音任务。',confirmText:'去设置',success:result=>{if(result.confirm)wx.navigateTo({url:'/pages/profile-settings/index'});resolve(false)}}))
}

async function requireCompleteProfile(app) {
  const session=app.globalData.session
  if(session.current()?.user?.profileComplete){
    session.refreshProfile().catch(()=>{})
    return true
  }
  try{
    const profile=await session.refreshProfile()
    if(profile.profileComplete)return true
    return promptForProfile()
  }catch(error){
    if(error.status===401)throw error
    feedback.error('网络不可用，请稍后重试')
    return false
  }
}
module.exports={requireCompleteProfile}
