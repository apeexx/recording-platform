let activePlayer = null

function formatDuration(milliseconds) {
	const seconds = Math.max(0, Math.round((Number(milliseconds) || 0) / 1000))
	return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`
}

function createAudioPlayback({ createContext, onState = () => {}, onError = () => {} } = {}) {
	const context = createContext()
	let source = ''
	let durationMillis = 0
	let currentMillis = 0
	let playing = false
	let disposed = false

	function emit() {
		const progress = durationMillis > 0 ? Math.min(100, Math.max(0, currentMillis / durationMillis * 100)) : 0
		onState({
			playing,
			currentMillis,
			durationMillis,
			progress,
			timeText: formatDuration(playing || currentMillis > 0 ? currentMillis : durationMillis),
		})
	}

	const player = {
		setSource(nextSource, nextDurationMillis = 0) {
			if (disposed) return
			if (activePlayer === player) activePlayer = null
			if (source) context.pause()
			source = nextSource || ''
			durationMillis = Math.max(0, Number(nextDurationMillis) || 0)
			currentMillis = 0
			playing = false
			context.src = source
			emit()
		},
		play() {
			if (disposed || !source) return
			if (activePlayer && activePlayer !== player) activePlayer.pause()
			activePlayer = player
			context.play()
		},
		pause() {
			if (disposed) return
			context.pause()
		},
		toggle() { playing ? player.pause() : player.play() },
		seekPercent(percent) {
			if (disposed || durationMillis <= 0) return
			const safePercent = Math.min(100, Math.max(0, Number(percent) || 0))
			currentMillis = durationMillis * safePercent / 100
			context.seek(currentMillis / 1000)
			emit()
		},
		dispose() {
			if (disposed) return
			disposed = true
			if (activePlayer === player) activePlayer = null
			context.destroy()
		},
	}

	context.onPlay(() => {
		if (disposed) return
		playing = true
		emit()
	})
	context.onPause(() => {
		if (disposed) return
		playing = false
		if (activePlayer === player) activePlayer = null
		emit()
	})
	context.onCanplay(() => {
		if (disposed) return
		const measuredDuration = Math.max(0, (Number(context.duration) || 0) * 1000)
		if (measuredDuration > 0) durationMillis = measuredDuration
		emit()
	})
	context.onTimeUpdate(() => {
		if (disposed) return
		currentMillis = Math.max(0, (Number(context.currentTime) || 0) * 1000)
		const measuredDuration = Math.max(0, (Number(context.duration) || 0) * 1000)
		if (measuredDuration > 0) durationMillis = measuredDuration
		emit()
	})
	context.onEnded(() => {
		if (disposed) return
		playing = false
		currentMillis = 0
		if (activePlayer === player) activePlayer = null
		context.seek(0)
		emit()
	})
	context.onError(() => {
		if (disposed) return
		playing = false
		if (activePlayer === player) activePlayer = null
		emit()
		onError('音频播放失败')
	})

	return player
}

module.exports = { createAudioPlayback, formatDuration }
