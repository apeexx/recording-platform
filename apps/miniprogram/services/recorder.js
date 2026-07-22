function recorderOptions(configuration) {
  const sampleRate = Number((configuration.sampleRates || [16000])[0])
  return {
    duration: Math.min(Number(configuration.maxDurationMillis || 600000), 600000),
    sampleRate,
    numberOfChannels: 1,
    encodeBitRate: sampleRate <= 8000 ? 24000 : 48000,
	format: 'PCM',
	frameSize: 4
  }
}

function validateSubmission(configuration, result) {
	const hasAudio = !!result.audio
	const hasText = !!(result.text && result.text.trim())
  if (configuration.resultType === 'TEXT' && !hasAudio && !hasText) return { code:'RESULT_REQUIRED', message:'文本或录音至少提交一项' }
	if (configuration.resultType === 'AUDIO' && !hasAudio) return { code:'AUDIO_REQUIRED', message:'该任务必须提交录音' }
	if (configuration.resultType === 'AUDIO' && hasText) return { code:'TEXT_NOT_ALLOWED', message:'该任务只允许提交录音' }
  return null
}

module.exports = { recorderOptions, validateSubmission }
