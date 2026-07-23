<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AsyncState from '../../../components/admin/AsyncState.vue'
import PageActions from '../../../components/admin/PageActions.vue'
import PaginationControls from '../../../components/admin/PaginationControls.vue'
import BaseSelect from '../../../components/form/BaseSelect.vue'
import TaskItemEditModal from '../../../components/admin/TaskItemEditModal.vue'
import { taskApi } from '../../../lib/taskApi.js'
import { operationId } from '../../../lib/apiUtils.js'
import { statusLabel } from '../../../lib/statusLabels.js'
import { useNotifications } from '../../../composables/useNotifications.js'

const notifications = useNotifications()
const route = useRoute()
const router = useRouter()
const itemPageSize = ref(10)
const task = ref(null)
const items = ref([])
const page = ref(0)
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const notice = ref('')
const selected = ref(new Set())
const targetStatus = ref('COMPLETED')
const statusOptions = ref([])
const importFile = ref(null)
const fileInput = ref(null)
const importBusy = ref(false)
const dragging = ref(false)
const job = ref(null)
const activeImportJobId = ref(null)
const pollTimer = ref(null)
const editingItem = ref(null)
const editBusy = ref(false)
const itemForm = reactive({ referenceText: '', referenceAudioUrl: '', referenceVideoUrl: '' })
const processedRows = computed(() => (Number(job.value?.successRows) || 0) + (Number(job.value?.failureRows) || 0))
const importProgress = computed(() => {
  const totalRows = Number(job.value?.totalRows) || 0
  return totalRows ? Math.min(100, Math.round(processedRows.value / totalRows * 100)) : 0
})
const jobErrorMessage = computed(() => job.value?.rowErrors?.[0]?.message || '')

async function loadItems() {
  const result = await taskApi.items(route.params.id, page.value, itemPageSize.value)
  const nextTotal = Number(result.total) || 0
  const lastPage = Math.max(Math.ceil(nextTotal / itemPageSize.value) - 1, 0)
  if (page.value > lastPage) {
    page.value = lastPage
    return loadItems()
  }
  total.value = nextTotal
  items.value = result.items || []
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const taskData = await taskApi.get(route.params.id)
    task.value = taskData
    statusOptions.value = [
      { value: 'RECORDING_PENDING', label: '待录制' },
      taskData.configuration?.humanReviewEnabled
        ? { value: 'SUBMITTED', label: '已提交' }
        : { value: 'COMPLETED', label: '已完成' },
    ]
    await loadItems()
  } catch (exception) {
    loadError.value = exception.message
  } finally {
    loading.value = false
  }
}

async function add() {
  try {
    await taskApi.addItem(route.params.id, { ...itemForm }, operationId('item-add'))
    Object.keys(itemForm).forEach((key) => { itemForm[key] = '' })
    notifications.success('数据条目已添加')
    page.value = 0
    await loadItems()
  } catch (exception) {
    notifications.error(exception.message)
  }
}

function chooseImportFile(file) {
  if (!file) return
  if (!file.name.toLowerCase().endsWith('.csv')) {
    notifications.error('请选择单个 CSV 文件')
    return
  }
  stopImportTracking()
  importFile.value = file
  job.value = null
  notice.value = ''
}

function onDrop(event) {
  dragging.value = false
  const files = [...(event.dataTransfer?.files || [])]
  if (files.length !== 1) {
    notifications.error('一次只能拖入一个 CSV 文件')
    return
  }
  chooseImportFile(files[0])
}

function clearPoll() {
  if (pollTimer.value) window.clearTimeout(pollTimer.value)
  pollTimer.value = null
}

function stopImportTracking() {
  clearPoll()
  activeImportJobId.value = null
}

function schedulePoll() {
  clearPoll()
  pollTimer.value = window.setTimeout(refreshJob, 1000)
}

