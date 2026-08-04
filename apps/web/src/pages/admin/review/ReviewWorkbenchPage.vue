<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AsyncState from '../../../components/admin/AsyncState.vue'
import PageActions from '../../../components/admin/PageActions.vue'
import HelpPopover from '../../../components/form/HelpPopover.vue'
import { useAdminSession } from '../../../composables/useAdminSession.js'
import { useNotifications } from '../../../composables/useNotifications.js'
import { operationId } from '../../../lib/apiUtils.js'
import { reviewApi } from '../../../lib/reviewApi.js'
import { taskApi } from '../../../lib/taskApi.js'
import { referenceMediaUrl } from '../../../lib/taskMedia.js'

const route = useRoute()
const router = useRouter()
const session = useAdminSession()
const notifications = useNotifications()
const item = ref(null)
const version = ref(null)
const loading = ref(false)
const loadError = ref('')
const finalAnswer = ref('')
const reasons = ref([])
const note = ref('')
const aiConfig = ref(null)
const aiJobs = ref({ AUDIO_TRANSCRIBE: null, TEXT_REFINE: null })
const pollTimers = new Map()

const isReviewer = computed(() => session.user.value?.role === 'REVIEWER')
const isOwnReviewAssignment = computed(() =>
	Boolean(item.value)
	&& item.value?.reviewerId === (session.user.value?.id || session.user.value?.userId)
  && Boolean(item.value?.reviewAssignmentId),
)
const canDecide = computed(() =>
	item.value?.status === 'REVIEW_PENDING'
	&& Boolean(item.value?.reviewerId)
	&& Boolean(item.value?.reviewAssignmentId)
	&& (session.user.value?.role === 'ADMIN' || isOwnReviewAssignment.value),
)
const originalText = computed(() => item.value?.currentResult?.text || '')
const hasOriginalAudio = computed(() => Boolean(item.value?.currentResult?.audio?.mediaId))
const canUseAi = computed(() => isOwnReviewAssignment.value && item.value?.status === 'REVIEW_PENDING')
const audioUrl = computed(() => item.value?.currentResult?.audio?.mediaId
  ? `/api/media/${encodeURIComponent(item.value.currentResult.audio.mediaId)}`
  : '')
const referenceAudioUrl = computed(() => referenceMediaUrl(item.value, 'audio'))
const referenceVideoUrl = computed(() => referenceMediaUrl(item.value, 'video'))
const reviewQueuePath = computed(() => `/admin/review/tasks/${item.value?.taskId || route.query.taskId || ''}`)

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    item.value = await taskApi.item(route.params.itemId)
    const task = await taskApi.get(item.value.taskId)
    version.value = task.configuration
    finalAnswer.value = item.value.reviewFinalAnswer || item.value.currentResult?.text || ''
    aiConfig.value = await reviewApi.aiConfig(item.value.taskId)
    resumeAiJobs()
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
  if (version.value?.resultType === 'TEXT' && !finalAnswer.value.trim()) {
    notifications.error('文本任务必须填写审核最终答案')
    return
  }
  try {
    await reviewApi.approve(item.value.id, item.value.revision, finalAnswer.value, operationId('review-approve'))
    notifications.success('审核已通过')
    backToQueue()
  } catch (error) {
    notifications.error(error.message)
  }
}

function storageKey(type) {
  return `review-ai-job:${item.value?.id || route.params.itemId}:${type}`
}

function resumeAiJobs() {
  for (const type of ['AUDIO_TRANSCRIBE', 'TEXT_REFINE']) {
    const jobId = window.sessionStorage.getItem(storageKey(type))
    if (jobId) pollAiJob(type, jobId)
  }
}

async function startAi(type) {
  try {
    const job = await reviewApi.createAiJob(item.value.id, {
      type,
      expectedRevision: item.value.revision,
      operationId: operationId(`review-ai-${type.toLowerCase()}`),
    })
    aiJobs.value = { ...aiJobs.value, [type]: job }
    window.sessionStorage.setItem(storageKey(type), job.id)
    pollAiJob(type, job.id)
  } catch (error) {
    notifications.error(error.message)
  }
}

function scheduleAiPoll(type, jobId) {
  window.clearTimeout(pollTimers.get(type))
  pollTimers.set(type, window.setTimeout(() => pollAiJob(type, jobId), 1000))
}

