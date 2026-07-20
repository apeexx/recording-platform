Page({
  data:{mode:'wechat',account:'',password:'',loading:false,error:''},
  onShow(){if(getApp().globalData.session.current()?.token)wx.switchTab({url:'/pages/tasks/index'})},
  switchMode(){this.setData({mode:this.data.mode==='wechat'?'account':'wechat',error:''})},
  input(e){this.setData({[e.currentTarget.dataset.field]:e.detail.value})},
  async wechatLogin(){await this.perform(()=>getApp().globalData.session.login())},
  async accountLogin(){if(!/^[1-9][0-9]{5,11}$/.test(this.data.account)){this.setData({error:'请输入 6–12 位非零开头数字账号'});return}await this.perform(()=>getApp().globalData.session.accountLogin(this.data.account,this.data.password))},
  async perform(action){
    if(this.data.loading)return
    this.setData({loading:true,error:''})
    try{
      await action()
      wx.switchTab({url:'/pages/tasks/index'})
    }catch(e){
      if(e.code==='ACCOUNT_IN_USE'&&e.details?.takeoverToken)await this.confirmTakeover(e.details.takeoverToken)
      else this.setData({error:this.loginError(e)})
    }finally{this.setData({loading:false})}
  },
  loginError(e){
    if(e.code==='INVALID_CREDENTIALS')return'账号或密码错误'
    if(e.code==='WECHAT_LOGIN_FAILED')return'微信登录失败，请稍后重试'
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
          this.setData({error:'已取消强制登录，原设备会话保持不变'})
          resolve()
          return
        }
        try{
          await getApp().globalData.session.takeover(takeoverToken)
          wx.switchTab({url:'/pages/tasks/index'})
        }catch(e){this.setData({error:this.loginError(e)})}
        resolve()
      }
    }))
  }
})
