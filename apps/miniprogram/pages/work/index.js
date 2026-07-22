const { validateSubmission } = require('../../services/recorder.js')
const { createRecordingSession } = require('../../services/recordingSession.js')
const { waveformBars } = require('../../services/pcm.js')
const feedback = require('../../services/feedback.js')

const statusText = {
  RECORDING_PENDING: '待录制',
  REWORK_PENDING: '待返修',
  SUBMITTED: '已提交',
  REVIEW_PENDING: '待审核',
  COMPLETED: '已完成',
}
const editableStatuses = new Set(['RECORDING_PENDING', 'REWORK_PENDING', 'SUBMITTED'])
const readOnlyStatuses = new Set(['REVIEW_PENDING', 'COMPLETED', 'AI_PROCESSING'])

Page({
  data: {
    item: {}, version: {}, loading: true, error: '', referenceAudioPath: '', referenceVideoPath: '',
    referenceAudioError: '', referenceVideoError: '', resultAudioError: '',
    audioPath: '', audioDuration: 0, text: '', recordState: 'idle',
    levelBars: waveformBars(0), submitting: false, releasing: false,
    statusText: '待录制', editable: true, readOnly: false, canRelease: true, submitLabel: '提交作业',
  },
  async onLoad(options) {
    this.itemId = options.itemId
    const { requireCompleteProfile } = require('../../services/profileGuard.js')
    if (await requireCompleteProfile(getApp())) this.load()
  },
  async onUnload() { await this.session?.dispose() },
  setupRecorder() {
    this.session = createRecordingSession({
      recorder: wx.getRecorderManager(), fs: wx.getFileSystemManager(), userDataPath: wx.env.USER_DATA_PATH,
      version: this.data.version,
      onLevel: level => this.setData({ levelBars: waveformBars(level) }),
      onState: recordState => this.setData({ recordState }),
      onComplete: result => this.setData({ audioPath: result.filePath, audioDuration: result.durationMillis }),
      onError: error => {
        const message = error.message || '录音保存失败'
        this.setData({ error: message, audioPath: '', audioDuration: 0, levelBars: waveformBars(0) })
        feedback.error(message)
      },
    })
  },
  async load() {
    if (!this.itemId) { this.setData({ loading: false, error: '缺少作业编号' }); return }
    this.setData({ loading: true, error: '' })
    try {
      const api = getApp().globalData.api
      const item = await api.item(this.itemId)
      const versions = await api.versions(item.taskId)
      const version = versions.find(candidate => candidate.id === item.taskVersionId) || {}
      const editable = editableStatuses.has(item.status)
      const readOnly = readOnlyStatuses.has(item.status) || !editable
      await this.session?.dispose()
      this.session = null
      this.setData({
        item, version, text: item.currentResult?.text || '', statusText: statusText[item.status] || item.status,
        editable, readOnly, canRelease: item.status === 'RECORDING_PENDING' || item.status === 'REWORK_PENDING',
        submitLabel: item.status === 'SUBMITTED' ? '保存修改' : '提交作业',
      })
      const [referenceAudio, referenceVideo, resultAudio] = await Promise.allSettled([
        Promise.resolve(item.referenceAudioUrl || api.referenceMediaUrl(item.referenceAudioMediaId)),
        Promise.resolve(item.referenceVideoUrl || api.referenceMediaUrl(item.referenceVideoMediaId)),
        api.media(item.currentResult?.audio?.mediaId),
      ])
      const referenceAudioPath = referenceAudio.status === 'fulfilled' ? referenceAudio.value : ''
      const referenceVideoPath = referenceVideo.status === 'fulfilled' ? referenceVideo.value : ''
      const resultAudioPath = resultAudio.status === 'fulfilled' ? resultAudio.value : ''
      const resultDuration = item.currentResult?.audio?.durationMillis || 0
      this.setData({
        referenceAudioPath, referenceVideoPath, audioPath: resultAudioPath,
        referenceAudioError: referenceAudio.status === 'rejected' ? '参考音频加载失败' : '',
        referenceVideoError: referenceVideo.status === 'rejected' ? '参考视频加载失败' : '',
        resultAudioError: resultAudio.status === 'rejected' ? '已提交录音加载失败' : '',
        audioDuration: resultDuration,
        recordState: resultAudioPath ? 'stopped' : 'idle',
      })
      if (editable) this.setupRecorder()
    } catch (error) {
      this.setData({ error: error.message || '加载作业失败' })
    } finally {
      this.setData({ loading: false })
    }
  },
  startRecord() {
    if (!this.data.editable || !this.session) return
    this.setData({ error: '', audioPath: '', audioDuration: 0, levelBars: waveformBars(0) })
    this.session.start()
  },
  pauseRecord() {
    if (!this.data.editable) return
    this.setData({ levelBars: waveformBars(0) })
    this.session?.pause()
  },
  resumeRecord() { if (this.data.editable) this.session?.resume() },
  async stopRecord() {
    if (!this.data.editable || !this.session) return
    try {
      const result = await this.session.stop()
      this.setData({ audioPath: result.filePath, audioDuration: result.durationMillis })
    } catch (error) { this.setData({ error: error.message || '录音保存失败' }) }
  },
  textInput(event) { if (this.data.editable) this.setData({ text: event.detail.value }) },
  audioPlaybackError(event) {
    feedback.error(event.detail?.message || '音频播放失败')
  },
  referenceAudioPlaybackError(event) {
    const message = event.detail?.message || '参考音频播放失败'
    this.setData({ referenceAudioError: message })
    feedback.error(message)
  },
  videoPlaybackError() {
    const message = '参考视频播放失败'
    this.setData({ referenceVideoError: message })
    feedback.error(message)
  },
  async submit() {
    if (!this.data.editable) return
    const validation = validateSubmission(this.data.version, { audio: this.data.audioPath, text: this.data.text })
    if (validation) { this.setData({ error: validation.message }); return }
    this.setData({ submitting: true, error: '' })
    const api = getApp().globalData.api
    try {
      await api.submit(this.itemId, {
        operationId: api.operationId('submit'), assignmentId: this.data.item.assignmentId,
        expectedRevision: this.data.item.revision, text: this.data.text.trim(), audioPath: this.data.audioPath,
      })
      feedback.success(this.data.item.status === 'SUBMITTED' ? '修改已保存' : '提交成功')
      setTimeout(() => wx.navigateBack({ delta: 1 }), 450)
    } catch (error) {
      if (error.code === 'STALE_STATE') {
        await this.load()
        const message = '状态已变化，已刷新为最新内容'
        this.setData({ error: message })
        feedback.error(message)
      } else {
        const message = error.message || '提交失败'
        this.setData({ error: message })
        feedback.error(message)
      }
    } finally { this.setData({ submitting: false }) }
  },
  release() {
    if (!this.data.canRelease) return
    wx.showModal({
      title: '释放到数据池', content: '是否释放回数据池？当前未提交结果将被清除，操作历史仍保留。', confirmColor: '#c2413b',
      success: async result => {
        if (!result.confirm) return
        this.setData({ releasing: true, error: '' })
        try {
          const api = getApp().globalData.api
          await api.release(this.itemId, this.data.item.revision, api.operationId('release'))
          feedback.success('已释放到数据池')
          setTimeout(() => wx.navigateBack({ delta: 1 }), 400)
        } catch (error) {
          this.setData({ error: error.message || '释放失败' })
          feedback.error(error.message)
        } finally { this.setData({ releasing: false }) }
      },
    })
  },
})
