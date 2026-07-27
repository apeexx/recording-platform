Page({
  data:{user:{},avatarSrc:'/assets/icons/default-collector-avatar.svg'},
  onShow(){const state=getApp().globalData.session.current();if(!state?.token){wx.reLaunch({url:'/pages/login/index'});return}this.setData({user:state.user,avatarSrc:'/assets/icons/default-collector-avatar.svg'});getApp().globalData.session.refreshProfile().then(user=>{this.setData({user});if(user.hasCustomAvatar)getApp().globalData.api.avatar().then(avatarSrc=>this.setData({avatarSrc})).catch(()=>{})}).catch(()=>{})},
  openSettings(){wx.navigateTo({url:'/pages/profile-settings/index'})},
  logout(){getApp().globalData.session.clear();wx.reLaunch({url:'/pages/login/index'})}
})
