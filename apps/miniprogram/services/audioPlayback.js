function formatDuration(milliseconds) {
	const seconds = Math.max(0, Math.round((Number(milliseconds) || 0) / 1000))
	return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`
}

function createAudioPlayback({
	createContext,
	onState = () => {},
	onError = () => {},
	schedule = (callback, delay) => setTimeout(callback, delay),
	cancel = timer => clearTimeout(timer),
} = {}) {
	const context = createContext()
	const durationProbeDelays = [80, 160, 320, 640, 1000]
	let source = ''
	let durationMillis = 0
	let currentMillis = 0
	let playing = false
	let disposed = false
	let sourceVersion = 0
	let durationProbeTimer = null
	let durationProbeAttempt = 0

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

	function cancelDurationProbe() {
		if (durationProbeTimer !== null) cancel(durationProbeTimer)
		durationProbeTimer = null
		durationProbeAttempt = 0
	}

	function readMeasuredDuration() {
		const measuredDuration = Math.max(0, (Number(context.duration) || 0) * 1000)
		if (measuredDuration <= 0) return false
		durationMillis = measuredDuration
		cancelDurationProbe()
		emit()
		return true
	}

	function scheduleDurationProbe(version) {
		if (disposed || !source || durationProbeTimer !== null
			|| durationProbeAttempt >= durationProbeDelays.length) return
		const delay = durationProbeDelays[durationProbeAttempt]
		durationProbeAttempt += 1
		durationProbeTimer = schedule(() => {
			durationProbeTimer = null
			if (disposed || version !== sourceVersion) return
			if (!readMeasuredDuration()) scheduleDurationProbe(version)
		}, delay)
	}

	const player = {
		setSource(nextSource, nextDurationMillis = 0) {
			if (disposed) return
			cancelDurationProbe()
			sourceVersion += 1
			if (source) context.pause()
			source = nextSource || ''
			durationMillis = Math.max(0, Number(nextDurationMillis) || 0)
			currentMillis = 0
			playing = false
			if (source) context.src = source
			emit()
		},
		play() {
			if (disposed || !source) return
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
			cancelDurationProbe()
			sourceVersion += 1
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
		emit()
	})
	context.onCanplay(() => {
		if (disposed) return
		if (!readMeasuredDuration()) {
			emit()
			scheduleDurationProbe(sourceVersion)
		}
	})
	context.onTimeUpdate(() => {
		if (disposed) return
		currentMillis = Math.max(0, (Number(context.currentTime) || 0) * 1000)
		if (!readMeasuredDuration()) emit()
	})
	context.onEnded(() => {
		if (disposed) return
		playing = false
		currentMillis = 0
		context.seek(0)
		emit()
	})
	context.onError(() => {
		if (disposed) return
		playing = false
		emit()
		onError('音频播放失败')
	})

	return player
}

module.exports = { createAudioPlayback, formatDuration }
