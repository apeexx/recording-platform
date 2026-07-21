const test = require('node:test')
const assert = require('node:assert/strict')

let waveformBars
try {
	({ waveformBars } = require('../services/pcm.js'))
} catch (_) { }

test('静音、底噪和非法值都返回七根平线', () => {
	assert.equal(typeof waveformBars, 'function')
	for (const level of [0, 0.02, 0.019, -1, NaN, undefined]) {
		assert.deepEqual(waveformBars(level), [8, 8, 8, 8, 8, 8, 8])
	}
})

test('中等音量生成中心最高且左右对称的当前帧波形', () => {
	assert.deepEqual(waveformBars(0.135), [21, 31, 43, 54, 43, 31, 21])
	assert.deepEqual(waveformBars(0.135), waveformBars(0.135))
})

test('大音量整体增高并限制在最大高度', () => {
	const medium = waveformBars(0.135)
	const loud = waveformBars(0.25)
	assert.deepEqual(loud, [34, 54, 77, 100, 77, 54, 34])
	assert.deepEqual(waveformBars(1), loud)
	assert.ok(loud.every((height, index) => height >= medium[index] && height <= 100))
})

test('每一帧独立计算，不混入上一帧音量', () => {
	waveformBars(0.25)
	assert.deepEqual(waveformBars(0), [8, 8, 8, 8, 8, 8, 8])
})
