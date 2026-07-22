<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AsyncState from '../../../components/admin/AsyncState.vue'
import PageActions from '../../../components/admin/PageActions.vue'
import { useAdminSession } from '../../../composables/useAdminSession.js'
import { useNotifications } from '../../../composables/useNotifications.js'
import { operationId } from '../../../lib/apiUtils.js'
import { reviewApi } from '../../../lib/reviewApi.js'
import { taskApi } from '../../../lib/taskApi.js'

const route = useRoute()
const router = useRouter()
const session = useAdminSession()
const notifications = useNotifications()
const item = ref(null)
const version = ref(null)
const loading = ref(false)
const loadError = ref('')
const validationError = ref('')
const text = ref('')
const reasons = ref([])
const note = ref('')

const isReviewer = computed(() => session.user.value?.role === 'REVIEWER')
const isOwnReviewAssignment = computed(() =>
	Boolean(item.value)
	&& item.value?.reviewerId === session.user.value?.userId
  && Boolean(item.value?.reviewAssignmentId),
)
const canDecide = computed(() =>
	item.value?.status === 'REVIEW_PENDING'
	&& Boolean(item.value?.reviewerId)
	&& Boolean(item.value?.reviewAssignmentId)
	&& (session.user.value?.role === 'ADMIN' || isOwnReviewAssignment.value),
)
const audioUrl = computed(() => item.value?.currentResult?.audio?.mediaId
  ? `/api/media/${encodeURIComponent(item.value.currentResult.audio.mediaId)}`
  : '')
const reviewQueuePath = computed(() => `/admin/review/tasks/${item.value?.taskId || route.query.taskId || ''}`)

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    item.value = await taskApi.item(route.params.itemId)
    const task = await taskApi.get(item.value.taskId)
    version.value = task.configuration
    text.value = item.value.currentResult?.text || ''
  } catch (error) {
    loadError.value = error.message
  } finally {
    loading.value = false
  }
}

function backToQueue() {
  router.push(reviewQueuePath.value)
}

async function approve() {
  validationError.value = ''
  try {
    await reviewApi.approve(item.value.id, item.value.revision, text.value, operationId('review-approve'))
    notifications.success('审核已通过')
    backToQueue()
  } catch (error) {
    validationError.value = error.message
  }
}

async function reject() {
  validationError.value = ''
  if (!reasons.value.length && !note.value.trim()) {
    validationError.value = '至少选择一个驳回原因或填写补充说明'
    return
  }
  try {
    await reviewApi.reject(
      item.value.id,
      item.value.revision,
      reasons.value,
      note.value,
      operationId('review-reject'),
    )
    notifications.success('已驳回并进入返修队列')
    backToQueue()
  } catch (error) {
    validationError.value = error.message
  }
}

async function release() {
  if (!window.confirm('确认释放审核领取？采集结果不会删除。')) return
  try {
    await reviewApi.release(item.value.id, item.value.revision, operationId('review-release'))
    notifications.success('审核领取已释放')
    backToQueue()
  } catch (error) {
    validationError.value = error.message
  }
}

onMounted(load)
</script>

<template>
  <section class="admin-page">
    <PageActions title="审核工作台" description="对照参考源检查采集结果，可补改文字后通过。">
      <button class="button-secondary" @click="backToQueue">返回审核池</button>
      <button v-if="isOwnReviewAssignment" class="button-secondary" @click="release">释放审核</button>
    </PageActions>
    <AsyncState :loading="loading" :error="loadError" :empty="!item" @retry="backToQueue">
      <div class="review-layout">
        <div class="business-card">
          <h3>参考源</h3>
          <div v-if="item.referenceText" class="reference-text">{{ item.referenceText }}</div>
          <audio v-if="item.referenceAudioMediaId" controls :src="`/api/media/${item.referenceAudioMediaId}`" />
          <video v-if="item.referenceVideoMediaId" controls :src="`/api/media/${item.referenceVideoMediaId}`" />
          <p v-if="!item.referenceText && !item.referenceAudioMediaId && !item.referenceVideoMediaId" class="business-note">无参考源</p>
        </div>
        <div class="business-card">
          <h3>采集结果</h3>
          <audio v-if="audioUrl" controls :src="audioUrl" />
          <p v-else class="business-note">本条未提交音频</p>
          <label v-if="version?.resultType === 'TEXT'">
            文本结果
            <textarea v-model="text" rows="8" placeholder="可由审核员补充或修正" />
          </label>
        </div>
        <div class="business-card">
          <h3>审核结论</h3>
          <div class="business-check-list">
            <label v-for="reason in version?.rejectionReasons || []" :key="reason">
              <input v-model="reasons" type="checkbox" :value="reason" />{{ reason }}
            </label>
          </div>
          <label>补充说明<textarea v-model="note" rows="4" /></label>
          <p v-if="validationError" class="business-error">{{ validationError }}</p>
          <div v-if="canDecide" class="business-actions">
            <button class="button-secondary is-danger" @click="reject">驳回到返修队列</button>
            <button class="button-primary" @click="approve">审核通过</button>
          </div>
          <p v-else class="business-note">请先从审核池领取该条目后再处理。</p>
        </div>
      </div>
    </AsyncState>
  </section>
</template>
