function pcm16Samples(arrayBuffer) { return new Int16Array(arrayBuffer) }
function pcmRms(arrayBuffer) {
  const samples = pcm16Samples(arrayBuffer)
  if (!samples.length) return 0
  let sum = 0
  for (const sample of samples) { const value = sample / 32768; sum += value * value }
  return Math.min(1, Math.sqrt(sum / samples.length))
}
function durationMillis(sampleCount, sampleRate) { return sampleRate > 0 ? Math.round(sampleCount * 1000 / sampleRate) : 0 }

const MIN_WAVE_HEIGHT = 8
const MAX_WAVE_HEIGHT = 100
const SILENCE_GATE = 0.02
const FULL_SCALE_LEVEL = 0.25
const BAR_WEIGHTS = [0.28, 0.5, 0.75, 1, 0.75, 0.5, 0.28]

function waveformBars(level) {
  const numericLevel = Number(level)
  if (!Number.isFinite(numericLevel) || numericLevel <= SILENCE_GATE) {
    return BAR_WEIGHTS.map(() => MIN_WAVE_HEIGHT)
  }
  const amplitude = Math.min(1, (numericLevel - SILENCE_GATE) / (FULL_SCALE_LEVEL - SILENCE_GATE))
  return BAR_WEIGHTS.map(weight => Math.round(
    MIN_WAVE_HEIGHT + (MAX_WAVE_HEIGHT - MIN_WAVE_HEIGHT) * amplitude * weight,
  ))
}

module.exports = { pcm16Samples, pcmRms, durationMillis, waveformBars }
