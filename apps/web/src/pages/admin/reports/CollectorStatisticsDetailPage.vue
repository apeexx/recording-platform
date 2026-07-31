<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AsyncState from '../../../components/admin/AsyncState.vue'
import DateRangePicker from '../../../components/form/DateRangePicker.vue'
import PageActions from '../../../components/admin/PageActions.vue'
import PaginationControls from '../../../components/admin/PaginationControls.vue'
import StageSummaryPanel from '../../../components/admin/StageSummaryPanel.vue'
import SubmissionHourDistribution from '../../../components/admin/SubmissionHourDistribution.vue'
import { reportApi } from '../../../lib/reportApi.js'
import { taskApi } from '../../../lib/taskApi.js'
import { statusLabel } from '../../../lib/statusLabels.js'
import { useNotifications } from '../../../composables/useNotifications.js'
import { businessDate, businessMonthStart, shiftBusinessDate } from '../../../lib/businessDate.js'

const route = useRoute()
const router = useRouter()
const notifications = useNotifications()
const taskId = String(route.params.taskId)
const collectorId = String(route.params.collectorId)
const today = businessDate()
const fromDate = ref(String(route.query.fromDate || ''))
const toDate = ref(String(route.query.toDate || ''))
const activePreset = ref(route.query.fromDate || route.query.toDate ? 'custom' : 'all')
const dateRangePicker = ref(null)
const activeTab = ref(route.query.tab === 'completions' ? 'completions' : 'submissions')
const submissionPage = ref(Math.max(0, Number(route.query.submissionPage) || 0))
const completionPage = ref(Math.max(0, Number(route.query.completionPage) || 0))
const pageSize = ref([10, 20, 50].includes(Number(route.query.detailSize)) ? Number(route.query.detailSize) : 20)
const detail = ref(null)
const submissions = ref([])
const completions = ref([])
const submissionTotal = ref(0)
const completionTotal = ref(0)
const loading = ref(false)
const loadError = ref('')
let requestSequence = 0
const reportParams = computed(() => ({
  ...(fromDate.value ? { fromDate: fromDate.value } : {}),
  ...(toDate.value ? { toDate: toDate.value } : {}),
}))
const presets = [
  ['today', '今天'], ['yesterday', '昨天'], ['seven-days', '近 7 日'],
  ['month', '本月'], ['all', '全部'], ['custom', '自定义范围'],
]
const currentRows = computed(() => activeTab.value === 'submissions' ? submissions.value : completions.value)
const currentTotal = computed(() => activeTab.value === 'submissions' ? submissionTotal.value : completionTotal.value)
const currentPage = computed(() => activeTab.value === 'submissions' ? submissionPage.value : completionPage.value)
const seconds = (value) => `${((Number(value) || 0) / 1000).toFixed(1)} 秒`
const time = (value) => value ? new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit',
}).format(new Date(value)) : '-'

