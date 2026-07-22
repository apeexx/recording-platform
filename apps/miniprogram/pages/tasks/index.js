const permissionText={ACTIVE:'已授权',PENDING:'待审批',NONE:'未授权'}
const lifecycleText={RUNNING:'进行中',PAUSED:'已暂停'}
const {requireCompleteProfile}=require('../../services/profileGuard.js')
Page({
  data:{tasks:[],loading:false,actingId:'',error:''},
  onShow(){if(!getApp().globalData.session.current()?.token){wx.reLaunch({url:'/pages/login/index'});return}this.load()},
  onPullDownRefresh(){this.load().finally(()=>wx.stopPullDownRefresh())},
  async load(){this.setData({loading:true,error:''});try{const data=await getApp().globalData.api.tasks();this.setData({tasks:(data.items||[]).map(x=>({...x,permissionText:permissionText[x.permissionStatus]||x.permissionStatus,lifecycleText:lifecycleText[x.lifecycle]||x.lifecycle}))})}catch(e){if(e.status===401){getApp().globalData.session.clear();wx.reLaunch({url:'/pages/login/index'});return}this.setData({error:e.message})}finally{this.setData({loading:false})}},
  async requestAccess(e){if(!await requireCompleteProfile(getApp()))return;const id=e.currentTarget.dataset.id;this.setData({actingId:id,error:''});try{await getApp().globalData.api.requestAccess(id);wx.showToast({title:'申请已提交'});await this.load()}catch(err){this.setData({error:err.message})}finally{this.setData({actingId:''})}},
  async start(e){if(!await requireCompleteProfile(getApp()))return;const taskId=e.currentTarget.dataset.id;this.setData({actingId:taskId,error:''});try{const item=await getApp().globalData.api.start(taskId);wx.navigateTo({url:`/pages/work/index?itemId=${item.id}`})}catch(err){this.setData({error:err.code==='NO_AVAILABLE_ITEM'?'当前任务池暂无可领取数据':err.message})}finally{this.setData({actingId:''})}},
	async openWorkList(e){if(await requireCompleteProfile(getApp()))wx.navigateTo({url:`/pages/work-list/index?taskId=${e.currentTarget.dataset.id}`})}
})
