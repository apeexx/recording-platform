<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AsyncState from '../../../components/admin/AsyncState.vue'
import PageActions from '../../../components/admin/PageActions.vue'
import PaginationControls from '../../../components/admin/PaginationControls.vue'
import UserSearchSelect from '../../../components/form/UserSearchSelect.vue'
import TaskItemFilters from '../../../components/admin/TaskItemFilters.vue'
import HelpPopover from '../../../components/form/HelpPopover.vue'
import { reviewApi } from '../../../lib/reviewApi.js'
import { batchOperationApi } from '../../../lib/batchOperationApi.js'
import { operationId } from '../../../lib/apiUtils.js'
import { useAdminSession } from '../../../composables/useAdminSession.js'
import { useBatchSelection } from '../../../composables/useBatchSelection.js'
import { useNotifications } from '../../../composables/useNotifications.js'
import { defaultReviewFilters, reviewSelectionFilters } from '../../../lib/reviewFilters.js'

const notifications = useNotifications()
const route = useRoute()
const router = useRouter()
const session = useAdminSession()
const rows = ref([])
const loading = ref(false)
const error = ref('')
const count = ref(5)
const notice = ref('')
const page = ref(0)
const size = ref(20)
const total = ref(0)
const summary = ref(null)
const reviewerId = ref('')
const preview = ref(null)
const batchJob = ref(null)
const pollTimer = ref(null)
const filters = ref(defaultReviewFilters())
const selection = useBatchSelection(rows, total)
const isAdmin = computed(() => session.user.value?.role === 'ADMIN')
const isReviewer = computed(() => session.user.value?.role === 'REVIEWER')
const selectedRows = computed(() => rows.value.filter(selection.isSelected))
const claimableRows = computed(() => selectedRows.value.filter(row => row.status === 'SUBMITTED'))
const approvableRows = computed(() => selectedRows.value.filter(row => row.status === 'REVIEW_PENDING'))
const processing = computed(() => ['PENDING', 'PROCESSING'].includes(batchJob.value?.status))

