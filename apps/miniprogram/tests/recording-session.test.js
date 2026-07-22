const test = require('node:test')
const assert = require('node:assert/strict')
const { createRecordingSession } = require('../services/recordingSession.js')

test('录音管理器自动停止时也会完成文件并按 PCM 样本计算时长', async () => {
  const handlers = {}
  const recorder = {
    onFrameRecorded(callback) { handlers.frame = callback },
    onStop(callback) { handlers.stop = callback },
    start() {},
    stop() { handlers.stop() },
    pause() {},
    resume() {},
  }
  const fs = {
    open({ success }) { success({ fd: 1 }) },
    write({ success }) { success({ bytesWritten: 0 }) },
    close({ success }) { success() },
    unlinkSync() {},
  }
  let complete
  const durations = []
  const completed = new Promise((resolve) => { complete = resolve })
  const session = createRecordingSession({
    recorder,
    fs,
    userDataPath: '/tmp',
    configuration: { sampleRates: [16000], recordingFormat: 'WAV', maxDurationMillis: 1000 },
    onDuration: value => durations.push(value),
    onComplete: complete,
  })

  session.start()
  handlers.frame({ frameBuffer: new ArrayBuffer(320) })
  session.pause()
  handlers.frame({ frameBuffer: new ArrayBuffer(320) })
  session.resume()
  handlers.frame({ frameBuffer: new ArrayBuffer(320) })
  handlers.stop()

  const result = await completed
  assert.equal(result.durationMillis, 20)
  assert.deepEqual(durations, [0, 10, 20])
  assert.equal(session.getState(), 'stopped')
})

test('流式写入失败会中止临时文件、通知页面并允许重新录制', async () => {
  const handlers = {}
  const recorder = {
    onFrameRecorded(callback) { handlers.frame = callback },
    onStop(callback) { handlers.stop = callback },
    start() {}, stop() { handlers.stop() }, pause() {}, resume() {},
  }
  let removed = 0
  const fs = {
    open({ success }) { success({ fd: 1 }) },
    write({ fail }) { fail(new Error('磁盘写入失败')) },
    close({ success }) { success() },
    unlinkSync() { removed += 1 },
  }
  let reported
  const session = createRecordingSession({
    recorder, fs, userDataPath: '/tmp',
    configuration: { sampleRates: [16000], recordingFormat: 'WAV' },
    onDuration: value => { if (value === 0) reported = reported || 'reset' },
    onError: error => { reported = error },
  })

  session.start()
  handlers.frame({ frameBuffer: new ArrayBuffer(320) })
  await new Promise(resolve => setImmediate(resolve))

  assert.equal(session.getState(), 'error')
  assert.equal(reported.message, '磁盘写入失败')
  assert.equal(removed, 1)
  await assert.rejects(session.stop(), /磁盘写入失败/)
  session.start()
  assert.equal(session.getState(), 'recording')
})
