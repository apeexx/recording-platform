function recorderOptions(version) {
  const sampleRate = Number((version.sampleRates || [16000])[0])
  return {
    duration: Math.min(Number(version.maxDurationMillis || 600000), 600000),
    sampleRate,
    numberOfChannels: 1,
    encodeBitRate: sampleRate <= 8000 ? 24000 : 48000,
    format: String(version.recordingFormat || 'WAV').toLowerCase()
  }
}

function validateSubmission(version, result) {
  const hasAudio = !!result.audio
  const hasText = !!(result.text && result.text.trim())
  if (!version.textInputEnabled && !hasAudio) return { code:'AUDIO_REQUIRED', message:'该任务必须提交录音' }
  if (version.textInputEnabled && !hasAudio && !hasText) return { code:'RESULT_REQUIRED', message:'录音或文字至少提交一项' }
  return null
}

module.exports = { recorderOptions, validateSubmission }
