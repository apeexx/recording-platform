<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AsyncState from '../../../components/admin/AsyncState.vue'
import PageActions from '../../../components/admin/PageActions.vue'
import TaskItemEditModal from '../../../components/admin/TaskItemEditModal.vue'
import { taskApi } from '../../../lib/taskApi.js'
import { reportApi } from '../../../lib/reportApi.js'
import { operationId } from '../../../lib/apiUtils.js'
import { statusLabel } from '../../../lib/statusLabels.js'
import { referenceMediaUrl } from '../../../lib/taskMedia.js'
import { useNotifications } from '../../../composables/useNotifications.js'

const route = useRoute(), router = useRouter(), notifications = useNotifications()
const item = ref(null), task = ref(null), operations = ref([]), operationPage = ref(0), operationTotal = ref(0)
const loading = ref(false), loadError = ref(''), editing = ref(false), editBusy = ref(false), loadingMore = ref(false)
const referenceAudioUrl = computed(() => referenceMediaUrl(item.value, 'audio'))
const referenceVideoUrl = computed(() => referenceMediaUrl(item.value, 'video'))
const resultAudioUrl = computed(() => item.value?.currentResult?.audio?.mediaId ? `/api/media/${encodeURIComponent(item.value.currentResult.audio.mediaId)}` : '')

async function load() {
  loading.value = true; loadError.value = ''
  try {
    item.value = await taskApi.item(route.params.itemId)
    const [taskValue, history] = await Promise.all([
      taskApi.get(item.value.taskId), reportApi.itemOperations(item.value.id, { page: 0, size: 20 }),
    ])
    task.value = taskValue; operations.value = history.items || []; operationTotal.value = history.total || 0; operationPage.value = 0
  } catch (error) { loadError.value = error.message }
  finally { loading.value = false }
}
async function loadMore() {
  loadingMore.value = true
  try {
    const next = operationPage.value + 1
    const result = await reportApi.itemOperations(item.value.id, { page: next, size: 20 })
    operations.value.push(...(result.items || [])); operationPage.value = next
  } catch (error) { notifications.error(error.message) }
  finally { loadingMore.value = false }
}
async function saveEdit(values) {
  editBusy.value = true
  try {
    item.value = await taskApi.updateItem(item.value.id, { expectedRevision: item.value.revision, ...values }, operationId('item-edit'))
    editing.value = false; notifications.success('待领取数据已更新'); await load()
  } catch (error) { notifications.error(error.message) }
  finally { editBusy.value = false }
}
async function remove() {
  if (!confirm(`确认永久删除待领取条目 ${item.value.itemCode}？`)) return
  try { await taskApi.deleteItem(item.value.id, item.value.revision, operationId('item-delete')); notifications.success('待领取数据已删除'); router.push(`/admin/tasks/${item.value.taskId}`) }
  catch (error) { notifications.error(error.message) }
}
async function mutate(action) {
  if (!confirm(`确认${{ release: '释放', discard: '废弃', restore: '恢复' }[action]}该条目？`)) return
  try { await taskApi[action](item.value.id, operationId(`item-${action}`), item.value.revision); notifications.success('操作已完成'); await load() }
  catch (error) { notifications.error(error.message) }
}
onMounted(load)
</script>

<template>
  <section class="admin-page">
    <PageActions :title="item?.itemCode || '条目详情'" :description="item ? `${task?.name || ''} · ${statusLabel('item', item.status)}` : ''">
      <button class="button-secondary" @click="router.back()">返回</button>
    </PageActions>
    <AsyncState :loading="loading" :error="loadError" :empty="!item" @retry="load">
      <div class="item-detail-layout">
        <main class="item-detail-main">
          <article class="business-card"><h3>参考源</h3>
            <section class="reference-source-block"><h4>参考文本</h4><div v-if="item.referenceText" class="reference-text">{{ item.referenceText }}</div><p v-else class="business-note">无参考文本</p></section>
            <section class="reference-source-block"><h4>参考音频</h4><audio v-if="referenceAudioUrl" controls :src="referenceAudioUrl"/><p v-else class="business-note">无参考音频</p></section>
            <section class="reference-source-block"><h4>参考视频</h4><video v-if="referenceVideoUrl" controls :src="referenceVideoUrl"/><p v-else class="business-note">无参考视频</p></section>
          </article>
          <article class="business-card"><h3>采集结果</h3><audio v-if="resultAudioUrl" controls :src="resultAudioUrl"/><p v-else class="business-note">无结果音频</p><div v-if="item.currentResult?.text" class="reference-text">{{ item.currentResult.text }}</div><p v-else class="business-note">无结果文本</p></article>
        </main>
        <aside class="item-detail-aside">
          <article class="business-card item-context"><h3>条目信息</h3>
            <dl><dt>状态</dt><dd>{{ statusLabel('item', item.status) }}</dd><dt>采集员</dt><dd>{{ item.collectorName || '-' }}<small>{{ item.collectorId || '未分配' }}</small></dd><dt>审核员</dt><dd>{{ item.reviewerName || '-' }}<small>{{ item.reviewerId || '未分配' }}</small></dd><dt>修订版本</dt><dd>{{ item.revision }}</dd><dt v-if="item.currentDiscard">废弃原因</dt><dd v-if="item.currentDiscard">{{ item.currentDiscard.reason || '-' }}</dd></dl>
            <div class="business-actions">
              <button v-if="item.status === 'AVAILABLE'" class="button-secondary" @click="editing = true">编辑</button>
              <button v-if="item.status === 'AVAILABLE'" class="button-secondary is-danger" @click="remove">删除</button>
              <button v-if="!['AVAILABLE','DISCARDED'].includes(item.status)" class="button-secondary" @click="mutate('release')">释放</button>
              <button v-if="!['AVAILABLE','DISCARDED'].includes(item.status)" class="button-secondary is-danger" @click="mutate('discard')">废弃</button>
              <button v-if="item.status === 'DISCARDED'" class="button-primary" @click="mutate('restore')">恢复</button>
            </div>
          </article>
          <article id="operations" class="business-card"><h3>全部操作记录</h3><div class="operation-stream"><div v-for="(row,index) in operations" :key="`${row.time}-${index}`"><i/><span><strong>{{ row.operator }}</strong>{{ row.content }}<small>{{ row.time }}</small></span></div></div><button v-if="operations.length < operationTotal" class="button-secondary load-more" :disabled="loadingMore" @click="loadMore">{{ loadingMore ? '加载中…' : '加载更多' }}</button><p v-if="!operations.length" class="business-note">暂无操作记录。</p></article>
        </aside>
      </div>
    </AsyncState>
    <TaskItemEditModal :open="editing" :item="item" :reference-types="task?.configuration?.referenceTypes || []" :busy="editBusy" @close="editing = false" @save="saveEdit"/>
  </section>
</template>
