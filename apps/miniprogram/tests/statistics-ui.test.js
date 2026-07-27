const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const root = path.resolve(__dirname, '..')
const read = file => fs.readFileSync(path.join(root, file), 'utf8')

test('底部导航包含任务统计和我的三个 Tab', () => {
  const app = JSON.parse(read('app.json'))
  assert.deepEqual(app.tabBar.list.map(item => item.text), ['任务', '统计', '我的'])
  assert.ok(app.pages.includes('pages/statistics/index'))
  assert.ok(app.pages.includes('pages/submission-records/index'))
})

test('统计页按最近任务展示五项总量每日数据和最近三条', () => {
  const script = read('pages/statistics/index.js')
  const template = read('pages/statistics/index.wxml')
  assert.match(script, /reportTasks\(\)/)
  assert.match(script, /selectedTaskId:\s*tasks\[0\]\.taskId/)
  assert.match(script, /recentSubmissions/)
  for (const label of ['提交条数', '完成条数', '录音时长', '参考音频时长', '参考视频时长']) {
    assert.ok(template.includes(label))
  }
  assert.match(template, /wx:for="{{report\.days}}"/)
  assert.match(template, /bindtap="openMore"/)
  assert.match(template, /bindtap="openSubmission"/)
})

test('统计页可选择单日并清除为全部日期', () => {
  const script = read('pages/statistics/index.js')
  const template = read('pages/statistics/index.wxml')
  assert.match(template, /mode="date"/)
  assert.match(template, /bindchange="dateChange"/)
  assert.match(template, /bindtap="clearDate"/)
  assert.match(script, /selectedDate:\s*''/)
  assert.match(script, /taskReport\(taskId,\s*this\.data\.selectedDate\)/)
  assert.match(script, /dateChange\(event\)/)
  assert.match(script, /clearDate\(\)/)
  assert.match(script, /date=\$\{encodeURIComponent\(this\.data\.selectedDate\)\}/)
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
