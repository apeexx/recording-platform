<script setup>
import { onMounted, ref } from 'vue'
import AsyncState from '../../../components/admin/AsyncState.vue'
import PageActions from '../../../components/admin/PageActions.vue'
import PaginationControls from '../../../components/admin/PaginationControls.vue'
import { taskApi } from '../../../lib/taskApi.js'
import { operationId } from '../../../lib/apiUtils.js'
import { statusLabel } from '../../../lib/statusLabels.js'
import { useNotifications } from '../../../composables/useNotifications.js'
const notifications = useNotifications(), rows = ref([]), loading = ref(false), error = ref(''), page = ref(0), total = ref(0)
const resultLabel = configuration => configuration?.resultType === 'TEXT' ? '文本或录音（可同时提交）' : '仅录音'
const durationLabel = configuration => `${(configuration?.minDurationMillis || 1000) / 1000}–${(configuration?.maxDurationMillis || 600000) / 1000} 秒`
async function load() { loading.value = true; error.value = ''; try { const result = await taskApi.list(page.value, 20); rows.value = result.items || []; total.value = result.total || 0 } catch (exception) { if (rows.value.length) notifications.error(exception.message); else error.value = exception.message } finally { loading.value = false } }
function changePage(value) { page.value = value; load() }
async function transition(row, action) { if (['end'].includes(action) && !confirm(`确认结束任务“${row.name}”？`)) return; try { await taskApi.transition(row.id, action, operationId(`task-${action}`)); notifications.success({ publish: '任务已发布', pause: '任务已暂停', resume: '任务已恢复', end: '任务已结束' }[action]); await load() } catch (exception) { notifications.error(exception.message) } }
async function deleteTask(row) {
  if (!confirm(`确认永久删除草稿任务“${row.name}”？任务编号不会复用。`)) return
  try {
    await taskApi.deleteTask(row.id, operationId('task-delete'))
    notifications.success('草稿任务已删除')
    await load()
  } catch (exception) {
    notifications.error(exception.message)
  }
}
onMounted(load)
</script>
<template><section class="admin-page"><PageActions title="任务管理" description="草稿可编辑并准备数据；发布后名称、说明和配置永久冻结。"><router-link class="button-primary" to="/admin/tasks/new">创建任务</router-link></PageActions><div class="business-card"><AsyncState :loading="loading" :error="error" :empty="!rows.length" empty-text="尚未创建任务" @retry="load"><div class="business-table-wrap"><table class="business-table"><thead><tr><th>任务编号</th><th>任务名称</th><th>最终成果</th><th>录音格式</th><th>采样率</th><th>时长范围</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="row in rows" :key="row.id"><td>{{ row.taskCode }}</td><td><router-link :to="`/admin/tasks/${row.id}`">{{ row.name }}</router-link></td><td>{{ resultLabel(row.configuration) }}</td><td>{{ row.configuration?.recordingFormat || '-' }}</td><td>{{ row.configuration?.sampleRates?.map(value => `${value}Hz`).join('、') || '-' }}</td><td>{{ durationLabel(row.configuration) }}</td><td>{{ statusLabel('task', row.lifecycle) }}</td><td><button v-if="row.lifecycle === 'DRAFT'" class="button-link" @click="transition(row, 'publish')">发布</button><button v-if="row.lifecycle === 'DRAFT'" class="button-link is-danger" @click="deleteTask(row)">删除</button><button v-if="row.lifecycle === 'RUNNING'" class="button-link" @click="transition(row, 'pause')">暂停</button><button v-if="row.lifecycle === 'PAUSED'" class="button-link" @click="transition(row, 'resume')">恢复</button><button v-if="row.lifecycle === 'PAUSED'" class="button-link is-danger" @click="transition(row, 'end')">结束</button></td></tr></tbody></table></div><PaginationControls :page="page" :total="total" :size="20" @change="changePage" /></AsyncState></div></section></template>
