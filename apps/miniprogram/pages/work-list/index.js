const {PAGE_SIZE,pageCount,clampPage,canPrevious,canNext}=require('./pagination.js')
const labels={RECORDING_PENDING:'待录制',REWORK_PENDING:'待返修',SUBMITTED:'已提交',REVIEW_PENDING:'待审核',COMPLETED:'已完成'}

Page({
	data:{items:[],kind:'PENDING',page:0,total:0,totalPages:1,loading:false,error:'',filters:[{value:'PENDING',label:'待处理'},{value:'SUBMITTED',label:'已提交'},{value:'FINISHED',label:'已完成'}]},
  onLoad(options){this.taskId=options.taskId||''},
  async onShow(){const {requireCompleteProfile}=require('../../services/profileGuard.js');if(await requireCompleteProfile(getApp()))this.load(this.data.page)},
	onPullDownRefresh(){this.load(this.data.page).finally(()=>wx.stopPullDownRefresh())},
	async load(targetPage=this.data.page){
		if(this.data.loading)return
		const requestedPage=Math.max(Number(targetPage)||0,0)
		this.setData({loading:true,error:''})
		try{
			const api=getApp().globalData.api
			let result=await api.myWork({taskId:this.taskId,kind:this.data.kind,page:requestedPage,size:PAGE_SIZE})
			const total=Number(result.total)||0
			const resolvedPage=clampPage(requestedPage,total)
			if(resolvedPage!==requestedPage)result=await api.myWork({taskId:this.taskId,kind:this.data.kind,page:resolvedPage,size:PAGE_SIZE})
			const resolvedTotal=Number(result.total)||0
			this.setData({
				items:(result.items||[]).map(item=>({...item,statusText:labels[item.status]||item.status})),
				page:resolvedPage,total:resolvedTotal,totalPages:pageCount(resolvedTotal)
			})
		}catch(e){this.setData({error:e.message||'加载任务数据失败'})}
		finally{this.setData({loading:false})}
	},
  filter(e){if(this.data.loading)return;const kind=e.currentTarget.dataset.kind;if(kind===this.data.kind)return;this.setData({kind,page:0},()=>this.load(0))},
	previousPage(){if(!this.data.loading&&canPrevious(this.data.page))this.load(this.data.page-1)},
	nextPage(){if(!this.data.loading&&canNext(this.data.page,this.data.total))this.load(this.data.page+1)},
  open(e){wx.navigateTo({url:`/pages/work/index?itemId=${e.currentTarget.dataset.id}`})}
})
