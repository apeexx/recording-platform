function duration(ms){const seconds=Math.floor((ms||0)/1000);if(seconds<60)return `${seconds}秒`;return `${Math.floor(seconds/60)}分${seconds%60}秒`}
function resultTypeText(item){const types=[];if(item.audioPresent)types.push('录音');if(item.textPresent)types.push('文字');return types.join('、')||'-'}
const statusText={AVAILABLE:'待领取',RECORDING_PENDING:'待录制',REWORK_PENDING:'待返修',AI_PROCESSING:'智能处理中',REVIEW_PENDING:'待审核',COMPLETED:'已完成',DISCARDED:'废弃数据'}
Page({
  data:{user:{},summary:{},submissions:[],cumulativeDuration:'0秒',currentDuration:'0秒',loading:false,error:''},
  onShow(){const state=getApp().globalData.session.current();if(!state?.token){wx.reLaunch({url:'/pages/login/index'});return}this.setData({user:state.user,avatarSrc:'/assets/icons/default-collector-avatar.svg'});getApp().globalData.session.refreshProfile().then(user=>{this.setData({user});if(user.hasCustomAvatar)getApp().globalData.api.avatar().then(avatarSrc=>this.setData({avatarSrc})).catch(()=>{})});this.load()},
  onPullDownRefresh(){this.load().finally(()=>wx.stopPullDownRefresh())},
  async load(){this.setData({loading:true,error:''});try{const [summary,page]=await Promise.all([getApp().globalData.api.myReport(),getApp().globalData.api.mySubmissions(0)]);this.setData({summary,cumulativeDuration:duration(summary.cumulativeDurationMillis),currentDuration:duration(summary.currentDurationMillis),submissions:(page.items||[]).map(x=>({...x,submittedAtText:x.submittedAt?new Date(x.submittedAt).toLocaleString('zh-CN',{hour12:false}):'-',durationText:duration(x.durationMillis),resultTypeText:resultTypeText(x),statusText:statusText[x.currentItemStatus]||'-'}))})}catch(e){this.setData({error:e.message||'加载失败，请重试'})}finally{this.setData({loading:false})}},
  openSettings(){wx.navigateTo({url:'/pages/profile-settings/index'})},
  logout(){getApp().globalData.session.clear();wx.removeStorageSync('currentTaskItemId');wx.reLaunch({url:'/pages/login/index'})}
})
