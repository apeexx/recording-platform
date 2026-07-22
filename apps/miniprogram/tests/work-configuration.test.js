const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const root = path.resolve(__dirname, '..')

test('作业页直接读取任务配置并移除版本文案', () => {
  const script = fs.readFileSync(path.join(root, 'pages/work/index.js'), 'utf8')
  const template = fs.readFileSync(path.join(root, 'pages/work/index.wxml'), 'utf8')
  assert.match(script, /api\.task\(item\.taskId\)/)
  assert.doesNotMatch(script, /api\.versions|taskVersion/)
  assert.doesNotMatch(template, /任务版本|taskVersion/)
  assert.match(template, /文本或录音至少提交一项/)
  assert.match(template, /recordingDurationText/)
})
