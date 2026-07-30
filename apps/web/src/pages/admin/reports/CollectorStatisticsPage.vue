<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AsyncState from '../../../components/admin/AsyncState.vue'
import BaseSelect from '../../../components/form/BaseSelect.vue'
import PageActions from '../../../components/admin/PageActions.vue'
import PaginationControls from '../../../components/admin/PaginationControls.vue'
import TaskSearchSelect from '../../../components/form/TaskSearchSelect.vue'
import WorkSummaryCards from '../../../components/admin/WorkSummaryCards.vue'
import { reportApi } from '../../../lib/reportApi.js'
import { taskApi } from '../../../lib/taskApi.js'
import { statusLabel } from '../../../lib/statusLabels.js'
import { useNotifications } from '../../../composables/useNotifications.js'

const route = useRoute()
const notifications = useNotifications()
const shanghaiToday = () => new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
}).format(new Date())
const shiftDate = (value, days) => {
  const date = new Date(`${value}T00:00:00Z`)
  date.setUTCDate(date.getUTCDate() + days)
  return date.toISOString().slice(0, 10)
}

const today = shanghaiToday()
const taskId = ref(String(route.query.taskId || ''))
const fromDate = ref(String(route.query.fromDate || today))
const toDate = ref(String(route.query.toDate || today))
const activePreset = ref(route.query.fromDate || route.query.toDate ? 'custom' : 'today')
const summary = ref(null)
const rankings = ref([])
const total = ref(0)
const page = ref(0)
const sortBy = ref('completedCount')
const loading = ref(false)
const loadError = ref('')
const selectedCollector = ref(null)
const detail = ref(null)
const detailRows = ref([])
const detailTotal = ref(0)
const detailPage = ref(0)
const detailLoading = ref(false)
const pageSize = 20
let detailRequestId = 0
const sortOptions = [
  { value: 'submissionCount', label: '提交条数' },
  { value: 'completedCount', label: '完成条数' },
  { value: 'recordingDurationMillis', label: '最终录音时长' },
  { value: 'referenceAudioDurationMillis', label: '参考音频时长' },
  { value: 'referenceVideoDurationMillis', label: '参考视频时长' },
]
const presets = [
  ['today', '今天'], ['yesterday', '昨天'], ['seven-days', '近 7 日'],
  ['month', '本月'], ['all', '全部'], ['custom', '自定义范围'],
]
const seconds = (value) => `${((Number(value) || 0) / 1000).toFixed(1)} 秒`
const reportParams = computed(() => ({
  ...(fromDate.value ? { fromDate: fromDate.value } : {}),
  ...(toDate.value ? { toDate: toDate.value } : {}),
}))

function applyPreset(key) {
  activePreset.value = key
  if (key === 'today') fromDate.value = toDate.value = today
  if (key === 'yesterday') fromDate.value = toDate.value = shiftDate(today, -1)
  if (key === 'seven-days') {
    fromDate.value = shiftDate(today, -6)
    toDate.value = today
  }
  if (key === 'month') {
    fromDate.value = `${today.slice(0, 7)}-01`
    toDate.value = today
  }
  if (key === 'all') fromDate.value = toDate.value = ''
}

function changeTask(value) {
  detailRequestId += 1
  taskId.value = value
  summary.value = null
  rankings.value = []
  total.value = 0
  selectedCollector.value = null
  detail.value = null
  detailRows.value = []
}

function validate() {
  if (!taskId.value) {
    notifications.error('请选择任务')
    return false
  }
  if (fromDate.value && toDate.value && fromDate.value > toDate.value) {
    notifications.error('开始日期不能晚于结束日期')
    return false
  }
  return true
}

async function search(refresh = false) {
  if (!validate()) return
  loading.value = true
  if (!summary.value) loadError.value = ''
  try {
    const [nextSummary, rankingPage] = await Promise.all([
      reportApi.tasks({ taskId: taskId.value, ...reportParams.value }),
      reportApi.taskCollectors(taskId.value, {
        ...reportParams.value, sortBy: sortBy.value, page: page.value, size: pageSize,
      }),
    ])
    summary.value = nextSummary
    rankings.value = rankingPage.items || []
    total.value = Number(rankingPage.total) || 0
    if (selectedCollector.value) await openCollector(selectedCollector.value, true)
  } catch (error) {
    if (refresh || summary.value) notifications.error(error.message)
    else loadError.value = error.message
  } finally {
    loading.value = false
  }
}

async function submitSearch() {
  detailRequestId += 1
  page.value = 0
  selectedCollector.value = null
  detail.value = null
  detailRows.value = []
  await search()
}

async function changePage(value) {
  page.value = value
  await search(true)
}

