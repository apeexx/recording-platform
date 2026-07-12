const test = require('node:test')
const assert = require('node:assert/strict')
const { recorderOptions, validateSubmission } = require('../services/recorder.js')

test('录音参数严格使用任务格式、采样率和单声道', () => {
  assert.deepEqual(recorderOptions({ recordingFormat:'WAV', sampleRates:[16000], maxDurationMillis:600000 }), {
    duration:600000, sampleRate:16000, numberOfChannels:1, encodeBitRate:48000, format:'wav'
  })
})

test('文字关闭时必须录音，文字开启时录音或文字至少一项', () => {
  assert.equal(validateSubmission({textInputEnabled:false}, {audio:null,text:'文字'}).code, 'AUDIO_REQUIRED')
  assert.equal(validateSubmission({textInputEnabled:true}, {audio:null,text:'  '}).code, 'RESULT_REQUIRED')
  assert.equal(validateSubmission({textInputEnabled:true}, {audio:null,text:'普通话'}), null)
})
