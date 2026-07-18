Page({
  data:{items:[],kind:'ALL',loading:false,error:'',filters:[{value:'ALL',label:'全部'},{value:'REWORK',label:'待返修'},{value:'RECORDING',label:'待录制'}]},
  onLoad(options){this.taskId=options.taskId||''},onShow(){this.load()},
  async load(){this.setData({loading:true,error:''});try{const result=await getApp().globalData.api.myWork({taskId:this.taskId,kind:this.data.kind,page:0,size:100});this.setData({items:result.items||[]})}catch(e){this.setData({error:e.message||'加载待办失败'})}finally{this.setData({loading:false})}},
  filter(e){this.setData({kind:e.currentTarget.dataset.kind},()=>this.load())},
  open(e){wx.navigateTo({url:`/pages/work/index?itemId=${e.currentTarget.dataset.id}`})}
})
