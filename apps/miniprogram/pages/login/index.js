const feedback=require('../../services/feedback.js')
Page({
  data:{mode:'wechat',account:'',password:'',loading:false,invitationVisible:false,invitationCode:'',invitationFocus:false},
  onShow(){if(getApp().globalData.session.current()?.token)wx.switchTab({url:'/pages/tasks/index'})},
  switchMode(){this.setData({mode:this.data.mode==='wechat'?'account':'wechat'})},
  input(e){this.setData({[e.currentTarget.dataset.field]:e.detail.value})},
  async wechatLogin(){await this.perform(()=>getApp().globalData.session.login(),true)},
  async accountLogin(){if(!/^[1-9][0-9]{5,11}$/.test(this.data.account)){feedback.error('请输入 6–12 位非零开头数字账号');return}await this.perform(()=>getApp().globalData.session.accountLogin(this.data.account,this.data.password))},
  async submitInvitation(){
    const code=(this.data.invitationCode||'').trim()
    if(!code){feedback.error('请输入邀请码');this.setData({invitationFocus:true});return}
    await this.perform(()=>getApp().globalData.session.login(code))
  },
  closeInvitation(){if(!this.data.loading)this.setData({invitationVisible:false,invitationCode:'',invitationFocus:false})},
  noop(){},
  async perform(action,allowInvitationPrompt=false){
    if(this.data.loading)return
    this.setData({loading:true})
    try{
      await action()
      wx.switchTab({url:'/pages/tasks/index'})
    }catch(e){
      if(allowInvitationPrompt&&e.code==='INVITATION_REQUIRED')this.setData({invitationVisible:true,invitationCode:'',invitationFocus:true})
      else if(e.code==='ACCOUNT_IN_USE'&&e.details?.takeoverToken)await this.confirmTakeover(e.details.takeoverToken)
      else feedback.error(this.loginError(e))
    }finally{this.setData({loading:false})}
  },
  loginError(e){
    if(e.code==='INVALID_CREDENTIALS')return'账号或密码错误'
    if(e.code==='WECHAT_LOGIN_FAILED')return'微信登录失败，请稍后重试'
    if(e.code==='INVITATION_CODE_INVALID')return'邀请码无效或已失效，请联系管理员'
    if(e.code==='SESSION_REPLACED')return'当前登录已在其他设备接管'
    return e.message||'登录失败'
  },
  confirmTakeover(takeoverToken){
    return new Promise(resolve=>wx.showModal({
      title:'账号已在其他设备登录',
      content:'强制登录将使原设备退出，是否继续？',
      confirmText:'强制登录',
      confirmColor:'#c2413b',
      success:async result=>{
        if(!result.confirm){
          feedback.info('已取消强制登录')
          resolve()
          return
        }
        try{
          await getApp().globalData.session.takeover(takeoverToken)
          wx.switchTab({url:'/pages/tasks/index'})
        }catch(e){feedback.error(this.loginError(e))}
        resolve()
      }
    }))
  }
})
