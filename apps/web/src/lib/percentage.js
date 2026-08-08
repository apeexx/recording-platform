export function percentageValue(value, total) {
  const numerator = Number(value)
  const denominator = Number(total)
  if (!Number.isFinite(numerator) || !Number.isFinite(denominator) || denominator <= 0) return 0
  return Math.min(100, Math.max(0, numerator / denominator * 100))
}

export function formatPercentage(value, total) {
  return `${percentageValue(value, total).toFixed(2)}%`
}
