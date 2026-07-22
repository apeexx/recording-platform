const permissionText={ACTIVE:'已授权',PENDING:'待审批',NONE:'未授权'}
const lifecycleText={RUNNING:'进行中',PAUSED:'已暂停'}
const {requireCompleteProfile}=require('../../services/profileGuard.js')
const feedback=require('../../services/feedback.js')
Page({
  data:{tasks:[],loading:false,actingId:'',loadError:''},
  onShow(){if(!getApp().globalData.session.current()?.token){wx.reLaunch({url:'/pages/login/index'});return}this.load()},
  onPullDownRefresh(){this.load().finally(()=>wx.stopPullDownRefresh())},
  async load(){this.setData({loading:true,loadError:''});try{const data=await getApp().globalData.api.tasks();this.setData({tasks:(data.items||[]).map(x=>({...x,permissionText:permissionText[x.permissionStatus]||x.permissionStatus,lifecycleText:lifecycleText[x.lifecycle]||x.lifecycle}))})}catch(e){if(e.status===401){getApp().globalData.session.clear();wx.reLaunch({url:'/pages/login/index'});return}this.setData({loadError:e.message||'任务加载失败'})}finally{this.setData({loading:false})}},
  async requestAccess(e){if(!await requireCompleteProfile(getApp()))return;const id=e.currentTarget.dataset.id;this.setData({actingId:id});try{await getApp().globalData.api.requestAccess(id);feedback.success('申请已提交');await this.load()}catch(err){feedback.error(err.message||'权限申请失败')}finally{this.setData({actingId:''})}},
  async start(e){if(!await requireCompleteProfile(getApp()))return;const taskId=e.currentTarget.dataset.id;this.setData({actingId:taskId});try{const item=await getApp().globalData.api.start(taskId);wx.navigateTo({url:`/pages/work/index?itemId=${item.id}`})}catch(err){feedback.error(err.code==='NO_AVAILABLE_ITEM'?'当前任务池暂无可领取数据':(err.message||'领取失败'))}finally{this.setData({actingId:''})}},
	async openWorkList(e){if(await requireCompleteProfile(getApp()))wx.navigateTo({url:`/pages/work-list/index?taskId=${e.currentTarget.dataset.id}`})}
})