async function openCollector(row, preservePage = false) {
  const requestId = ++detailRequestId
  selectedCollector.value = row
  if (!preservePage) detailPage.value = 0
  detailLoading.value = true
  try {
    const [report, submissions] = await Promise.all([
      reportApi.collectorTask(row.collectorId, taskId.value, reportParams.value),
      reportApi.collectorTaskSubmissions(row.collectorId, taskId.value, {
        ...reportParams.value, page: detailPage.value, size: pageSize,
      }),
    ])
    if (requestId !== detailRequestId) return
    detail.value = report
    detailRows.value = submissions.items || []
    detailTotal.value = Number(submissions.total) || 0
  } catch (error) {
    if (requestId === detailRequestId) notifications.error(error.message)
  } finally {
    if (requestId === detailRequestId) detailLoading.value = false
  }
}

async function changeDetailPage(value) {
  detailPage.value = value
  await openCollector(selectedCollector.value, true)
}

function startDownload(url) {
  const link = document.createElement('a')
  link.href = url
  link.download = ''
  document.body.appendChild(link)
  link.click()
  link.remove()
}

async function exportCsv(collectorOnly = false) {
  if (!validate()) return
  const filters = collectorOnly && selectedCollector.value
    ? { collectorIds: [selectedCollector.value.collectorId] }
    : {}
  try {
    await taskApi.prepareExport(taskId.value, filters, reportParams.value)
    startDownload(taskApi.exportItems(taskId.value, filters, reportParams.value))
    notifications.success('CSV 导出已开始')
  } catch (error) {
    notifications.error(error.message || 'CSV 导出失败')
  }
}

onMounted(() => {
  if (taskId.value) search()
})
</script>

