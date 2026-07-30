<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AsyncState from '../../../components/admin/AsyncState.vue'
import DateRangePicker from '../../../components/form/DateRangePicker.vue'
import PageActions from '../../../components/admin/PageActions.vue'
import PaginationControls from '../../../components/admin/PaginationControls.vue'
import StageSummaryPanel from '../../../components/admin/StageSummaryPanel.vue'
import SubmissionHourDistribution from '../../../components/admin/SubmissionHourDistribution.vue'
import TaskSearchSelect from '../../../components/form/TaskSearchSelect.vue'
import { reportApi } from '../../../lib/reportApi.js'
import { taskApi } from '../../../lib/taskApi.js'
import { useNotifications } from '../../../composables/useNotifications.js'

const route = useRoute()
const router = useRouter()
const notifications = useNotifications()
const shanghaiToday = () => new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
}).format(new Date())
const shiftDate = (value, days) => {
  const date = new Date(`${value}T00:00:00Z`)
  date.setUTCDate(date.getUTCDate() + days)
  return new Date(date).toISOString().slice(0, 10)
}
const today = shanghaiToday()
const taskId = ref(String(route.query.taskId || ''))
const fromDate = ref(String(route.query.fromDate ?? today))
const toDate = ref(String(route.query.toDate ?? today))
const activePreset = ref(route.query.fromDate || route.query.toDate ? 'custom' : 'today')
const sortBy = ref(String(route.query.sortBy || 'completionCount'))
const sortDirection = ref(route.query.sortDirection === 'asc' ? 'asc' : 'desc')
const page = ref(Math.max(0, Number(route.query.page) || 0))
const summary = ref(null)
const rankings = ref([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const dateRangePicker = ref(null)
const pageSize = 20
let requestSequence = 0
const seconds = (value) => `${((Number(value) || 0) / 1000).toFixed(1)} 秒`
const time = (value) => value ? new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
}).format(new Date(value)) : '-'
const reportParams = computed(() => ({
  ...(fromDate.value ? { fromDate: fromDate.value } : {}),
  ...(toDate.value ? { toDate: toDate.value } : {}),
}))
const presets = [
  ['today', '今天'], ['yesterday', '昨天'], ['seven-days', '近 7 日'],
  ['month', '本月'], ['all', '全部'], ['custom', '自定义范围'],
]
async function applyPreset(key) {
  activePreset.value = key
  if (key === 'custom') {
    await dateRangePicker.value?.openPicker()
    return
  }
  if (key === 'today') fromDate.value = toDate.value = today
  if (key === 'yesterday') fromDate.value = toDate.value = shiftDate(today, -1)
  if (key === 'seven-days') { fromDate.value = shiftDate(today, -6); toDate.value = today }
  if (key === 'month') { fromDate.value = `${today.slice(0, 7)}-01`; toDate.value = today }
  if (key === 'all') fromDate.value = toDate.value = ''
  page.value = 0
  if (taskId.value) await search(true)
}
async function changeTask(value) {
  taskId.value = value
  page.value = 0
  if (taskId.value) await search(true)
  else {
    requestSequence += 1
    summary.value = null
    rankings.value = []
    total.value = 0
    loading.value = false
  }
}
async function changeDateRange(range) {
  fromDate.value = range.fromDate
  toDate.value = range.toDate
  activePreset.value = 'custom'
  page.value = 0
  if (taskId.value) await search(true)
}
async function changeSort(field) {
  if (sortBy.value === field) sortDirection.value = sortDirection.value === 'desc' ? 'asc' : 'desc'
  else {
    sortBy.value = field
    sortDirection.value = 'desc'
  }
  page.value = 0
  await search(true)
}
function validate() {
  if (!taskId.value) { notifications.error('请选择任务'); return false }
  if (fromDate.value && toDate.value && fromDate.value > toDate.value) {
    notifications.error('开始日期不能晚于结束日期'); return false
  }
  return true
}
function syncQuery() {
  return router.replace({
    name: 'collector-statistics',
    query: {
      taskId: taskId.value,
      ...reportParams.value,
      sortBy: sortBy.value,
      sortDirection: sortDirection.value,
      page: page.value || undefined,
    },
  })
}
async function search(refresh = false) {
  if (!validate()) return
  const requestId = ++requestSequence
  loading.value = true
  if (!summary.value) loadError.value = ''
  try {
    await syncQuery()
    const [nextSummary, rankingPage] = await Promise.all([
      reportApi.tasks({ taskId: taskId.value, ...reportParams.value }),
      reportApi.taskCollectors(taskId.value, {
        ...reportParams.value,
        sortBy: sortBy.value,
        sortDirection: sortDirection.value,
        page: page.value,
        size: pageSize,
      }),
    ])
    if (requestId !== requestSequence) return
    summary.value = nextSummary
    rankings.value = rankingPage.items || []
    total.value = Number(rankingPage.total) || 0
  } catch (error) {
    if (requestId !== requestSequence) return
    if (refresh || summary.value) notifications.error(error.message)
    else loadError.value = error.message
  } finally {
    if (requestId === requestSequence) loading.value = false
  }
}
async function changePage(value) { page.value = value; await search(true) }
const ariaSort = field => sortBy.value === field
  ? (sortDirection.value === 'asc' ? 'ascending' : 'descending')
  : 'none'
