Page({
	data:{items:[],kind:'PENDING',loading:false,error:'',filters:[{value:'PENDING',label:'待处理'},{value:'SUBMITTED',label:'已提交'},{value:'FINISHED',label:'已完成'}]},
  onLoad(options){this.taskId=options.taskId||''},async onShow(){const {requireCompleteProfile}=require('../../services/profileGuard.js');if(await requireCompleteProfile(getApp()))this.load()},
	onPullDownRefresh(){this.load().finally(()=>wx.stopPullDownRefresh())},
	async load(){this.setData({loading:true,error:''});try{const result=await getApp().globalData.api.myWork({taskId:this.taskId,kind:this.data.kind,page:0,size:100});const labels={RECORDING_PENDING:'待录制',REWORK_PENDING:'待返修',SUBMITTED:'已提交',REVIEW_PENDING:'待审核',COMPLETED:'已完成'};this.setData({items:(result.items||[]).map(item=>({...item,statusText:labels[item.status]||item.status}))})}catch(e){this.setData({error:e.message||'加载任务数据失败'})}finally{this.setData({loading:false})}},
  filter(e){this.setData({kind:e.currentTarget.dataset.kind},()=>this.load())},
  open(e){wx.navigateTo({url:`/pages/work/index?itemId=${e.currentTarget.dataset.id}`})}
})
