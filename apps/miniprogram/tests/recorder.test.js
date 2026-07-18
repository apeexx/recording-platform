const test = require('node:test')
const assert = require('node:assert/strict')
const { recorderOptions, validateSubmission } = require('../services/recorder.js')

test('录音参数使用 PCM 帧、任务采样率和单声道', () => {
  assert.deepEqual(recorderOptions({ recordingFormat:'WAV', sampleRates:[16000], maxDurationMillis:600000 }), {
	duration:600000, sampleRate:16000, numberOfChannels:1, encodeBitRate:48000, format:'PCM', frameSize:4
  })
})

test('所有任务都提交录音，文本成果额外要求文本', () => {
	assert.equal(validateSubmission({resultType:'AUDIO'}, {audio:'voice.wav',text:'文字'}).code, 'TEXT_NOT_ALLOWED')
	assert.equal(validateSubmission({resultType:'AUDIO'}, {audio:null,text:'  '}).code, 'AUDIO_REQUIRED')
	assert.equal(validateSubmission({resultType:'TEXT'}, {audio:'voice.wav',text:'普通话'}), null)
	assert.equal(validateSubmission({resultType:'TEXT'}, {audio:null,text:'普通话'}).code, 'AUDIO_REQUIRED')
	assert.equal(validateSubmission({resultType:'TEXT'}, {audio:'voice.wav',text:''}).code, 'TEXT_REQUIRED')
})
