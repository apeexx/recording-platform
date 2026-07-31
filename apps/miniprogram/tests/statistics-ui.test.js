const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')

const root = path.resolve(__dirname, '..')
const read = file => fs.readFileSync(path.join(root, file), 'utf8')

function loadStatisticsPage(api = {}) {
  let definition
  vm.runInNewContext(read('pages/statistics/index.js'), {
    Page: value => { definition = value },
    require: id => id.includes('feedback')
      ? {error() {}}
      : require('../pages/statistics/date-range.js'),
    getApp: () => ({globalData:{api}}),
    wx: {},
  }, {filename:'pages/statistics/index.js'})
  return {
    ...definition,
    data:{...definition.data},
    setData(patch, callback) {
      Object.assign(this.data, patch)
      callback?.()
    },
  }
}

test('底部导航包含任务统计和我的三个 Tab', () => {
  const app = JSON.parse(read('app.json'))
  assert.deepEqual(app.tabBar.list.map(item => item.text), ['任务', '统计', '我的'])
  assert.ok(app.pages.includes('pages/statistics/index'))
  assert.ok(app.pages.includes('pages/submission-records/index'))
})

test('统计页按最近任务展示双阶段、小时分布、每日数据和双明细', () => {
  const script = read('pages/statistics/index.js')
  const template = read('pages/statistics/index.wxml')
  assert.match(script, /reportTasks\(\)/)
  assert.match(script, /taskCompletions/)
  assert.match(script, /requestSequence/)
  for (const label of ['提交统计', '完成统计', '最终录音', '参考音频', '参考视频']) {
    assert.ok(template.includes(label))
  }
  assert.match(template, /wx:for="{{hourBars}}"/)
  assert.match(template, /bindtap="selectHour"/)
  assert.match(template, /wx:for="{{report\.stageDays}}"/)
  assert.match(template, /提交明细/)
  assert.match(template, /完成明细/)
  assert.match(template, /bindtap="openSubmission"/)
})

test('统计页提供六种日期范围和底部日历', () => {
  const script = read('pages/statistics/index.js')
  const template = read('pages/statistics/index.wxml')
  for (const label of ['今天', '昨天', '近 7 日', '本月', '全部', '自定义']) {
    assert.ok(template.includes(label))
  }
  assert.match(template, /calendar-sheet/)
  assert.match(template, /bindtap="selectCalendarDay"/)
  assert.match(template, /bindtap="cancelCalendar"/)
  assert.match(template, /Asia\/Shanghai 04:00 至次日 04:00/)
  assert.match(script, /applyPreset\(event\)/)
  assert.match(script, /openCalendar\(\)/)
  assert.match(script, /selectCalendarDay\(event\)/)
})

test('统计页两套明细各自使用十条分页和原生页码选择器', () => {
  const script = read('pages/statistics/index.js')
  const template = read('pages/statistics/index.wxml')
  assert.match(script, /detailSize:\s*10/)
  assert.match(script, /submissionPage:\s*0/)
  assert.match(script, /completionPage:\s*0/)
  assert.match(script, /submissionPageOptions/)
  assert.match(script, /completionPageOptions/)
  assert.match(template, /上一页/)
  assert.match(template, /下一页/)
  assert.match(template, /<picker[^>]*mode="selector"[^>]*bindchange="selectDetailPage"/)
  assert.match(template, /第 \{\{submissionPage\+1\}\} 页 \/ 共 \{\{submissionTotalPages\}\} 页/)
  assert.match(template, /第 \{\{completionPage\+1\}\} 页 \/ 共 \{\{completionTotalPages\}\} 页/)
  assert.doesNotMatch(template, /当前第/)
})

test('统计页直接选择页码时保持提交和完成页码独立', async () => {
  const page = loadStatisticsPage()
  let refreshes = 0
  page.refreshCurrent = async () => { refreshes += 1 }
  page.setData({
    activeDetailTab:'submissions',
    submissionPage:0,
    completionPage:2,
    submissionTotalPages:4,
    completionTotalPages:5,
    loading:false,
  })

  await page.selectDetailPage({detail:{value:'2'}})
  assert.equal(page.data.submissionPage, 2)
  assert.equal(page.data.completionPage, 2)
  assert.equal(refreshes, 1)

  await page.selectDetailPage({detail:{value:'2'}})
  assert.equal(refreshes, 1)

  page.setData({activeDetailTab:'completions'})
  await page.selectDetailPage({detail:{value:'4'}})
  assert.equal(page.data.submissionPage, 2)
  assert.equal(page.data.completionPage, 4)
  assert.equal(refreshes, 2)
})

test('统计页刷新后将越界页收敛到最后有效页', async () => {
  const submissionPages = []
  const completionPages = []
  const api = {
    taskReport: async () => ({stageSummary:{submissionHourDistribution:[]},stageDays:[]}),
    taskSubmissions: async (_taskId, page) => {
      submissionPages.push(page)
      return {items:[],total:12}
    },
    taskCompletions: async (_taskId, page) => {
      completionPages.push(page)
      return {items:[],total:5}
    },
  }
  const page = loadStatisticsPage(api)
  page.setData({
    selectedTaskId:'task-1',
    report:{},
    submissionPage:3,
    completionPage:2,
  })

  await page.refreshCurrent()

  assert.deepEqual(submissionPages, [3, 1])
  assert.deepEqual(completionPages, [2, 0])
  assert.equal(page.data.submissionPage, 1)
  assert.equal(page.data.completionPage, 0)
  assert.deepEqual(Array.from(page.data.submissionPageOptions), ['第 1 页', '第 2 页'])
  assert.deepEqual(Array.from(page.data.completionPageOptions), ['第 1 页'])
})

test('业务日期工具支持凌晨四点换日、快捷范围和反向日期交换', () => {
  const dates = require('../pages/statistics/date-range.js')
  assert.equal(dates.businessDate(new Date('2026-07-31T19:59:59Z')), '2026-07-31')
  assert.equal(dates.businessDate(new Date('2026-07-31T20:00:00Z')), '2026-08-01')
  assert.deepEqual(dates.normalizeRange('2026-08-02', '2026-07-31'), {
    fromDate:'2026-07-31', toDate:'2026-08-02',
  })
  const presets = dates.presetRanges(new Date('2026-07-31T03:00:00Z'))
  assert.deepEqual(presets.today, {fromDate:'2026-07-31',toDate:'2026-07-31'})
  assert.deepEqual(presets.last7, {fromDate:'2026-07-25',toDate:'2026-07-31'})
  assert.deepEqual(presets.month, {fromDate:'2026-07-01',toDate:'2026-07-31'})
  assert.deepEqual(presets.all, {fromDate:'',toDate:''})
})

test('更多提交页按二十条触底分页并继承日期', () => {
  const script = read('pages/submission-records/index.js')
  const config = JSON.parse(read('pages/submission-records/index.json'))
  assert.match(script, /taskSubmissions\(this\.taskId,\s*0,\s*20,\s*this\.date\)/)
  assert.match(script, /taskSubmissions\(this\.taskId,\s*nextPage,\s*20,\s*this\.date\)/)
  assert.match(script, /onReachBottom\(\)/)
  assert.match(script, /loadingMore|hasMore/)
  assert.match(script, /pages\/work\/index\?itemId=/)
  assert.equal(config.enablePullDownRefresh, true)
})

test('小程序 API 使用新的任务统计端点', () => {
  const api = read('services/api.js')
  assert.match(api, /reportTasks:\s*\(\)=>request\('\/api\/reports\/me\/tasks'\)/)
  assert.match(api, /taskReport:/)
  assert.match(api, /taskSubmissions:/)
})
