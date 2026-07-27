const feedback = require('../../services/feedback.js')
const statusText = {
  RECORDING_PENDING: '待录制', REWORK_PENDING: '待返修', SUBMITTED: '已提交',
  REVIEW_PENDING: '待审核', COMPLETED: '已完成', AI_PROCESSING: '处理中',
}
function durationText(value) {
  const seconds = Math.floor(Math.max(0, Number(value) || 0) / 1000)
  if (seconds < 60) return `${seconds}秒`
  return `${Math.floor(seconds / 60)}分${seconds % 60}秒`
}
function rowView(row) {
  return {
    ...row,
    latestSubmittedAtText: row.latestSubmittedAt ? new Date(row.latestSubmittedAt).toLocaleString('zh-CN', { hour12: false }) : '-',
    statusText: statusText[row.currentItemStatus] || row.currentItemStatus,
    recordingDurationText: durationText(row.recordingDurationMillis),
  }
}
Page({
  data: { taskName: '', taskCode: '', items: [], page: 0, hasMore: true, loading: true, loadingMore: false, loadError: '' },
  onLoad(options) {
    this.taskId = options.taskId || ''
    this.date = options.date || ''
    this.setData({ taskName: decodeURIComponent(options.taskName || ''), taskCode: decodeURIComponent(options.taskCode || '') })
    this.refresh()
  },
  onPullDownRefresh() { this.refresh().finally(() => wx.stopPullDownRefresh()) },
  onReachBottom() { this.loadMore() },
  async refresh() {
    const previous = {
      items: this.data.items, page: this.data.page, hasMore: this.data.hasMore,
    }
    const hadData = previous.items.length > 0
    this.setData({ page: 0, hasMore: true, loading: true, loadError: '' })
    try {
      const response = await getApp().globalData.api.taskSubmissions(this.taskId, 0, 20, this.date)
      this.setData({ items: (response.items || []).map(rowView), hasMore: (response.items || []).length < response.total })
    } catch (error) {
      const message = error.message || '提交记录加载失败'
      if (hadData) {
        this.setData({ ...previous, loadError: '' })
        feedback.error(message)
      } else {
        this.setData({ loadError: message })
      }
    } finally { this.setData({ loading: false }) }
  },
  async loadMore() {
    if (this.data.loadingMore || !this.data.hasMore) return
    const nextPage = this.data.page + 1
    this.setData({ page: nextPage, loadingMore: true })
    try {
      const response = await getApp().globalData.api.taskSubmissions(this.taskId, nextPage, 20, this.date)
      const nextItems = (response.items || []).map(rowView)
      const items = this.data.items.concat(nextItems)
      this.setData({ items, hasMore: items.length < response.total })
    } catch (error) {
      this.setData({ page: nextPage - 1 })
      feedback.error(error.message || '加载更多失败')
    } finally { this.setData({ loadingMore: false }) }
  },
  openSubmission(event) {
    wx.navigateTo({ url: `/pages/work/index?itemId=${encodeURIComponent(event.currentTarget.dataset.itemId)}` })
  },
})
