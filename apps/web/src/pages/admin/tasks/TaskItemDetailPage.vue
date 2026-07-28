<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AsyncState from '../../../components/admin/AsyncState.vue'
import PageActions from '../../../components/admin/PageActions.vue'
import { taskApi } from '../../../lib/taskApi.js'
import { reportApi } from '../../../lib/reportApi.js'
import { operationId } from '../../../lib/apiUtils.js'
import { statusLabel } from '../../../lib/statusLabels.js'
import { referenceMediaUrl } from '../../../lib/taskMedia.js'
import { useNotifications } from '../../../composables/useNotifications.js'

const route = useRoute()
const router = useRouter()
const notifications = useNotifications()
const item = ref(null)
const task = ref(null)
const recentOperations = ref([])
const operationTotal = ref(0)
const loading = ref(false)
const loadError = ref('')
const editing = ref(false)
const editBusy = ref(false)
const operationsOpen = ref(false)
const allOperations = ref([])
const allOperationsLoading = ref(false)
const allOperationsError = ref('')
const editForm = reactive({ referenceText: '', referenceAudioUrl: '', referenceVideoUrl: '' })
const referenceTypes = computed(() => task.value?.configuration?.referenceTypes || [])
const referenceAudioUrl = computed(() => referenceMediaUrl(item.value, 'audio'))
const referenceVideoUrl = computed(() => referenceMediaUrl(item.value, 'video'))
const resultAudioUrl = computed(() => item.value?.currentResult?.audio?.mediaId
  ? `/api/media/${encodeURIComponent(item.value.currentResult.audio.mediaId)}`
  : '')

function copyEditValues() {
  editForm.referenceText = item.value?.referenceText || ''
  editForm.referenceAudioUrl = item.value?.referenceAudioUrl || ''
  editForm.referenceVideoUrl = item.value?.referenceVideoUrl || ''
}
function beginEdit() {
  copyEditValues()
  editing.value = true
}
function cancelEdit() {
  editing.value = false
  copyEditValues()
}
async function load() {
  loading.value = true
  loadError.value = ''
  try {
    item.value = await taskApi.item(route.params.itemId)
    const [taskValue, history] = await Promise.all([
      taskApi.get(item.value.taskId),
      reportApi.itemOperations(item.value.id, { page: 0, size: 3 }),
    ])
    task.value = taskValue
    recentOperations.value = history.items || []
    operationTotal.value = Number(history.total) || 0
    copyEditValues()
  } catch (error) {
    loadError.value = error.message
  } finally {
    loading.value = false
  }
}
async function saveEdit() {
  editBusy.value = true
  try {
    item.value = await taskApi.updateItem(item.value.id, {
      expectedRevision: item.value.revision,
      referenceText: editForm.referenceText,
      referenceAudioUrl: editForm.referenceAudioUrl,
      referenceVideoUrl: editForm.referenceVideoUrl,
    }, operationId('item-edit'))
    editing.value = false
    notifications.success('待领取数据已更新')
    await load()
  } catch (error) {
    notifications.error(error.message)
  } finally {
    editBusy.value = false
  }
}
async function remove() {
  if (!confirm(`确认永久删除待领取条目 ${item.value.itemCode}？`)) return
  try {
    await taskApi.deleteItem(item.value.id, item.value.revision, operationId('item-delete'))
    notifications.success('待领取数据已删除')
    await router.push(`/admin/tasks/${item.value.taskId}`)
  } catch (error) {
    notifications.error(error.message)
  }
}
async function mutate(action) {
  if (!confirm(`确认${{ release: '释放', discard: '废弃', restore: '恢复' }[action]}该条目？`)) return
  try {
    await taskApi[action](item.value.id, operationId(`item-${action}`), item.value.revision)
    notifications.success('操作已完成')
    await load()
  } catch (error) {
    notifications.error(error.message)
  }
}
async function loadAllOperations() {
  allOperationsLoading.value = true
  allOperationsError.value = ''
  try {
    const merged = []
    let currentPage = 0
    let expectedTotal = operationTotal.value
    do {
      const result = await reportApi.itemOperations(item.value.id, { page: currentPage, size: 100 })
      const pageItems = result.items || []
      merged.push(...pageItems)
      expectedTotal = Number(result.total) || 0
      currentPage += 1
      if (!pageItems.length) break
    } while (merged.length < expectedTotal)
    allOperations.value = merged
    operationTotal.value = expectedTotal
  } catch (error) {
    allOperationsError.value = error.message
    if (allOperations.value.length) notifications.error(error.message)
  } finally {
    allOperationsLoading.value = false
  }
}
async function openOperations() {
  operationsOpen.value = true
  if (!allOperations.value.length || allOperations.value.length < operationTotal.value) await loadAllOperations()
}
function closeOperations() {
  operationsOpen.value = false
  if (route.hash === '#operations') router.replace({ path: route.path, query: route.query })
}
function onKeydown(event) {
  if (event.key === 'Escape' && operationsOpen.value) closeOperations()
}

watch(() => route.hash, (hash) => {
  if (hash === '#operations' && item.value) openOperations()
})
onMounted(async () => {
  document.addEventListener('keydown', onKeydown)
  await load()
  if (route.hash === '#operations') await openOperations()
})
onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))
</script>