async function upload() {
  if (!importFile.value || importBusy.value) return
  stopImportTracking()
  importBusy.value = true
  try {
    job.value = await taskApi.importItems(route.params.id, importFile.value, operationId('import'))
    activeImportJobId.value = job.value.importJobId
    notice.value = '导入任务已进入队列，进度会自动刷新'
    notifications.success('CSV 已进入导入队列')
    schedulePoll()
  } catch (exception) {
    notifications.error(exception.message)
  } finally {
    importBusy.value = false
  }
}

async function refreshJob() {
  clearPoll()
  const importJobId = activeImportJobId.value
  if (!importJobId) return
  try {
    job.value = await taskApi.importJob(importJobId)
    if (['PENDING', 'PROCESSING'].includes(job.value.status)) {
      schedulePoll()
      return
    }
    activeImportJobId.value = null
    if (job.value.status === 'COMPLETED') {
      importFile.value = null
      if (fileInput.value) fileInput.value.value = ''
      notifications.success('批量导入已完成，数据池已刷新')
      page.value = 0
      await loadItems()
    } else if (job.value.status === 'PARTIAL_SUCCESS') {
      notifications.error(jobErrorMessage.value || '部分数据导入成功，可重试失败行')
      page.value = 0
      await loadItems()
    } else if (job.value.status === 'FAILED') {
      notifications.error(jobErrorMessage.value || '批量导入失败')
    }
  } catch (exception) {
    notifications.error(exception.message)
  }
}

async function retryImport() {
  const importJobId = activeImportJobId.value || job.value?.id
  if (!importJobId) return
  try {
    clearPoll()
    job.value = await taskApi.retryImport(importJobId, operationId('import-retry'))
    activeImportJobId.value = job.value.importJobId
    notifications.success('失败行已重新进入导入队列')
    schedulePoll()
  } catch (exception) {
    notifications.error(exception.message)
  }
}

