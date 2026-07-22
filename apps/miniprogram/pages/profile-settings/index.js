const DEFAULT_AVATAR='/assets/icons/default-collector-avatar.svg'
const feedback=require('../../services/feedback.js')

Page({
  data:{profile:{},avatarSrc:DEFAULT_AVATAR,avatarPreviewVisible:false,pendingAvatarPath:'',pendingAvatarMode:'',avatarSaving:false,name:'',account:'',password:'',confirmPassword:'',currentPassword:'',newPassword:''},
  onShow(){this.applyProfile(getApp().globalData.session.current()?.user);this.load()},
  input(e){this.setData({[e.currentTarget.dataset.field]:e.detail.value})},
  copyUserId(){const{copyUserId}=require('../../services/userIdClipboard.js');copyUserId(this.data.profile?.userId)},
  applyProfile(profile){if(profile)this.setData({profile,name:profile.name||'',account:profile.account||''})},
  async load(){
    try{
      const profile=await getApp().globalData.session.refreshProfile()
      this.applyProfile(profile)
      if(!profile.hasCustomAvatar)return this.setData({avatarSrc:DEFAULT_AVATAR})
      try{this.setData({avatarSrc:await getApp().globalData.api.avatar()})}catch(_){this.setData({avatarSrc:DEFAULT_AVATAR})}
    }catch(e){feedback.error(e.message||'资料加载失败')}
  },
  async save(){const name=this.data.name.trim();if(!name)return feedback.error('请填写姓名');try{if(!this.data.profile.profileComplete){if(this.data.password!==this.data.confirmPassword)return feedback.error('两次输入的密码不一致');await getApp().globalData.session.completeProfile({name,account:this.data.account,password:this.data.password});feedback.success('资料已完成')}else{await getApp().globalData.session.setName(name);feedback.success('姓名已更新')}await this.load()}catch(e){feedback.error(e.message||'资料保存失败')}},
  async changePassword(){try{await getApp().globalData.api.changePassword({currentPassword:this.data.currentPassword,newPassword:this.data.newPassword});getApp().globalData.session.clear();wx.showModal({title:'密码已修改',content:'请使用新密码重新登录',showCancel:false,success:()=>wx.reLaunch({url:'/pages/login/index'})})}catch(e){feedback.error(e.message||'密码修改失败')}},
  preventClose(){},
  chooseAvatar(e){if(e.detail.avatarUrl)this.openAvatarPreview('upload',e.detail.avatarUrl)},
  previewDefaultAvatar(){this.openAvatarPreview('default','')},
  openAvatarPreview(mode,path){this.setData({avatarPreviewVisible:true,pendingAvatarMode:mode,pendingAvatarPath:path})},
  closeAvatarPreview(){if(!this.data.avatarSaving)this.setData({avatarPreviewVisible:false,pendingAvatarMode:'',pendingAvatarPath:''})},
  async saveAvatar(){if(this.data.avatarSaving)return
    const {pendingAvatarMode,pendingAvatarPath}=this.data
    if(!pendingAvatarMode)return
    this.setData({avatarSaving:true})
    try{
      const profile=pendingAvatarMode==='default'?await getApp().globalData.api.deleteAvatar():await getApp().globalData.api.uploadAvatar(pendingAvatarPath)
      const cachedProfile=getApp().globalData.session.updateProfile(profile)
      this.setData({profile:cachedProfile,avatarSrc:pendingAvatarMode==='default'?DEFAULT_AVATAR:pendingAvatarPath,avatarPreviewVisible:false,pendingAvatarMode:'',pendingAvatarPath:''})
      feedback.success(pendingAvatarMode==='default'?'已恢复默认头像':'头像已更新')
    }catch(e){feedback.error('头像保存失败，请重试')}finally{this.setData({avatarSaving:false})}
  }
})