async function pollAiJob(type, jobId) {
  try {
    const job = await reviewApi.aiJob(jobId)
    aiJobs.value = { ...aiJobs.value, [type]: job }
    if (['PENDING', 'PROCESSING'].includes(job.status)) {
      scheduleAiPoll(type, jobId)
      return
    }
    window.sessionStorage.removeItem(storageKey(type))
    if (job.status === 'FAILED') notifications.error(job.failureMessage || 'AI 识别失败')
  } catch (error) {
    window.sessionStorage.removeItem(storageKey(type))
    notifications.error(error.message)
  }
}

function adoptAi(type) {
  const result = aiJobs.value[type]?.resultText || ''
  if (!result) return
  if (finalAnswer.value.trim() && finalAnswer.value.trim() !== result.trim()
    && !window.confirm('当前最终答案已有内容，确认使用 AI 结果覆盖？')) return
  finalAnswer.value = result
  notifications.success('已采用 AI 结果，请确认后再审核通过')
}

function aiBusy(type) {
  return ['PENDING', 'PROCESSING'].includes(aiJobs.value[type]?.status)
}

async function reject() {
  if (!reasons.value.length && !note.value.trim()) {
    notifications.error('至少选择一个驳回原因或填写补充说明')
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
    notifications.error(error.message)
  }
}

async function release() {
  if (!window.confirm('确认释放审核领取？采集结果不会删除。')) return
  try {
    await reviewApi.release(item.value.id, item.value.revision, operationId('review-release'))
    notifications.success('审核领取已释放')
    backToQueue()
  } catch (error) {
    notifications.error(error.message)
  }
}

async function discard() {
  if (!window.confirm('确认将该条数据标记为无效？系统会保留采集结果和操作记录，管理员可在废弃数据中恢复。')) return
  try {
    await reviewApi.discard(item.value.id, item.value.revision, operationId('review-discard'))
    notifications.success('已标记为无效数据')
    backToQueue()
  } catch (error) {
    notifications.error(error.message)
  }
}

onMounted(load)
onBeforeUnmount(() => pollTimers.forEach((timer) => window.clearTimeout(timer)))
</script>

