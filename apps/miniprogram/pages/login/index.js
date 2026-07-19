Page({
  data:{mode:'wechat',account:'',password:'',loading:false,error:''},
  onShow(){if(getApp().globalData.session.current()?.token)wx.switchTab({url:'/pages/tasks/index'})},
  switchMode(){this.setData({mode:this.data.mode==='wechat'?'account':'wechat',error:''})},
  input(e){this.setData({[e.currentTarget.dataset.field]:e.detail.value})},
  async wechatLogin(){await this.perform(()=>getApp().globalData.session.login())},
  async accountLogin(){if(!/^[1-9][0-9]{5,11}$/.test(this.data.account)){this.setData({error:'请输入 6–12 位非零开头数字账号'});return}await this.perform(()=>getApp().globalData.session.accountLogin(this.data.account,this.data.password))},
  async perform(action){this.setData({loading:true,error:''});try{await action();wx.switchTab({url:'/pages/tasks/index'})}catch(e){this.setData({error:e.message||'登录失败'})}finally{this.setData({loading:false})}}
})
