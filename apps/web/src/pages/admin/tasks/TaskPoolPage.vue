<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import AsyncState from '../../../components/admin/AsyncState.vue'
import PageActions from '../../../components/admin/PageActions.vue'
import PaginationControls from '../../../components/admin/PaginationControls.vue'
import BaseSelect from '../../../components/form/BaseSelect.vue'
import TaskBatchActionMenu from '../../../components/admin/TaskBatchActionMenu.vue'
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
const selectedTask = computed(() => tasks.value.find(task => task.id === taskId.value))
const statusOptions = computed(() => [
  { value: 'RECORDING_PENDING', label: '待录制' },
  selectedTask.value?.configuration?.humanReviewEnabled
    ? { value: 'SUBMITTED', label: '已提交' }
    : { value: 'COMPLETED', label: '已完成' },
])
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
function startDownload(url) {
  const link = document.createElement('a')
  link.href = url
  link.download = ''
  document.body.appendChild(link)
  link.click()
  link.remove()
}
async function exportCsv() {
  if (!taskId.value) return
  try {
    await taskApi.prepareExport(taskId.value, filters.value)
    startDownload(taskApi.exportItems(taskId.value, filters.value))
    notifications.success('CSV 导出已开始')
  } catch (error) {
    notifications.error(error.message || 'CSV 导出失败')
  }
}
async function copySourceId(value) {
  try {
    await navigator.clipboard.writeText(value)
    notifications.success('脚本 ID 已复制')
  } catch {
    notifications.error('复制失败，请手动选择脚本 ID')
  }
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
  if (selection.mode.value === 'ALL') {
    const key = action === 'status' ? 'STATUS' : action.toUpperCase()
    return Number(preview.value?.applicableCounts?.[key]) || 0
  }
  return selection.pageCommands().filter(command => {
    const row = rows.value.find(item => item.id === command.itemId)
    if (action === 'restore') return row?.status === 'DISCARDED'
    if (action === 'discard') return row?.status !== 'DISCARDED'
    if (action === 'release') return row?.status !== 'AVAILABLE' && row?.status !== 'DISCARDED'
    return row?.status !== 'DISCARDED'
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
async function changeStatus(targetStatus) {
  const count = applicableCount('status')
  if (!count || !confirm(`确认将 ${count} 条适用数据调整为 ${targetStatus}？`)) return
  try {
    if (selection.mode.value === 'ALL') {
      batchJob.value = await batchOperationApi.create({
        operationId: operationId('pool-all-status'),
        action: 'STATUS',
        selection: selection.selectionPayload(taskId.value, 'TASK_POOL', selectionFilters(filters.value)),
        targetStatus,
      })
      notifications.success('跨页状态调整已进入队列')
      schedulePoll()
      return
    }
    const result = await taskApi.batchStatus(targetStatus, selection.pageCommands(), operationId('pool-status'))
    notice.value = `状态调整完成：成功 ${result.filter(row => row.success).length}，冲突 ${result.filter(row => !row.success).length}`
    notifications.success(notice.value)
    selection.clear()
    await load()
  } catch (exception) {
    notifications.error(exception.message)
  }
}
async function rowAction(row, action) {
  const label = action === 'release' ? '释放' : action === 'discard' ? '废弃' : '恢复'
  if (!window.confirm(`确认${label}条目 ${row.itemCode}？`)) return
  try {
    await taskApi[action](row.id, operationId(`pool-row-${action}`), row.revision)
    notifications.success(`${label}成功`)
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
        <button class="button-secondary" :disabled="!taskId" @click="exportCsv">导出 CSV</button>
        <TaskBatchActionMenu :selected-count="selection.selectedCount.value"
          :counts="{ status: applicableCount('status'), release: applicableCount('release'), discard: applicableCount('discard'), restore: applicableCount('restore') }"
          :status-options="statusOptions" :disabled="processing" @status="changeStatus" @release="batch('release')" @discard="batch('discard')" @restore="batch('restore')" />
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
              <th><TaskItemFilters kind="code" :task-id="taskId" :model-value="filters" @change="changeFilters"/></th><th><TaskItemFilters kind="source" :model-value="filters" @change="changeFilters"/></th><th><TaskItemFilters kind="status" :model-value="filters" @change="changeFilters"/></th><th>采集员 ID</th><th><TaskItemFilters kind="collector" :model-value="filters" @change="changeFilters"/></th><th>审核员 ID</th><th>审核员姓名</th><th><TaskItemFilters kind="result" :model-value="filters" @change="changeFilters"/></th><th>修订</th><th>操作</th>
            </tr></thead>
            <tbody><tr v-for="row in rows" :key="row.id">
              <td><input type="checkbox" :checked="selection.isSelected(row)" :aria-label="`选择 ${row.itemCode}`" @change="selection.toggle(row)"/></td>
              <td>{{ row.itemCode }}</td>
              <td><span v-if="row.sourceItemId" class="source-binding"><small>{{ row.sourcePlatform }}</small><button class="button-link" :title="row.sourceItemId" @click="copySourceId(row.sourceItemId)">{{ row.sourceItemId }}</button></span><span v-else class="business-note">平台内创建</span></td>
              <td>{{ statusLabel('item', row.status) }}</td><td>{{ row.collectorId || '-' }}</td><td>{{ row.collectorName || '-' }}</td>
              <td>{{ row.reviewerId || '-' }}</td><td>{{ row.reviewerName || '-' }}</td><td>{{ row.currentResult?.audio ? '音频' : row.currentResult?.text ? '文本' : '-' }}</td><td>{{ row.revision }}</td>
              <td class="table-row-actions">
                <router-link class="button-link" :to="`/admin/items/${row.id}`">查看</router-link>
                <button v-if="row.status === 'AVAILABLE'" class="button-link is-danger" @click="rowAction(row, 'discard')">废弃</button>
                <button v-else-if="row.status === 'DISCARDED'" class="button-link" @click="rowAction(row, 'restore')">恢复</button>
                <template v-else>
                  <button class="button-link" @click="rowAction(row, 'release')">释放</button>
                  <button class="button-link is-danger" @click="rowAction(row, 'discard')">废弃</button>
                </template>
              </td>
            </tr></tbody>
          </table>
        </div>
        <PaginationControls :page="page" :size="20" :total="total" @change="changePage"/>
      </AsyncState>
    </div>
  </section>
</template>

<style scoped>
.table-row-actions{display:flex;align-items:center;gap:3px;white-space:nowrap}
.source-binding{display:grid;max-width:220px}.source-binding small{color:var(--muted-foreground)}.source-binding button{overflow:hidden;text-overflow:ellipsis;text-align:left}
</style>
