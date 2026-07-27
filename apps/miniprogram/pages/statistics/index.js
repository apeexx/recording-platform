const feedback = require('../../services/feedback.js')

const statusText = {
  RECORDING_PENDING: '待录制', REWORK_PENDING: '待返修', SUBMITTED: '已提交',
  REVIEW_PENDING: '待审核', COMPLETED: '已完成', AI_PROCESSING: '处理中',
}
function durationText(value) {
  let seconds = Math.floor(Math.max(0, Number(value) || 0) / 1000)
  const hours = Math.floor(seconds / 3600)
  seconds %= 3600
  const minutes = Math.floor(seconds / 60)
  const rest = seconds % 60
  if (hours) return `${hours}小时${minutes}分${rest}秒`
  if (minutes) return `${minutes}分${rest}秒`
  return `${rest}秒`
}
function dateTime(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}
function viewReport(report) {
  const summary = report.summary || {}
  return {
    ...report,
    summary: {
      ...summary,
      recordingDurationText: durationText(summary.recordingDurationMillis),
      referenceAudioDurationText: durationText(summary.referenceAudioDurationMillis),
      referenceVideoDurationText: durationText(summary.referenceVideoDurationMillis),
    },
    days: (report.days || []).map(day => ({
      ...day,
      recordingDurationText: durationText(day.recordingDurationMillis),
      referenceAudioDurationText: durationText(day.referenceAudioDurationMillis),
      referenceVideoDurationText: durationText(day.referenceVideoDurationMillis),
    })),
    recentSubmissions: (report.recentSubmissions || []).map(row => ({
      ...row,
      latestSubmittedAtText: dateTime(row.latestSubmittedAt),
      statusText: statusText[row.currentItemStatus] || row.currentItemStatus,
    })),
  }
}

Page({
  data: { tasks: [], taskNames: [], selectedTaskIndex: 0, selectedTaskId: '', report: null, loading: true, loadError: '' },
  onShow() {
    const state = getApp().globalData.session.current()
    if (!state?.token) { wx.reLaunch({ url: '/pages/login/index' }); return }
    this.loadTasks()
  },
  onPullDownRefresh() { this.loadTasks().finally(() => wx.stopPullDownRefresh()) },
  async loadTasks() {
    const hadData = !!this.data.report
    this.setData({ loading: !hadData, loadError: '' })
    try {
      const response = await getApp().globalData.api.reportTasks()
      const tasks = response.items || []
      if (!tasks.length) {
        this.setData({ tasks: [], taskNames: [], selectedTaskId: '', report: null, loading: false })
        return
      }
      this.setData({
        tasks, taskNames: tasks.map(task => `${task.taskCode} · ${task.taskName}`),
        selectedTaskIndex: 0, selectedTaskId: tasks[0].taskId,
      })
      await this.loadReport(tasks[0].taskId)
    } catch (error) {
      const message = error.message || '统计加载失败，请重试'
      if (hadData) {
        this.setData({ loadError: '' })
        feedback.error(message || '刷新失败，已保留原数据')
      } else {
        this.setData({ loadError: message })
      }
    } finally {
      this.setData({ loading: false })
    }
  },
  async loadReport(taskId) {
    const report = await getApp().globalData.api.taskReport(taskId)
    this.setData({ report: viewReport(report), loadError: '' })
  },
  async taskChange(event) {
    const selectedTaskIndex = Number(event.detail.value) || 0
    const selectedTaskId = this.data.tasks[selectedTaskIndex]?.taskId
    if (!selectedTaskId) return
    const previousTaskIndex = this.data.selectedTaskIndex
    const previousTaskId = this.data.selectedTaskId
    this.setData({ selectedTaskIndex, selectedTaskId, loading: true, loadError: '' })
    try { await this.loadReport(selectedTaskId) }
    catch (error) {
      this.setData({ selectedTaskIndex: previousTaskIndex, selectedTaskId: previousTaskId, loadError: '' })
      feedback.error(error.message || '统计加载失败，请重试')
    }
    finally { this.setData({ loading: false }) }
  },
  openSubmission(event) {
    wx.navigateTo({ url: `/pages/work/index?itemId=${encodeURIComponent(event.currentTarget.dataset.itemId)}` })
  },
  openMore() {
    const task = this.data.tasks[this.data.selectedTaskIndex]
    if (!task) return
    wx.navigateTo({ url: `/pages/submission-records/index?taskId=${encodeURIComponent(task.taskId)}&taskName=${encodeURIComponent(task.taskName)}&taskCode=${encodeURIComponent(task.taskCode)}` })
  },
})
