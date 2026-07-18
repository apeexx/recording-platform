function pcm16Samples(arrayBuffer) { return new Int16Array(arrayBuffer) }
function pcmRms(arrayBuffer) {
  const samples = pcm16Samples(arrayBuffer)
  if (!samples.length) return 0
  let sum = 0
  for (const sample of samples) { const value = sample / 32768; sum += value * value }
  return Math.min(1, Math.sqrt(sum / samples.length))
}
function durationMillis(sampleCount, sampleRate) { return sampleRate > 0 ? Math.round(sampleCount * 1000 / sampleRate) : 0 }
module.exports = { pcm16Samples, pcmRms, durationMillis }
