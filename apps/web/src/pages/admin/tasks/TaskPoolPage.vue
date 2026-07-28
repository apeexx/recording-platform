<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import AsyncState from '../../../components/admin/AsyncState.vue'
import PageActions from '../../../components/admin/PageActions.vue'
import PaginationControls from '../../../components/admin/PaginationControls.vue'
import BaseSelect from '../../../components/form/BaseSelect.vue'
import TaskItemFilters from '../../../components/admin/TaskItemFilters.vue'
import { taskApi } from '../../../lib/taskApi.js'
import { batchOperationApi } from '../../../lib/batchOperationApi.js'
import { operationId } from '../../../lib/apiUtils.js'
import { statusLabel } from '../../../lib/statusLabels.js'
import { useBatchSelection } from '../../../composables/useBatchSelection.js'
import { useNotifications } from '../../../composables/useNotifications.js'
import { defaultTaskItemFilters, selectionFilters } from '../../../lib/taskItemFilters.js'

const notifications = useNotifications()
const tasks = ref([])
const taskId = ref('')
const rows = ref([])
const page = ref(0)
const total = ref(0)
const loading = ref(false)
const error = ref('')
const notice = ref('')
const preview = ref(null)
const batchJob = ref(null)
const pollTimer = ref(null)
const selection = useBatchSelection(rows, total)
const filters = ref(defaultTaskItemFilters())
const taskOptions = computed(() => tasks.value.map(task => ({ value: task.id, label: task.name })))
const processing = computed(() => ['PENDING', 'PROCESSING'].includes(batchJob.value?.status))
const progress = computed(() => batchJob.value?.selectedCount
  ? Math.round((batchJob.value.processedCount || 0) / batchJob.value.selectedCount * 100)
  : 0)

async function init() {
  try { tasks.value = (await taskApi.list(0, 100)).items || [] } catch (exception) { error.value = exception.message }
}
async function load() {
  if (!taskId.value) return
  loading.value = true
  error.value = ''
  try {
    const result = await taskApi.items(taskId.value, page.value, 20, filters.value)
    rows.value = result.items || []
    total.value = result.total || 0
  } catch (exception) {
    if (rows.value.length) notifications.error(exception.message)
    else error.value = exception.message
  } finally {
    loading.value = false
  }
}
async function choose() {
  page.value = 0
  selection.clear()
  preview.value = null
  batchJob.value = null
  await load()
  await resumeRecent()
}
async function changePage(value) {
  page.value = value
  selection.clearPageMode()
  await load()
}
async function changeFilters(value) {
  filters.value = value
  page.value = 0
  selection.clear()
  preview.value = null
  await load()
}
async function selectAllMatching() {
  try {
    preview.value = await batchOperationApi.preview(selection.selectionPayload(taskId.value, 'TASK_POOL', selectionFilters(filters.value)))
    selection.selectAllMatching()
  } catch (exception) {
    notifications.error(exception.message)
  }
}
function applicableCount(action) {
  if (selection.mode.value === 'ALL') return Number(preview.value?.applicableCounts?.[action.toUpperCase()]) || 0
  return selection.pageCommands().filter(command => {
    const row = rows.value.find(item => item.id === command.itemId)
    if (action === 'restore') return row?.status === 'DISCARDED'
    if (action === 'discard') return row?.status !== 'DISCARDED'
    return row?.status !== 'AVAILABLE' && row?.status !== 'DISCARDED'
  }).length
}
async function batch(action) {
  const count = applicableCount(action)
  if (!count || !confirm(`确认批量${action === 'release' ? '释放' : action === 'discard' ? '废弃' : '恢复'} ${count} 条适用数据？`)) return
  try {
    if (selection.mode.value === 'ALL') {
      batchJob.value = await batchOperationApi.create({
        operationId: operationId(`pool-all-${action}`),
        action: action.toUpperCase(),
        selection: selection.selectionPayload(taskId.value, 'TASK_POOL', selectionFilters(filters.value)),
      })
      notifications.success('跨页批处理已进入队列')
      schedulePoll()
      return
    }
    const result = await taskApi.batchAction(action, selection.pageCommands(), operationId(`pool-${action}`))
    notice.value = `成功 ${result.filter(row => row.success).length}，冲突 ${result.filter(row => !row.success).length}`
    notifications.success(notice.value)
    selection.clear()
    await load()
  } catch (exception) {
    notifications.error(exception.message)
  }
}
function schedulePoll() {
  clearPoll()
  pollTimer.value = window.setTimeout(refreshBatchJob, 1000)
}
function clearPoll() {
  if (pollTimer.value) window.clearTimeout(pollTimer.value)
  pollTimer.value = null
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
    await load()
  } catch (exception) {
    notifications.error(exception.message)
  }
}
async function resumeRecent() {
  if (!taskId.value) return
  try {
    const recent = await batchOperationApi.recent(taskId.value, 'TASK_POOL')
    batchJob.value = recent.find(job => ['PENDING', 'PROCESSING'].includes(job.status)) || recent[0] || null
    if (processing.value) schedulePoll()
  } catch (exception) {
    notifications.error(exception.message)
  }
}
onMounted(init)
onBeforeUnmount(clearPoll)
</script>

