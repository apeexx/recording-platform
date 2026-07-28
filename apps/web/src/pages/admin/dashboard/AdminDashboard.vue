<script setup>
import { computed, onMounted, ref } from 'vue'
import AsyncState from '../../../components/admin/AsyncState.vue'
import PageActions from '../../../components/admin/PageActions.vue'
import { reportApi } from '../../../lib/reportApi.js'
import { useNotifications } from '../../../composables/useNotifications.js'

const notifications = useNotifications()
const dashboard = ref(null)
const operations = ref([])
const loading = ref(false)
const loadError = ref('')
const maxTrend = computed(() => Math.max(...(dashboard.value?.trend || []).map((row) => row.firstSubmissionCount), 1))
const trendPoints = computed(() => (dashboard.value?.trend || []).map((row, index, values) => {
  const x = values.length < 2 ? 50 : index / (values.length - 1) * 100
  const y = 90 - row.firstSubmissionCount / maxTrend.value * 72
  return `${x},${y}`
}).join(' '))
const itemSegments = computed(() => {
  const values = dashboard.value?.items || {}
  return [
    ['待领取', values.available, 'var(--muted-foreground)'],
    ['待录制/返修', (values.recordingPending || 0) + (values.reworkPending || 0), 'var(--primary)'],
    ['已提交/待审核', (values.submitted || 0) + (values.reviewPending || 0), 'color-mix(in srgb,var(--primary) 55%,var(--card))'],
    ['已完成', values.completed, 'color-mix(in srgb,var(--primary) 78%,var(--foreground))'],
    ['已废弃', values.discarded, 'var(--destructive)'],
  ].map(([label, value, color]) => ({ label, value: Number(value) || 0, color }))
})
const seconds = (value) => `${Math.round((Number(value) || 0) / 1000)} 秒`

async function load(refresh = false) {
  loading.value = !dashboard.value
  if (!dashboard.value) loadError.value = ''
  try {
    const [summary, recent] = await Promise.all([reportApi.dashboard(), reportApi.operations({ page: 0, size: 8 })])
    dashboard.value = summary
    operations.value = recent.items || []
  } catch (error) {
    if (refresh || dashboard.value) notifications.error(error.message)
    else loadError.value = error.message
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<template>
  <section class="admin-page dashboard-page">
    <PageActions title="数据大屏" description="展示录音任务平台当前真实业务数据与最近 7 日工作趋势。">
      <button class="button-secondary" @click="load(true)">刷新数据</button>
    </PageActions>
    <AsyncState :loading="loading" :error="loadError" :empty="!dashboard" @retry="load">
      <div class="dashboard-metrics">
        <article><span>任务总数</span><strong>{{ dashboard.tasks.total }}</strong><small>进行中 {{ dashboard.tasks.running }}</small></article>
        <article><span>数据总量</span><strong>{{ dashboard.items.total }}</strong><small>已完成 {{ dashboard.items.completed }}</small></article>
        <article><span>当前采集人数</span><strong>{{ dashboard.currentCollectorCount }}</strong><small>按当前分配去重</small></article>
        <article><span>今日首次提交</span><strong>{{ dashboard.todayFirstSubmissionCount }}</strong><small>Asia/Shanghai</small></article>
      </div>
      <div class="dashboard-grid">
        <article class="business-card dashboard-chart">
          <div class="business-heading"><div><h3>近 7 日首次提交</h3><p>按条目首次提交日期统计</p></div></div>
          <svg viewBox="0 0 100 100" preserveAspectRatio="none" role="img" aria-label="近七日首次提交趋势">
            <path d="M0 90H100" class="chart-axis"/><polyline :points="trendPoints" class="chart-line"/>
          </svg>
          <div class="trend-labels"><span v-for="row in dashboard.trend" :key="row.date">{{ row.date.slice(5) }}<strong>{{ row.firstSubmissionCount }}</strong></span></div>
        </article>
        <article class="business-card">
          <h3>条目状态分布</h3>
          <div class="status-distribution">
            <div v-for="segment in itemSegments" :key="segment.label">
              <span>{{ segment.label }}</span><strong>{{ segment.value }}</strong>
              <i><b :style="{ width: `${dashboard.items.total ? segment.value / dashboard.items.total * 100 : 0}%`, background: segment.color }"/></i>
            </div>
          </div>
        </article>
        <article class="business-card dashboard-ranking">
          <h3>任务完成排行</h3>
          <div v-if="dashboard.taskRanking.length" class="business-list">
            <div v-for="(row,index) in dashboard.taskRanking" :key="row.taskId">
              <span><strong>{{ index + 1 }} · {{ row.taskName }}</strong><small>{{ row.taskCode }} · 当前提交 {{ row.submissionCount }} · {{ seconds(row.recordingDurationMillis) }}</small></span>
              <b>{{ row.completedCount }}</b>
            </div>
          </div>
          <p v-else class="business-note">暂无可排行的任务数据。</p>
        </article>
        <article class="business-card">
          <h3>最近操作</h3>
          <div v-if="operations.length" class="operation-stream">
            <div v-for="(row,index) in operations" :key="`${row.time}-${index}`"><i/><span><strong>{{ row.operator }}</strong>{{ row.content }}<small>{{ row.time }}</small></span></div>
          </div>
          <p v-else class="business-note">暂无操作记录。</p>
        </article>
      </div>
    </AsyncState>
  </section>
</template>
