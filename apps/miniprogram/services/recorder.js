function recorderOptions(version) {
  const sampleRate = Number((version.sampleRates || [16000])[0])
  return {
    duration: Math.min(Number(version.maxDurationMillis || 600000), 600000),
    sampleRate,
    numberOfChannels: 1,
    encodeBitRate: sampleRate <= 8000 ? 24000 : 48000,
	format: 'PCM',
	frameSize: 4
  }
}

function validateSubmission(version, result) {
	const hasAudio = !!result.audio
	const hasText = !!(result.text && result.text.trim())
	if (!hasAudio) return { code:'AUDIO_REQUIRED', message:'录音任务必须提交录音' }
	if (version.resultType === 'TEXT' && !hasText) return { code:'TEXT_REQUIRED', message:'该任务必须提交文本' }
	if (version.resultType === 'AUDIO' && hasText) return { code:'TEXT_NOT_ALLOWED', message:'该任务只允许提交录音' }
  return null
}

module.exports = { recorderOptions, validateSubmission }