async function load(showToast = false) {
  loading.value = true
  error.value = ''
  try {
    const [result, nextSummary] = await Promise.all([
      reviewApi.pool(route.params.taskId, page.value, size.value, filters.value),
      reviewApi.taskSummary(route.params.taskId),
    ])
    rows.value = result.items || []
    total.value = result.total || 0
    summary.value = nextSummary
  } catch (exception) {
    if (showToast || rows.value.length) notifications.error(exception.message)
    else error.value = exception.message
  } finally {
    loading.value = false
  }
}
async function claim() {
  try {
    const item = await reviewApi.claim(route.params.taskId, operationId('review-claim'))
    notifications.success('已领取一条审核数据')
    router.push(`/admin/review/${item.id}`)
  } catch (exception) { notifications.error(exception.message) }
}
async function claimBatch() {
  try {
    const items = await reviewApi.claimBatch(route.params.taskId, Number(count.value), operationId('review-claim-batch'))
    notifications.success(`已领取 ${items.length} 条审核数据`)
    items[0] ? router.push(`/admin/review/${items[0].id}`) : router.push('/admin/review')
  } catch (exception) { notifications.error(exception.message) }
}
async function claimItem(row) {
  try {
    const item = await reviewApi.claimItem(row.id, row.revision, operationId('review-claim-item'))
    notifications.success('已领取该审核数据')
    router.push(`/admin/review/${item.id}`)
  } catch (exception) { notifications.error(exception.message) }
}
function commands(values) { return values.map(row => ({ itemId: row.id, expectedRevision: row.revision })) }
function actionCount(action) {
  if (selection.mode.value === 'ALL') return Number(preview.value?.applicableCounts?.[action]) || 0
  return action === 'REVIEW_APPROVE' ? approvableRows.value.length : claimableRows.value.length
}
function resultNotice(label, result, skipped) {
  const success = result.filter(row => row.success).length
  const failed = result.length - success
  notice.value = `${label}完成：成功 ${success}，失败 ${failed}，不适用 ${skipped}`
  notifications.success(notice.value)
}
async function selectAllMatching() {
  try {
    preview.value = await batchOperationApi.preview(selection.selectionPayload(
      route.params.taskId, 'REVIEW_QUEUE', reviewSelectionFilters(filters.value),
    ))
    selection.selectAllMatching()
  } catch (exception) { notifications.error(exception.message) }
}
async function runCrossPage(action, extra = {}) {
  batchJob.value = await batchOperationApi.create({
    operationId: operationId(`review-all-${action.toLowerCase()}`),
    action,
    selection: selection.selectionPayload(
      route.params.taskId, 'REVIEW_QUEUE', reviewSelectionFilters(filters.value),
    ),
    ...extra,
  })
  notifications.success('跨页批处理已进入队列')
  schedulePoll()
}
async function batchClaimSelected() {
  const applicable = actionCount('REVIEW_CLAIM')
  if (!applicable || !confirm(`确认批量领取 ${applicable} 条已提交数据？`)) return
  try {
    if (selection.mode.value === 'ALL') return await runCrossPage('REVIEW_CLAIM')
    const result = await reviewApi.batchClaim(commands(claimableRows.value), operationId('review-batch-claim-selected'))
    resultNotice('批量领取审核', result, selectedRows.value.length - claimableRows.value.length)
    selection.clear()
    await load(true)
  } catch (exception) { notifications.error(exception.message) }
}
async function batchAssign() {
  if (!reviewerId.value) { notifications.error('请先选择审核员'); return }
  const applicable = actionCount('REVIEW_ASSIGN')
  if (!applicable || !confirm(`确认批量分配 ${applicable} 条已提交数据？`)) return
  try {
    if (selection.mode.value === 'ALL') return await runCrossPage('REVIEW_ASSIGN', { reviewerId: reviewerId.value })
    const result = await reviewApi.batchAssign(commands(claimableRows.value), reviewerId.value, operationId('review-batch-assign'))
    resultNotice('批量分配', result, selectedRows.value.length - claimableRows.value.length)
    selection.clear()
    await load(true)
  } catch (exception) { notifications.error(exception.message) }
}
async function batchApprove() {
  const applicable = actionCount('REVIEW_APPROVE')
  if (!applicable || !confirm(`确认批量通过 ${applicable} 条待审核数据？`)) return
  try {
    if (selection.mode.value === 'ALL') return await runCrossPage('REVIEW_APPROVE')
    const payload = approvableRows.value.map(row => ({ itemId: row.id, expectedRevision: row.revision, text: null }))
    const result = await reviewApi.batchApprove(payload, operationId('review-batch-approve'))
    resultNotice('批量通过', result, selectedRows.value.length - approvableRows.value.length)
    selection.clear()
    await load(true)
  } catch (exception) { notifications.error(exception.message) }
}
async function assign(row) {
  if (!reviewerId.value) { notifications.error('请先选择审核员'); return }
  try {
    await reviewApi.assign(row.id, reviewerId.value, row.revision, operationId('review-assign'))
    notifications.success('审核数据已分配')
    await load(true)
  } catch (exception) { notifications.error(exception.message) }
}
async function changePage(value) {
  page.value = value
  selection.clearPageMode()
  await load()
}
async function changeSize(value) {
  size.value = value
  page.value = 0
  selection.clear()
  await load(true)
}
async function changeFilters(value) {
  filters.value = value
  page.value = 0
  selection.clear()
  preview.value = null
  await load()
}
async function clearFilters() {
  filters.value = defaultReviewFilters()
  page.value = 0
  selection.clear()
  preview.value = null
  await load(true)
}
function clearPoll() {
  if (pollTimer.value) window.clearTimeout(pollTimer.value)
  pollTimer.value = null
}
function schedulePoll() {
  clearPoll()
  pollTimer.value = window.setTimeout(refreshBatchJob, 1000)
}
async function refreshBatchJob() {
  clearPoll()
  if (!batchJob.value?.id) return
  try {
    batchJob.value = await batchOperationApi.get(batchJob.value.id)
    if (processing.value) return schedulePoll()
    notice.value = `批处理完成：成功 ${batchJob.value.succeededCount || 0}，失败 ${batchJob.value.failedCount || 0}，跳过 ${batchJob.value.skippedCount || 0}`
    notifications.success(notice.value)
    selection.clear()
    preview.value = null
    await load(true)
  } catch (exception) { notifications.error(exception.message) }
}
async function resumeRecent() {
  try {
    const recent = await batchOperationApi.recent(route.params.taskId, 'REVIEW_QUEUE')
    batchJob.value = recent.find(job => ['PENDING', 'PROCESSING'].includes(job.status)) || recent[0] || null
    if (processing.value) schedulePoll()
  } catch (exception) { notifications.error(exception.message) }
}
onMounted(async () => { await load(); await resumeRecent() })
onBeforeUnmount(clearPoll)
</script>