<template>
  <section class="admin-page">
    <PageActions title="审核工作台" description="原始采集结果保持只读，审核最终答案独立保存。">
      <button class="button-secondary" @click="backToQueue">返回审核池</button>
      <button v-if="isOwnReviewAssignment" class="button-secondary" @click="release">释放审核</button>
      <button v-if="canDecide" class="button-secondary is-danger" @click="discard">标记为无效</button>
    </PageActions>
    <AsyncState :loading="loading" :error="loadError" :empty="!item" @retry="backToQueue">
      <div class="review-layout">
        <div class="business-card review-source-column">
          <h3>参考源</h3>
          <section class="reference-source-block">
            <h4>参考文本</h4>
            <div v-if="item.referenceText" class="reference-text">{{ item.referenceText }}</div>
            <p v-else class="business-note">无参考文本</p>
          </section>
          <section class="reference-source-block">
            <h4>参考音频</h4>
            <audio v-if="referenceAudioUrl" controls :src="referenceAudioUrl" />
            <p v-else class="business-note">无参考音频</p>
          </section>
          <section class="reference-source-block">
            <h4>参考视频</h4>
            <div v-if="referenceVideoUrl" class="review-video-stage"><video controls :src="referenceVideoUrl" /></div>
            <p v-else class="business-note">无参考视频</p>
          </section>
        </div>
        <div class="review-decision-column">
          <div class="business-card review-result-card">
          <h3>原始采集结果 <HelpPopover label="原始采集结果说明" content="展示采集员当前提交的文字和录音，是审核判断的原始依据；AI 候选不会覆盖这里的内容。" /></h3>
          <section>
            <h4>音频结果</h4>
            <audio v-if="audioUrl" controls :src="audioUrl" />
            <p v-else class="business-note">本条未提交音频</p>
          </section>
          <section>
            <h4>文本结果</h4>
            <div v-if="originalText" class="review-original-text">{{ originalText }}</div>
            <p v-else class="business-note">本条未提交文本</p>
          </section>
          </div>
          <div class="business-card review-ai-card">
            <h3>AI 辅助审核 <HelpPopover label="AI 候选说明" content="AI 只生成候选转写或修订文字，不会自动通过、驳回或改写原始采集结果。" /></h3>
            <p class="business-note">AI 结果仅作为候选文字，不会自动保存或作出审核结论。</p>
            <div class="review-ai-actions">
              <div v-if="hasOriginalAudio">
                <button v-if="canUseAi && aiConfig?.audio?.enabled" class="button-secondary" :disabled="aiBusy('AUDIO_TRANSCRIBE')" @click="startAi('AUDIO_TRANSCRIBE')">
                  {{ aiBusy('AUDIO_TRANSCRIBE') ? '音频识别中…' : 'AI 音频转文字' }}
                </button>
                <button v-if="aiJobs.AUDIO_TRANSCRIBE?.status === 'COMPLETED'" class="button-link" @click="adoptAi('AUDIO_TRANSCRIBE')">采用结果</button>
                <div v-if="aiJobs.AUDIO_TRANSCRIBE?.resultText" class="review-ai-result">{{ aiJobs.AUDIO_TRANSCRIBE.resultText }}</div>
              </div>
              <div v-if="originalText">
                <button v-if="canUseAi && aiConfig?.text?.enabled" class="button-secondary" :disabled="aiBusy('TEXT_REFINE')" @click="startAi('TEXT_REFINE')">
                  {{ aiBusy('TEXT_REFINE') ? '文本处理中…' : 'AI 文本结果转写' }}
                </button>
                <button v-if="aiJobs.TEXT_REFINE?.status === 'COMPLETED'" class="button-link" @click="adoptAi('TEXT_REFINE')">采用结果</button>
                <div v-if="aiJobs.TEXT_REFINE?.resultText" class="review-ai-result">{{ aiJobs.TEXT_REFINE.resultText }}</div>
              </div>
              <p v-if="!hasOriginalAudio && !originalText" class="business-note">当前没有可供 AI 处理的原始结果。</p>
            </div>
          </div>
          <div class="business-card">
            <h3>审核最终答案 <HelpPopover label="审核最终答案说明" content="审核员可采用或编辑候选文字；审核通过后该内容优先作为最终结果，留空时回退当前采集文字。" /></h3>
            <label>
              最终文本{{ version?.resultType === 'TEXT' ? '（必填）' : '（选填）' }}
              <textarea v-model="finalAnswer" rows="8" placeholder="填写或采用 AI 生成的最终答案" />
            </label>
          </div>
          <div class="business-card">
          <h3>审核结论 <HelpPopover label="审核结论说明" content="通过会进入已完成；驳回会进入待返修并保留原采集员。提交前仍会校验当前修订版本，防止覆盖他人操作。" /></h3>
          <div class="business-check-list">
            <label v-for="reason in version?.rejectionReasons || []" :key="reason">
              <input v-model="reasons" type="checkbox" :value="reason" />{{ reason }}
            </label>
          </div>
          <label>补充说明<textarea v-model="note" rows="4" /></label>
          <div v-if="canDecide" class="business-actions">
            <button class="button-secondary is-danger" @click="reject">驳回到返修队列</button>
            <button class="button-primary" @click="approve">审核通过</button>
          </div>
          <p v-else class="business-note">请先从审核池领取该条目后再处理。</p>
          </div>
        </div>
      </div>
    </AsyncState>
  </section>
</template>

<style scoped>
.review-source-column{position:sticky;top:18px;align-self:start}.review-result-card,.review-ai-card{display:grid;gap:16px}.review-original-text,.review-ai-result{white-space:pre-wrap;line-height:1.75;padding:14px;border:1px solid var(--border);border-radius:var(--radius);background:var(--accent);overflow-wrap:anywhere}.review-ai-actions{display:grid;gap:14px}.review-ai-actions>div{display:grid;gap:9px}.review-video-stage{display:grid;place-items:center;width:100%;min-height:260px;max-height:520px;border-radius:var(--radius);overflow:hidden;background:#111}.review-video-stage video{width:100%;height:100%;max-height:520px;object-fit:contain}@media(max-width:960px){.review-source-column{position:static}.review-video-stage{min-height:200px}}
</style>
