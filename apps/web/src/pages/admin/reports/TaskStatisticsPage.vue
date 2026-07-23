<script setup>
import { computed, onMounted, ref } from 'vue'
import PageActions from '../../../components/admin/PageActions.vue'
import WorkSummaryCards from '../../../components/admin/WorkSummaryCards.vue'
import BaseSelect from '../../../components/form/BaseSelect.vue'
import { reportApi } from '../../../lib/reportApi.js'
import { taskApi } from '../../../lib/taskApi.js'
import { useNotifications } from '../../../composables/useNotifications.js'
const notifications = useNotifications()
const tasks = ref([]), taskId = ref(''), summary = ref(null)
const taskOptions = computed(() => tasks.value.map(task => ({ value: task.id, label: task.name })))
async function init() { try { tasks.value = (await taskApi.list(0, 100)).items || [] } catch (exception) { notifications.error(exception.message) } }
async function search() { if (!taskId.value) { notifications.error('请选择任务'); return } try { summary.value = await reportApi.tasks({ taskId: taskId.value }) } catch (exception) { notifications.error(exception.message) } }
onMounted(init)
</script>
<template><section class="admin-page"><PageActions title="任务统计" description="同时展示累计工作量与当前有效结果。" /><div class="business-card"><form class="business-inline" novalidate @submit.prevent="search"><BaseSelect v-model="taskId" :options="taskOptions" placeholder="选择任务" aria-label="选择任务" /><button class="button-primary">查询</button></form><WorkSummaryCards v-if="summary" :summary="summary" /></div></section></template>
