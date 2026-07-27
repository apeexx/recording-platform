<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import PageActions from '../../../components/admin/PageActions.vue'
import WorkSummaryCards from '../../../components/admin/WorkSummaryCards.vue'
import UserSearchSelect from '../../../components/form/UserSearchSelect.vue'
import TaskSearchSelect from '../../../components/form/TaskSearchSelect.vue'
import { reportApi } from '../../../lib/reportApi.js'
import { useNotifications } from '../../../composables/useNotifications.js'

const route = useRoute(), notifications = useNotifications()
const userId = ref(String(route.query.userId || '')), taskId = ref(String(route.query.taskId || ''))
const fromDate = ref(String(route.query.fromDate || '')), toDate = ref(String(route.query.toDate || ''))
const summary = ref(null), loading = ref(false)
async function init() {
  if (userId.value) await search()
}
async function search() {
  if (!userId.value) { notifications.error('请选择采集员'); return }
  if (fromDate.value && toDate.value && fromDate.value > toDate.value) {
    notifications.error('开始日期不能晚于结束日期'); return
  }
  loading.value = true
  try {
    summary.value = await reportApi.collectors({
      userId: userId.value, taskId: taskId.value, fromDate: fromDate.value, toDate: toDate.value,
    })
  } catch (error) { notifications.error(error.message) }
  finally { loading.value = false }
}
onMounted(init)
</script>

<template>
  <section class="admin-page">
    <PageActions title="采集员统计" description="按采集员、任务和日期范围查看当前有效工作量。" />
    <div class="business-card">
      <form class="business-inline report-filter" novalidate @submit.prevent="search">
        <UserSearchSelect v-model="userId" role="COLLECTOR" user-type="MINIPROGRAM" placeholder="姓名、用户 ID 或登录账号" />
        <TaskSearchSelect v-model="taskId" allow-empty />
        <label>开始日期 <input v-model="fromDate" type="date" /></label>
        <label>结束日期 <input v-model="toDate" type="date" /></label>
        <button class="button-primary" :disabled="loading">{{loading?'查询中':'查询'}}</button>
      </form>
      <WorkSummaryCards v-if="summary" :summary="summary" />
      <p v-else class="business-note">选择采集员后查看工作量、最终结果和参考来源统计。</p>
    </div>
  </section>
</template>
