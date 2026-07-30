<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps({
  fromDate: { type: String, default: '' },
  toDate: { type: String, default: '' },
  today: { type: String, required: true },
})
const emit = defineEmits(['change'])
const root = ref(null)
const trigger = ref(null)
const open = ref(false)
const draftStart = ref('')
const displayMonth = ref('')
const weekdays = ['一', '二', '三', '四', '五', '六', '日']

function monthStart(value) {
  const source = /^\d{4}-\d{2}-\d{2}$/.test(value || '') ? value : props.today
  return `${source.slice(0, 7)}-01`
}
function dateFromIso(value) {
  const [year, month, day] = value.split('-').map(Number)
  return new Date(Date.UTC(year, month - 1, day))
}
function isoDate(value) {
  return value.toISOString().slice(0, 10)
}
function shiftDays(value, days) {
  const date = dateFromIso(value)
  date.setUTCDate(date.getUTCDate() + days)
  return isoDate(date)
}
function shiftMonth(delta) {
  const date = dateFromIso(displayMonth.value)
  date.setUTCMonth(date.getUTCMonth() + delta)
  displayMonth.value = isoDate(date)
}
const monthTitle = computed(() => {
  const date = dateFromIso(displayMonth.value || monthStart(props.fromDate))
  return `${date.getUTCFullYear()}年${date.getUTCMonth() + 1}月`
})
const calendarDays = computed(() => {
  const first = dateFromIso(displayMonth.value || monthStart(props.fromDate))
  const mondayOffset = (first.getUTCDay() + 6) % 7
  const firstCell = shiftDays(isoDate(first), -mondayOffset)
  return Array.from({ length: 42 }, (_, index) => {
    const date = shiftDays(firstCell, index)
    return {
      date,
      day: Number(date.slice(-2)),
      currentMonth: date.slice(0, 7) === displayMonth.value.slice(0, 7),
    }
  })
})
const displayValue = computed(() => {
  if (!props.fromDate && !props.toDate) return '全部日期'
  const format = value => value ? value.replaceAll('-', '/') : '—'
  return `${format(props.fromDate)} — ${format(props.toDate)}`
})
function rangeStart() { return draftStart.value || props.fromDate }
function rangeEnd() { return draftStart.value ? draftStart.value : props.toDate }
function dayClass(date) {
  const start = rangeStart()
  const end = rangeEnd()
  return {
    'is-today': date === props.today,
    'is-start': date === start,
    'is-end': date === end,
    'is-in-range': !!start && !!end && date >= start && date <= end,
  }
}
async function openPicker() {
  displayMonth.value = monthStart(props.fromDate || props.today)
  draftStart.value = ''
  open.value = true
  await nextTick()
}
function closePicker() {
  open.value = false
  draftStart.value = ''
}
function chooseDate(date) {
  if (!draftStart.value) {
    draftStart.value = date
    return
  }
  const fromDate = date < draftStart.value ? date : draftStart.value
  const toDate = date < draftStart.value ? draftStart.value : date
  emit('change', { fromDate, toDate })
  closePicker()
  trigger.value?.focus()
}
function keydown(event) {
  if (event.key === 'Escape' && open.value) {
    event.preventDefault()
    closePicker()
    trigger.value?.focus()
  }
}
function outside(event) {
  if (root.value && !root.value.contains(event.target)) closePicker()
}
onMounted(() => {
  document.addEventListener('pointerdown', outside)
  document.addEventListener('keydown', keydown)
})
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', outside)
  document.removeEventListener('keydown', keydown)
})
defineExpose({ openPicker })
</script>

<template>
  <div ref="root" class="date-range-picker">
    <button ref="trigger" type="button" class="date-range-trigger" :aria-expanded="open" @click="open ? closePicker() : openPicker()">
      <span>{{ displayValue }}</span>
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M7 3v3m10-3v3M4 9h16M5 5h14a1 1 0 0 1 1 1v14H4V6a1 1 0 0 1 1-1Z"/></svg>
    </button>
    <div v-if="open" class="date-range-popover">
      <div class="date-range-month">
        <button type="button" aria-label="上个月" @click="shiftMonth(-1)">‹</button>
        <strong>{{ monthTitle }}</strong>
        <button type="button" aria-label="下个月" @click="shiftMonth(1)">›</button>
      </div>
      <div class="date-range-weekdays" aria-hidden="true">
        <span v-for="weekday in weekdays" :key="weekday">{{ weekday }}</span>
      </div>
      <div class="date-range-grid">
        <button
          v-for="day in calendarDays"
          :key="day.date"
          type="button"
          :data-date="day.date"
          :class="[dayClass(day.date), { 'is-outside': !day.currentMonth }]"
          :aria-label="day.date"
          @click="chooseDate(day.date)"
        >{{ day.day }}</button>
      </div>
      <p>{{ draftStart ? '请选择结束日期' : '请选择开始日期' }}</p>
    </div>
  </div>
</template>