<template>
  <section class="admin-page collector-report-page">
    <PageActions title="采集员统计" description="先选任务，再按首次提交日期核对采集产出与当前有效结果。">
      <button class="button-secondary" :disabled="!taskId" @click="exportCsv(false)">导出当前对表 CSV</button>
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
      <WorkSummaryCards :summary="summary" />

      <section class="business-card report-section">
        <div class="business-heading">
          <div><h3>任务内采集员</h3><p>点击人员可在本页查看每日趋势和完整提交明细。</p></div>
          <span>共 {{ total }} 人</span>
        </div>
        <div class="business-table-wrap">
          <table class="business-table">
            <thead><tr><th>排名</th><th>采集员</th><th>提交</th><th>完成</th><th>最终录音</th><th>参考音频</th><th>参考视频</th></tr></thead>
            <tbody>
              <tr v-for="(row,index) in rankings" :key="row.collectorId" class="clickable-row"
                :class="{ 'is-selected': selectedCollector?.collectorId === row.collectorId }" @click="openCollector(row)">
                <td>{{ page * pageSize + index + 1 }}</td>
                <td><strong>{{ row.collectorName || '未设置姓名' }}</strong><small>{{ row.collectorId }}</small></td>
                <td>{{ row.submissionCount }}</td><td>{{ row.completedCount }}</td>
                <td>{{ seconds(row.recordingDurationMillis) }}</td>
                <td>{{ seconds(row.referenceAudioDurationMillis) }}</td>
                <td>{{ seconds(row.referenceVideoDurationMillis) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-if="!rankings.length" class="business-note">当前范围内暂无采集员统计。</p>
        <PaginationControls :page="page" :size="pageSize" :total="total" @change="changePage" />
      </section>

      <section v-if="selectedCollector" class="collector-drilldown">
        <div class="business-heading drilldown-heading">
          <div>
            <span class="section-kicker">人员下钻</span>
            <h2>{{ selectedCollector.collectorName || '未设置姓名' }}</h2>
            <p>{{ selectedCollector.collectorId }}</p>
          </div>
          <button class="button-secondary" @click="exportCsv(true)">导出此采集员 CSV</button>
        </div>
        <AsyncState :loading="detailLoading && !detail" :error="''" :empty="!detail">
          <WorkSummaryCards :summary="detail.summary" />

          <div class="report-detail-grid">
            <section class="business-card">
              <div class="business-heading"><div><h3>每日统计</h3><p>全部按首次提交日归属。</p></div></div>
              <div class="business-table-wrap">
                <table class="business-table compact-table">
                  <thead><tr><th>日期</th><th>每日提交数</th><th>每日当前完成数</th><th>最终录音</th><th>参考音频</th><th>参考视频</th></tr></thead>
                  <tbody><tr v-for="day in detail.days" :key="day.date">
                    <td>{{ day.date }}</td><td>{{ day.submissionCount }}</td><td>{{ day.completedCount }}</td>
                    <td>{{ seconds(day.recordingDurationMillis) }}</td>
                    <td>{{ seconds(day.referenceAudioDurationMillis) }}</td>
                    <td>{{ seconds(day.referenceVideoDurationMillis) }}</td>
                  </tr></tbody>
                </table>
              </div>
              <p v-if="!detail.days?.length" class="business-note">当前范围内暂无每日数据。</p>
            </section>

            <section class="business-card">
              <div class="business-heading"><div><h3>最近提交</h3><p>用于快速核对最近发生的提交。</p></div></div>
              <div v-if="detail.recentSubmissions?.length" class="recent-submissions">
                <article v-for="row in detail.recentSubmissions" :key="row.itemId">
                  <div><strong>{{ row.itemCode }}</strong><span>{{ statusLabel('item', row.currentItemStatus) }}</span></div>
                  <p>首次 {{ row.firstSubmittedAt || '-' }}</p>
                  <small>最终录音 {{ seconds(row.recordingDurationMillis) }}</small>
                </article>
              </div>
              <p v-else class="business-note">暂无最近提交。</p>
            </section>
          </div>

          <section class="business-card report-section">
            <div class="business-heading"><div><h3>完整提交明细</h3><p>分页展示当前采集员在此任务内的全部匹配条目。</p></div><span>共 {{ detailTotal }} 条</span></div>
            <div class="business-table-wrap">
              <table class="business-table">
                <thead><tr><th>条目编号</th><th>状态</th><th>首次提交</th><th>最新提交</th><th>文本</th><th>录音</th><th>最终录音</th><th>参考音频</th><th>参考视频</th></tr></thead>
                <tbody><tr v-for="row in detailRows" :key="row.itemId">
                  <td>{{ row.itemCode }}</td><td>{{ statusLabel('item', row.currentItemStatus) }}</td>
                  <td>{{ row.firstSubmittedAt || '-' }}</td><td>{{ row.latestSubmittedAt || '-' }}</td>
                  <td>{{ row.textPresent ? '有' : '无' }}</td><td>{{ row.audioPresent ? '有' : '无' }}</td>
                  <td>{{ seconds(row.recordingDurationMillis) }}</td>
                  <td>{{ seconds(row.referenceAudioDurationMillis) }}</td>
                  <td>{{ seconds(row.referenceVideoDurationMillis) }}</td>
                </tr></tbody>
              </table>
            </div>
            <PaginationControls :page="detailPage" :size="pageSize" :total="detailTotal" @change="changeDetailPage" />
          </section>
        </AsyncState>
      </section>
    </AsyncState>
  </section>
</template>

<style scoped>
.collector-report-page{display:grid;gap:18px}.report-control-card{padding:18px}.report-controls{display:grid;grid-template-columns:minmax(320px,1fr) auto auto auto;gap:14px;align-items:end}.date-presets{grid-column:1/-1;display:flex;gap:7px;flex-wrap:wrap}.date-presets button{border:1px solid var(--border);border-radius:999px;background:var(--card);color:var(--muted-foreground);padding:7px 13px;cursor:pointer}.date-presets button.is-active{border-color:color-mix(in srgb,var(--primary) 45%,var(--border));background:color-mix(in srgb,var(--primary) 10%,var(--card));color:var(--primary)}.date-range{display:flex;align-items:end;gap:8px}.date-range label{display:grid;gap:6px;color:var(--muted-foreground);font-size:12px}.date-range span{padding-bottom:10px;color:var(--muted-foreground)}.report-section{margin-top:18px}.clickable-row{cursor:pointer}.clickable-row:hover,.clickable-row.is-selected{background:color-mix(in srgb,var(--primary) 7%,var(--card))}.clickable-row small{display:block;margin-top:3px;color:var(--muted-foreground)}.collector-drilldown{margin-top:26px;padding-top:26px;border-top:1px solid var(--border)}.drilldown-heading{margin-bottom:16px}.drilldown-heading h2{margin:4px 0}.section-kicker{text-transform:uppercase;letter-spacing:.12em;color:var(--primary);font-size:12px;font-weight:700}.report-detail-grid{display:grid;grid-template-columns:minmax(0,1.35fr) minmax(280px,.65fr);gap:18px;margin-top:18px}.compact-table{font-size:13px}.recent-submissions{display:grid;gap:10px}.recent-submissions article{padding:12px;border:1px solid var(--border);border-radius:calc(var(--radius) - 4px)}.recent-submissions article div{display:flex;justify-content:space-between;gap:12px}.recent-submissions p,.recent-submissions small{margin:6px 0 0;color:var(--muted-foreground)}
@media (max-width:1100px){.report-controls{grid-template-columns:1fr 1fr}.report-detail-grid{grid-template-columns:1fr}.date-presets{grid-column:1/-1}}
@media (max-width:720px){.report-controls{grid-template-columns:1fr}.date-range{align-items:stretch;flex-direction:column}.date-range span{display:none}}
</style>