<template>
  <section class="admin-page">
    <PageActions :title="summary?.taskName || '任务审核池'" :description="`${summary?.taskCode || ''} · 已提交数据需先领取或分配，进入待审核后才能作出决定。`">
      <router-link class="button-secondary" to="/admin/review">返回选择任务</router-link>
      <router-link v-if="isAdmin" class="button-secondary" :to="`/admin/review/tasks/${route.params.taskId}/ai-settings`">AI 审核设置</router-link>
      <button class="button-secondary" @click="load(true)">刷新</button>
      <button v-if="isReviewer" class="button-primary" @click="claim">领取一条</button>
    </PageActions>
    <section class="review-summary-grid">
      <article><span>当前积压</span><strong>{{ summary?.pendingCount || 0 }}</strong></article>
      <article><span>待领取</span><strong>{{ summary?.submittedCount || 0 }}</strong></article>
      <article><span>审核中</span><strong>{{ summary?.reviewPendingCount || 0 }}</strong></article>
      <article><span>今日完成</span><strong>{{ summary?.todayCompletedCount || 0 }}</strong></article>
    </section>
    <div class="business-card">
      <section class="review-filter-toolbar">
        <div class="business-heading"><div><h3>审核池筛选 <HelpPopover label="审核池筛选说明" content="筛选可组合使用；跨页全选会作用于全部匹配结果，而不是仅当前页面。" /></h3><p>按条目、采集员、审核员、状态和成果类型组合筛选。</p></div><button class="button-secondary" @click="clearFilters">清除筛选</button></div>
        <div class="review-filter-grid">
          <TaskItemFilters review-mode kind="code" :task-id="route.params.taskId" :model-value="filters" @change="changeFilters"/>
          <TaskItemFilters review-mode kind="collector" :task-id="route.params.taskId" :model-value="filters" @change="changeFilters"/>
          <TaskItemFilters review-mode kind="reviewer" :task-id="route.params.taskId" :model-value="filters" @change="changeFilters"/>
          <TaskItemFilters review-mode kind="status" :model-value="filters" @change="changeFilters"/>
          <TaskItemFilters review-mode kind="result" :model-value="filters" @change="changeFilters"/>
        </div>
      </section>
      <div v-if="isReviewer" class="business-inline review-claim-tools">
        <template v-if="isReviewer">
          <label>批量领取数量 <input v-model.number="count" type="number" min="1" max="100"/></label>
          <button class="button-secondary" @click="claimBatch">批量领取</button>
        </template>
      </div>
      <div v-if="selection.selectedCount.value" class="batch-selection-bar">
        <HelpPopover label="审核批量处理说明" content="批量领取、分配和通过由后台按条目状态逐项校验；已变化或不适用的条目会跳过并计入结果。" />
        <span>已选择 {{ selection.selectedCount.value }} 条</span>
        <button v-if="selection.mode.value === 'PAGE' && total > selection.selectedCount.value" class="button-link" @click="selectAllMatching">选择全部 {{ total }} 条筛选结果</button>
        <strong v-else-if="selection.mode.value === 'ALL'">已跨页全选</strong>
        <button class="button-link" @click="selection.clear(); preview = null">清除选择</button>
        <button class="button-secondary" :disabled="!actionCount('REVIEW_CLAIM') || processing" @click="batchClaimSelected">批量领取审核（{{ actionCount('REVIEW_CLAIM') }}）</button>
        <template v-if="isAdmin">
          <UserSearchSelect v-model="reviewerId" role="REVIEWER" user-type="WEB" placeholder="选择审核员"/>
          <button class="button-secondary" :disabled="!actionCount('REVIEW_ASSIGN') || !reviewerId || processing" @click="batchAssign">批量分配（{{ actionCount('REVIEW_ASSIGN') }}）</button>
          <button class="button-secondary" :disabled="!actionCount('REVIEW_APPROVE') || processing" @click="batchApprove">批量通过（{{ actionCount('REVIEW_APPROVE') }}）</button>
        </template>
      </div>
      <div v-if="batchJob" class="batch-job-card" aria-live="polite">
        <strong>{{ processing ? '批处理中' : '最近批处理' }}</strong>
        <progress :value="batchJob.processedCount || 0" :max="batchJob.selectedCount || 1"/>
        <span>总数 {{ batchJob.selectedCount || 0 }} · 成功 {{ batchJob.succeededCount || 0 }} · 失败 {{ batchJob.failedCount || 0 }} · 跳过 {{ batchJob.skippedCount || 0 }}</span>
      </div>
      <p v-if="notice" class="business-success">{{ notice }}</p>
      <AsyncState :loading="loading" :error="error" :empty="!rows.length" empty-text="当前任务没有已提交或待审核数据" @retry="load">
        <div class="business-table-wrap">
          <table class="business-table">
            <thead><tr>
              <th><input type="checkbox" :checked="selection.pageAllSelected.value" aria-label="选择当前页面" @change="selection.togglePage"/></th>
              <th>条目编号</th><th>采集员 ID</th><th>采集员</th>
              <th>审核员 ID</th><th>审核员</th><th>状态</th>
              <th>成果类型</th><th>时长</th><th>操作</th>
            </tr></thead>
            <tbody><tr v-for="r in rows" :key="r.id">
              <td><input type="checkbox" :checked="selection.isSelected(r)" :aria-label="`选择 ${r.itemCode}`" @change="selection.toggle(r)"/></td>
              <td>{{ r.itemCode }}</td><td>{{ r.collectorId || '-' }}</td><td>{{ r.collectorName || '-' }}</td>
              <td>{{ r.reviewerId || '-' }}</td><td>{{ r.reviewerName || '-' }}</td>
              <td>{{ r.status === 'SUBMITTED' ? '已提交' : '待审核' }}</td>
              <td>{{ r.hasText && r.hasAudio ? '文本和音频' : r.hasText ? '仅文本' : r.hasAudio ? '仅音频' : '无结果' }}</td>
              <td>{{ r.audioDurationMillis ? `${Math.round(r.audioDurationMillis / 1000)}秒` : '-' }}</td>
              <td>
                <button v-if="r.status === 'SUBMITTED'" class="button-link" @click="claimItem(r)">领取审核</button>
                <button v-if="isAdmin && r.status === 'SUBMITTED'" class="button-link" @click="assign(r)">分配</button>
                <router-link v-if="r.status === 'REVIEW_PENDING'" class="button-link" :to="`/admin/review/${r.id}`">审核</router-link>
              </td>
            </tr></tbody>
          </table>
        </div>
        <PaginationControls :page="page" :total="total" :size="size" @change="changePage" @size-change="changeSize"/>
      </AsyncState>
    </div>
  </section>
</template>

<style scoped>
.review-summary-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px;margin-bottom:18px}.review-summary-grid article{display:grid;gap:7px;padding:18px;border:1px solid var(--border);border-radius:var(--radius);background:var(--card)}.review-summary-grid span{color:var(--muted-foreground)}.review-summary-grid strong{font-size:28px}.review-filter-toolbar{display:grid;gap:14px;padding-bottom:18px;border-bottom:1px solid var(--border)}.review-filter-toolbar h3,.review-filter-toolbar p{margin:0}.review-filter-grid{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:10px}.review-claim-tools{margin-top:16px}@media(max-width:1100px){.review-filter-grid{grid-template-columns:repeat(3,minmax(0,1fr))}}@media(max-width:720px){.review-summary-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.review-filter-grid{grid-template-columns:1fr}}
</style>
