const { createAudioPlayback, formatDuration } = require('../../services/audioPlayback.js')

Component({
	properties: {
		src: { type: String, value: '' },
		label: { type: String, value: '录制音频' },
		durationMillis: { type: Number, value: 0 },
	},
	data: { playing: false, progress: 0, timeText: '00:00' },
	observers: {
		'src,durationMillis'(src, durationMillis) {
			this.player?.setSource(src, durationMillis)
			if (!this.player) this.setData({ timeText: formatDuration(durationMillis), progress: 0, playing: false })
		},
	},
	lifetimes: {
		attached() {
			this.player = createAudioPlayback({
				createContext: () => wx.createInnerAudioContext(),
				onState: ({ playing, progress, timeText, durationMillis }) => {
					this.setData({ playing, progress, timeText })
					if (durationMillis > 0 && durationMillis !== this.lastReportedDuration) {
						this.lastReportedDuration = durationMillis
						this.triggerEvent('durationchange', { durationMillis })
					}
				},
				onError: message => this.triggerEvent('error', { message }),
			})
			this.player.setSource(this.properties.src, this.properties.durationMillis)
		},
		detached() {
			this.player?.dispose()
			this.player = null
		},
	},
	methods: {
		toggle() { this.player?.toggle() },
		seek(event) { this.player?.seekPercent(event.detail.value) },
	},
})
