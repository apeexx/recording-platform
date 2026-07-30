const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')

function loadPage({ confirm = true, unlinkError = null } = {}) {
  const source = fs.readFileSync(path.resolve('pages/work/index.js'), 'utf8')
  const toasts = []
  const removed = []
  let definition
  const wx = {
    showToast(options) { toasts.push(options) },
    showModal(options) { options.success({ confirm, cancel: !confirm }) },
    getFileSystemManager() {
      return {
        unlinkSync(filePath) {
          if (unlinkError) throw unlinkError
          removed.push(filePath)
        },
      }
    },
    env: { USER_DATA_PATH: '/user' },
    getRecorderManager() { return {} },
  }
  const requireStub = request => {
    if (request.includes('recorder.js')) return { validateSubmission: () => null }
    if (request.includes('recordingSession.js')) return { createRecordingSession: () => ({}) }
    if (request.includes('pcm.js')) return { waveformBars: value => [value] }
    if (request.includes('workflow.js')) return { claimNextWithRetry: async () => ({}) }
    if (request.includes('feedback.js')) {
      return {
        success: title => wx.showToast({ title, icon: 'success' }),
        error: title => wx.showToast({ title, icon: 'none' }),
      }
    }
    throw new Error(`unexpected require: ${request}`)
  }
  vm.runInNewContext(source, {
    Page: value => { definition = value },
    wx,
    require: requireStub,
    getApp: () => ({ globalData: { api: {} } }),
    setTimeout,
  }, { filename: 'pages/work/index.js' })
  const page = {
    ...definition,
    data: { ...definition.data },
    setData(patch) { Object.assign(this.data, patch) },
  }
  return { page, removed, toasts }
}

test('确认删除本次录音会清空音频状态并保留文本', () => {
  const { page, removed, toasts } = loadPage()
  Object.assign(page.data, {
    editable: true,
    text: '保留的文本结果',
    audioPath: '/user/recording.wav',
    audioDuration: 0,
    recordingDurationText: '00:00',
    recordState: 'stopped',
    audioOrigin: 'local',
    levelBars: [12, 18],
  })

  page.deleteRecording()

  assert.equal(page.data.audioPath, '')
  assert.equal(page.data.audioDuration, 0)
  assert.equal(page.data.recordingDurationText, '00:00')
  assert.equal(page.data.recordState, 'idle')
  assert.equal(page.data.audioOrigin, '')
  assert.equal(page.data.text, '保留的文本结果')
  assert.deepEqual(removed, ['/user/recording.wav'])
  assert.equal(toasts.at(-1).title, '录音已删除')
})

test('取消删除保持录音且本地清理失败不影响确认后的界面重置', () => {
  const cancelled = loadPage({ confirm: false })
  Object.assign(cancelled.page.data, {
    editable: true,
    audioPath: '/user/keep.wav',
    recordState: 'stopped',
    audioOrigin: 'local',
  })
  cancelled.page.deleteRecording()
  assert.equal(cancelled.page.data.audioPath, '/user/keep.wav')
  assert.deepEqual(cancelled.removed, [])

  const failedCleanup = loadPage({ unlinkError: new Error('locked') })
  Object.assign(failedCleanup.page.data, {
    editable: true,
    audioPath: '/user/locked.wav',
    recordState: 'stopped',
    audioOrigin: 'local',
  })
  assert.doesNotThrow(() => failedCleanup.page.deleteRecording())
  assert.equal(failedCleanup.page.data.audioPath, '')
})

test('删除服务器已有录音只清空页面引用且不删除本地文件', () => {
  const { page, removed } = loadPage()
  Object.assign(page.data, {
    editable: true,
    audioPath: 'wxfile://existing-recording.wav',
    audioDuration: 3200,
    recordState: 'stopped',
    audioOrigin: 'existing',
  })

  page.deleteRecording()

  assert.equal(page.data.audioPath, '')
  assert.deepEqual(removed, [])
})
