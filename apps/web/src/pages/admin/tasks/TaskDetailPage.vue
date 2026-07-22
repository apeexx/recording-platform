<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import AsyncState from '../../../components/admin/AsyncState.vue'
import PageActions from '../../../components/admin/PageActions.vue'
import PaginationControls from '../../../components/admin/PaginationControls.vue'
import BaseSelect from '../../../components/form/BaseSelect.vue'
import { taskApi } from '../../../lib/taskApi.js'
import { operationId } from '../../../lib/apiUtils.js'
import { statusLabel } from '../../../lib/statusLabels.js'
import { useNotifications } from '../../../composables/useNotifications.js'

const notifications = useNotifications()
const route = useRoute()
const itemPageSize = ref(10)
const task = ref(null)
const items = ref([])
const page = ref(0)
const total = ref(0)
const loading = ref(false)
const error = ref('')
const notice = ref('')
const selected = ref(new Set())
const targetStatus = ref('COMPLETED')
const statusOptions = ref([])
const importFile = ref(null)
const job = ref(null)
const itemForm = reactive({ referenceText: '', referenceAudioUrl: '', referenceVideoUrl: '' })

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
  error.value = ''
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
    error.value = exception.message
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
    await load()
  } catch (exception) {
    error.value = exception.message
    notifications.error(exception.message)
  }
}

async function upload() {
  if (!importFile.value) return
  try {
    job.value = await taskApi.importItems(route.params.id, importFile.value, operationId('import'))
    notice.value = `导入任务 ${job.value.importJobId} 已进入队列`
    notifications.success('CSV 已进入导入队列')
  } catch (exception) {
    error.value = exception.message
    notifications.error(exception.message)
  }
}

async function refreshJob() {
  if (job.value?.importJobId) job.value = await taskApi.importJob(job.value.importJobId)
}

async function retryImport() {
  if (!job.value?.importJobId) return
  try {
    job.value = await taskApi.retryImport(job.value.importJobId, operationId('import-retry'))
    notifications.success('失败行已重新进入导入队列')
  } catch (exception) {
    error.value = exception.message
    notifications.error(exception.message)
  }
}

async function itemAction(row, action) {
  if (!confirm(`确认${action === 'discard' ? '废弃' : '恢复'}条目 ${row.itemCode}？`)) return
  try {
    await taskApi[action](row.id, operationId(`item-${action}`), row.revision)
    notifications.success(action === 'discard' ? '条目已废弃' : '条目已恢复')
    await load()
  } catch (exception) {
    error.value = exception.message
    notifications.error(exception.message)
  }
}

function toggle(row) {
  const next = new Set(selected.value)
  next.has(row.id) ? next.delete(row.id) : next.add(row.id)
  selected.value = next
}

async function changePage(value) {
  page.value = value
  selected.value = new Set()
  await load()
}

async function changePageSize(value) {
  itemPageSize.value = value
  page.value = 0
  selected.value = new Set()
  await load()
}

async function batch(action) {
  if (!selected.value.size || !confirm(`确认批量执行 ${action}，共 ${selected.value.size} 条？`)) return
  const commands = items.value.filter((row) => selected.value.has(row.id)).map((row) => ({ itemId: row.id, expectedRevision: row.revision }))
  try {
    const result = await taskApi.batchAction(action, commands, operationId(`batch-${action}`))
    notice.value = `批量操作完成：成功 ${result.filter((row) => row.success).length}，冲突 ${result.filter((row) => !row.success).length}`
    notifications.success(notice.value)
    selected.value = new Set()
    await load()
  } catch (exception) {
    error.value = exception.message
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
    await load()
  } catch (exception) {
    error.value = exception.message
    notifications.error(exception.message)
  }
}

onMounted(load)
</script>

<template>
  <section class="admin-page">
    <PageActions :title="task?.name || '任务详情'" :description="task ? `${task.taskCode} · ${statusLabel('task', task.lifecycle)}` : ''">
      <router-link v-if="task?.lifecycle === 'DRAFT'" class="button-secondary" :to="`/admin/tasks/${route.params.id}/edit`">编辑草稿</router-link>
      <router-link class="button-primary" :to="`/admin/tasks/${route.params.id}/permissions`">采集权限</router-link>
    </PageActions>
    <AsyncState :loading="loading" :error="error" :empty="!task" @retry="load">
      <div class="business-grid">
        <form class="business-card business-form" @submit.prevent="add">
          <h3>添加池数据</h3>
          <label v-if="task?.configuration?.referenceTypes?.includes('TEXT')">参考文字<textarea v-model.trim="itemForm.referenceText" rows="4" /></label>
          <label v-if="task?.configuration?.referenceTypes?.includes('AUDIO')">参考音频 URL<input v-model.trim="itemForm.referenceAudioUrl" type="url" /></label>
          <label v-if="task?.configuration?.referenceTypes?.includes('VIDEO')">参考视频 URL<input v-model.trim="itemForm.referenceVideoUrl" type="url" /></label>
          <p class="business-note">条目编号由系统自动生成；仅显示任务启用的参考内容，远程 URL 由后端安全下载。</p>
          <button class="button-primary">添加</button>
          <hr>
          <h3>批量导入</h3>
          <input type="file" accept=".csv" @change="importFile = $event.target.files[0]">
          <button type="button" class="button-primary" @click="upload">开始导入</button>
          <div v-if="job" class="business-note">状态：{{ statusLabel('import', job.status) }} <button type="button" class="button-link" @click="refreshJob">刷新</button><button v-if="['PARTIAL_SUCCESS', 'FAILED'].includes(job.status)" type="button" class="button-link" @click="retryImport">重试失败行</button></div>
          <p v-if="notice" class="business-success">{{ notice }}</p>
        </form>
        <div class="business-card business-card-wide">
          <div class="business-heading">
            <h3>数据池（共 {{ total }} 条）</h3>
            <div class="business-actions">
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
                <tbody><tr v-for="row in items" :key="row.id"><td><input type="checkbox" :checked="selected.has(row.id)" @change="toggle(row)"></td><td>{{ row.itemCode }}</td><td>{{ statusLabel('item', row.status) }}</td><td>{{ row.collectorId || '-' }}</td><td>{{ row.currentResult?.audio?.durationMillis ? `${Math.round(row.currentResult.audio.durationMillis / 1000)}秒` : row.currentResult?.text ? '文本' : '-' }}</td><td><button v-if="row.status !== 'DISCARDED'" class="button-link is-danger" @click="itemAction(row, 'discard')">废弃</button><button v-else class="button-link" @click="itemAction(row, 'restore')">恢复</button><router-link class="button-link" :to="`/admin/items/${row.id}/operations`">记录</router-link></td></tr></tbody>
              </table>
            </div>
            <PaginationControls numbered :page="page" :size="itemPageSize" :page-sizes="[5, 10, 20]" :total="total" @change="changePage" @size-change="changePageSize" />
          </AsyncState>
        </div>
      </div>
    </AsyncState>
  </section>
</template>
