const FORMATTER = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

export function businessDate(value = new Date()) {
  const instant = value instanceof Date ? value : new Date(value)
  return FORMATTER.format(new Date(instant.getTime() - 4 * 60 * 60 * 1000))
}

export function shiftBusinessDate(value, days) {
  const date = new Date(`${value}T00:00:00Z`)
  date.setUTCDate(date.getUTCDate() + days)
  return date.toISOString().slice(0, 10)
}

export function businessMonthStart(value) {
  return `${value.slice(0, 7)}-01`
}
