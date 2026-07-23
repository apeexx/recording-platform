const DEFAULT_AVATAR='/assets/icons/default-collector-avatar.svg'
const feedback=require('../../services/feedback.js')

Page({
  data:{profile:{},avatarSrc:DEFAULT_AVATAR,avatarPreviewVisible:false,pendingAvatarPath:'',avatarSaving:false,name:'',account:'',password:'',confirmPassword:'',currentPassword:'',newPassword:''},
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
  async save(){
    const name=this.data.name.trim()
    if(!name)return feedback.error('请填写姓名')
    const account=this.data.account.trim()
    const password=this.data.password
    const confirmPassword=this.data.confirmPassword
    try{
      if(this.data.profile.account){
        await getApp().globalData.session.setName(name)
        feedback.success('姓名已更新')
      }else{
        const wantsAccount=Boolean(account||password||confirmPassword)
        if(wantsAccount&&!/^[1-9][0-9]{5,11}$/.test(account))return feedback.error('数字账号必须为 6–12 位数字且首位不能为 0')
        if(wantsAccount&&password.length<8)return feedback.error('登录密码至少需要 8 个字符')
        if(wantsAccount&&password!==confirmPassword)return feedback.error('两次输入的密码不一致')
        await getApp().globalData.session.completeProfile(wantsAccount?{name,account,password}:{name})
        feedback.success(this.data.profile.profileComplete?'资料已更新':'资料已完成')
      }
      await this.load()
    }catch(e){feedback.error(e.message||'资料保存失败')}
  },
  showAccountHelp(){wx.showModal({title:'数字登录账号',content:'用于不使用微信快捷登录时，通过数字账号和密码登录；可稍后设置，不影响任务使用。',showCancel:false,confirmText:'知道了'})},
  async changePassword(){try{await getApp().globalData.api.changePassword({currentPassword:this.data.currentPassword,newPassword:this.data.newPassword});getApp().globalData.session.clear();wx.showModal({title:'密码已修改',content:'请使用新密码重新登录',showCancel:false,success:()=>wx.reLaunch({url:'/pages/login/index'})})}catch(e){feedback.error(e.message||'密码修改失败')}},
  preventClose(){},
  chooseAvatar(e){if(e.detail.avatarUrl)this.openAvatarPreview(e.detail.avatarUrl)},
  openAvatarPreview(path){this.setData({avatarPreviewVisible:true,pendingAvatarPath:path})},
  closeAvatarPreview(){if(!this.data.avatarSaving)this.setData({avatarPreviewVisible:false,pendingAvatarPath:''})},
  async saveAvatar(){if(this.data.avatarSaving)return
    const {pendingAvatarPath}=this.data
    if(!pendingAvatarPath)return
    this.setData({avatarSaving:true})
    try{
      const profile=await getApp().globalData.api.uploadAvatar(pendingAvatarPath)
      const cachedProfile=getApp().globalData.session.updateProfile(profile)
      this.setData({profile:cachedProfile,avatarSrc:pendingAvatarPath,avatarPreviewVisible:false,pendingAvatarPath:''})
      feedback.success('头像已更新')
    }catch(e){feedback.error('头像保存失败，请重试')}finally{this.setData({avatarSaving:false})}
  }
})
