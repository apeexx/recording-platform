<script setup>
import { computed, onMounted, ref } from 'vue'
import PageActions from '../../../components/admin/PageActions.vue'
import UserSearchSelect from '../../../components/form/UserSearchSelect.vue'
import TaskSearchSelect from '../../../components/form/TaskSearchSelect.vue'
import { reportApi } from '../../../lib/reportApi.js'
import { useAdminSession } from '../../../composables/useAdminSession.js'
import { useNotifications } from '../../../composables/useNotifications.js'

const session = useAdminSession(), notifications = useNotifications()
const userId = ref(''), taskId = ref(''), fromDate = ref(''), toDate = ref('')
const data = ref(null), loading = ref(false)
const isAdmin = computed(() => session.user.value?.role === 'ADMIN')
async function init() {
  if (!isAdmin.value) {
    userId.value = session.user.value?.id || session.user.value?.userId || ''
    await load()
  }
}
async function load() {
  if (!userId.value) { notifications.error('请选择审核员'); return }
  if (fromDate.value && toDate.value && fromDate.value > toDate.value) {
    notifications.error('开始日期不能晚于结束日期'); return
  }
  loading.value = true
  try {
    data.value = await reportApi.reviewers({
      userId: userId.value, taskId: taskId.value, fromDate: fromDate.value, toDate: toDate.value,
    })
  } catch (error) { notifications.error(error.message) }
  finally { loading.value = false }
}
onMounted(init)
</script>

<template>
  <section class="admin-page">
    <PageActions title="审核统计" description="按审核员、任务和日期范围查看领取、决定及处理时长。" />
    <div class="business-card">
      <form class="business-inline report-filter" novalidate @submit.prevent="load">
        <UserSearchSelect v-if="isAdmin" v-model="userId" role="REVIEWER" user-type="WEB" placeholder="姓名、用户 ID 或登录账号" />
        <TaskSearchSelect v-model="taskId" allow-empty />
        <label>开始日期 <input v-model="fromDate" type="date" /></label>
        <label>结束日期 <input v-model="toDate" type="date" /></label>
        <button class="button-primary" :disabled="loading">{{loading?'查询中':'查询'}}</button>
      </form>
      <div v-if="data" class="summary-grid">
        <div><span>领取</span><strong>{{data.claimCount||0}}</strong></div>
        <div><span>释放</span><strong>{{data.releaseCount||0}}</strong></div>
        <div><span>通过</span><strong>{{data.approveCount||0}}</strong></div>
        <div><span>驳回</span><strong>{{data.rejectCount||0}}</strong></div>
        <div><span>平均处理</span><strong>{{Math.round((data.averageProcessingMillis||0)/1000)}} 秒</strong></div>
      </div>
      <p v-else class="business-note">选择审核员查看统计。</p>
    </div>
  </section>
</template>
