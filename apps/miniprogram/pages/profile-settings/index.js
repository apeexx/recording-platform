const DEFAULT_AVATAR='/assets/icons/default-collector-avatar.svg'

Page({
  data:{profile:{},avatarSrc:DEFAULT_AVATAR,avatarSheetVisible:false,avatarPreviewVisible:false,pendingAvatarPath:'',pendingAvatarMode:'',avatarSaving:false,name:'',account:'',password:'',confirmPassword:'',currentPassword:'',newPassword:'',error:''},
  onShow(){this.load()},
  input(e){this.setData({[e.currentTarget.dataset.field]:e.detail.value})},
  async load(){
    try{
      const profile=await getApp().globalData.session.refreshProfile()
      this.setData({profile,name:profile.name||'',account:profile.account||''})
      if(!profile.hasCustomAvatar)return this.setData({avatarSrc:DEFAULT_AVATAR})
      try{this.setData({avatarSrc:await getApp().globalData.api.avatar()})}catch(_){this.setData({avatarSrc:DEFAULT_AVATAR})}
    }catch(e){this.setData({error:e.message})}
  },
  async save(){const name=this.data.name.trim();if(!name)return this.setData({error:'请填写姓名'});try{if(!this.data.profile.profileComplete){if(this.data.password!==this.data.confirmPassword)return this.setData({error:'两次输入的密码不一致'});await getApp().globalData.session.completeProfile({name,account:this.data.account,password:this.data.password});wx.showToast({title:'资料已完成'})}else{await getApp().globalData.session.setName(name);wx.showToast({title:'姓名已更新'})}await this.load()}catch(e){this.setData({error:e.message})}},
  async changePassword(){try{await getApp().globalData.api.changePassword({currentPassword:this.data.currentPassword,newPassword:this.data.newPassword});getApp().globalData.session.clear();wx.showModal({title:'密码已修改',content:'请使用新密码重新登录',showCancel:false,success:()=>wx.reLaunch({url:'/pages/login/index'})})}catch(e){this.setData({error:e.message})}},
  openAvatarSheet(){this.setData({avatarSheetVisible:true})},
  closeAvatarSheet(){this.setData({avatarSheetVisible:false})},
  preventClose(){},
  chooseAvatar(e){if(e.detail.avatarUrl)this.openAvatarPreview('upload',e.detail.avatarUrl)},
  chooseFromAlbum(){wx.chooseMedia({count:1,mediaType:['image'],sourceType:['album'],sizeType:['compressed'],success:result=>{const file=result.tempFiles&&result.tempFiles[0];if(file&&file.tempFilePath)this.openAvatarPreview('upload',file.tempFilePath)},fail:reason=>{if(reason&&reason.errMsg&&reason.errMsg.indexOf('cancel')===-1){this.setData({error:'图片选择失败，请重试'});wx.showToast({icon:'none',title:'图片选择失败，请重试'})}}})},
  previewDefaultAvatar(){this.openAvatarPreview('default','')},
  openAvatarPreview(mode,path){this.setData({avatarSheetVisible:false,avatarPreviewVisible:true,pendingAvatarMode:mode,pendingAvatarPath:path})},
  closeAvatarPreview(){if(!this.data.avatarSaving)this.setData({avatarPreviewVisible:false,pendingAvatarMode:'',pendingAvatarPath:''})},
  async saveAvatar(){if(this.data.avatarSaving)return
    const {pendingAvatarMode,pendingAvatarPath}=this.data
    if(!pendingAvatarMode)return
    this.setData({avatarSaving:true,error:''})
    try{
      const profile=pendingAvatarMode==='default'?await getApp().globalData.api.deleteAvatar():await getApp().globalData.api.uploadAvatar(pendingAvatarPath)
      this.setData({profile,avatarSrc:pendingAvatarMode==='default'?DEFAULT_AVATAR:pendingAvatarPath,avatarPreviewVisible:false,pendingAvatarMode:'',pendingAvatarPath:''})
      wx.showToast({title:pendingAvatarMode==='default'?'已恢复默认头像':'头像已更新'})
    }catch(e){this.setData({error:'头像保存失败，请重试'});wx.showToast({icon:'none',title:'头像保存失败，请重试'})}finally{this.setData({avatarSaving:false})}
  }
})
