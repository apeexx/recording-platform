Page({
  data:{loading:false,needsName:false,name:'',error:''},
  onShow(){const state=getApp().globalData.session.current();if(state?.token&&state.user?.name)wx.switchTab({url:'/pages/tasks/index'})},
  async login(){this.setData({loading:true,error:''});try{const user=await getApp().globalData.session.login();if(user.name)wx.switchTab({url:'/pages/tasks/index'});else this.setData({needsName:true})}catch(e){this.setData({error:e.message||'登录失败'})}finally{this.setData({loading:false})}},
  nameInput(e){this.setData({name:e.detail.value})},
  async saveName(){const name=this.data.name.trim();if(!name){this.setData({error:'请填写真实姓名'});return}this.setData({loading:true,error:''});try{await getApp().globalData.session.setName(name);wx.switchTab({url:'/pages/tasks/index'})}catch(e){this.setData({error:e.message||'保存失败'})}finally{this.setData({loading:false})}}
})
