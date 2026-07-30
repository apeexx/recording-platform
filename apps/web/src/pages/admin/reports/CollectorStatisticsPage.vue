<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AsyncState from '../../../components/admin/AsyncState.vue'
import BaseSelect from '../../../components/form/BaseSelect.vue'
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
const page = ref(Math.max(0, Number(route.query.page) || 0))
const summary = ref(null)
const rankings = ref([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const pageSize = 20
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
const sortOptions = [
  { value: 'completionCount', label: '完成条数' },
  { value: 'submissionCount', label: '提交条数' },
  { value: 'completionRecordingDurationMillis', label: '完成·最终录音' },
  { value: 'submissionRecordingDurationMillis', label: '提交·最终录音' },
  { value: 'completionReferenceAudioDurationMillis', label: '完成·参考音频' },
  { value: 'submissionReferenceAudioDurationMillis', label: '提交·参考音频' },
  { value: 'completionReferenceVideoDurationMillis', label: '完成·参考视频' },
  { value: 'submissionReferenceVideoDurationMillis', label: '提交·参考视频' },
  { value: 'firstSubmissionAt', label: '首次提交时间' },
  { value: 'latestSubmissionAt', label: '最近提交时间' },
]

function applyPreset(key) {
  activePreset.value = key
  if (key === 'today') fromDate.value = toDate.value = today
  if (key === 'yesterday') fromDate.value = toDate.value = shiftDate(today, -1)
  if (key === 'seven-days') { fromDate.value = shiftDate(today, -6); toDate.value = today }
  if (key === 'month') { fromDate.value = `${today.slice(0, 7)}-01`; toDate.value = today }
  if (key === 'all') fromDate.value = toDate.value = ''
}
function changeTask(value) {
  taskId.value = value
  summary.value = null
  rankings.value = []
  total.value = 0
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
    query: { taskId: taskId.value, ...reportParams.value, sortBy: sortBy.value, page: page.value || undefined },
  })
}
async function search(refresh = false) {
  if (!validate()) return
  loading.value = true
  if (!summary.value) loadError.value = ''
  try {
    await syncQuery()
    const [nextSummary, rankingPage] = await Promise.all([
      reportApi.tasks({ taskId: taskId.value, ...reportParams.value }),
      reportApi.taskCollectors(taskId.value, {
        ...reportParams.value, sortBy: sortBy.value, page: page.value, size: pageSize,
      }),
    ])
    summary.value = nextSummary
    rankings.value = rankingPage.items || []
    total.value = Number(rankingPage.total) || 0
  } catch (error) {
    if (refresh || summary.value) notifications.error(error.message)
    else loadError.value = error.message
  } finally { loading.value = false }
}
async function submitSearch() { page.value = 0; await search() }
async function changePage(value) { page.value = value; await search(true) }
function openCollector(row) {
  router.push({
    name: 'collector-statistics-detail',
    params: { taskId: taskId.value, collectorId: row.collectorId },
    query: { ...reportParams.value, sortBy: sortBy.value, page: page.value || undefined },
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
      <form class="report-controls" novalidate @submit.prevent="submitSearch">
        <TaskSearchSelect :model-value="taskId" @update:model-value="changeTask" />
        <div class="date-presets" aria-label="日期快捷选择">
          <button v-for="[key,label] in presets" :key="key" type="button"
            :class="{ 'is-active': activePreset === key }" @click="applyPreset(key)">{{ label }}</button>
        </div>
        <div class="date-range">
          <label>开始日期 <input v-model="fromDate" type="date" @input="activePreset = 'custom'" /></label>
          <span>至</span>
          <label>结束日期 <input v-model="toDate" type="date" @input="activePreset = 'custom'" /></label>
        </div>
        <BaseSelect v-model="sortBy" :options="sortOptions" aria-label="采集员排序指标" />
        <button class="button-primary" :disabled="loading">{{ loading ? '查询中…' : '查询统计' }}</button>
      </form>
    </section>
    <AsyncState :loading="loading && !summary" :error="loadError" :empty="!summary"
      empty-text="请选择任务并查询统计" @retry="search">
      <StageSummaryPanel :summary="summary" />
      <SubmissionHourDistribution :values="summary?.submissionHourDistribution" />
      <section class="business-card report-section">
        <div class="business-heading"><div><h3>任务内采集员</h3><p>点击人员进入独立详情页，查看每日统计与两套明细。</p></div><span>共 {{ total }} 人</span></div>
        <div class="business-table-wrap">
          <table class="business-table report-table">
            <thead>
              <tr><th rowspan="2">排名</th><th rowspan="2">采集员</th><th colspan="4">提交统计</th><th colspan="4">完成统计</th><th rowspan="2">首次提交</th><th rowspan="2">最近提交</th><th rowspan="2">高峰时段</th></tr>
              <tr><th>条数</th><th>最终录音</th><th>参考音频</th><th>参考视频</th><th>条数</th><th>最终录音</th><th>参考音频</th><th>参考视频</th></tr>
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
.collector-report-page{display:grid;gap:18px}.report-control-card{padding:18px}.report-controls{display:grid;grid-template-columns:minmax(320px,1fr) auto auto auto;gap:14px;align-items:end}.date-presets{grid-column:1/-1;display:flex;gap:7px;flex-wrap:wrap}.date-presets button{border:1px solid var(--border);border-radius:999px;background:var(--card);color:var(--muted-foreground);padding:7px 13px;cursor:pointer}.date-presets button.is-active{border-color:color-mix(in srgb,var(--primary) 45%,var(--border));background:color-mix(in srgb,var(--primary) 10%,var(--card));color:var(--primary)}.date-range{display:flex;align-items:end;gap:8px}.date-range label{display:grid;gap:6px;color:var(--muted-foreground);font-size:12px}.date-range span{padding-bottom:10px;color:var(--muted-foreground)}.report-section{margin-top:0}.report-table{min-width:1420px}.report-table thead tr:first-child th{text-align:center;background:color-mix(in srgb,var(--primary) 5%,var(--card))}.clickable-row{cursor:pointer}.clickable-row:hover{background:color-mix(in srgb,var(--primary) 7%,var(--card))}.clickable-row small{display:block;margin-top:3px;color:var(--muted-foreground)}@media(max-width:1100px){.report-controls{grid-template-columns:1fr 1fr}.date-presets{grid-column:1/-1}}@media(max-width:720px){.report-controls{grid-template-columns:1fr}.date-range{align-items:stretch;flex-direction:column}.date-range span{display:none}}
</style>