<template>
  <section class="admin-page">
    <PageActions :title="item?.itemCode || '条目详情'" :description="item ? `${task?.name || ''} · ${statusLabel('item', item.status)}` : ''">
      <button class="button-secondary" @click="router.back()">返回</button>
    </PageActions>
    <AsyncState :loading="loading" :error="loadError" :empty="!item" @retry="load">
      <div class="item-detail-layout">
        <main class="item-detail-main">
          <article class="business-card">
            <div class="business-heading"><div><h3>参考源</h3><p v-if="editing">正在编辑参考内容，编号不可修改。</p></div></div>
            <section v-if="referenceTypes.includes('TEXT')" class="reference-source-block">
              <h4>参考文本</h4>
              <textarea v-if="editing" v-model.trim="editForm.referenceText" class="task-reference-textarea" rows="7"/>
              <div v-else-if="item.referenceText" class="reference-text">{{ item.referenceText }}</div>
              <p v-else class="business-note">无参考文本</p>
            </section>
            <section v-if="referenceTypes.includes('AUDIO')" class="reference-source-block">
              <h4>参考音频</h4>
              <audio v-if="referenceAudioUrl" controls :src="referenceAudioUrl"/>
              <p v-else class="business-note">无参考音频</p>
              <label v-if="editing" class="inline-reference-field">参考音频 URL<input v-model.trim="editForm.referenceAudioUrl" type="url" placeholder="https://..."></label>
            </section>
            <section v-if="referenceTypes.includes('VIDEO')" class="reference-source-block">
              <h4>参考视频</h4>
              <video v-if="referenceVideoUrl" controls :src="referenceVideoUrl"/>
              <p v-else class="business-note">无参考视频</p>
              <label v-if="editing" class="inline-reference-field">参考视频 URL<input v-model.trim="editForm.referenceVideoUrl" type="url" placeholder="https://..."></label>
            </section>
            <div v-if="editing" class="business-actions inline-edit-actions">
              <button type="button" class="button-secondary" :disabled="editBusy" @click="cancelEdit">取消</button>
              <button type="button" class="button-primary" :disabled="editBusy" @click="saveEdit">{{ editBusy ? '保存中…' : '保存修改' }}</button>
            </div>
          </article>
        </main>
        <aside class="item-detail-aside">
          <article class="business-card item-context">
            <h3>条目信息</h3>
            <dl>
              <dt>状态</dt><dd>{{ statusLabel('item', item.status) }}</dd>
              <dt>采集员</dt><dd>{{ item.collectorName || '-' }}<small>{{ item.collectorId || '未分配' }}</small></dd>
              <dt>审核员</dt><dd>{{ item.reviewerName || '-' }}<small>{{ item.reviewerId || '未分配' }}</small></dd>
              <dt>修订版本</dt><dd>{{ item.revision }}</dd>
              <dt v-if="item.currentDiscard">废弃原因</dt><dd v-if="item.currentDiscard">{{ item.currentDiscard.reason || '-' }}</dd>
            </dl>
            <div class="business-actions">
              <button v-if="item.status === 'AVAILABLE'" class="button-secondary" @click="beginEdit">编辑</button>
              <button v-if="item.status === 'AVAILABLE'" class="button-secondary is-danger" @click="remove">删除</button>
              <button v-if="!['AVAILABLE','DISCARDED'].includes(item.status)" class="button-secondary" @click="mutate('release')">释放</button>
              <button v-if="!['AVAILABLE','DISCARDED'].includes(item.status)" class="button-secondary is-danger" @click="mutate('discard')">废弃</button>
              <button v-if="item.status === 'DISCARDED'" class="button-primary" @click="mutate('restore')">恢复</button>
            </div>
          </article>
          <article class="business-card">
            <h3>采集结果</h3>
            <audio v-if="resultAudioUrl" controls :src="resultAudioUrl"/>
            <p v-else class="business-note">无结果音频</p>
            <div v-if="item.currentResult?.text" class="reference-text">{{ item.currentResult.text }}</div>
            <p v-else class="business-note">无结果文本</p>
          </article>
          <article id="operations" class="business-card">
            <div class="business-heading"><div><h3>最近操作记录</h3><p>显示最近 3 条，共 {{ operationTotal }} 条。</p></div><button type="button" class="button-link" @click="openOperations">查看更多</button></div>
            <div v-if="recentOperations.length" class="operation-stream">
              <div v-for="(row,index) in recentOperations" :key="`${row.time}-${index}`"><i/><span><strong>{{ row.operator }}</strong>{{ row.content }}<small>{{ row.time }}</small></span></div>
            </div>
            <p v-else class="business-note">暂无操作记录。</p>
          </article>
        </aside>
      </div>
    </AsyncState>

    <Teleport to="body">
      <div v-if="operationsOpen" class="modal-backdrop" @click.self="closeOperations">
        <section class="business-card operations-dialog" role="dialog" aria-modal="true" aria-labelledby="operations-dialog-title">
          <div class="business-heading"><div><h3 id="operations-dialog-title">全部操作记录</h3><p>已读取 {{ allOperations.length }} / {{ operationTotal }} 条。</p></div><button type="button" class="button-link" aria-label="关闭操作记录" @click="closeOperations">关闭</button></div>
          <div class="operations-dialog__body">
            <div v-if="allOperationsError" class="operations-dialog__error"><span>{{ allOperationsError }}</span><button type="button" class="button-link" :disabled="allOperationsLoading" @click="loadAllOperations">重试</button></div>
            <p v-if="allOperationsLoading" class="business-note">正在分批读取完整记录…</p>
            <div v-else-if="allOperations.length" class="operation-stream">
              <div v-for="(row,index) in allOperations" :key="`${row.time}-${index}`"><i/><span><strong>{{ row.operator }}</strong>{{ row.content }}<small>{{ row.time }}</small></span></div>
            </div>
            <p v-else class="business-note">暂无操作记录。</p>
          </div>
        </section>
      </div>
    </Teleport>
  </section>
</template>
