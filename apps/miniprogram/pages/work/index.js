const { validateSubmission } = require('../../services/recorder.js')
const { createRecordingSession } = require('../../services/recordingSession.js')
const { waveformBars } = require('../../services/pcm.js')
const { claimNextWithRetry } = require('../../services/workflow.js')
const feedback = require('../../services/feedback.js')

const statusText = {
  RECORDING_PENDING: '待录制',
  REWORK_PENDING: '待返修',
  SUBMITTED: '已提交',
  REVIEW_PENDING: '待审核',
  COMPLETED: '已完成',
  DISCARDED: '已废弃',
}
const editableStatuses = new Set(['RECORDING_PENDING', 'REWORK_PENDING', 'SUBMITTED'])
const readOnlyStatuses = new Set(['REVIEW_PENDING', 'COMPLETED', 'AI_PROCESSING'])
function durationText(duration) {
  const seconds = Math.floor(Math.max(Number(duration) || 0, 0) / 1000)
  return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`
}
function dateTimeText(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  const part = number => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${part(date.getMonth() + 1)}-${part(date.getDate())} ${part(date.getHours())}:${part(date.getMinutes())}:${part(date.getSeconds())}`
}

Page({
  data: {
    item: {}, configuration: {}, loading: true, loadError: '', referenceAudioPath: '', referenceVideoPath: '',
    audioPath: '', audioDuration: 0, audioOrigin: '', recordingDurationText: '00:00', text: '', recordState: 'idle',
    referenceAudioDurationMillis: 0, referenceVideoDurationMillis: 0,
    levelBars: waveformBars(0), submitting: false, releasing: false, discarding: false, restoring: false,
    statusText: '待录制', editable: true, readOnly: false, canRelease: true, canDiscard: true,
    isDiscarded: false, discardedAtText: '-', submitLabel: '提交作业',
    autoClaimNextEnabled: true, showAutoClaimNext: true,
  },
  async onLoad(options) {
    this.itemId = options.itemId
    const storedAutoClaim = wx.getStorageSync('autoClaimNextEnabled')
    this.setData({ autoClaimNextEnabled: typeof storedAutoClaim === 'boolean' ? storedAutoClaim : true })
    const { requireCompleteProfile } = require('../../services/profileGuard.js')
    if (await requireCompleteProfile(getApp())) this.load()
  },
  async onUnload() { await this.session?.dispose() },
  setupRecorder() {
    this.session = createRecordingSession({
      recorder: wx.getRecorderManager(), fs: wx.getFileSystemManager(), userDataPath: wx.env.USER_DATA_PATH,
      configuration: this.data.configuration,
      onLevel: level => this.setData({ levelBars: waveformBars(level) }),
      onDuration: duration => this.setData({ audioDuration: duration, recordingDurationText: durationText(duration) }),
      onState: recordState => this.setData({ recordState }),
      onComplete: result => this.setData({
        audioPath: result.filePath, audioDuration: result.durationMillis, audioOrigin: 'local',
      }),
      onError: error => {
        const message = error.message || '录音保存失败'
        this.setData({
          audioPath: '', audioDuration: 0, audioOrigin: '',
          recordingDurationText: '00:00', levelBars: waveformBars(0),
        })
        feedback.error(message)
      },
    })
  },
  async load() {
    if (!this.itemId) { this.setData({ loading: false, loadError: '缺少作业编号' }); return }
    this.setData({ loading: true, loadError: '' })
    try {
      const api = getApp().globalData.api
      const item = await api.item(this.itemId)
      const task = await api.task(item.taskId)
      const configuration = task.configuration || {}
      const editable = editableStatuses.has(item.status)
      const readOnly = readOnlyStatuses.has(item.status) || !editable
      const activeRecording = item.status === 'RECORDING_PENDING' || item.status === 'REWORK_PENDING'
      await this.session?.dispose()
      this.session = null
      this.setData({
        item, configuration, text: item.currentResult?.text || '', statusText: statusText[item.status] || item.status,
        referenceAudioDurationMillis: 0, referenceVideoDurationMillis: 0,
        referenceAudioPath: '', referenceVideoPath: '',
        editable, readOnly, canRelease: activeRecording, canDiscard: activeRecording,
        isDiscarded: item.status === 'DISCARDED',
        discardedAtText: dateTimeText(item.currentDiscard?.discardedAt),
        submitLabel: item.status === 'SUBMITTED' ? '保存修改' : '提交作业',
        showAutoClaimNext: item.status === 'RECORDING_PENDING' || item.status === 'REWORK_PENDING',
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
        audioOrigin: resultAudioPath ? 'existing' : '',
        audioDuration: resultDuration, recordingDurationText: durationText(resultDuration),
        recordState: resultAudioPath ? 'stopped' : 'idle',
      })
      if (referenceAudio.status === 'rejected') feedback.error('参考音频加载失败')
      if (referenceVideo.status === 'rejected') feedback.error('参考视频加载失败')
      if (resultAudio.status === 'rejected') feedback.error('已提交录音加载失败')
      if (editable) this.setupRecorder()
    } catch (error) {
      const loadError = error.network ? '网络链接失败，请检查网络。' : (error.message || '加载作业失败')
      this.setData({ loadError })
    } finally {
      this.setData({ loading: false })
    }
  },
  startRecord() {
    if (!this.data.editable || !this.session) return
    this.clearRecording()
    this.session.start()
  },
  clearRecording() {
    const { audioPath, audioOrigin } = this.data
    if (audioPath && audioOrigin === 'local') {
      try { wx.getFileSystemManager().unlinkSync(audioPath) } catch {}
    }
    this.setData({
      audioPath: '', audioDuration: 0, audioOrigin: '',
      recordingDurationText: '00:00', recordState: 'idle', levelBars: waveformBars(0),
    })
  },
  deleteRecording() {
    if (!this.data.editable || !this.data.audioPath || this.data.recordState !== 'stopped') return
    const existing = this.data.audioOrigin === 'existing'
    wx.showModal({
      title: '删除录音',
      content: existing
        ? '删除后需点击“保存修改”才会同步移除已提交录音。'
        : '删除后可重新录制，文本结果不会被清空。',
      confirmText: '删除',
      confirmColor: '#be123c',
      success: result => {
        if (!result.confirm) return
        this.clearRecording()
        feedback.success('录音已删除')
      },
    })
  },
  pauseRecord() {
    if (!this.data.editable) return
    this.setData({ levelBars: waveformBars(0) })
    this.session?.pause()
  },
  resumeRecord() {
    if (!this.data.editable) return
    this.session?.resume()
  },
  async stopRecord() {
    if (!this.data.editable || !this.session) return
    try {
      const result = await this.session.stop()
      this.setData({
        audioPath: result.filePath, audioDuration: result.durationMillis, audioOrigin: 'local',
      })
    } catch (error) { feedback.error(error.message || '录音保存失败') }
  },
  textInput(event) { if (this.data.editable) this.setData({ text: event.detail.value }) },
  copyReferenceText() {
    const data = this.data.item.referenceText
    if (!data) return
    wx.setClipboardData({
      data,
      success: () => feedback.success('参考文本已复制'),
      fail: () => feedback.error('复制失败，请重试'),
    })
  },
  referenceAudioDurationChange(event) {
    this.setData({ referenceAudioDurationMillis: Math.max(0, Number(event.detail?.durationMillis) || 0) })
  },
  referenceVideoLoadedMetadata(event) {
    this.setData({ referenceVideoDurationMillis: Math.max(0, Math.round((Number(event.detail?.duration) || 0) * 1000)) })
  },
  audioPlaybackError(event) {
    feedback.error(event.detail?.message || '音频播放失败')
  },
  referenceAudioPlaybackError(event) {
    const message = event.detail?.message || '参考音频播放失败'
    feedback.error(message)
  },
  videoPlaybackError() {
    const message = '参考视频播放失败'
    feedback.error(message)
  },
  autoClaimNextChange(event) {
    const enabled = !!event.detail.value
    this.setData({ autoClaimNextEnabled: enabled })
    wx.setStorageSync('autoClaimNextEnabled', enabled)
  },
  nextClaimFailureMessage(error) {
    if (error.code === 'NO_AVAILABLE_ITEM') return '提交成功，当前任务池暂无下一条'
    if (error.code === 'TASK_NOT_RUNNING') return '提交成功，任务已停止领取'
    if (error.code === 'TASK_GRANT_REQUIRED') return '提交成功，采集权限已失效'
    return '提交成功，但下一条领取失败，请在任务数据中查看'
  },
  redirectToTaskData(taskId) {
    wx.redirectTo({ url: `/pages/work-list/index?taskId=${encodeURIComponent(taskId)}` })
  },
  async submit() {
    if (!this.data.editable) return
    const hasReferenceAudio = !!(this.data.item.referenceAudioUrl || this.data.item.referenceAudioMediaId)
    const hasReferenceVideo = !!(this.data.item.referenceVideoUrl || this.data.item.referenceVideoMediaId)
    if (hasReferenceAudio && this.data.referenceAudioDurationMillis <= 0
      || hasReferenceVideo && this.data.referenceVideoDurationMillis <= 0) {
      feedback.error('参考媒体尚未加载完成，请稍后重试')
      return
    }
    const validation = validateSubmission(this.data.configuration, { audio: this.data.audioPath, text: this.data.text })
    if (validation) { feedback.error(validation.message); return }
    this.setData({ submitting: true })
    const api = getApp().globalData.api
    const sourceStatus = this.data.item.status
    try {
      await api.submit(this.itemId, {
        operationId: api.operationId('submit'), assignmentId: this.data.item.assignmentId,
        expectedRevision: this.data.item.revision, text: this.data.text.trim(), audioPath: this.data.audioPath,
        referenceAudioDurationMillis: this.data.referenceAudioDurationMillis,
        referenceVideoDurationMillis: this.data.referenceVideoDurationMillis,
      })
      if (sourceStatus === 'SUBMITTED') {
        feedback.success('修改已保存')
        setTimeout(() => wx.navigateBack({ delta: 1 }), 450)
      } else if (this.data.autoClaimNextEnabled) {
        try {
          const next = await claimNextWithRetry({ taskId: this.data.item.taskId, operationId: api.operationId, start: api.start })
          feedback.success('提交成功，已领取下一条')
          wx.redirectTo({ url: `/pages/work/index?itemId=${encodeURIComponent(next.id)}` })
        } catch (claimError) {
          feedback.error(this.nextClaimFailureMessage(claimError))
          this.redirectToTaskData(this.data.item.taskId)
        }
      } else {
        feedback.success('提交成功')
        setTimeout(() => wx.navigateBack({ delta: 1 }), 450)
      }
    } catch (error) {
      if (error.code === 'STALE_STATE') {
        await this.load()
        const message = '状态已变化，已刷新为最新内容'
        feedback.error(message)
      } else {
        const message = error.message || '提交失败'
        feedback.error(message)
      }
    } finally { this.setData({ submitting: false }) }
  },
  release() {
    if (!this.data.canRelease) return
    wx.showModal({
      title: '释放回数据池', content: '', editable: true, placeholderText: '可选：填写释放备注', confirmColor: '#2563eb',
      success: async result => {
        if (!result.confirm) return
        this.setData({ releasing: true })
        try {
          const api = getApp().globalData.api
          await api.release(this.itemId, this.data.item.revision, api.operationId('release'), (result.content || '').trim())
          feedback.success('已释放到数据池')
          setTimeout(() => wx.navigateBack({ delta: 1 }), 400)
        } catch (error) {
          feedback.error(error.message || '释放失败')
        } finally { this.setData({ releasing: false }) }
      },
    })
  },
  discard() {
    if (!this.data.canDiscard) return
    wx.showModal({
      title: '标记为无效数据', content: '', editable: true, placeholderText: '请输入无效原因（必填）', confirmColor: '#c2413b',
      success: result => {
        if (!result.confirm) return
        const reason = (result.content || '').trim()
        if (!reason || reason.length > 200) {
          feedback.error('无效原因必须为 1 到 200 个字符')
          return
        }
        wx.showModal({
          title: '确认标记无效', content: '标记后数据进入“废弃数据”，不会回到可领取池。',
          confirmColor: '#c2413b',
          success: async confirmation => {
            if (!confirmation.confirm) return
            this.setData({ discarding: true })
            try {
              const api = getApp().globalData.api
              await api.discard(this.itemId, this.data.item.revision, api.operationId('discard'), reason)
              feedback.success('已标记为无效数据')
              setTimeout(() => wx.navigateBack({ delta: 1 }), 400)
            } catch (error) {
              if (error.code === 'STALE_STATE') await this.load()
              feedback.error(error.message || '标记无效失败')
            } finally { this.setData({ discarding: false }) }
          },
        })
      },
    })
  },
  restore() {
    if (!this.data.isDiscarded) return
    wx.showModal({
      title: '恢复数据', content: '确认恢复到废弃前的录制状态？', confirmColor: '#2563eb',
      success: async result => {
        if (!result.confirm) return
        this.setData({ restoring: true })
        try {
          const api = getApp().globalData.api
          await api.restore(this.itemId, this.data.item.revision, api.operationId('restore'))
          feedback.success('数据已恢复')
          await this.load()
        } catch (error) {
          if (error.code === 'STALE_STATE') await this.load()
          feedback.error(error.message || '恢复失败')
        } finally { this.setData({ restoring: false }) }
      },
    })
  },
})