function detailQuery() {
  return {
    ...reportParams.value,
    sortBy: route.query.sortBy || undefined,
    sortDirection: route.query.sortDirection || undefined,
    page: route.query.page || undefined,
    tab: activeTab.value === 'completions' ? 'completions' : undefined,
    submissionPage: submissionPage.value || undefined,
    completionPage: completionPage.value || undefined,
    detailSize: pageSize.value === 20 ? undefined : pageSize.value,
  }
}
function syncQuery() {
  return router.replace({
    name: 'collector-statistics-detail',
    params: { taskId, collectorId },
    query: detailQuery(),
  })
}
async function applyPreset(key) {
  activePreset.value = key
  if (key === 'custom') {
    await dateRangePicker.value?.openPicker()
    return
  }
  if (key === 'today') fromDate.value = toDate.value = today
  if (key === 'yesterday') fromDate.value = toDate.value = shiftBusinessDate(today, -1)
  if (key === 'seven-days') {
    fromDate.value = shiftBusinessDate(today, -6)
    toDate.value = today
  }
  if (key === 'month') {
    fromDate.value = businessMonthStart(today)
    toDate.value = today
  }
  if (key === 'all') fromDate.value = toDate.value = ''
  submissionPage.value = 0
  completionPage.value = 0
  await syncQuery()
  await load(true)
}
async function changeDateRange(range) {
  fromDate.value = range.fromDate
  toDate.value = range.toDate
  activePreset.value = 'custom'
  submissionPage.value = 0
  completionPage.value = 0
  await syncQuery()
  await load(true)
}
async function load(refresh = false) {
  const sequence = ++requestSequence
  loading.value = true
  if (!detail.value) loadError.value = ''
  try {
    const [nextDetail, submissionResult, completionResult] = await Promise.all([
      reportApi.collectorTask(collectorId, taskId, reportParams.value),
      reportApi.collectorTaskSubmissions(collectorId, taskId, {
        ...reportParams.value, page: submissionPage.value, size: pageSize.value,
      }),
      reportApi.collectorTaskCompletions(collectorId, taskId, {
        ...reportParams.value, page: completionPage.value, size: pageSize.value,
      }),
    ])
    if (sequence !== requestSequence) return
    detail.value = nextDetail
    submissions.value = submissionResult.items || []
    completions.value = completionResult.items || []
    submissionTotal.value = Number(submissionResult.total) || 0
    completionTotal.value = Number(completionResult.total) || 0
  } catch (error) {
    if (sequence !== requestSequence) return
    if (refresh || detail.value) notifications.error(error.message)
    else loadError.value = error.message
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}
async function selectTab(tab) {
  activeTab.value = tab
  await syncQuery()
}
async function changePage(value) {
  if (activeTab.value === 'submissions') submissionPage.value = value
  else completionPage.value = value
  await syncQuery()
  await load(true)
}
async function changeSize(value) {
  pageSize.value = value
  submissionPage.value = 0
  completionPage.value = 0
  await syncQuery()
  await load(true)
}
function goBack() {
  router.push({
    name: 'collector-statistics',
    query: {
      taskId, ...reportParams.value,
      sortBy: route.query.sortBy || undefined,
      sortDirection: route.query.sortDirection || undefined,
      page: route.query.page || undefined,
      size: route.query.size || undefined,
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
  const filters = { collectorIds: [collectorId] }
  try {
    await taskApi.prepareExport(taskId, filters, reportParams.value)
    startDownload(taskApi.exportItems(taskId, filters, reportParams.value))
    notifications.success('此采集员 CSV 导出已开始')
  } catch (error) { notifications.error(error.message || 'CSV 导出失败') }
}
onMounted(() => load())
</script>

<template>
  <section class="admin-page detail-page">
    <PageActions :title="detail?.collectorName || '采集员详情'"
      :description="`${detail?.collectorId || collectorId} · ${detail?.taskCode || ''} ${detail?.taskName || ''} · 业务日按 Asia/Shanghai 04:00 至次日 04:00 统计`">
      <button class="button-secondary" @click="goBack">返回统计列表</button>
      <button class="button-primary" @click="exportCsv">导出此采集员 CSV</button>
    </PageActions>
    <section class="business-card detail-date-filter">
      <div class="date-presets" aria-label="日期快捷选择">
        <button v-for="[key,label] in presets" :key="key" type="button"
          :class="{ 'is-active': activePreset === key }" @click="applyPreset(key)">{{ label }}</button>
      </div>
      <DateRangePicker ref="dateRangePicker" :from-date="fromDate" :to-date="toDate"
        :today="today" @change="changeDateRange" />
    </section>
    <AsyncState :loading="loading && !detail" :error="loadError" :empty="!detail" @retry="load">
      <StageSummaryPanel :summary="detail?.summary" />
      <SubmissionHourDistribution :values="detail?.summary?.submissionHourDistribution" />
      <section class="business-card day-section">
        <div class="business-heading"><div><h3>提交与完成每日统计</h3><p>两阶段分别按首次提交日与首次完成日归属。</p></div></div>
        <div class="business-table-wrap">
          <table class="business-table day-table">
            <thead><tr><th rowspan="2">日期</th><th colspan="4">提交统计</th><th colspan="4">完成统计</th></tr><tr><th>条数</th><th>最终录音</th><th>参考音频</th><th>参考视频</th><th>条数</th><th>最终录音</th><th>参考音频</th><th>参考视频</th></tr></thead>
            <tbody><tr v-for="day in detail?.days || []" :key="day.date">
              <td>{{ day.date }}</td>
              <td>{{ day.submissions?.count || 0 }}</td><td>{{ seconds(day.submissions?.recordingDurationMillis) }}</td><td>{{ seconds(day.submissions?.referenceAudioDurationMillis) }}</td><td>{{ seconds(day.submissions?.referenceVideoDurationMillis) }}</td>
              <td>{{ day.completions?.count || 0 }}</td><td>{{ seconds(day.completions?.recordingDurationMillis) }}</td><td>{{ seconds(day.completions?.referenceAudioDurationMillis) }}</td><td>{{ seconds(day.completions?.referenceVideoDurationMillis) }}</td>
            </tr></tbody>
          </table>
        </div>
        <p v-if="!detail?.days?.length" class="business-note">当前日期范围内暂无每日统计。</p>
      </section>
      <section class="business-card detail-list">
        <div class="detail-tabs" role="tablist" aria-label="统计明细">
          <button :class="{ 'is-active': activeTab === 'submissions' }" role="tab" @click="selectTab('submissions')">提交明细（{{ submissionTotal }}）</button>
          <button :class="{ 'is-active': activeTab === 'completions' }" role="tab" @click="selectTab('completions')">完成明细（{{ completionTotal }}）</button>
        </div>
        <div class="business-table-wrap">
          <table class="business-table">
            <thead><tr><th>条目编号</th><th>状态</th><th>首次提交</th><th>最新提交</th><th>首次完成</th><th>文本</th><th>录音</th><th>最终录音</th><th>参考音频</th><th>参考视频</th></tr></thead>
            <tbody><tr v-for="row in currentRows" :key="row.itemId">
              <td>{{ row.itemCode }}</td><td>{{ statusLabel('item', row.currentItemStatus) }}</td>
              <td>{{ time(row.firstSubmittedAt) }}</td><td>{{ time(row.latestSubmittedAt) }}</td><td>{{ time(row.firstCompletedAt) }}</td>
              <td>{{ row.textPresent ? '有' : '无' }}</td><td>{{ row.audioPresent ? '有' : '无' }}</td>
              <td>{{ seconds(row.recordingDurationMillis) }}</td><td>{{ seconds(row.referenceAudioDurationMillis) }}</td><td>{{ seconds(row.referenceVideoDurationMillis) }}</td>
            </tr></tbody>
          </table>
        </div>
        <p v-if="!currentRows.length" class="business-note">当前范围内暂无{{ activeTab === 'submissions' ? '提交' : '完成' }}明细。</p>
        <PaginationControls :page="currentPage" :size="pageSize" :total="currentTotal" @change="changePage" @size-change="changeSize" />
      </section>
    </AsyncState>
  </section>
</template>

<style scoped>
.detail-page{display:grid;gap:18px}.detail-date-filter{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:16px 18px}.date-presets{display:flex;flex-wrap:wrap;gap:8px}.date-presets button{border:1px solid var(--border);border-radius:999px;background:var(--card);color:var(--muted-foreground);padding:8px 14px;font:inherit;cursor:pointer}.date-presets button.is-active{border-color:color-mix(in srgb,var(--primary) 42%,var(--border));background:color-mix(in srgb,var(--primary) 10%,var(--card));color:var(--primary);font-weight:700}.day-section,.detail-list{padding:20px}.day-table{min-width:1060px}.day-table thead tr:first-child th{text-align:center;background:color-mix(in srgb,var(--primary) 5%,var(--card))}.detail-tabs{display:flex;gap:4px;border-bottom:1px solid var(--border);margin:-4px 0 18px}.detail-tabs button{border:0;border-bottom:2px solid transparent;background:transparent;color:var(--muted-foreground);padding:11px 16px;font:inherit;cursor:pointer}.detail-tabs button.is-active{border-bottom-color:var(--primary);color:var(--foreground);font-weight:700}@media(max-width:760px){.detail-date-filter{align-items:stretch;flex-direction:column}.date-presets{overflow-x:auto;flex-wrap:nowrap;padding-bottom:2px}.date-presets button{flex:0 0 auto}}
</style>
