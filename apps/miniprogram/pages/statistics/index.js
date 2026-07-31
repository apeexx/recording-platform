const feedback = require('../../services/feedback.js')
const dates = require('./date-range.js')

const statusText = {
  RECORDING_PENDING: '待录制', REWORK_PENDING: '待返修', SUBMITTED: '已提交',
  REVIEW_PENDING: '待审核', COMPLETED: '已完成', AI_PROCESSING: '处理中',
}
const presetLabels = {today:'今天', yesterday:'昨天', last7:'近 7 日', month:'本月', all:'全部', custom:'自定义'}

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
  return value ? new Date(value).toLocaleString('zh-CN', {hour12:false}) : '-'
}

function stageMetrics(value = {}) {
  return {
    count: Number(value.count) || 0,
    recordingDurationText: durationText(value.recordingDurationMillis),
    referenceAudioDurationText: durationText(value.referenceAudioDurationMillis),
    referenceVideoDurationText: durationText(value.referenceVideoDurationMillis),
  }
}

function detailRows(rows = []) {
  return rows.map(row => ({
    ...row,
    statusText: statusText[row.currentItemStatus] || row.currentItemStatus || '-',
    firstSubmittedAtText: dateTime(row.firstSubmittedAt),
    firstCompletedAtText: dateTime(row.firstCompletedAt),
  }))
}

function viewReport(report = {}) {
  const stageSummary = report.stageSummary || {}
  return {
    ...report,
    stageSummary: {
      submissions: stageMetrics(stageSummary.submissions),
      completions: stageMetrics(stageSummary.completions),
    },
    stageDays: (report.stageDays || []).map(day => ({
      ...day,
      submissions: stageMetrics(day.submissions),
      completions: stageMetrics(day.completions),
    })),
  }
}

function hourBars(distribution = []) {
  const values = distribution.length
    ? distribution
    : Array.from({length:24}, (_, index) => ({hour:(index + 4) % 24, count:0}))
  const maximum = Math.max(1, ...values.map(item => Number(item.count) || 0))
  return values.map((item, index) => ({
    hour:Number(item.hour), count:Number(item.count) || 0,
    height:Math.max(2, Math.round((Number(item.count) || 0) / maximum * 100)),
    label:[0, 6, 12, 18, 23].includes(index) ? `${String(item.hour).padStart(2, '0')}时` : '',
  }))
}

function monthTitle(month) {
  const [year, value] = month.split('-')
  return `${year}年${Number(value)}月`
}

