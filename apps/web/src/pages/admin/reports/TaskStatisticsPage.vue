<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import PageActions from '../../../components/admin/PageActions.vue'
import WorkSummaryCards from '../../../components/admin/WorkSummaryCards.vue'
import BaseSelect from '../../../components/form/BaseSelect.vue'
import TaskSearchSelect from '../../../components/form/TaskSearchSelect.vue'
import PaginationControls from '../../../components/admin/PaginationControls.vue'
import { reportApi } from '../../../lib/reportApi.js'
import { useNotifications } from '../../../composables/useNotifications.js'

const router = useRouter()
const notifications = useNotifications()
const taskId = ref(''), fromDate = ref(''), toDate = ref('')
const summary = ref(null), rankings = ref([]), total = ref(0), page = ref(0)
const sortBy = ref('completedCount'), loading = ref(false)
const sortOptions = [
  { value: 'completedCount', label: '完成条数' },
  { value: 'submissionCount', label: '提交条数' },
  { value: 'recordingDurationMillis', label: '最终录音时长' },
  { value: 'referenceAudioDurationMillis', label: '参考音频时长' },
  { value: 'referenceVideoDurationMillis', label: '参考视频时长' },
]
const seconds = value => `${Math.round((Number(value) || 0) / 1000)} 秒`
function params() { return { fromDate: fromDate.value, toDate: toDate.value } }
async function search() {
  if (!taskId.value) { notifications.error('请选择任务'); return }
  if (fromDate.value && toDate.value && fromDate.value > toDate.value) {
    notifications.error('开始日期不能晚于结束日期'); return
  }
  loading.value = true
  try {
    const [summaryResult, rankingResult] = await Promise.all([
      reportApi.tasks({ taskId: taskId.value, ...params() }),
      reportApi.taskCollectors(taskId.value, { ...params(), sortBy: sortBy.value, page: page.value, size: 20 }),
    ])
    summary.value = summaryResult
    rankings.value = rankingResult.items || []
    total.value = rankingResult.total || 0
  } catch (error) { notifications.error(error.message) }
  finally { loading.value = false }
}
function submitSearch() { page.value = 0; search() }
function changePage(value) { page.value = value; search() }
function openCollector(row) {
  router.push({
    path: '/admin/reports/collectors',
    query: { userId: row.collectorId, taskId: taskId.value, fromDate: fromDate.value, toDate: toDate.value },
  })
}
</script>

<template>
  <section class="admin-page">
    <PageActions title="任务统计" description="查看任务总量、来源时长和采集员时间范围排名。" />
    <div class="business-card">
      <form class="business-inline report-filter" novalidate @submit.prevent="submitSearch">
        <TaskSearchSelect v-model="taskId" />
        <label>开始日期 <input v-model="fromDate" type="date" /></label>
        <label>结束日期 <input v-model="toDate" type="date" /></label>
        <BaseSelect v-model="sortBy" :options="sortOptions" aria-label="排名指标" />
        <button class="button-primary" :disabled="loading">{{loading?'查询中':'查询'}}</button>
      </form>
      <WorkSummaryCards v-if="summary" :summary="summary" />
      <section v-if="summary" class="ranking-section">
        <h3>采集员排名</h3>
        <div class="business-table-wrap">
          <table class="business-table">
            <thead><tr><th>排名</th><th>采集员 ID</th><th>采集员姓名</th><th>提交</th><th>完成</th><th>最终录音</th><th>参考音频</th><th>参考视频</th></tr></thead>
            <tbody>
              <tr v-for="(row,index) in rankings" :key="row.collectorId" class="clickable-row" @click="openCollector(row)">
                <td>{{page*20+index+1}}</td><td>{{row.collectorId}}</td><td>{{row.collectorName||'-'}}</td>
                <td>{{row.submissionCount}}</td><td>{{row.completedCount}}</td><td>{{seconds(row.recordingDurationMillis)}}</td>
                <td>{{seconds(row.referenceAudioDurationMillis)}}</td><td>{{seconds(row.referenceVideoDurationMillis)}}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-if="!rankings.length" class="business-note">当前条件下暂无采集员统计。</p>
        <PaginationControls :page="page" :total="total" :size="20" @change="changePage" />
      </section>
    </div>
  </section>
</template>

<style scoped>
.ranking-section{margin-top:24px}.clickable-row{cursor:pointer}.clickable-row:hover{background:var(--accent)}
</style>