<template>
  <section class="admin-page">
    <PageActions title="任务数据池" description="支持单条、本页和全部筛选结果三种批量选择。"/>
    <div class="business-card">
      <div class="business-inline">
        <BaseSelect v-model="taskId" :options="taskOptions" placeholder="请选择任务" aria-label="选择任务" @update:model-value="choose"/>
        <button class="button-secondary" :disabled="!taskId" @click="load">刷新</button>
        <button class="button-secondary" :disabled="!applicableCount('release') || processing" @click="batch('release')">批量释放（{{ applicableCount('release') }}）</button>
        <button class="button-secondary is-danger" :disabled="!applicableCount('discard') || processing" @click="batch('discard')">批量废弃（{{ applicableCount('discard') }}）</button>
        <button class="button-secondary" :disabled="!applicableCount('restore') || processing" @click="batch('restore')">批量恢复（{{ applicableCount('restore') }}）</button>
      </div>
      <div v-if="selection.selectedCount.value" class="batch-selection-bar">
        <span>已选择 {{ selection.selectedCount.value }} 条</span>
        <button v-if="selection.mode.value === 'PAGE' && total > selection.selectedCount.value" class="button-link" @click="selectAllMatching">选择全部 {{ total }} 条筛选结果</button>
        <strong v-else-if="selection.mode.value === 'ALL'">已跨页全选</strong>
        <button class="button-link" @click="selection.clear(); preview = null">清除选择</button>
      </div>
      <div v-if="batchJob" class="batch-job-card" aria-live="polite">
        <strong>{{ processing ? '批处理中' : '最近批处理' }} · {{ progress }}%</strong>
        <progress :value="progress" max="100">{{ progress }}%</progress>
        <span>总数 {{ batchJob.selectedCount || 0 }} · 成功 {{ batchJob.succeededCount || 0 }} · 失败 {{ batchJob.failedCount || 0 }} · 跳过 {{ batchJob.skippedCount || 0 }}</span>
      </div>
      <p v-if="notice" class="business-success">{{ notice }}</p>
      <AsyncState :loading="loading" :error="error" :empty="!taskId || !rows.length" :empty-text="taskId ? '当前任务池为空' : '请选择任务'" @retry="load">
        <div class="business-table-wrap">
          <table class="business-table">
            <thead><tr>
              <th><input type="checkbox" :checked="selection.pageAllSelected.value" aria-label="选择当前页面" @change="selection.togglePage"/></th>
              <th>条目编号</th><th><TaskItemFilters kind="status" :model-value="filters" @change="changeFilters"/></th><th>采集员 ID</th><th><TaskItemFilters kind="collector" :model-value="filters" @change="changeFilters"/></th><th>审核员 ID</th><th>审核员姓名</th><th><TaskItemFilters kind="result" :model-value="filters" @change="changeFilters"/></th><th>修订</th><th>操作</th>
            </tr></thead>
            <tbody><tr v-for="row in rows" :key="row.id">
              <td><input type="checkbox" :checked="selection.isSelected(row)" :aria-label="`选择 ${row.itemCode}`" @change="selection.toggle(row)"/></td>
              <td><router-link :to="`/admin/items/${row.id}/operations`">{{ row.itemCode }}</router-link></td>
              <td>{{ statusLabel('item', row.status) }}</td><td>{{ row.collectorId || '-' }}</td><td>{{ row.collectorName || '-' }}</td>
              <td>{{ row.reviewerId || '-' }}</td><td>{{ row.reviewerName || '-' }}</td><td>{{ row.currentResult?.audio ? '音频' : row.currentResult?.text ? '文本' : '-' }}</td><td>{{ row.revision }}</td>
              <td><router-link class="button-link" :to="`/admin/items/${row.id}`">查看</router-link></td>
            </tr></tbody>
          </table>
        </div>
        <PaginationControls :page="page" :size="20" :total="total" @change="changePage"/>
      </AsyncState>
    </div>
  </section>
</template>