Page({
  requestSequence: 0,
  data: {
    tasks: [], taskNames: [], selectedTaskIndex:0, selectedTaskId:'',
    fromDate:'', toDate:'', activePreset:'today', rangeLabel:'今天',
    report:null, hourBars:[], selectedHour:null,
    activeDetailTab:'submissions', submissions:[], completions:[],
    submissionPage:0, completionPage:0, detailSize:5,
    submissionTotal:0, completionTotal:0, submissionTotalPages:1, completionTotalPages:1,
    showCalendar:false, calendarMonth:'', calendarTitle:'', calendarCells:[],
    draftStart:'', draftEnd:'',
    loading:true, refreshing:false, loadError:'',
  },
  onShow() {
    const state = getApp().globalData.session.current()
    if (!state?.token) { wx.reLaunch({url:'/pages/login/index'}); return }
    if (!this.data.tasks.length) {
      const today = dates.presetRanges().today
      this.setData({...today, rangeLabel:'今天'})
      this.loadTasks()
    }
  },
  onPullDownRefresh() {
    this.refreshCurrent().finally(() => wx.stopPullDownRefresh())
  },
  range() {
    return {fromDate:this.data.fromDate, toDate:this.data.toDate}
  },
  async loadTasks() {
    const hadData = !!this.data.report
    this.setData({loading:!hadData, refreshing:hadData, loadError:''})
    try {
      const response = await getApp().globalData.api.reportTasks()
      const tasks = response.items || []
      if (!tasks.length) {
        this.setData({tasks:[], taskNames:[], selectedTaskId:'', report:null})
        return
      }
      const currentIndex = Math.max(0, tasks.findIndex(task => task.taskId === this.data.selectedTaskId))
      const selectedTaskId = tasks[currentIndex].taskId
      this.setData({
        tasks, taskNames:tasks.map(task => `${task.taskCode} · ${task.taskName}`),
        selectedTaskIndex:currentIndex, selectedTaskId,
      })
      await this.refreshCurrent()
    } catch (error) {
      this.handleLoadError(error, hadData)
    } finally {
      this.setData({loading:false, refreshing:false})
    }
  },
  async refreshCurrent() {
    const taskId = this.data.selectedTaskId
    if (!taskId) return
    const sequence = ++this.requestSequence
    const hadData = !!this.data.report
    this.setData({loading:!hadData, refreshing:hadData, loadError:''})
    try {
      const [report, submissions, completions] = await Promise.all([
        getApp().globalData.api.taskReport(taskId, this.range()),
        getApp().globalData.api.taskSubmissions(taskId, this.data.submissionPage, this.data.detailSize, this.range()),
        getApp().globalData.api.taskCompletions(taskId, this.data.completionPage, this.data.detailSize, this.range()),
      ])
      if (sequence !== this.requestSequence) return
      const viewed = viewReport(report)
      this.setData({
        report:viewed,
        hourBars:hourBars(report.stageSummary?.submissionHourDistribution),
        submissions:detailRows(submissions.items), completions:detailRows(completions.items),
        submissionTotal:Number(submissions.total) || 0, completionTotal:Number(completions.total) || 0,
        submissionTotalPages:Math.max(1, Math.ceil((Number(submissions.total) || 0) / this.data.detailSize)),
        completionTotalPages:Math.max(1, Math.ceil((Number(completions.total) || 0) / this.data.detailSize)),
        loadError:'',
      })
    } catch (error) {
      if (sequence === this.requestSequence) this.handleLoadError(error, hadData)
    } finally {
      if (sequence === this.requestSequence) this.setData({loading:false, refreshing:false})
    }
  },
  handleLoadError(error, hadData) {
    const message = error.message || '统计加载失败，请重试'
    if (hadData) feedback.error(`${message}，已保留原数据`)
    else this.setData({loadError:message})
  },
  async taskChange(event) {
    const selectedTaskIndex = Number(event.detail.value) || 0
    const selectedTaskId = this.data.tasks[selectedTaskIndex]?.taskId
    if (!selectedTaskId) return
    this.setData({selectedTaskIndex, selectedTaskId, submissionPage:0, completionPage:0})
    await this.refreshCurrent()
  },
  async applyPreset(event) {
    const preset = event.currentTarget.dataset.preset
    if (preset === 'custom') { this.openCalendar(); return }
    const range = dates.presetRanges()[preset]
    if (!range) return
    this.setData({
      ...range, activePreset:preset, rangeLabel:presetLabels[preset],
      submissionPage:0, completionPage:0, selectedHour:null,
    })
    await this.refreshCurrent()
  },
  openCalendar() {
    const start = this.data.fromDate || dates.businessDate()
    const end = this.data.toDate || start
    const calendarMonth = start.slice(0, 7)
    this.setData({
      showCalendar:true, calendarMonth, calendarTitle:monthTitle(calendarMonth),
      draftStart:start, draftEnd:end,
      calendarCells:dates.monthCells(calendarMonth, start, end),
    })
  },
  cancelCalendar() {
    this.setData({showCalendar:false, draftStart:'', draftEnd:''})
  },
  stopBubble() {},
  changeCalendarMonth(event) {
    const calendarMonth = dates.shiftMonth(this.data.calendarMonth, Number(event.currentTarget.dataset.delta))
    this.setData({
      calendarMonth, calendarTitle:monthTitle(calendarMonth),
      calendarCells:dates.monthCells(calendarMonth, this.data.draftStart, this.data.draftEnd),
    })
  },
  async selectCalendarDay(event) {
    const value = event.currentTarget.dataset.date
    if (!value) return
    if (!this.data.draftStart || this.data.draftEnd) {
      this.setData({
        draftStart:value, draftEnd:'',
        calendarCells:dates.monthCells(this.data.calendarMonth, value, ''),
      })
      return
    }
    const range = dates.normalizeRange(this.data.draftStart, value)
    this.setData({
      ...range, activePreset:'custom', rangeLabel:`${range.fromDate} 至 ${range.toDate}`,
      showCalendar:false, draftStart:'', draftEnd:'',
      submissionPage:0, completionPage:0, selectedHour:null,
    })
    await this.refreshCurrent()
  },
  selectHour(event) {
    const hour = Number(event.currentTarget.dataset.hour)
    const count = Number(event.currentTarget.dataset.count) || 0
    this.setData({selectedHour:{hour, count, nextHour:(hour + 1) % 24}})
  },
  switchDetailTab(event) {
    this.setData({activeDetailTab:event.currentTarget.dataset.tab})
  },
  async previousDetailPage() {
    const field = this.data.activeDetailTab === 'submissions' ? 'submissionPage' : 'completionPage'
    if (this.data[field] <= 0) return
    this.setData({[field]:this.data[field] - 1})
    await this.refreshCurrent()
  },
  async nextDetailPage() {
    const submissions = this.data.activeDetailTab === 'submissions'
    const field = submissions ? 'submissionPage' : 'completionPage'
    const totalPages = submissions ? this.data.submissionTotalPages : this.data.completionTotalPages
    if (this.data[field] + 1 >= totalPages) return
    this.setData({[field]:this.data[field] + 1})
    await this.refreshCurrent()
  },
  openSubmission(event) {
    wx.navigateTo({url:`/pages/work/index?itemId=${encodeURIComponent(event.currentTarget.dataset.itemId)}`})
  },
})
