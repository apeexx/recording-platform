<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import AsyncState from '../../../components/admin/AsyncState.vue'
import PageActions from '../../../components/admin/PageActions.vue'
import { reviewApi } from '../../../lib/reviewApi.js'

const rows = ref([])
const loading = ref(false)
const error = ref('')
const slide = ref(0)
const locked = ref(false)
const hovering = ref(false)
const focused = ref(false)
const dragStartX = ref(null)
let timer = null

const totals = computed(() => rows.value.reduce((sum, row) => ({
  pendingCount: sum.pendingCount + Number(row.pendingCount || 0),
  submittedCount: sum.submittedCount + Number(row.submittedCount || 0),
  reviewPendingCount: sum.reviewPendingCount + Number(row.reviewPendingCount || 0),
  todayCompletedCount: sum.todayCompletedCount + Number(row.todayCompletedCount || 0),
  effectiveItemCount: sum.effectiveItemCount + Number(row.effectiveItemCount || 0),
  completedCount: sum.completedCount + Number(row.completedCount || 0),
  reviewEnteredCount: sum.reviewEnteredCount + Number(row.reviewEnteredCount || 0),
  reviewProcessedCount: sum.reviewProcessedCount + Number(row.reviewProcessedCount || 0),
}), {
  pendingCount: 0, submittedCount: 0, reviewPendingCount: 0, todayCompletedCount: 0,
  effectiveItemCount: 0, completedCount: 0, reviewEnteredCount: 0, reviewProcessedCount: 0,
}))
const backlogRows = computed(() => rows.value.filter(row => Number(row.pendingCount || 0) > 0))
const percent = (value, total) => total > 0 ? Math.round(value / total * 100) : 0
const slides = computed(() => [
  {
    title: '任务整体进度',
    value: totals.value.completedCount,
    total: totals.value.effectiveItemCount,
    description: '已完成条目 / 当前有效条目',
  },
  {
    title: '审核处理进度',
    value: totals.value.reviewProcessedCount,
    total: totals.value.reviewEnteredCount,
    description: '已处理审核 / 已进入审核流程',
  },
])

