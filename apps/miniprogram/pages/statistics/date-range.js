const SHANGHAI_OFFSET_MILLIS = 8 * 60 * 60 * 1000
const BUSINESS_DAY_OFFSET_MILLIS = 4 * 60 * 60 * 1000

function pad(value) {
  return String(value).padStart(2, '0')
}

function dateFromShiftedMillis(millis) {
  const date = new Date(millis)
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`
}

function businessDate(now = new Date()) {
  return dateFromShiftedMillis(now.getTime() + SHANGHAI_OFFSET_MILLIS - BUSINESS_DAY_OFFSET_MILLIS)
}

function shiftDate(dateText, days) {
  const [year, month, day] = dateText.split('-').map(Number)
  return dateFromShiftedMillis(Date.UTC(year, month - 1, day + days))
}

function normalizeRange(first, second) {
  return first <= second
    ? {fromDate:first, toDate:second}
    : {fromDate:second, toDate:first}
}

function presetRanges(now = new Date()) {
  const today = businessDate(now)
  return {
    today: {fromDate:today, toDate:today},
    yesterday: {fromDate:shiftDate(today, -1), toDate:shiftDate(today, -1)},
    last7: {fromDate:shiftDate(today, -6), toDate:today},
    month: {fromDate:`${today.slice(0, 7)}-01`, toDate:today},
    all: {fromDate:'', toDate:''},
  }
}

function monthCells(monthText, start = '', end = '') {
  const [year, month] = monthText.split('-').map(Number)
  const firstWeekday = new Date(Date.UTC(year, month - 1, 1)).getUTCDay()
  const days = new Date(Date.UTC(year, month, 0)).getUTCDate()
  const cells = []
  for (let index = 0; index < firstWeekday; index += 1) cells.push({key:`blank-${index}`, blank:true})
  for (let day = 1; day <= days; day += 1) {
    const date = `${year}-${pad(month)}-${pad(day)}`
    const normalized = start && end ? normalizeRange(start, end) : null
    cells.push({
      key:date, date, day,
      selected: date === start || date === end,
      inRange: !!normalized && date >= normalized.fromDate && date <= normalized.toDate,
      isToday: date === businessDate(),
    })
  }
  return cells
}

function shiftMonth(monthText, delta) {
  const [year, month] = monthText.split('-').map(Number)
  const value = new Date(Date.UTC(year, month - 1 + delta, 1))
  return `${value.getUTCFullYear()}-${pad(value.getUTCMonth() + 1)}`
}

module.exports = { businessDate, shiftDate, normalizeRange, presetRanges, monthCells, shiftMonth }