const sortArrow = field => sortBy.value === field
  ? (sortDirection.value === 'asc' ? '↑' : '↓')
  : '↕'
function openCollector(row) {
  router.push({
    name: 'collector-statistics-detail',
    params: { taskId: taskId.value, collectorId: row.collectorId },
    query: {
      ...reportParams.value,
      sortBy: sortBy.value,
      sortDirection: sortDirection.value,
      page: page.value || undefined,
    },
  })
}
function startDownload(url) {
  const link = document.createElement('a')
  link.href = url
  link.download = ''
  document.body.appendChild(link)
  link.click()
  link.remove()
}
async function exportCsv() {
  if (!validate()) return
  try {
    await taskApi.prepareExport(taskId.value, {}, reportParams.value)
    startDownload(taskApi.exportItems(taskId.value, {}, reportParams.value))
    notifications.success('CSV 导出已开始')
  } catch (error) { notifications.error(error.message || 'CSV 导出失败') }
}
onMounted(() => { if (taskId.value) search() })
</script>

<template>
  <section class="admin-page collector-report-page">
    <PageActions title="采集员统计" description="分别按首次提交和首次完成日期核对产出，数据可直接用于对表。">
      <button class="button-secondary" :disabled="!taskId" @click="exportCsv">导出当前对表 CSV</button>
    </PageActions>
    <section class="business-card report-control-card">
      <div class="report-controls">
        <TaskSearchSelect :model-value="taskId" @update:model-value="changeTask" />
        <div class="date-toolbar">
          <div class="date-presets" aria-label="日期快捷选择">
          <button v-for="[key,label] in presets" :key="key" type="button"
            :class="{ 'is-active': activePreset === key }" @click="applyPreset(key)">{{ label }}</button>
          </div>
          <DateRangePicker ref="dateRangePicker" :from-date="fromDate" :to-date="toDate"
            :today="today" @change="changeDateRange" />
        </div>
        <span v-if="loading && summary" class="report-refreshing">正在更新统计…</span>
      </div>
    </section>
    <AsyncState :loading="loading && !summary" :error="loadError" :empty="!summary"
      empty-text="请选择任务，选择后将自动加载统计" @retry="search">
      <StageSummaryPanel :summary="summary" />
      <SubmissionHourDistribution :values="summary?.submissionHourDistribution" />
      <section class="business-card report-section">
        <div class="business-heading"><div><h3>任务内采集员</h3><p>点击人员进入独立详情页，查看每日统计与两套明细。</p></div><span>共 {{ total }} 人</span></div>
        <div class="business-table-wrap">
          <table class="business-table report-table">
            <thead>
              <tr>
                <th rowspan="2">排名</th><th rowspan="2">采集员</th>
                <th colspan="4">提交统计</th><th colspan="4">完成统计</th>
                <th rowspan="2" :aria-sort="ariaSort('firstSubmissionAt')"><button type="button" class="sort-header" data-sort="firstSubmissionAt" @click="changeSort('firstSubmissionAt')">首次提交 <span>{{ sortArrow('firstSubmissionAt') }}</span></button></th>
                <th rowspan="2" :aria-sort="ariaSort('latestSubmissionAt')"><button type="button" class="sort-header" data-sort="latestSubmissionAt" @click="changeSort('latestSubmissionAt')">最近提交 <span>{{ sortArrow('latestSubmissionAt') }}</span></button></th>
                <th rowspan="2">高峰时段</th>
              </tr>
              <tr>
                <th :aria-sort="ariaSort('submissionCount')"><button type="button" class="sort-header" data-sort="submissionCount" @click="changeSort('submissionCount')">条数 <span>{{ sortArrow('submissionCount') }}</span></button></th>
                <th :aria-sort="ariaSort('submissionRecordingDurationMillis')"><button type="button" class="sort-header" data-sort="submissionRecordingDurationMillis" @click="changeSort('submissionRecordingDurationMillis')">最终录音 <span>{{ sortArrow('submissionRecordingDurationMillis') }}</span></button></th>
                <th :aria-sort="ariaSort('submissionReferenceAudioDurationMillis')"><button type="button" class="sort-header" data-sort="submissionReferenceAudioDurationMillis" @click="changeSort('submissionReferenceAudioDurationMillis')">参考音频 <span>{{ sortArrow('submissionReferenceAudioDurationMillis') }}</span></button></th>
                <th :aria-sort="ariaSort('submissionReferenceVideoDurationMillis')"><button type="button" class="sort-header" data-sort="submissionReferenceVideoDurationMillis" @click="changeSort('submissionReferenceVideoDurationMillis')">参考视频 <span>{{ sortArrow('submissionReferenceVideoDurationMillis') }}</span></button></th>
                <th :aria-sort="ariaSort('completionCount')"><button type="button" class="sort-header" data-sort="completionCount" @click="changeSort('completionCount')">条数 <span>{{ sortArrow('completionCount') }}</span></button></th>
                <th :aria-sort="ariaSort('completionRecordingDurationMillis')"><button type="button" class="sort-header" data-sort="completionRecordingDurationMillis" @click="changeSort('completionRecordingDurationMillis')">最终录音 <span>{{ sortArrow('completionRecordingDurationMillis') }}</span></button></th>
                <th :aria-sort="ariaSort('completionReferenceAudioDurationMillis')"><button type="button" class="sort-header" data-sort="completionReferenceAudioDurationMillis" @click="changeSort('completionReferenceAudioDurationMillis')">参考音频 <span>{{ sortArrow('completionReferenceAudioDurationMillis') }}</span></button></th>
                <th :aria-sort="ariaSort('completionReferenceVideoDurationMillis')"><button type="button" class="sort-header" data-sort="completionReferenceVideoDurationMillis" @click="changeSort('completionReferenceVideoDurationMillis')">参考视频 <span>{{ sortArrow('completionReferenceVideoDurationMillis') }}</span></button></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row,index) in rankings" :key="row.collectorId" class="clickable-row" @click="openCollector(row)">
                <td>{{ page * pageSize + index + 1 }}</td>
                <td><strong>{{ row.collectorName || '未设置姓名' }}</strong><small>{{ row.collectorId }}</small></td>
                <td>{{ row.submissions?.count || 0 }}</td><td>{{ seconds(row.submissions?.recordingDurationMillis) }}</td><td>{{ seconds(row.submissions?.referenceAudioDurationMillis) }}</td><td>{{ seconds(row.submissions?.referenceVideoDurationMillis) }}</td>
                <td>{{ row.completions?.count || 0 }}</td><td>{{ seconds(row.completions?.recordingDurationMillis) }}</td><td>{{ seconds(row.completions?.referenceAudioDurationMillis) }}</td><td>{{ seconds(row.completions?.referenceVideoDurationMillis) }}</td>
                <td>{{ time(row.firstSubmissionAt) }}</td><td>{{ time(row.latestSubmissionAt) }}</td><td>{{ row.peakSubmissionHour == null ? '-' : `${String(row.peakSubmissionHour).padStart(2, '0')}:00` }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-if="!rankings.length" class="business-note">当前范围内暂无采集员统计。</p>
        <PaginationControls :page="page" :size="pageSize" :total="total" @change="changePage" />
      </section>
    </AsyncState>
  </section>
</template>

<style scoped>
.collector-report-page{display:grid;gap:18px}.report-control-card{padding:18px}.report-controls{display:grid;gap:15px}.report-controls>.task-search-select{max-width:720px}.date-toolbar{display:flex;align-items:center;gap:12px}.date-presets{display:flex;gap:7px;flex-wrap:wrap}.date-presets button{border:1px solid var(--border);border-radius:999px;background:var(--card);color:var(--muted-foreground);padding:7px 13px;cursor:pointer}.date-presets button.is-active{border-color:color-mix(in srgb,var(--primary) 45%,var(--border));background:color-mix(in srgb,var(--primary) 10%,var(--card));color:var(--primary)}.date-toolbar>.date-range-picker{margin-left:auto}.report-refreshing{color:var(--primary);font-size:13px}.report-section{margin-top:0}.report-table{min-width:1480px}.report-table thead tr:first-child th{text-align:center;background:color-mix(in srgb,var(--primary) 5%,var(--card))}.sort-header{display:inline-flex;align-items:center;justify-content:center;gap:5px;width:100%;padding:4px;background:transparent;color:inherit;font:inherit;font-weight:700;white-space:nowrap;cursor:pointer}.sort-header span{color:var(--primary);font-size:12px}.clickable-row{cursor:pointer}.clickable-row:hover{background:color-mix(in srgb,var(--primary) 7%,var(--card))}.clickable-row small{display:block;margin-top:3px;color:var(--muted-foreground)}@media(max-width:900px){.date-toolbar{align-items:stretch;flex-direction:column}.date-toolbar>.date-range-picker{width:100%;margin-left:0}}@media(max-width:720px){.report-control-card{padding:14px}.date-presets{gap:6px}.date-presets button{padding:7px 11px}}
</style>