function downloadTemplate() {
  const content = '\uFEFFreferenceText,referenceAudioUrl,referenceVideoUrl\r\n示例文字,https://cdn.example.com/reference.wav,https://cdn.example.com/reference.mp4\r\n'
  const url = URL.createObjectURL(new Blob([content], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = 'task-items-import-example.csv'
  link.click()
  URL.revokeObjectURL(url)
}

async function deleteTask() {
  if (!confirm(`确认永久删除草稿任务 ${task.value?.taskCode}？任务编号不会复用。`)) return
  try {
    await taskApi.deleteTask(route.params.id, operationId('task-delete'))
    notifications.success('草稿任务已删除')
    await router.push('/admin/tasks')
  } catch (exception) {
    notifications.error(exception.message)
  }
}

function openEdit(row) {
  editingItem.value = row
}

async function saveEdit(values) {
  editBusy.value = true
  try {
    await taskApi.updateItem(editingItem.value.id, {
      expectedRevision: editingItem.value.revision,
      ...values,
    }, operationId('item-edit'))
    notifications.success('待领取数据已更新')
    editingItem.value = null
    await loadItems()
  } catch (exception) {
    notifications.error(exception.message)
  } finally {
    editBusy.value = false
  }
}

async function deleteItem(row) {
  if (!confirm(`确认永久删除待领取条目 ${row.itemCode}？条目编号不会复用。`)) return
  try {
    await taskApi.deleteItem(row.id, row.revision, operationId('item-delete'))
    notifications.success('待领取数据已删除')
    selected.value.delete(row.id)
    await loadItems()
  } catch (exception) {
    notifications.error(exception.message)
  }
}

async function itemAction(row, action) {
  if (!confirm(`确认${action === 'discard' ? '废弃' : '恢复'}条目 ${row.itemCode}？`)) return
  try {
    await taskApi[action](row.id, operationId(`item-${action}`), row.revision)
    notifications.success(action === 'discard' ? '条目已废弃' : '条目已恢复')
    await loadItems()
  } catch (exception) {
    notifications.error(exception.message)
  }
}

function toggle(row) {
  const next = new Set(selected.value)
  next.has(row.id) ? next.delete(row.id) : next.add(row.id)
  selected.value = next
}

async function changePage(value) { page.value = value; selected.value = new Set(); await loadItems() }
async function changePageSize(value) { itemPageSize.value = value; page.value = 0; selected.value = new Set(); await loadItems() }

async function batch(action) {
  if (!selected.value.size || !confirm(`确认批量执行 ${action}，共 ${selected.value.size} 条？`)) return
  const commands = items.value.filter((row) => selected.value.has(row.id)).map((row) => ({ itemId: row.id, expectedRevision: row.revision }))
  try {
    const result = await taskApi.batchAction(action, commands, operationId(`batch-${action}`))
    notice.value = `批量操作完成：成功 ${result.filter((row) => row.success).length}，冲突 ${result.filter((row) => !row.success).length}`
    notifications.success(notice.value)
    selected.value = new Set()
    await loadItems()
  } catch (exception) {
    notifications.error(exception.message)
  }
}

async function changeStatus() {
  if (!selected.value.size || !confirm(`确认将 ${selected.value.size} 条数据调整为 ${targetStatus.value}？`)) return
  const commands = items.value.filter((row) => selected.value.has(row.id)).map((row) => ({ itemId: row.id, expectedRevision: row.revision, collectorId: row.collectorId }))
  try {
    const result = await taskApi.batchStatus(targetStatus.value, commands, operationId('batch-status'))
    notice.value = `状态调整完成：成功 ${result.filter((row) => row.success).length}，冲突 ${result.filter((row) => !row.success).length}`
    notifications.success(notice.value)
    selected.value = new Set()
    await loadItems()
  } catch (exception) {
    notifications.error(exception.message)
  }
}

onMounted(load)
onBeforeUnmount(stopImportTracking)
</script>

<template>
  <section class="admin-page">
    <PageActions :title="task?.name || '任务详情'" :description="task ? `${task.taskCode} · ${statusLabel('task', task.lifecycle)}` : ''">
      <router-link v-if="task?.lifecycle === 'DRAFT'" class="button-secondary" :to="`/admin/tasks/${route.params.id}/edit`">编辑草稿</router-link>
      <button v-if="task?.lifecycle === 'DRAFT'" class="button-secondary is-danger" @click="deleteTask">删除任务</button>
      <router-link class="button-primary" :to="`/admin/tasks/${route.params.id}/permissions`">采集权限</router-link>
    </PageActions>
    <AsyncState :loading="loading" :error="loadError" :empty="!task" @retry="load">
      <div class="business-grid task-detail-grid">
        <div class="task-tools-column">
          <form class="business-card business-form" novalidate @submit.prevent="add">
            <h3>添加数据</h3>
            <label v-if="task?.configuration?.referenceTypes?.includes('TEXT')">参考文字<textarea v-model.trim="itemForm.referenceText" class="task-reference-textarea" rows="5" /></label>
            <label v-if="task?.configuration?.referenceTypes?.includes('AUDIO')">参考音频 URL<input v-model.trim="itemForm.referenceAudioUrl" type="url" /></label>
            <label v-if="task?.configuration?.referenceTypes?.includes('VIDEO')">参考视频 URL<input v-model.trim="itemForm.referenceVideoUrl" type="url" /></label>
            <p class="business-note">条目编号由系统自动生成；仅显示任务启用的参考内容。</p>
            <button class="button-primary">添加</button>
          </form>

          <section class="business-card business-form">
            <div class="business-heading">
              <h3>批量导入</h3>
              <button type="button" class="button-link" @click="downloadTemplate">下载示例 CSV</button>
            </div>
            <div class="csv-dropzone" :class="{ 'is-dragging': dragging }"
              role="button" tabindex="0" @click="fileInput?.click()" @keydown.enter="fileInput?.click()"
              @dragenter.prevent="dragging = true" @dragover.prevent="dragging = true"
              @dragleave.prevent="dragging = false" @drop.prevent="onDrop">
              <input ref="fileInput" class="visually-hidden" type="file" accept=".csv,text/csv" @change="chooseImportFile($event.target.files?.[0])">
              <strong>{{ importFile ? importFile.name : '拖入 CSV，或点击选择' }}</strong>
              <span>{{ importFile ? `${(importFile.size / 1024).toFixed(1)} KB` : '一次选择一个文件，选择后再开始导入' }}</span>
            </div>
            <button type="button" class="button-primary" :disabled="!importFile || importBusy" @click="upload">{{ importBusy ? '提交中…' : '开始导入' }}</button>
            <div v-if="job" class="import-progress-card" aria-live="polite">
              <div><strong>{{ statusLabel('import', job.status) }}</strong><span>{{ importProgress }}%</span></div>
              <progress :value="importProgress" max="100">{{ importProgress }}%</progress>
              <p>总数 {{ job.totalRows || 0 }} · 已处理 {{ processedRows }} · 成功 {{ job.successRows || 0 }} · 失败 {{ job.failureRows || 0 }}</p>
              <button v-if="['PARTIAL_SUCCESS', 'FAILED'].includes(job.status) && job.failureRows" type="button" class="button-secondary" @click="retryImport">重试失败行</button>
            </div>
            <p v-if="notice" class="business-success">{{ notice }}</p>
          </section>
        </div>

        <div class="business-card business-card-wide task-pool-card">
          <div class="business-heading task-pool-heading">
            <h3>数据池（共 {{ total }} 条）</h3>
            <div class="business-actions task-pool-toolbar">
              <BaseSelect v-model="targetStatus" :options="statusOptions" aria-label="目标状态" />
              <button class="button-secondary" :disabled="!selected.size" @click="changeStatus">调整状态</button>
              <button class="button-secondary" :disabled="!selected.size" @click="batch('release')">批量释放</button>
              <button class="button-secondary is-danger" :disabled="!selected.size" @click="batch('discard')">批量废弃</button>
              <button class="button-secondary" :disabled="!selected.size" @click="batch('restore')">批量恢复</button>
            </div>
          </div>
          <AsyncState :loading="false" :error="''" :empty="!items.length">
            <div class="business-table-wrap">
              <table class="business-table">
                <thead><tr><th>选择</th><th>编号</th><th>状态</th><th>采集员</th><th>结果</th><th>操作</th></tr></thead>
                <tbody><tr v-for="row in items" :key="row.id">
                  <td><label class="business-check colored-checkbox"><input type="checkbox" :checked="selected.has(row.id)" :aria-label="`选择 ${row.itemCode}`" @change="toggle(row)"><span class="visually-hidden">选择</span></label></td>
                  <td>{{ row.itemCode }}</td><td>{{ statusLabel('item', row.status) }}</td><td>{{ row.collectorId || '-' }}</td>
                  <td>{{ row.currentResult?.audio?.durationMillis ? `${Math.round(row.currentResult.audio.durationMillis / 1000)}秒` : row.currentResult?.text ? '文本' : '-' }}</td>
                  <td class="table-actions">
                    <template v-if="row.status === 'AVAILABLE'">
                      <button class="button-link" @click="openEdit(row)">编辑</button>
                      <button class="button-link is-danger" @click="deleteItem(row)">删除</button>
                    </template>
                    <button v-else-if="row.status !== 'DISCARDED'" class="button-link is-danger" @click="itemAction(row, 'discard')">废弃</button>
                    <button v-else class="button-link" @click="itemAction(row, 'restore')">恢复</button>
                    <router-link class="button-link" :to="`/admin/items/${row.id}/operations`">记录</router-link>
                  </td>
                </tr></tbody>
              </table>
            </div>
            <PaginationControls numbered :page="page" :size="itemPageSize" :page-sizes="[5, 10, 20]" :total="total" @change="changePage" @size-change="changePageSize" />
          </AsyncState>
        </div>
      </div>
    </AsyncState>
    <TaskItemEditModal :open="Boolean(editingItem)" :item="editingItem" :reference-types="task?.configuration?.referenceTypes || []"
      :busy="editBusy" @close="editingItem = null" @save="saveEdit" />
  </section>
</template>
