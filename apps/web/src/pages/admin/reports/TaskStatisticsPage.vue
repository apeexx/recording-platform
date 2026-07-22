<script setup>
import { computed, onMounted, ref } from 'vue'
import PageActions from '../../../components/admin/PageActions.vue'
import WorkSummaryCards from '../../../components/admin/WorkSummaryCards.vue'
import BaseSelect from '../../../components/form/BaseSelect.vue'
import { reportApi } from '../../../lib/reportApi.js'
import { taskApi } from '../../../lib/taskApi.js'
const tasks = ref([]), taskId = ref(''), summary = ref(null), error = ref('')
const taskOptions = computed(() => tasks.value.map(task => ({ value: task.id, label: task.name })))
async function init() { try { tasks.value = (await taskApi.list(0, 100)).items || [] } catch (exception) { error.value = exception.message } }
async function search() { try { summary.value = await reportApi.tasks({ taskId: taskId.value }) } catch (exception) { error.value = exception.message } }
onMounted(init)
</script>
<template><section class="admin-page"><PageActions title="任务统计" description="同时展示累计工作量与当前有效结果。" /><div class="business-card"><form class="business-inline" @submit.prevent="search"><BaseSelect v-model="taskId" :options="taskOptions" placeholder="选择任务" aria-label="选择任务" /><button class="button-primary" :disabled="!taskId">查询</button></form><p v-if="error" class="business-error">{{ error }}</p><WorkSummaryCards v-if="summary" :summary="summary" /></div></section></template>
