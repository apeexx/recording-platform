const test = require('node:test')
const assert = require('node:assert/strict')

let createAudioPlayback
let formatDuration
try {
	({ createAudioPlayback, formatDuration } = require('../services/audioPlayback.js'))
} catch (_) { }

function fakeContext() {
	const handlers = {}
	const context = {
		_src: '', srcWrites: [], currentTime: 0, duration: 0, playCount: 0, pauseCount: 0, destroyCount: 0, seekValues: [],
		play() { this.playCount += 1; handlers.play?.() },
		pause() { this.pauseCount += 1; handlers.pause?.() },
		seek(value) { this.seekValues.push(value); this.currentTime = value },
		destroy() { this.destroyCount += 1 },
		onPlay(fn) { handlers.play = fn }, onPause(fn) { handlers.pause = fn },
		onCanplay(fn) { handlers.canplay = fn },
		onTimeUpdate(fn) { handlers.time = fn }, onEnded(fn) { handlers.ended = fn },
		onError(fn) { handlers.error = fn },
		emit(name, value) { handlers[name]?.(value) },
	}
	Object.defineProperty(context, 'src', {
		get() { return this._src },
		set(value) { this._src = value; this.srcWrites.push(value) },
	})
	return context
}

test('音频播放模块提供稳定的时间格式', () => {
	assert.equal(typeof formatDuration, 'function')
	assert.equal(formatDuration(0), '00:00')
	assert.equal(formatDuration(65_000), '01:05')
})

test('空音频源不会写入 InnerAudioContext 触发解码', () => {
	const context = fakeContext()
	const player = createAudioPlayback({ createContext: () => context })
	player.setSource('', 0)
	player.setSource(null, 0)
	assert.deepEqual(context.srcWrites, [])
	player.dispose()
})

test('播放器更新进度、允许拖动并在结束后复位', () => {
	assert.equal(typeof createAudioPlayback, 'function')
	const context = fakeContext()
	const states = []
	const player = createAudioPlayback({ createContext: () => context, onState: state => states.push(state) })
	player.setSource('/tmp/result.wav', 10_000)
	player.toggle()
	assert.equal(states.at(-1).timeText, '00:00')
	context.currentTime = 2
	context.duration = 10
	context.emit('time')
	assert.equal(states.at(-1).currentMillis, 2_000)
	assert.equal(states.at(-1).progress, 20)
	assert.equal(states.at(-1).timeText, '00:02')
	player.seekPercent(50)
	assert.deepEqual(context.seekValues, [5])
	context.emit('ended')
	assert.equal(states.at(-1).playing, false)
	assert.equal(states.at(-1).currentMillis, 0)
	assert.equal(states.at(-1).timeText, '00:10')
	player.dispose()
	assert.equal(context.destroyCount, 1)
})

test('资源可播放后会补齐未知总时长', () => {
	const context = fakeContext()
	const states = []
	const player = createAudioPlayback({ createContext: () => context, onState: state => states.push(state) })
	player.setSource('/tmp/reference.wav', 0)
	context.duration = 7
	context.emit('canplay')
	assert.equal(states.at(-1).durationMillis, 7_000)
	assert.equal(states.at(-1).timeText, '00:07')
	player.dispose()
})

test('多个音频实例可以同时播放并转发各自错误', () => {
	assert.equal(typeof createAudioPlayback, 'function')
	const firstContext = fakeContext()
	const secondContext = fakeContext()
	const errors = []
	const first = createAudioPlayback({ createContext: () => firstContext })
	const second = createAudioPlayback({ createContext: () => secondContext, onError: message => errors.push(message) })
	first.setSource('/tmp/first.wav', 1_000)
	second.setSource('/tmp/second.wav', 1_000)
	first.toggle()
	second.toggle()
	assert.equal(firstContext.playCount, 1)
	assert.equal(secondContext.playCount, 1)
	assert.equal(firstContext.pauseCount, 0)
	secondContext.emit('error', { errMsg: 'decode failed' })
	assert.deepEqual(errors, ['音频播放失败'])
	first.dispose()
	second.dispose()
})

test('音频播放模块不再暴露全局活动播放器暂停入口', () => {
	const playback = require('../services/audioPlayback.js')
	assert.equal(playback.pauseActiveAudio, undefined)
})

test('资源切换会暂停旧音频、复位进度并在销毁后停止响应', () => {
	const context = fakeContext()
	const states = []
	const errors = []
	const player = createAudioPlayback({
		createContext: () => context,
		onState: state => states.push(state),
		onError: message => errors.push(message),
	})
	player.setSource('/tmp/first.wav', 8_000)
	player.play()
	context.currentTime = 3
	context.duration = 8
	context.emit('time')
	player.setSource('/tmp/second.wav', 12_000)
	assert.equal(context.pauseCount, 1)
	assert.equal(context.src, '/tmp/second.wav')
	assert.equal(states.at(-1).playing, false)
	assert.equal(states.at(-1).progress, 0)
	assert.equal(states.at(-1).timeText, '00:12')
	player.dispose()
	const stateCountAfterDispose = states.length
	context.emit('time')
	context.emit('canplay')
	context.emit('error')
	assert.equal(states.length, stateCountAfterDispose)
	assert.deepEqual(errors, [])
	player.play()
	assert.equal(context.playCount, 1)
})
