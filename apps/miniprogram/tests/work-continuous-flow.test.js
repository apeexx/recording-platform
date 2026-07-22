const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const root = path.resolve(__dirname, '..')
const script = fs.readFileSync(path.join(root, 'pages/work/index.js'), 'utf8')
const template = fs.readFileSync(path.join(root, 'pages/work/index.wxml'), 'utf8')

test('作业页默认记忆自动下一条且已提交修改不显示开关', () => {
  assert.match(script, /autoClaimNextEnabled/)
  assert.match(script, /getStorageSync\(['"]autoClaimNextEnabled['"]\)/)
  assert.match(script, /setStorageSync\(['"]autoClaimNextEnabled['"]/)
  assert.match(template, /bindchange="autoClaimNextChange"/)
  assert.match(template, /showAutoClaimNext/)
})

test('作业页成功领取后直接重定向，失败回当前任务数据页', () => {
  assert.match(script, /claimNextWithRetry/)
  assert.match(script, /wx\.redirectTo\(\{ url: `\/pages\/work\/index\?itemId=/)
  assert.match(script, /\/pages\/work-list\/index\?taskId=/)
})

test('录音、音频和视频播放入口统一暂停其他媒体', () => {
  assert.match(script, /pauseAllPlayback/)
  assert.match(script, /audioPlayRequest/)
  assert.match(script, /videoPlay/)
  assert.match(template, /bindplayrequest="audioPlayRequest"/)
  assert.match(template, /bindplay="videoPlay"/)
})

test('操作错误不再作为作业卡片底部文字渲染', () => {
  assert.doesNotMatch(template, /referenceAudioError|referenceVideoError|resultAudioError/)
  assert.doesNotMatch(template, /class="media-error"/)
})