function clearTimer() {
  if (timer) window.clearInterval(timer)
  timer = null
}
function startTimer() {
  clearTimer()
  if (locked.value || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
  timer = window.setInterval(() => {
    if (!hovering.value && !focused.value) slide.value = (slide.value + 1) % slides.value.length
  }, 6000)
}
function showSlide(index, manual = false) {
  slide.value = (index + slides.value.length) % slides.value.length
  if (manual) {
    locked.value = true
    clearTimer()
  }
}
function beginDrag(event) {
  dragStartX.value = event.clientX
}
function endDrag(event) {
  if (dragStartX.value == null) return
  const distance = event.clientX - dragStartX.value
  dragStartX.value = null
  if (Math.abs(distance) >= 48) showSlide(slide.value + (distance < 0 ? 1 : -1), true)
}
async function load() {
  loading.value = true
  error.value = ''
  try { rows.value = await reviewApi.tasks(true) } catch (exception) { error.value = exception.message } finally { loading.value = false }
}
onMounted(async () => { await load(); startTimer() })
onBeforeUnmount(clearTimer)
</script>

<template>
  <section class="admin-page review-entry-page">
    <PageActions title="录音审核" description="集中查看任务进度、审核积压，并进入对应任务的审核池。">
      <button class="button-secondary" @click="load">刷新</button>
    </PageActions>
    <AsyncState :loading="loading" :error="error" :empty="false" @retry="load">
      <section class="review-entry-overview">
        <section class="review-progress-carousel business-card"
          aria-roledescription="轮播图" tabindex="0"
          @mouseenter="hovering = true" @mouseleave="hovering = false"
          @focusin="focused = true" @focusout="focused = false"
          @pointerdown="beginDrag" @pointerup="endDrag">
          <button type="button" aria-label="上一项" @click="showSlide(slide - 1, true)">‹</button>
          <div class="review-progress-slide">
            <span>{{ slides[slide].title }}</span>
            <strong>{{ percent(slides[slide].value, slides[slide].total) }}%</strong>
            <p>{{ slides[slide].description }} · {{ slides[slide].value }} / {{ slides[slide].total }}</p>
            <div><i :style="{ width: `${percent(slides[slide].value, slides[slide].total)}%` }"></i></div>
          </div>
          <button type="button" aria-label="下一项" @click="showSlide(slide + 1, true)">›</button>
          <div class="review-carousel-dots">
            <button v-for="(_, index) in slides" :key="index" type="button"
              :class="{ 'is-active': slide === index }" :aria-label="`显示第 ${index + 1} 项`"
              @click="showSlide(index, true)"></button>
          </div>
        </section>

        <section class="review-overview-grid" aria-label="审核概览">
          <article><span>当前积压</span><strong>{{ totals.pendingCount }}</strong><small>等待处理的全部条目</small></article>
          <article><span>待领取</span><strong>{{ totals.submittedCount }}</strong><small>尚未进入审核中的条目</small></article>
          <article><span>审核中</span><strong>{{ totals.reviewPendingCount }}</strong><small>已被领取的审核条目</small></article>
          <article><span>今日完成</span><strong>{{ totals.todayCompletedCount }}</strong><small>今日首次完成的条目</small></article>
        </section>
      </section>

      <section class="review-task-grid">
        <router-link v-for="row in backlogRows" :key="row.taskId" class="business-card review-task-card" :to="`/admin/review/tasks/${row.taskId}`">
          <div class="review-task-header">
            <div class="review-task-identity">
              <span>{{ row.taskCode }}</span>
              <h3 :title="row.taskName">{{ row.taskName }}</h3>
            </div>
            <b>{{ row.pendingCount }} 条积压</b>
          </div>
          <div class="review-task-progress-grid">
            <section class="review-task-progress">
              <div><span>任务完成度</span><b>{{ percent(row.completedCount, row.effectiveItemCount) }}%</b></div>
              <small>{{ row.completedCount }} / {{ row.effectiveItemCount }}</small>
              <progress :value="row.completedCount" :max="row.effectiveItemCount || 1"></progress>
            </section>
            <section class="review-task-progress">
              <div><span>审核处理进度</span><b>{{ percent(row.reviewProcessedCount, row.reviewEnteredCount) }}%</b></div>
              <small>{{ row.reviewProcessedCount }} / {{ row.reviewEnteredCount }}</small>
              <progress :value="row.reviewProcessedCount" :max="row.reviewEnteredCount || 1"></progress>
            </section>
          </div>
          <small>待领取 {{ row.submittedCount }} · 审核中 {{ row.reviewPendingCount }} · 今日完成 {{ row.todayCompletedCount }}</small>
          <strong>进入审核池 →</strong>
        </router-link>
        <article v-if="!backlogRows.length" class="business-card review-complete-state">
          <strong>当前没有待审核任务</strong>
          <p>所有审核积压已处理完成</p>
        </article>
      </section>
    </AsyncState>
  </section>
</template>

<style scoped>
.review-entry-page{display:grid;gap:18px}.review-entry-overview{display:grid;grid-template-columns:minmax(0,3fr) minmax(360px,2fr);gap:16px;align-items:stretch}.review-progress-carousel{position:relative;display:grid;grid-template-columns:44px 1fr 44px;align-items:center;gap:16px;min-height:224px;overflow:hidden;background:linear-gradient(135deg,color-mix(in srgb,var(--primary) 13%,var(--card)),var(--card))}.review-progress-carousel>button{width:40px;height:40px;border:1px solid var(--border);border-radius:50%;background:var(--card);color:var(--foreground);font-size:24px;cursor:pointer}.review-progress-slide{display:grid;gap:8px;text-align:center}.review-progress-slide>span{color:var(--muted-foreground)}.review-progress-slide>strong{font-size:42px;color:var(--primary)}.review-progress-slide p{margin:0;color:var(--muted-foreground)}.review-progress-slide>div{height:10px;overflow:hidden;border-radius:999px;background:var(--accent)}.review-progress-slide i{display:block;height:100%;border-radius:inherit;background:var(--primary);transition:width .3s ease}.review-carousel-dots{position:absolute;right:18px;bottom:14px;display:flex;gap:7px}.review-carousel-dots button{width:8px;height:8px;padding:0;border:0;border-radius:50%;background:var(--border);cursor:pointer}.review-carousel-dots button.is-active{width:22px;border-radius:999px;background:var(--primary)}.review-overview-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}.review-overview-grid article{display:grid;align-content:center;gap:6px;min-height:0;padding:18px;border:1px solid color-mix(in srgb,var(--primary) 16%,var(--border));border-radius:var(--radius);background:linear-gradient(145deg,var(--card),color-mix(in srgb,var(--primary) 5%,var(--card)))}.review-overview-grid span,.review-overview-grid small{color:var(--muted-foreground)}.review-overview-grid small{line-height:1.4}.review-overview-grid strong{font-size:30px;line-height:1.1}.review-task-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px}.review-task-card{display:grid;gap:14px;text-decoration:none;color:inherit}.review-task-header{display:flex;align-items:center;justify-content:space-between;gap:14px}.review-task-header>b{flex:0 0 auto}.review-task-identity{display:flex;align-items:center;gap:12px;min-width:0}.review-task-identity>span{flex:0 0 auto;color:var(--muted-foreground)}.review-task-identity h3{min-width:0;margin:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.review-task-progress-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}.review-task-progress{display:grid;gap:6px;min-width:0}.review-task-progress>div{display:flex;align-items:center;justify-content:space-between;gap:8px}.review-task-progress span,.review-task-progress small,.review-task-card>small{color:var(--muted-foreground)}.review-task-progress progress{width:100%;accent-color:var(--primary)}.review-task-card>strong{color:var(--primary)}.review-complete-state{grid-column:1/-1;display:grid;place-items:center;gap:8px;min-height:150px;text-align:center}.review-complete-state p{margin:0;color:var(--muted-foreground)}@media(prefers-reduced-motion:reduce){.review-progress-slide i{transition:none}}@media(max-width:1080px){.review-entry-overview{grid-template-columns:1fr}.review-progress-carousel{min-height:210px}.review-task-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:720px){.review-task-grid{grid-template-columns:1fr}.review-progress-carousel{grid-template-columns:36px 1fr 36px;min-height:200px;padding:18px 12px}.review-progress-slide>strong{font-size:34px}}@media(max-width:460px){.review-overview-grid,.review-task-progress-grid{grid-template-columns:1fr}.review-overview-grid article{min-height:116px}.review-task-header{align-items:flex-start}}
</style>
